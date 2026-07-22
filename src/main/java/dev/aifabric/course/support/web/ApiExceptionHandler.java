package dev.aifabric.course.support.web;

import ai.fabric.chat.exception.ChatSessionAccessDeniedException;
import ai.fabric.chat.exception.ChatSessionNotFoundException;
import dev.aifabric.course.support.identity.CourseAccessDeniedException;
import dev.aifabric.course.support.knowledge.ArticleNotFoundException;
import dev.aifabric.course.support.knowledge.EvidenceBoundaryException;
import dev.aifabric.course.support.knowledge.EvidenceOperationException;
import dev.aifabric.course.support.privacy.PrivacyBoundaryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

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

    @ExceptionHandler(CourseAccessDeniedException.class)
    ProblemDetail handleCourseAccessDenied(CourseAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Course capability access denied");
    }

    @ExceptionHandler({EvidenceBoundaryException.class, PrivacyBoundaryException.class})
    ProblemDetail handleSecurityBoundaryFailure(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The request could not be completed under the configured security policy."
        );
        problem.setTitle("Security policy could not be proved");
        return problem;
    }
}
