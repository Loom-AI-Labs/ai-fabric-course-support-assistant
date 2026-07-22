package dev.aifabric.course.support.conversation;

import ai.fabric.chat.domain.ChatSession;
import ai.fabric.chat.domain.ChatTurn;
import ai.fabric.chat.service.ChatSessionService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConversationHistoryService {

    private final ChatSessionService chatSessionService;

    public ConversationHistoryService(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    public ConversationView get(String conversationId, CoursePrincipal principal) {
        ChatSession session = chatSessionService.getSession(conversationId, requireOwner(principal));
        List<TurnView> turns = session.getTurns() == null
            ? List.of()
            : session.getTurns().stream().map(this::project).toList();
        return new ConversationView(
            session.getId(),
            session.getStatus().name(),
            session.getCreatedAt(),
            session.getLastInteractionAt(),
            turns
        );
    }

    public void delete(String conversationId, CoursePrincipal principal) {
        chatSessionService.deleteConversation(conversationId, requireOwner(principal));
    }

    private String requireOwner(CoursePrincipal principal) {
        if (principal == null || !principal.authenticated()) {
            throw new IllegalArgumentException("Authenticated conversation owner is required");
        }
        return principal.userId();
    }

    private TurnView project(ChatTurn turn) {
        return new TurnView(turn.getUserQuery(), turn.getAiResponse(), turn.getTimestamp());
    }

    public record ConversationView(
        String conversationId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastInteractionAt,
        List<TurnView> turns
    ) {
    }

    public record TurnView(String userMessage, String assistantMessage, LocalDateTime timestamp) {
    }
}
