package dev.aifabric.course.support.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import ai.fabric.config.PromptBundleProperties;
import ai.fabric.prompt.PromptTemplateResolver;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(CourseTestAIConfiguration.class)
class CoursePromptOverlayContractTest {

    @Autowired
    private PromptBundleProperties bundleProperties;

    @Autowired
    private PromptTemplateResolver promptTemplateResolver;

    @Test
    void courseClassifierPrecedesSupportPackWithoutReplacingItsOtherPromptFamilies() {
        assertThat(bundleProperties.candidateVersions())
            .containsExactly("v1-course-support", "v1-support", "v1");

        var classifier = promptTemplateResolver.resolve("intent-extraction/multi-step", "classify");
        assertThat(classifier.template().key().version()).isEqualTo("v1-course-support");
        assertThat(classifier.template().template()).isNotBlank();

        var compoundSystem = promptTemplateResolver.resolve("intent-extraction/compound", "system");
        assertThat(compoundSystem.template().key().version()).isEqualTo("v1-course-support");
        assertThat(compoundSystem.template().template()).isNotBlank();

        var supportAnswer = promptTemplateResolver.resolve("rag/generation", "answer");
        assertThat(supportAnswer.template().key().version()).isEqualTo("v1-course-support");
        assertThat(supportAnswer.template().template())
            .contains("{{query}}")
            .contains("{{context}}");

        var actionSelector = promptTemplateResolver.resolve("intent-extraction/multi-step", "select-actions");
        assertThat(actionSelector.template().key().version()).isEqualTo("v1");
    }

    @Test
    void overlayResourcesArePackagedAndDiagnosticsExposeVersionsWithoutPromptBodies() {
        assertThat(getClass().getResource(
            "/prompts/rag/generation/v1-course-support/answer.md")).isNotNull();

        dev.aifabric.course.support.demo.CoursePromptDiagnosticsService.PromptPosture posture =
            new dev.aifabric.course.support.demo.CoursePromptDiagnosticsService(
                bundleProperties, promptTemplateResolver).posture();

        assertThat(posture.candidateVersions())
            .containsExactly("v1-course-support", "v1-support", "v1");
        assertThat(posture.resolvedVersions())
            .containsEntry("intent-classifier", "v1-course-support")
            .containsEntry("compound-intent", "v1-course-support")
            .containsEntry("support-answer", "v1-course-support")
            .containsEntry("action-selector", "v1");
        assertThat(posture.toString()).doesNotContain("Course support answer rules");

        var contract = new dev.aifabric.course.support.demo.CoursePromptDiagnosticsService(
            bundleProperties, promptTemplateResolver).qualityContract();
        assertThat(contract.passed()).isTrue();
        assertThat(contract.querySlotPresent()).isTrue();
        assertThat(contract.contextSlotPresent()).isTrue();
    }
}
