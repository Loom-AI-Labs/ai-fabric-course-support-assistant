package dev.aifabric.course.support.testsupport;

import ai.fabric.dto.AIEmbeddingRequest;
import ai.fabric.dto.AIEmbeddingResponse;
import ai.fabric.dto.AIChatMessage;
import ai.fabric.dto.AIGenerationRequest;
import ai.fabric.dto.AIGenerationResponse;
import ai.fabric.embedding.EmbeddingProvider;
import ai.fabric.provider.AIProvider;
import ai.fabric.provider.ProviderConfig;
import ai.fabric.provider.ProviderStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class CourseTestAIConfiguration {

    @Bean
    EmbeddingProvider courseTestEmbeddingProvider() {
        return new CourseTestEmbeddingProvider();
    }

    @Bean
    CourseTestGenerationProvider courseTestGenerationProvider() {
        return new CourseTestGenerationProvider();
    }

    /**
     * An explicitly test-only generation fixture. It never exists in a runtime application profile.
     */
    public static final class CourseTestGenerationProvider implements AIProvider {

        private final AtomicInteger generationCalls = new AtomicInteger();
        private final AtomicBoolean failNext = new AtomicBoolean();
        private volatile String lastPrompt;
        private volatile List<AIChatMessage> lastMessages = List.of();
        private volatile String response = """
            {"answer":"Wait fifteen minutes, then use account recovery to verify your registered email and reset access.","citationIds":["policy-account-lockout-01"]}
            """;

        @Override
        public String getProviderName() {
            return "course-test";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public AIGenerationResponse generateContent(AIGenerationRequest request) {
            generationCalls.incrementAndGet();
            lastPrompt = (request != null ? request.getSystemPrompt() : "") + "\n"
                + (request != null ? request.getContext() : "") + "\n"
                + (request != null ? request.getPrompt() : "");
            lastMessages = request != null && request.getMessages() != null
                ? request.getMessages().stream()
                    .map(message -> new AIChatMessage(message.getRole(), message.getContent()))
                    .toList()
                : List.of();
            if (failNext.getAndSet(false)) {
                throw new IllegalStateException("deliberate test provider failure");
            }
            return AIGenerationResponse.builder()
                .requestId("course-test-generation-request")
                .entityId(request != null ? request.getEntityId() : null)
                .entityType(request != null ? request.getEntityType() : null)
                .generationType(request != null ? request.getGenerationType() : null)
                .content(response)
                .model("course-test-generation")
                .tokensUsed(0)
                .processingTimeMs(0L)
                .generatedAt(LocalDateTime.now())
                .status("SUCCESS")
                .build();
        }

        @Override
        public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
            throw new UnsupportedOperationException("The test generation provider does not provide embeddings");
        }

        @Override
        public ProviderStatus getStatus() {
            return ProviderStatus.builder()
                .providerName(getProviderName())
                .available(true)
                .healthy(true)
                .successRate(1.0)
                .lastUpdated(LocalDateTime.now())
                .details("explicitly test-only generation fixture")
                .build();
        }

        @Override
        public ProviderConfig getConfig() {
            return ProviderConfig.builder()
                .providerName(getProviderName())
                .enabled(true)
                .apiKey("test-only")
                .baseUrl("test://course-generation")
                .defaultModel("course-test-generation")
                .timeoutSeconds(1)
                .maxRetries(0)
                .build();
        }

        public int generationCalls() {
            return generationCalls.get();
        }

        public String lastPrompt() {
            return lastPrompt;
        }

        public List<AIChatMessage> lastMessages() {
            return lastMessages;
        }

        public void failNext() {
            failNext.set(true);
        }

        public void response(String response) {
            this.response = response;
        }

        public void reset() {
            generationCalls.set(0);
            failNext.set(false);
            lastPrompt = null;
            lastMessages = List.of();
            response = """
                {"answer":"Wait fifteen minutes, then use account recovery to verify your registered email and reset access.","citationIds":["policy-account-lockout-01"]}
                """;
        }
    }

    /**
     * An explicitly test-only semantic fixture. Production and local profiles use real providers.
     */
    static final class CourseTestEmbeddingProvider implements EmbeddingProvider {

        private static final int DIMENSIONS = 8;

        @Override
        public String getProviderName() {
            return "course-test";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
            return response(request != null ? request.getText() : "");
        }

        @Override
        public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts) {
            return texts == null ? List.of() : texts.stream().map(this::response).toList();
        }

        @Override
        public int getEmbeddingDimension() {
            return DIMENSIONS;
        }

        @Override
        public Map<String, Object> getStatus() {
            return Map.of("provider", getProviderName(), "available", true, "dimensions", DIMENSIONS);
        }

        private AIEmbeddingResponse response(String text) {
            return AIEmbeddingResponse.builder()
                .embedding(semanticFixture(text))
                .model("course-test-fixture")
                .dimensions(DIMENSIONS)
                .processingTimeMs(0L)
                .requestId(UUID.randomUUID().toString())
                .build();
        }

        private List<Double> semanticFixture(String text) {
            String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
            double[] vector = new double[DIMENSIONS];
            addTopic(vector, 0, normalized, "account", "locked", "lockout", "access", "sign in", "recover", "attempt");
            addTopic(vector, 1, normalized, "billing", "payment", "method", "card", "renewal", "invoice", "receipt");
            addTopic(vector, 2, normalized, "subscription", "cancel", "plan", "paid period");
            addTopic(vector, 3, normalized, "api key", "rotate", "revoke", "developer");
            addTopic(vector, 4, normalized, "two-factor", "2fa", "recovery code", "device", "identity");
            addTopic(vector, 5, normalized, "vpn", "certificate", "network", "enroll", "credentials");

            int bucket = Math.floorMod(normalized.hashCode(), 2) + 6;
            vector[bucket] += 0.05;
            normalize(vector);

            List<Double> values = new ArrayList<>(DIMENSIONS);
            for (double value : vector) {
                values.add(value);
            }
            return values;
        }

        private void addTopic(double[] vector, int index, String text, String... terms) {
            for (String term : terms) {
                if (text.contains(term)) {
                    vector[index] += 1.0;
                }
            }
        }

        private void normalize(double[] vector) {
            double magnitude = 0.0;
            for (double value : vector) {
                magnitude += value * value;
            }
            magnitude = Math.sqrt(magnitude);
            if (magnitude == 0.0) {
                vector[7] = 1.0;
                return;
            }
            for (int index = 0; index < vector.length; index++) {
                vector[index] /= magnitude;
            }
        }
    }
}
