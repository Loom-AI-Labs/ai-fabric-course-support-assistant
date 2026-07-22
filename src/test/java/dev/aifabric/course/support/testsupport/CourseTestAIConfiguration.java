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
    CourseTestGenerationProvider courseTestGenerationFixture() {
        return new CourseTestGenerationProvider();
    }

    @Bean
    AIProvider courseTestOrchestrationProvider(CourseTestGenerationProvider fixture) {
        return new CoursePurposeProvider(
            "course-orchestration-test", "course-test-orchestration", fixture);
    }

    @Bean
    AIProvider courseTestAnswerProvider(CourseTestGenerationProvider fixture) {
        return new CoursePurposeProvider(
            "course-generation-test", "course-test-generation", fixture);
    }

    /**
     * An explicitly test-only generation fixture. It never exists in a runtime application profile.
     */
    public static final class CourseTestGenerationProvider {

        private final AtomicInteger generationCalls = new AtomicInteger();
        private final AtomicBoolean failNext = new AtomicBoolean();
        private volatile String lastPrompt;
        private volatile List<AIChatMessage> lastMessages = List.of();
        private volatile String lastProvider;
        private volatile String lastModel;
        private volatile String response = """
            {"answer":"Wait fifteen minutes, then use account recovery to verify your registered email and reset access.","citationIds":["policy-account-lockout-01"]}
            """;

        private AIGenerationResponse generateContent(String providerName, AIGenerationRequest request) {
            generationCalls.incrementAndGet();
            lastProvider = providerName;
            lastModel = request != null ? request.getModel() : null;
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
                .model(lastModel)
                .tokensUsed(0)
                .processingTimeMs(0L)
                .generatedAt(LocalDateTime.now())
                .status("SUCCESS")
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

        public String lastProvider() {
            return lastProvider;
        }

        public String lastModel() {
            return lastModel;
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
            lastProvider = null;
            lastModel = null;
            response = """
                {"answer":"Wait fifteen minutes, then use account recovery to verify your registered email and reset access.","citationIds":["policy-account-lockout-01"]}
                """;
        }
    }

    private static final class CoursePurposeProvider implements AIProvider {

        private final String providerName;
        private final String defaultModel;
        private final CourseTestGenerationProvider fixture;

        private CoursePurposeProvider(String providerName, String defaultModel,
                                      CourseTestGenerationProvider fixture) {
            this.providerName = providerName;
            this.defaultModel = defaultModel;
            this.fixture = fixture;
        }

        @Override
        public String getProviderName() {
            return providerName;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public AIGenerationResponse generateContent(AIGenerationRequest request) {
            return fixture.generateContent(providerName, request);
        }

        @Override
        public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
            throw new UnsupportedOperationException("The test generation provider does not provide embeddings");
        }

        @Override
        public ProviderStatus getStatus() {
            return ProviderStatus.builder()
                .providerName(providerName)
                .available(true)
                .healthy(true)
                .successRate(1.0)
                .lastUpdated(LocalDateTime.now())
                .details("explicitly test-only purpose provider")
                .build();
        }

        @Override
        public ProviderConfig getConfig() {
            return ProviderConfig.builder()
                .providerName(providerName)
                .enabled(true)
                .apiKey("test-only")
                .baseUrl("test://" + providerName)
                .defaultModel(defaultModel)
                .timeoutSeconds(1)
                .maxRetries(0)
                .build();
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
