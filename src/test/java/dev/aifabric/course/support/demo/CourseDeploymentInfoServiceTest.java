package dev.aifabric.course.support.demo;

import static org.assertj.core.api.Assertions.assertThat;

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

        CourseDeploymentInfoService.DeploymentHealth health = new CourseDeploymentInfoService(
            environment,
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
        assertThat(health.checkpoint()).isEqualTo("course-0.3.3-06-tested-solution");
        assertThat(health.provider().mode()).isEqualTo("live-openai");
        assertThat(health.provider().generationEnabled()).isTrue();
        assertThat(health.provider().generation()).isEqualTo("openai");
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
            environment, Optional.empty(), Optional.empty()).health();

        assertThat(health.commit()).isEqualTo("unknown");
        assertThat(health.branch()).isEqualTo("unknown");
        assertThat(health.builtAt()).isEqualTo("unknown");
        assertThat(health.provider().generationEnabled()).isFalse();
        assertThat(health.provider().generation()).isEqualTo("disabled");
        assertThat(health.provider().mode()).isEqualTo("local-retrieval");
    }
}
