package dev.aifabric.course.support.identity;

import org.springframework.util.StringUtils;

public record CoursePrincipal(String userId, String tenantId, String sessionId) {

    public boolean authenticated() {
        return StringUtils.hasText(userId) && StringUtils.hasText(tenantId);
    }

    public static CoursePrincipal anonymous(String sessionId) {
        return new CoursePrincipal(null, null, sessionId);
    }
}
