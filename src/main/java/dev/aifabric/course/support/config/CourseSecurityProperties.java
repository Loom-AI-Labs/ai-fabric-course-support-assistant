package dev.aifabric.course.support.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "course.security")
public class CourseSecurityProperties {

    @NotEmpty
    private List<@Valid PrincipalDefinition> principals = new ArrayList<>();

    public List<PrincipalDefinition> getPrincipals() {
        return principals;
    }

    public void setPrincipals(List<PrincipalDefinition> principals) {
        this.principals = principals;
    }

    public static class PrincipalDefinition {
        @NotBlank
        private String token;
        @NotBlank
        private String userId;
        @NotBlank
        private String tenantId;
        @NotBlank
        private String sessionId;
        private List<String> scopes = new ArrayList<>();
        private List<String> roles = new ArrayList<>();

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
