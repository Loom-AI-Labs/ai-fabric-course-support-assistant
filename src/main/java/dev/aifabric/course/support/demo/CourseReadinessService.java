package dev.aifabric.course.support.demo;

import ai.fabric.chat.service.ChatSessionService;
import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.chat.storage.ChatSessionPendingActionStore;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.PendingActionStore;
import ai.fabric.spi.RAGProvider;
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
    private final RAGProvider ragProvider;
    private final AIActionRegistry actionRegistry;
    private final ChatSessionService chatSessionService;
    private final ChatSessionStorageProvider chatSessionStorageProvider;
    private final PendingActionStore pendingActionStore;

    public CourseReadinessService(CourseDataService dataService, Environment environment,
                                  VectorDatabaseService vectorDatabaseService,
                                  RAGProvider ragProvider,
                                  AIActionRegistry actionRegistry,
                                  ChatSessionService chatSessionService,
                                  ChatSessionStorageProvider chatSessionStorageProvider,
                                  PendingActionStore pendingActionStore) {
        this.dataService = dataService;
        this.environment = environment;
        this.vectorDatabaseService = vectorDatabaseService;
        this.ragProvider = ragProvider;
        this.actionRegistry = actionRegistry;
        this.chatSessionService = chatSessionService;
        this.chatSessionStorageProvider = chatSessionStorageProvider;
        this.pendingActionStore = pendingActionStore;
    }

    public ReadinessResponse readiness() {
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("semanticSearch", true);
        capabilities.put("rag", ragProvider.isAvailable());
        capabilities.put("governedActions", actionRegistry.getAllMetadata().size() >= 3);
        capabilities.put("conversationMemory", chatSessionService != null
            && chatSessionStorageProvider != null
            && pendingActionStore instanceof ChatSessionPendingActionStore);
        capabilities.put("tenantSecurity", false);
        capabilities.put("piiProtection", false);

        return new ReadinessResponse(
            "course-0.3.3-04-memory",
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
