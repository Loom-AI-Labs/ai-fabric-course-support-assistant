package dev.aifabric.course.support.identity;

public class CourseAccessDeniedException extends RuntimeException {

    public CourseAccessDeniedException(String message) {
        super(message);
    }
}
