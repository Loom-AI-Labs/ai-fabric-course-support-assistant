package dev.aifabric.course.support.identity;

import dev.aifabric.course.support.demo.CourseDataService;
import org.springframework.stereotype.Component;

/**
 * Supplies a trusted demo identity until the security lesson replaces it with authenticated request data.
 */
@Component
public class CoursePrincipalProvider {

    public CoursePrincipal currentPrincipal() {
        return new CoursePrincipal(
            CourseDataService.COURSE_CUSTOMER,
            CourseDataService.COURSE_TENANT,
            "course-session-alex"
        );
    }
}
