package lk.finora.api.support;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import lk.finora.api.ApiApplication;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Explicit test-classpath launcher for synthetic SMS wire evidence. */
public final class SmsPrivacyCapture {
    private static final String BATCH = "/api/v1/transactions/batch";
    private static final String DIAGNOSTIC = "/api/v1/support/sms-diagnostics";
    private static final JsonMapper JSON = new JsonMapper();

    public static void main(String[] args) {
        if (!"true".equals(System.getenv("FINORA_SMS_CAPTURE"))) {
            throw new IllegalStateException("Explicit synthetic capture opt-in required");
        }
        var target = new PersistentEvaluationDatasetLoader.Target(3317,
                java.util.UUID.fromString(System.getenv("FINORA_T55_DB_UUID")),
                System.getenv("FINORA_T55_DB_DATADIR"), "finora_t55_loader@127.0.0.1");
        new PersistentEvaluationDatasetLoader(new DriverManagerDataSource(target.url(), "finora_t55_loader",
                System.getenv("FINORA_T55_LOADER_PASSWORD"))).preflight(target);
        if (!target.url().equals(System.getenv("DB_URL")) || !"finora_t55_api".equals(System.getenv("DB_USER"))) {
            throw new IllegalStateException("Unexpected evaluation API data source");
        }
        new SpringApplicationBuilder(ApiApplication.class, CaptureConfiguration.class).run(
                "--server.address=127.0.0.1", "--server.port=8080", "--finora.llm.enabled=false",
                "--finora.llm.groq-api-key=", "--finora.llm.gemini-api-key=");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfiguration {
        @Bean
        FilterRegistrationBean<Filter> smsWireCapture() {
            var capture = new Capture();
            var registration = new FilterRegistrationBean<Filter>(capture::handle);
            registration.addUrlPatterns(BATCH, DIAGNOSTIC);
            return registration;
        }
    }

    private static final class Capture {
        private byte[] firstBatch;
        private int sequence;

        synchronized void handle(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response,
                jakarta.servlet.FilterChain chain) throws IOException, ServletException {
            var http = (HttpServletRequest) request;
            if (!"POST".equals(http.getMethod())) {
                chain.doFilter(request, response);
                return;
            }
            var input = new ContentCachingRequestWrapper(http, 32_768);
            var output = new ContentCachingResponseWrapper((HttpServletResponse) response);
            chain.doFilter(input, output);
            int status = output.getStatus();
            byte[] body = input.getContentAsByteArray();
            String path = http.getRequestURI();
            boolean batch = BATCH.equals(path);
            boolean markers = containsMarker(new String(body, StandardCharsets.UTF_8));
            try {
                var json = JSON.readTree(body);
                String result = batch ? batchVerdict(json) : diagnosticVerdict(json);
                boolean same = batch && firstBatch != null && Arrays.equals(firstBatch, body);
                boolean interrupt = batch && firstBatch == null && status >= 200 && status < 300
                        && !markers && result.contains("approved=true");
                if (interrupt) firstBatch = body;
                String responseVerdict = batch && status >= 200 && status < 300
                        ? statuses(JSON.readTree(output.getContentAsByteArray())) : "n/a";
                System.out.printf("SMS_WIRE count=%d method=POST path=%s status=%d delivered=%d rawMarker=%s "
                        + "identicalFirstBatch=%s response=%s %s%n", ++sequence, path, status,
                        interrupt ? 503 : status, markers, same, responseVerdict, result);
                if (interrupt) {
                    output.resetBuffer();
                    output.setStatus(503);
                }
            } catch (RuntimeException invalid) {
                System.out.printf("SMS_WIRE count=%d method=POST path=%s status=%d invalid=true%n", ++sequence, path, status);
            }
            output.copyBodyToResponse();
        }

        private static String batchVerdict(JsonNode root) {
            var items = root.path("items");
            boolean good = keys(root).equals(Set.of("items")) && items.isArray() && !items.isEmpty();
            var summaries = new java.util.ArrayList<String>();
            for (JsonNode item : items) {
                var txn = item.path("transaction");
                var fields = keys(txn);
                boolean itemApproved = keys(item).equals(Set.of("dedupHash", "transaction"))
                        && item.path("dedupHash").asText().matches("[a-f0-9]{64}")
                        && Set.of("accountId", "categoryId", "type", "amount", "txnDate", "merchant", "source").containsAll(fields)
                        && fields.containsAll(Set.of("accountId", "type", "amount", "txnDate", "source"))
                        && txn.path("accountId").asLong() > 0 && expected(txn);
                good &= itemApproved;
                // Emit only enumerated fields, even when an unexpected field is present.
                var approvedFields = new TreeSet<>(fields);
                approvedFields.retainAll(Set.of("accountId", "categoryId", "type", "amount", "txnDate", "merchant", "source"));
                summaries.add("transactionFields=" + approvedFields + " sample=" + sample(txn)
                        + " source=" + ("PASTE".equals(txn.path("source").asText()) ? "PASTE" : "OTHER"));
            }
            return "approved=" + good + " root=items itemFields=dedupHash,transaction items=" + items.size() + " " + summaries;
        }

        private static String diagnosticVerdict(JsonNode root) {
            boolean good = keys(root).equals(Set.of("bankCode", "formatVersion", "errorCode"))
                    && "FORMAT_UNRECOGNIZED".equals(root.path("errorCode").asText());
            return "approved=" + good + " diagnosticExpectedFields=bankCode,formatVersion,errorCode";
        }

        private static boolean expected(JsonNode txn) {
            String sample = sample(txn);
            return !"UNKNOWN".equals(sample)
                    && Set.of("SMS", "PASTE").contains(txn.path("source").asText())
                    && !txn.hasNonNull("categoryId");
        }

        private static String sample(JsonNode txn) {
            if (!txn.path("amount").isNumber()) return "UNKNOWN";
            var amount = txn.path("amount").decimalValue();
            String date = txn.path("txnDate").asText();
            String type = txn.path("type").asText();
            if (amount.compareTo(new BigDecimal("1250.75")) == 0 && "2026-11-21".equals(date)
                    && "EXPENSE".equals(type) && "TEST SUPERMARKET".equals(txn.path("merchant").asText())) return "POS";
            if (amount.compareTo(new BigDecimal("4500.25")) == 0 && "2026-11-21".equals(date)
                    && "EXPENSE".equals(type) && !txn.hasNonNull("merchant")) return "OUT";
            if (amount.compareTo(new BigDecimal("750.50")) == 0 && "2026-11-22".equals(date)
                    && "INCOME".equals(type) && !txn.hasNonNull("merchant")) return "IN";
            return "UNKNOWN";
        }

        private static Set<String> keys(JsonNode node) {
            return node.isObject() ? Set.copyOf(node.propertyNames()) : Set.of();
        }

        private static boolean containsMarker(String body) {
            return List.of("MR TEST USER", "XXXXXXXX0000", "AvlBal", "Call Centre", "T55_DIAGNOSTIC_RAW_MARKER")
                    .stream().anyMatch(body::contains);
        }

        private static String statuses(JsonNode response) {
            var result = new java.util.ArrayList<String>();
            for (JsonNode item : response.path("items")) {
                String value = item.path("status").asText();
                result.add(Set.of("SAVED", "DUPLICATE").contains(value) ? value : "OTHER");
            }
            return result.toString();
        }
    }
}
