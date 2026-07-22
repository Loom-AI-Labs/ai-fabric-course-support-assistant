package dev.aifabric.course.support.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import ai.fabric.core.AICoreService;
import ai.fabric.core.LlmPurpose;
import ai.fabric.dto.AIGenerationRequest;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration.CourseTestGenerationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(CourseTestAIConfiguration.class)
class PurposeRoutingIntegrationTest {

    @Autowired
    private AICoreService aiCoreService;

    @Autowired
    private CourseTestGenerationProvider recordingProvider;

    @BeforeEach
    void resetProvider() {
        recordingProvider.reset();
    }

    @Test
    void routesOrchestrationAndAnswerGenerationToTheirConfiguredProviderAndModel() {
        aiCoreService.generateContent(request("classify this support request"), LlmPurpose.ORCHESTRATION);

        assertThat(recordingProvider.lastProvider()).isEqualTo("course-orchestration-test");
        assertThat(recordingProvider.lastModel()).isEqualTo("course-test-orchestration");

        aiCoreService.generateContent(request("answer from approved evidence"), LlmPurpose.GENERATION);

        assertThat(recordingProvider.lastProvider()).isEqualTo("course-generation-test");
        assertThat(recordingProvider.lastModel()).isEqualTo("course-test-generation");
        assertThat(recordingProvider.generationCalls()).isEqualTo(2);
    }

    @Test
    void failedGenerationDoesNotFallBackToTheOrchestrationProvider() {
        recordingProvider.failNext();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                aiCoreService.generateContent(request("answer this request"), LlmPurpose.GENERATION))
            .hasMessageContaining("Failed to generate AI content")
            .hasRootCauseMessage("deliberate test provider failure");

        assertThat(recordingProvider.lastProvider()).isEqualTo("course-generation-test");
        assertThat(recordingProvider.generationCalls()).isOne();
    }

    private AIGenerationRequest request(String prompt) {
        return AIGenerationRequest.builder()
            .entityId("course-purpose-routing")
            .entityType("course-diagnostic")
            .generationType("structured")
            .prompt(prompt)
            .build();
    }
}
