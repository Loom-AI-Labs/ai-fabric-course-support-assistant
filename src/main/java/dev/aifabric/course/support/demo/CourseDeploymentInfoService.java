package dev.aifabric.course.support.demo;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CourseDeploymentInfoService {

    private final Environment environment;
    private final Optional<BuildProperties> buildProperties;
    private final Optional<GitProperties> gitProperties;
    private final Instant startedAt = Instant.now();

    public CourseDeploymentInfoService(Environment environment,
                                       Optional<BuildProperties> buildProperties,
                                       Optional<GitProperties> gitProperties) {
        this.environment = environment;
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
    }

    public DeploymentHealth health() {
        boolean generationEnabled = environment.getProperty(
            "ai.service.features.enable-generation", Boolean.class, false);
        boolean fallbackEnabled = environment.getProperty(
            "ai.providers.enable-fallback", Boolean.class, false);

        return new DeploymentHealth(
            "UP",
            environment.getProperty("spring.application.name", "ai-fabric-course-support-assistant"),
            firstText(buildProperties.map(BuildProperties::getVersion).orElse(null),
                environment.getProperty("APP_VERSION"), "development"),
            firstText(buildProperties.map(properties -> properties.get("aiFabricVersion")).orElse(null),
                environment.getProperty("AI_FABRIC_VERSION"), "unknown"),
            firstText(gitProperties.map(GitProperties::getCommitId).orElse(null),
                environment.getProperty("COOLIFY_GIT_COMMIT_SHA"),
                environment.getProperty("GITHUB_SHA"),
                environment.getProperty("SOURCE_COMMIT"),
                environment.getProperty("GIT_COMMIT"),
                "unknown"),
            firstText(gitProperties.map(GitProperties::getBranch).orElse(null),
                environment.getProperty("COOLIFY_GIT_BRANCH"),
                environment.getProperty("GITHUB_REF_NAME"),
                environment.getProperty("SOURCE_BRANCH"),
                environment.getProperty("GIT_BRANCH"),
                "unknown"),
            firstText(buildTime(), environment.getProperty("BUILD_TIME"), "unknown"),
            startedAt.toString(),
            Instant.now().toString(),
            activeProfiles(),
            CourseReadinessService.CHECKPOINT,
            new ProviderPosture(
                environment.getProperty("course.release.runtime-mode", "retrieval-only"),
                generationEnabled,
                generationEnabled
                    ? environment.getProperty("ai.providers.llm-provider", "unconfigured")
                    : "disabled",
                environment.getProperty("ai.providers.embedding-provider", "unconfigured"),
                environment.getProperty("ai.vector-db.type", "unconfigured"),
                fallbackEnabled
            )
        );
    }

    private String buildTime() {
        return buildProperties
            .map(BuildProperties::getTime)
            .map(Instant::toString)
            .orElseGet(() -> gitProperties
                .map(properties -> properties.get("commit.time"))
                .orElse(null));
    }

    private List<String> activeProfiles() {
        return Arrays.stream(environment.getActiveProfiles()).sorted().toList();
    }

    private String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && !"unknown".equalsIgnoreCase(candidate.trim())) {
                return candidate.trim();
            }
        }
        return "unknown";
    }

    public record DeploymentHealth(
        String status,
        String service,
        String version,
        String aiFabricVersion,
        String commit,
        String branch,
        String builtAt,
        String startedAt,
        String checkedAt,
        List<String> activeProfiles,
        String checkpoint,
        ProviderPosture provider
    ) {
        public DeploymentHealth {
            activeProfiles = activeProfiles == null ? List.of() : List.copyOf(activeProfiles);
        }
    }

    public record ProviderPosture(
        String mode,
        boolean generationEnabled,
        String generation,
        String embedding,
        String vector,
        boolean fallbackEnabled
    ) {
    }
}
