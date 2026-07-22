package dev.aifabric.course.support.assistant;

import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.OrchestrationContextMetadataKeys;
import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.RAGOrchestrator;
import ai.fabric.intent.orchestration.attachment.OrchestrationAttachment;
import dev.aifabric.course.support.identity.CoursePrincipal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SupportOrchestrationService {

    private final RAGOrchestrator orchestrator;

    public SupportOrchestrationService(RAGOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public SupportOrchestrationResponse orchestrate(String message, String requestedConversationId,
                                                    CoursePrincipal principal) {
        return orchestrate(message, requestedConversationId, List.of(), principal);
    }

    public SupportOrchestrationResponse orchestrate(String message, String requestedConversationId,
                                                    List<OrchestrationAttachment> attachments,
                                                    CoursePrincipal principal) {
        return orchestrate(message, requestedConversationId, attachments, principal, false);
    }

    public SupportOrchestrationResponse orchestrateTransient(String message, String requestedConversationId,
                                                             CoursePrincipal principal) {
        return orchestrate(message, requestedConversationId, List.of(), principal, true);
    }

    private SupportOrchestrationResponse orchestrate(String message, String requestedConversationId,
                                                     List<OrchestrationAttachment> attachments,
                                                     CoursePrincipal principal,
                                                     boolean neverPersist) {
        String conversationId = StringUtils.hasText(requestedConversationId)
            ? requestedConversationId.trim()
            : "course-conversation-" + UUID.randomUUID();
        OrchestrationContext context = context(conversationId, attachments, principal, neverPersist);
        OrchestrationResult result = orchestrator.orchestrate(message, context);
        return new SupportOrchestrationResponse(conversationId, result);
    }

    private OrchestrationContext context(String conversationId, List<OrchestrationAttachment> attachments,
                                         CoursePrincipal principal, boolean neverPersist) {
        OrchestrationContext.OrchestrationContextBuilder builder = OrchestrationContext.builder()
            .conversationId(conversationId)
            .position("support")
            .mode("support_assistant")
            .attachments(attachments != null ? List.copyOf(attachments) : List.of());

        if (principal != null && principal.authenticated()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(OrchestrationContextMetadataKeys.SUBJECT_ID, principal.userId());
            metadata.put(OrchestrationContextMetadataKeys.SUBJECT_TYPE, "END_USER");
            metadata.put(OrchestrationContextMetadataKeys.TENANT_ID, principal.tenantId());
            metadata.put(OrchestrationContextMetadataKeys.AUTH_MODE, "COURSE_DEMO_PRINCIPAL");
            metadata.put(OrchestrationContextMetadataKeys.CALLER_TYPE, "COURSE_API");
            if (neverPersist) {
                metadata.put(OrchestrationContextMetadataKeys.QUERY_PERSISTENCE_MODE, "NEVER_PERSIST");
            }
            return builder
                .userId(principal.userId())
                .sessionId(principal.sessionId())
                .metadata(Map.copyOf(metadata))
                .build();
        }

        String anonymousSession = principal != null && StringUtils.hasText(principal.sessionId())
            ? principal.sessionId().trim()
            : "course-anonymous-" + UUID.randomUUID();
        if (neverPersist) {
            return builder
                .sessionId(anonymousSession)
                .metadata(Map.of(OrchestrationContextMetadataKeys.QUERY_PERSISTENCE_MODE, "NEVER_PERSIST"))
                .build();
        }
        return builder.sessionId(anonymousSession).build();
    }
}
