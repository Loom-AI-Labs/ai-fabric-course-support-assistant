package dev.aifabric.course.support.config;

import ai.fabric.access.policy.EntityAccessPolicy;
import ai.fabric.chat.spi.ChatSessionAccessControlPolicy;
import ai.fabric.dto.AIAccessSubjectContext;
import dev.aifabric.course.support.conversation.CourseConversationAuthorization;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class CourseAccessControlConfiguration {

    private static final String ORCHESTRATION_RESOURCE = "rag:intent";
    private static final String READ_OPERATION = "READ";

    @Bean
    EntityAccessPolicy courseEntityAccessPolicy() {
        return (authContext, entity) -> hasRequestSubject(authContext)
            && ORCHESTRATION_RESOURCE.equals(value(entity, "resourceId"))
            && READ_OPERATION.equalsIgnoreCase(value(entity, "operationType"));
    }

    @Bean
    ChatSessionAccessControlPolicy courseChatSessionAccessControlPolicy(
        CourseConversationAuthorization authorization
    ) {
        return new ChatSessionAccessControlPolicy() {
            @Override
            public boolean canCreateConversation(String ownerId) {
                return authorization.canCreate(ownerId);
            }

            @Override
            public boolean canAccessConversation(String ownerId, String conversationId) {
                return authorization.canAccess(ownerId, conversationId);
            }

            @Override
            public boolean canRecordTurn(String ownerId, String conversationId) {
                return authorization.canRecord(ownerId, conversationId);
            }

            @Override
            public boolean canDeleteConversation(String ownerId, String conversationId) {
                return authorization.canDelete(ownerId, conversationId);
            }
        };
    }

    private boolean hasRequestSubject(AIAccessSubjectContext authContext) {
        return authContext != null
            && (StringUtils.hasText(authContext.getSubjectId())
                || StringUtils.hasText(authContext.getSessionId()));
    }

    private String value(Map<String, Object> entity, String key) {
        Object value = entity != null ? entity.get(key) : null;
        return value != null ? value.toString() : null;
    }
}
