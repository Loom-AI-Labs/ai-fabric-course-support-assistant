package dev.aifabric.course.support.identity;

import org.springframework.util.StringUtils;
import java.util.List;

public record CoursePrincipal(String userId, String tenantId, String sessionId,
                              List<String> scopes, List<String> roles) {

    public CoursePrincipal {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public CoursePrincipal(String userId, String tenantId, String sessionId) {
        this(userId, tenantId, sessionId, List.of("support:read", "support:write"), List.of("CUSTOMER"));
    }

    public boolean authenticated() {
        return StringUtils.hasText(userId) && StringUtils.hasText(tenantId);
    }

    public static CoursePrincipal anonymous(String sessionId) {
        return new CoursePrincipal(null, null, sessionId, List.of(), List.of());
    }
}
