package dev.aifabric.course.support.identity;

import java.util.List;

public record CourseAuthenticatedPrincipal(
    String userId,
    String tenantId,
    String sessionId,
    List<String> scopes,
    List<String> roles
) {
    public CourseAuthenticatedPrincipal {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public CoursePrincipal toCoursePrincipal() {
        return new CoursePrincipal(userId, tenantId, sessionId, scopes, roles);
    }
}
