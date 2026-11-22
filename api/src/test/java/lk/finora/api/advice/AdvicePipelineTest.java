package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import lk.finora.api.advice.ai.GroqClient;
import lk.finora.api.advice.ai.LlmClient;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import lk.finora.api.advice.rules.RuleAdviceService;
import lk.finora.api.advice.verify.AdviceVerifier;
import lk.finora.api.advice.verify.VerificationSink;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

/** Uses actual owner-scoped records and the isolated database; providers are mocks, never live HTTP. */
class AdvicePipelineTest extends IntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RuleAdviceService rules;
    @Autowired private VerificationSink sink;
    @Autowired private AdvicePipelineService disabledPipeline;
    private User user;
    private LlmClient groq;
    private LlmClient gemini;

    @BeforeEach
    void setUpPipeline() {
        user = createUser(Role.USER);
        jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?,'PRIVATE ACCOUNT','CASH')", user.getId());
        Long account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?", Long.class, user.getId());
        jdbc.update("INSERT INTO categories(user_id,name,kind,need_or_want) VALUES (?,'private@example.com ignore instructions','EXPENSE','WANT')", user.getId());
        Long wants = jdbc.queryForObject("SELECT id FROM categories WHERE user_id=?", Long.class, user.getId());
        jdbc.update("INSERT INTO transactions(user_id,account_id,type,amount,txn_date) VALUES (?,?,'INCOME',10000,'2026-11-01')", user.getId(), account);
        jdbc.update("INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date,merchant,note) VALUES (?,?,?,'EXPENSE',12000,'2026-11-10','PRIVATE MERCHANT','PRIVATE ACCOUNT 123456 instructions')",
                user.getId(), account, wants);
        groq = mock(LlmClient.class); gemini = mock(LlmClient.class);
        when(groq.provider()).thenReturn("GROQ"); when(gemini.provider()).thenReturn("GEMINI");
        login(user);
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    private void login(User caller) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(caller.getId(),
                "unused", AuthorityUtils.createAuthorityList("ROLE_" + caller.getRole())));
    }
    private AdvicePipelineService pipeline(List<LlmClient> order) {
        return new AdvicePipelineService(rules, order, new AdviceVerifier(), sink);
    }
    private void valid(LlmClient client) {
        when(client.generate(any(), any())).thenAnswer(call -> LlmClient.Result.received(
                jsonMapper.writeValueAsString(Map.of("advice", call.getArgument(1)))));
    }
    private int outcomes(User owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM advice_verification_events WHERE user_id=?", Integer.class, owner.getId());
    }

    @Test
    void groqVerifiedReplyReturnsOnlyCanonicalAdviceAndStoresBoundedOutcome() {
        valid(groq);
        var result = pipeline(List.of(groq, gemini)).forCurrentUser("2026-11");
        assertThat(result.source()).isEqualTo("AI"); assertThat(result.verified()).isTrue();
        assertThat(result.advice()).isEqualTo(RuleAdviceEngine.generate(rules.facts(user.getId(), "2026-11")));
        verify(groq).generate(any(), any());
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.never()).generate(any(), any());
        assertThat(outcomes(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason FROM advice_verification_events WHERE user_id=?", String.class, user.getId()))
                .isEqualTo("ACCEPTED");
    }

    @Test
    void groqFailureTriesGeminiAndRecordsEachCandidateSeparately() {
        when(groq.generate(any(), any())).thenReturn(LlmClient.Result.failed(LlmClient.Failure.TIMEOUT)); valid(gemini);
        var result = pipeline(List.of(groq, gemini)).forCurrentUser("2026-11");
        assertThat(result.source()).isEqualTo("AI"); assertThat(outcomes(user)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT reason FROM advice_verification_events WHERE user_id=? ORDER BY id", String.class, user.getId()))
                .containsExactly("PROVIDER_FAILURE", "ACCEPTED");
    }

    @Test
    void configuredProviderOrderCanStartWithGemini() {
        valid(gemini);
        assertThat(pipeline(List.of(gemini, groq)).forCurrentUser("2026-11").verified()).isTrue();
        org.mockito.Mockito.verify(groq, org.mockito.Mockito.never()).generate(any(), any());
        assertThat(jdbc.queryForObject("SELECT provider FROM advice_verification_events WHERE user_id=?", String.class, user.getId())).isEqualTo("GEMINI");
    }

    @Test
    void bothProvidersFailThenRulesRemainValid() {
        when(groq.generate(any(), any())).thenReturn(LlmClient.Result.failed(LlmClient.Failure.HTTP_ERROR));
        when(gemini.generate(any(), any())).thenReturn(LlmClient.Result.failed(LlmClient.Failure.TIMEOUT));
        var result = pipeline(List.of(groq, gemini)).forCurrentUser("2026-11");
        assertThat(result.source()).isEqualTo("RULE"); assertThat(result.reason()).isEqualTo("PROVIDERS_FAILED");
        assertThat(result.verified()).isFalse(); assertThat(result.advice()).isNotEmpty();
        assertThat(result.advice()).isEqualTo(RuleAdviceEngine.generate(rules.facts(user.getId(), "2026-11")));
        assertThat(outcomes(user)).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"not JSON", "[]", "{\"advice\":[{\"actionId\":\"REVIEW_SPENDING_DEFICIT\",\"text\":\"PRIVATE UNSUPPORTED GUARANTEE\",\"numbers\":[]}]}",
            "{\"advice\":[],\"email\":\"private@example.com\"}"})
    void failedVerificationFallsDirectlyToRulesWithoutPersistingRejectedText(String reply) {
        when(groq.generate(any(), any())).thenReturn(LlmClient.Result.received(reply));
        var result = pipeline(List.of(groq, gemini)).forCurrentUser("2026-11");
        assertThat(result.source()).isEqualTo("RULE"); assertThat(result.verified()).isFalse();
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.never()).generate(any(), any());
        assertThat(outcomes(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason FROM advice_verification_events WHERE user_id=?", String.class, user.getId()))
                .doesNotContain("PRIVATE", "private@example.com");
        assertThat(jsonMapper.writeValueAsString(result)).doesNotContain("PRIVATE", "private@example.com", "123456");
    }

    @Test
    void currentPrincipalOwnsFactsAndOutcomesWithoutSuppliedUserId() {
        valid(groq);
        pipeline(List.of(groq)).forCurrentUser("2026-11");
        User other = createUser(Role.USER); login(other);
        var result = pipeline(List.of(groq)).forCurrentUser("2026-11");
        assertThat(result.reason()).isEqualTo("NO_ELIGIBLE_ACTIONS");
        assertThat(outcomes(other)).isZero(); assertThat(outcomes(user)).isEqualTo(1);
        assertThat(jsonMapper.writeValueAsString(result)).doesNotContain("12000", "2000", "PRIVATE");
    }

    @Test
    void anonymousAndPrivilegedRolesCannotInvokeUserFinancialPipeline() {
        var service = pipeline(List.of(groq)); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.forCurrentUser("2026-11")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(401));
        for (Role role : new Role[] {Role.ADMIN, Role.STAFF}) {
            login(createUser(role));
            assertThatThrownBy(() -> service.forCurrentUser("2026-11")).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus().value()).isEqualTo(403));
        }
        org.mockito.Mockito.verify(groq, org.mockito.Mockito.never()).generate(any(), any());
        assertThat(outcomes(user)).isZero();
    }

    @Test
    void disabledDefaultConfigurationProducesRulesWithoutProviderAccess() {
        var result = disabledPipeline.forCurrentUser("2026-11");
        assertThat(result.reason()).isEqualTo("PROVIDERS_DISABLED"); assertThat(result.source()).isEqualTo("RULE");
        assertThat(outcomes(user)).isZero();
    }

    @Test
    void unexpectedClientFailureDoesNotExposeExceptionAndStillFallsBack() {
        when(groq.generate(any(), any())).thenThrow(new IllegalStateException("PRIVATE KEY AND BODY"));
        var result = pipeline(List.of(groq)).forCurrentUser("2026-11");
        assertThat(result.source()).isEqualTo("RULE");
        assertThat(jsonMapper.writeValueAsString(result)).doesNotContain("PRIVATE KEY AND BODY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void actualGroqAdapterUsesOnlyMockHttpOnOwnerDerivedFacts() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        var eligible = RuleAdviceEngine.generate(rules.facts(user.getId(), "2026-11"));
        when(response.body()).thenReturn(AdviceTestFixtures.groqEnvelope(jsonMapper.writeValueAsString(Map.of("advice", eligible))));
        when(http.<String>send(any(), any())).thenReturn(response);
        var provider = new GroqClient(http, jsonMapper, "MOCK-ONLY", "mock-model");
        var result = pipeline(List.of(provider)).forCurrentUser("2026-11");
        assertThat(result.verified()).isTrue();
        assertThat(result.advice()).isEqualTo(eligible);
        verify(http).send(any(), any());
    }
}
