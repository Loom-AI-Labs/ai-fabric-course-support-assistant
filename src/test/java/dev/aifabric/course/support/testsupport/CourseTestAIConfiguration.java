package dev.aifabric.course.support.testsupport;

import ai.fabric.dto.AIEmbeddingRequest;
import ai.fabric.dto.AIEmbeddingResponse;
import ai.fabric.embedding.EmbeddingProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class CourseTestAIConfiguration {

    @Bean
    EmbeddingProvider courseTestEmbeddingProvider() {
        return new CourseTestEmbeddingProvider();
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

            int bucket = Math.floorMod(normalized.hashCode(), 3) + 5;
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
