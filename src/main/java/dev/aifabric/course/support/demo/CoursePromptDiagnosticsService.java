package dev.aifabric.course.support.demo;

import ai.fabric.config.PromptBundleProperties;
import ai.fabric.prompt.PromptTemplateResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CoursePromptDiagnosticsService {

    private final PromptBundleProperties bundleProperties;
    private final PromptTemplateResolver resolver;

    public CoursePromptDiagnosticsService(PromptBundleProperties bundleProperties,
                                          PromptTemplateResolver resolver) {
        this.bundleProperties = bundleProperties;
        this.resolver = resolver;
    }

    public PromptPosture posture() {
        Map<String, String> resolvedVersions = new LinkedHashMap<>();
        resolvedVersions.put("intent-classifier", version("intent-extraction/multi-step", "classify"));
        resolvedVersions.put("compound-intent", version("intent-extraction/compound", "system"));
        resolvedVersions.put("support-answer", version("rag/generation", "answer"));
        resolvedVersions.put("action-selector", version("intent-extraction/multi-step", "select-actions"));
        return new PromptPosture(bundleProperties.candidateVersions(), Map.copyOf(resolvedVersions));
    }

    public PromptQualityContract qualityContract() {
        var answer = resolver.resolve("rag/generation", "answer").template();
        var fallback = resolver.resolve("intent-extraction/multi-step", "select-actions").template();
        String body = answer.template();
        boolean querySlotPresent = body != null && body.contains("{{query}}");
        boolean contextSlotPresent = body != null && body.contains("{{context}}");
        boolean passed = "v1-course-support".equals(answer.key().version())
            && "v1".equals(fallback.key().version())
            && querySlotPresent
            && contextSlotPresent;
        return new PromptQualityContract(
            passed,
            answer.key().version(),
            fallback.key().version(),
            querySlotPresent,
            contextSlotPresent
        );
    }

    private String version(String family, String name) {
        return resolver.resolve(family, name).template().key().version();
    }

    public record PromptPosture(List<String> candidateVersions, Map<String, String> resolvedVersions) {
        public PromptPosture {
            candidateVersions = List.copyOf(candidateVersions);
            resolvedVersions = Map.copyOf(resolvedVersions);
        }
    }

    public record PromptQualityContract(
        boolean passed,
        String supportAnswerVersion,
        String baseFallbackVersion,
        boolean querySlotPresent,
        boolean contextSlotPresent
    ) {
    }
}
