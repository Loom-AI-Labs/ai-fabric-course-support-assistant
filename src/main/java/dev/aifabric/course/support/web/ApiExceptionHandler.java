package dev.aifabric.course.support.web;

import ai.fabric.chat.exception.ChatSessionAccessDeniedException;
import ai.fabric.chat.exception.ChatSessionNotFoundException;
import dev.aifabric.course.support.common.FeatureUnavailableException;
import dev.aifabric.course.support.knowledge.ArticleNotFoundException;
import dev.aifabric.course.support.knowledge.EvidenceOperationException;
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

    @ExceptionHandler(ArticleNotFoundException.class)
    ProblemDetail handleNotFound(ArticleNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(EvidenceOperationException.class)
    ProblemDetail handleEvidenceFailure(EvidenceOperationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setTitle("AI evidence operation failed");
        return problem;
    }

    @ExceptionHandler(ChatSessionAccessDeniedException.class)
    ProblemDetail handleConversationAccessDenied(ChatSessionAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Conversation access denied");
    }

    @ExceptionHandler(ChatSessionNotFoundException.class)
    ProblemDetail handleConversationNotFound(ChatSessionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Conversation was not found");
    }
}
