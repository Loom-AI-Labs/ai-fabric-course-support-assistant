package dev.aifabric.course.support.demo;

import static org.assertj.core.api.Assertions.assertThat;

import ai.fabric.config.AIProviderConfig;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.mock.env.MockEnvironment;

class CourseDeploymentInfoServiceTest {

    @Test
    void reportsSourceDerivedBuildIdentityAndExplicitProviderPosture() {
        Properties buildValues = new Properties();
        buildValues.setProperty("version", "0.3.3-course.1-SNAPSHOT");
        buildValues.setProperty("time", "2026-07-22T12:00:00Z");
        buildValues.setProperty("aiFabricVersion", "0.3.3");

        Properties gitValues = new Properties();
        gitValues.setProperty("commit.id", "abc123def456");
        gitValues.setProperty("branch", "main");

        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.application.name", "course-test-app")
            .withProperty("course.release.runtime-mode", "live-openai")
            .withProperty("ai.service.features.enable-generation", "true")
            .withProperty("ai.providers.llm-provider", "openai")
            .withProperty("ai.providers.embedding-provider", "onnx")
            .withProperty("ai.vector-db.type", "lucene")
            .withProperty("ai.providers.enable-fallback", "false");
        environment.setActiveProfiles("openai");
        AIProviderConfig providerConfig = providerConfig(
            "openai", "gpt-4o-mini-orchestration", "openai", "gpt-4o-mini-generation");

        CourseDeploymentInfoService.DeploymentHealth health = new CourseDeploymentInfoService(
            environment,
            providerConfig,
            Optional.of(new BuildProperties(buildValues)),
            Optional.of(new GitProperties(gitValues))
        ).health();

        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.service()).isEqualTo("course-test-app");
        assertThat(health.version()).isEqualTo("0.3.3-course.1-SNAPSHOT");
        assertThat(health.aiFabricVersion()).isEqualTo("0.3.3");
        assertThat(health.commit()).isEqualTo("abc123def456");
        assertThat(health.branch()).isEqualTo("main");
        assertThat(health.builtAt()).isEqualTo("2026-07-22T12:00:00Z");
        assertThat(health.activeProfiles()).containsExactly("openai");
        assertThat(health.checkpoint()).isEqualTo("course-0.3.3-p07-qdrant");
        assertThat(health.provider().mode()).isEqualTo("live-openai");
        assertThat(health.provider().generationEnabled()).isTrue();
        assertThat(health.provider().orchestration()).isEqualTo("openai");
        assertThat(health.provider().orchestrationModel()).isEqualTo("gpt-4o-mini-orchestration");
        assertThat(health.provider().generation()).isEqualTo("openai");
        assertThat(health.provider().generationModel()).isEqualTo("gpt-4o-mini-generation");
        assertThat(health.provider().embedding()).isEqualTo("onnx");
        assertThat(health.provider().vector()).isEqualTo("lucene");
        assertThat(health.provider().fallbackEnabled()).isFalse();
    }

    @Test
    void usesHonestUnknownsWhenSourceMetadataIsUnavailable() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("course.release.runtime-mode", "local-retrieval")
            .withProperty("ai.providers.embedding-provider", "onnx")
            .withProperty("ai.vector-db.type", "lucene");

        CourseDeploymentInfoService.DeploymentHealth health = new CourseDeploymentInfoService(
            environment, providerConfig(null, null, null, null), Optional.empty(), Optional.empty()).health();

        assertThat(health.commit()).isEqualTo("unknown");
        assertThat(health.branch()).isEqualTo("unknown");
        assertThat(health.builtAt()).isEqualTo("unknown");
        assertThat(health.provider().generationEnabled()).isFalse();
        assertThat(health.provider().orchestration()).isEqualTo("disabled");
        assertThat(health.provider().orchestrationModel()).isEqualTo("disabled");
        assertThat(health.provider().generation()).isEqualTo("disabled");
        assertThat(health.provider().generationModel()).isEqualTo("disabled");
        assertThat(health.provider().mode()).isEqualTo("local-retrieval");
    }

    private AIProviderConfig providerConfig(String orchestrationProvider, String orchestrationModel,
                                            String generationProvider, String generationModel) {
        AIProviderConfig config = new AIProviderConfig();
        AIProviderConfig.OrchestrationLlmConfig orchestration = new AIProviderConfig.OrchestrationLlmConfig();
        orchestration.setLlmProvider(orchestrationProvider);
        orchestration.setModel(orchestrationModel);
        config.setOrchestration(orchestration);
        AIProviderConfig.GenerationLlmConfig generation = new AIProviderConfig.GenerationLlmConfig();
        generation.setLlmProvider(generationProvider);
        generation.setModel(generationModel);
        config.setGeneration(generation);
        return config;
    }
}
