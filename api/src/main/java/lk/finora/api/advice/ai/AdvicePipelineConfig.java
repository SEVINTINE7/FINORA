package lk.finora.api.advice.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import lk.finora.api.advice.AdvicePipelineService;
import lk.finora.api.advice.rules.RuleAdviceService;
import lk.finora.api.advice.verify.AdviceVerifier;
import lk.finora.api.advice.verify.VerificationSink;
import tools.jackson.databind.json.JsonMapper;

/** Disabled by default: no credentials are read and no HTTP client is constructed until explicitly enabled. */
@Configuration
public class AdvicePipelineConfig {
    @Bean
    AdvicePipelineService advicePipelineService(Environment env, RuleAdviceService rules, VerificationSink sink, JsonMapper mapper) {
        List<LlmClient> providers = new ArrayList<>();
        if (Boolean.parseBoolean(env.getProperty("finora.llm.enabled", "false"))) {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            for (String name : env.getProperty("finora.llm.provider-order", "GROQ,GEMINI").split(",")) {
                switch (name.trim()) {
                    case "GROQ" -> providers.add(new GroqClient(http, mapper,
                            env.getProperty("finora.llm.groq-api-key", ""), env.getProperty("finora.llm.groq-model", "")));
                    case "GEMINI" -> providers.add(new GeminiClient(http, mapper,
                            env.getProperty("finora.llm.gemini-api-key", ""), env.getProperty("finora.llm.gemini-model", "")));
                    default -> throw new IllegalArgumentException("Unknown configured provider");
                }
            }
        }
        return new AdvicePipelineService(rules, providers, new AdviceVerifier(), sink);
    }
}
