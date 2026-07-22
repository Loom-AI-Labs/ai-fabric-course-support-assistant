package dev.aifabric.course.support.demo;

import ai.fabric.rag.VectorDatabaseService;
import dev.aifabric.course.support.knowledge.KnowledgeArticle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class CourseReadinessService {

    private final CourseDataService dataService;
    private final Environment environment;
    private final VectorDatabaseService vectorDatabaseService;

    public CourseReadinessService(CourseDataService dataService, Environment environment,
                                  VectorDatabaseService vectorDatabaseService) {
        this.dataService = dataService;
        this.environment = environment;
        this.vectorDatabaseService = vectorDatabaseService;
    }

    public ReadinessResponse readiness() {
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("semanticSearch", true);
        capabilities.put("rag", false);
        capabilities.put("governedActions", false);
        capabilities.put("conversationMemory", false);
        capabilities.put("tenantSecurity", false);
        capabilities.put("piiProtection", false);

        return new ReadinessResponse(
            "course-0.3.3-01-first-search",
            dataService.snapshot(),
            vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE),
            List.of(environment.getActiveProfiles()),
            Map.copyOf(capabilities)
        );
    }

    public record ReadinessResponse(
        String checkpoint,
        CourseDataService.DatasetSnapshot sourceRecords,
        long indexedVectors,
        java.util.List<String> activeProfiles,
        Map<String, Boolean> capabilities
    ) {
    }
}
