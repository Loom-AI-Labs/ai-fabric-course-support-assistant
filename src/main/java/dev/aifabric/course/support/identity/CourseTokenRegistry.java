package dev.aifabric.course.support.identity;

import dev.aifabric.course.support.config.CourseSecurityProperties;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CourseTokenRegistry {

    private final CourseSecurityProperties properties;

    public CourseTokenRegistry(CourseSecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        Set<String> users = new HashSet<>();
        for (CourseSecurityProperties.PrincipalDefinition definition : properties.getPrincipals()) {
            if (!users.add(definition.getUserId().trim())) {
                throw new IllegalStateException("Course security contains a duplicate user ID");
            }
        }
    }

    public Optional<CourseAuthenticatedPrincipal> authenticate(String candidateToken) {
        if (!StringUtils.hasText(candidateToken)) {
            return Optional.empty();
        }
        return properties.getPrincipals().stream()
            .filter(definition -> secureEquals(candidateToken.trim(), definition.getToken()))
            .findFirst()
            .map(this::principal);
    }

    public int configuredPrincipalCount() {
        return properties.getPrincipals().size();
    }

    private CourseAuthenticatedPrincipal principal(CourseSecurityProperties.PrincipalDefinition definition) {
        return new CourseAuthenticatedPrincipal(
            definition.getUserId().trim(),
            definition.getTenantId().trim(),
            definition.getSessionId().trim(),
            definition.getScopes(),
            definition.getRoles()
        );
    }

    private boolean secureEquals(String candidate, String configured) {
        if (!StringUtils.hasText(configured)) {
            return false;
        }
        return MessageDigest.isEqual(
            candidate.getBytes(StandardCharsets.UTF_8),
            configured.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}
