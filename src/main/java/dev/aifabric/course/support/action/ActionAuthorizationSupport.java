package dev.aifabric.course.support.action;

import ai.fabric.intent.action.ActionContext;
import java.util.List;
import org.springframework.util.StringUtils;

final class ActionAuthorizationSupport {

    private static final String COURSE_ROLES = "courseRoles";

    private ActionAuthorizationSupport() {
    }

    static boolean hasScopeAndRole(ActionContext context, String requiredScope, String requiredRole) {
        if (context == null || !StringUtils.hasText(context.userId())
            || !StringUtils.hasText(context.authContext().getTenantId())) {
            return false;
        }
        List<String> scopes = context.authContext().getGrantedScopes();
        return scopes != null
            && scopes.contains(requiredScope)
            && metadataValues(context, COURSE_ROLES).contains(requiredRole);
    }

    static String actionParameter(ActionContext context, String name) {
        Object value = context != null && context.actionParams() != null
            ? context.actionParams().get(name)
            : null;
        return value != null && StringUtils.hasText(String.valueOf(value))
            ? String.valueOf(value).trim()
            : null;
    }

    private static List<String> metadataValues(ActionContext context, String name) {
        Object raw = context.metadata().get(name);
        if (!(raw instanceof Iterable<?> values)) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .map(String::valueOf)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }
}
