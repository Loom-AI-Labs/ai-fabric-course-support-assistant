package dev.aifabric.course.support.web;

import dev.aifabric.course.support.common.FeatureUnavailableException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(FeatureUnavailableException.class)
    ProblemDetail handleFeatureUnavailable(FeatureUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, exception.getMessage());
        problem.setTitle("Course capability not implemented");
        problem.setType(URI.create("https://ai-fabric.dev/problems/course-capability-not-implemented"));
        problem.setProperty("capability", exception.getCapability());
        return problem;
    }
}
