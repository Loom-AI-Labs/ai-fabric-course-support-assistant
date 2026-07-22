package dev.aifabric.course.support.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CoursePrincipalProvider {

    public CoursePrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
            && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof CourseAuthenticatedPrincipal principal) {
            return principal.toCoursePrincipal();
        }
        return CoursePrincipal.anonymous("course-anonymous");
    }
}
