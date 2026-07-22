package dev.aifabric.course.support.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.fabric.chat.config.ChatSessionProperties;
import ai.fabric.chat.domain.ChatSession;
import ai.fabric.chat.exception.ChatSessionAccessDeniedException;
import ai.fabric.chat.service.ChatSessionService;
import ai.fabric.chat.spi.ChatSessionAccessControlPolicy;
import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.chat.storage.ChatSessionPendingActionStore;
import ai.fabric.dto.AIChatMessage;
import ai.fabric.dto.AIChatRole;
import ai.fabric.intent.action.PendingAction;
import ai.fabric.intent.action.PendingActionStore;
import ai.fabric.intent.orchestration.OrchestrationResultType;
import ai.fabric.intent.orchestration.attachment.OrchestrationAttachment;
import dev.aifabric.course.support.account.CustomerAccount;
import dev.aifabric.course.support.account.CustomerAccountRepository;
import dev.aifabric.course.support.assistant.SupportOrchestrationResponse;
import dev.aifabric.course.support.assistant.SupportOrchestrationService;
import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration.CourseTestGenerationProvider;
import dev.aifabric.course.support.ticket.SupportTicketRepository;
import dev.aifabric.course.support.web.AssistantController.AssistantQueryRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CourseTestAIConfiguration.class)
class ConversationMemoryIntegrationTest {

    private static final String OWNER = CourseDataService.COURSE_CUSTOMER;
    private static final String ALEX_BEARER = "Bearer course-alex-local-token";
    private static final CoursePrincipal PRINCIPAL = new CoursePrincipal(
        OWNER, CourseDataService.COURSE_TENANT, "course-session-alex");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseDataService dataService;

    @Autowired
    private CourseTestGenerationProvider generationProvider;

    @Autowired
    private SupportOrchestrationService orchestrationService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatSessionStorageProvider storageProvider;

    @Autowired
    private ChatSessionAccessControlPolicy accessControlPolicy;

    @Autowired
    private PendingActionStore pendingActionStore;

    @Autowired
    private ChatSessionProperties properties;

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private CustomerAccountRepository accountRepository;

    @BeforeEach
    void reset() {
        generationProvider.reset();
        dataService.seed();
    }

    @Test
    void runtimeUsesJpaSessionsAndThePublicRequestOwnsOnlyCurrentTurnData() {
        assertThat(chatSessionService).isNotNull();
        assertThat(storageProvider.getClass().getSimpleName()).isEqualTo("DefaultDatabaseChatSessionStorage");
        assertThat(accessControlPolicy).isNotNull();
        assertThat(pendingActionStore).isInstanceOf(ChatSessionPendingActionStore.class);
        assertThat(properties.getWindowSize()).isEqualTo(8);
        assertThat(properties.getMaxContextChars()).isEqualTo(4_000);
        assertThat(properties.getPinnedTargetReuseWindowTurns()).isEqualTo(3);
        assertThat(properties.getMaxPendingActionStackDepth()).isEqualTo(4);

        assertThat(Arrays.stream(AssistantQueryRequest.class.getRecordComponents())
            .map(component -> component.getName()))
            .containsExactly("message", "conversationId", "attachments", "mode", "position")
            .doesNotContain("historyMessages", "pendingAction", "actionDraft", "ownerId", "tenantId");
    }

    @Test
    void backendHistoryCarriesAReadTargetIntoOneTimeEscalationConfirmation() throws Exception {
        String conversationId = "memory-escalation";
        generationProvider.response(actionIntent(
            "get_my_ticket_status", "{\"ticketNumber\":\"T-1001\"}"));

        SupportOrchestrationResponse first = orchestrationService.orchestrate(
            "Why is ticket T-1001 unresolved?", conversationId, PRINCIPAL);

        assertThat(first.result().getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        ChatSession recorded = chatSessionService.getSession(conversationId, OWNER);
        assertThat(recorded.getOwnerId()).isEqualTo(OWNER);
        assertThat(recorded.getTurns()).hasSize(1);
        assertThat(recorded.getTurns().getFirst().getUserQuery())
            .isEqualTo("Why is ticket T-1001 unresolved?");
        assertThat(recorded.getTurns().getFirst().getAiResponse()).contains("Ticket status loaded");

        generationProvider.response(actionIntent(
            "escalate_support_ticket", "{\"ticketNumber\":\"T-1001\"}"));
        SupportOrchestrationResponse followUp = orchestrationService.orchestrate(
            "Escalate it.", conversationId, PRINCIPAL);

        assertThat(followUp.result().getType()).isEqualTo(OrchestrationResultType.CONFIRMATION_REQUIRED);
        assertThat(generationProvider.lastMessages())
            .extracting(AIChatMessage::getRole)
            .containsExactly(AIChatRole.USER, AIChatRole.ASSISTANT);
        assertThat(generationProvider.lastMessages().getFirst().getContent())
            .isEqualTo("Why is ticket T-1001 unresolved?");
        assertThat(generationProvider.lastMessages().get(1).getContent())
            .contains("Ticket status loaded")
            .contains("T-1001");
        assertThat(pendingActionStore.peekPendingAction(conversationId, OWNER))
            .get().extracting(PendingAction::action).isEqualTo("escalate_support_ticket");
        assertThat(ticketRepository.findById("T-1001").orElseThrow().getStatus()).isEqualTo("OPEN");

        mockMvc.perform(get("/api/assistant/conversations/{conversationId}", conversationId)
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId").value(conversationId))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.turns.length()").value(2))
            .andExpect(jsonPath("$.turns[0].userMessage").value("Why is ticket T-1001 unresolved?"));

        generationProvider.response(confirmationIntent("CONFIRMATION_POSITIVE"));
        SupportOrchestrationResponse confirmation = orchestrationService.orchestrate(
            "Yes.", conversationId, PRINCIPAL);

        assertThat(confirmation.result().getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(ticketRepository.findById("T-1001").orElseThrow().getStatus()).isEqualTo("ESCALATED");
        assertThat(pendingActionStore.peekPendingAction(conversationId, OWNER)).isEmpty();

        generationProvider.response(confirmationIntent("CONFIRMATION_POSITIVE"));
        SupportOrchestrationResponse duplicate = orchestrationService.orchestrate(
            "Yes.", conversationId, PRINCIPAL);
        assertThat(duplicate.result().getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(duplicate.result().getMessage()).isEqualTo("There is no pending action to confirm.");
        assertThat(ticketRepository.findById("T-1001").orElseThrow().getStatus()).isEqualTo("ESCALATED");
    }

    @Test
    void aNewConversationCannotReuseHistoryTargetOrPendingState() {
        generationProvider.response(actionIntent(
            "get_my_ticket_status", "{\"ticketNumber\":\"T-1001\"}"));
        orchestrationService.orchestrate("Read T-1001", "memory-original", PRINCIPAL);

        generationProvider.response(actionIntent("escalate_support_ticket", "{}"));
        SupportOrchestrationResponse isolated = orchestrationService.orchestrate(
            "Escalate it.", "memory-new", PRINCIPAL);

        assertThat(isolated.result().getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
        assertThat(generationProvider.lastMessages()).isEmpty();
        assertThat(pendingActionStore.peekPendingAction("memory-new", OWNER)).isEmpty();
        assertThat(ticketRepository.findById("T-1001").orElseThrow().getStatus()).isEqualTo("OPEN");
    }

    @Test
    void ownerChecksHistoryBoundsAndPendingStackRemainBackendEnforced() {
        String conversationId = "memory-bounds";
        chatSessionService.getConversationMessages(conversationId, OWNER);

        accountRepository.save(new CustomerAccount(
            "customer-sam", CourseDataService.COURSE_TENANT, "sam@example.test", "PRO", "CUSTOMER"));
        assertThatThrownBy(() -> chatSessionService.getSession(conversationId, "customer-sam"))
            .isInstanceOf(ChatSessionAccessDeniedException.class)
            .hasMessageContaining("different user");
        assertThatThrownBy(() -> chatSessionService.getConversationMessages("invalid conversation", OWNER))
            .isInstanceOf(ChatSessionAccessDeniedException.class);
        assertThat(accessControlPolicy.canCreateConversation("")).isFalse();

        for (int index = 0; index < 10; index++) {
            chatSessionService.recordTurn(
                conversationId,
                OWNER,
                "user-" + index + "-" + "u".repeat(290),
                "assistant-" + index + "-" + "a".repeat(290),
                Map.of()
            );
        }
        List<AIChatMessage> bounded = chatSessionService.getConversationMessages(conversationId, OWNER);
        assertThat(bounded).isNotEmpty();
        assertThat(bounded.stream().map(AIChatMessage::getContent).mapToInt(String::length).sum())
            .isLessThanOrEqualTo(4_000);
        assertThat(bounded).noneMatch(message -> message.getContent().startsWith("user-0-"));
        assertThat(bounded).anyMatch(message -> message.getContent().startsWith("assistant-9-"));
        assertThat(bounded).hasSizeLessThanOrEqualTo(16);

        for (int index = 1; index <= 5; index++) {
            pendingActionStore.pushPendingAction(conversationId, OWNER, new PendingAction(
                "pending-" + index,
                Map.of("ticketNumber", "T-" + index),
                null,
                Instant.now()
            ));
        }
        assertThat(pendingActionStore.getPendingActionStack(conversationId, OWNER))
            .extracting(PendingAction::action)
            .containsExactly("pending-5", "pending-4", "pending-3", "pending-2");
    }

    @Test
    void neverPersistSkipsBothHistoryEnrichmentAndTurnRecording() {
        String conversationId = "memory-transient";
        generationProvider.response(actionIntent(
            "get_my_ticket_status", "{\"ticketNumber\":\"T-1001\"}"));
        orchestrationService.orchestrate("Read T-1001", conversationId, PRINCIPAL);
        assertThat(chatSessionService.getSession(conversationId, OWNER).getTurns()).hasSize(1);

        generationProvider.response(actionIntent(
            "get_my_ticket_status", "{\"ticketNumber\":\"T-1001\"}"));
        SupportOrchestrationResponse transientResult = orchestrationService.orchestrateTransient(
            "Read it without storing this turn", conversationId, PRINCIPAL);

        assertThat(transientResult.result().getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(generationProvider.lastMessages()).isEmpty();
        assertThat(chatSessionService.getSession(conversationId, OWNER).getTurns()).hasSize(1);
    }

    @Test
    void pinnedTargetsExpireAndFreshAttachmentsTakePrecedence() {
        String expiringConversation = "memory-target-expiry";
        generationProvider.response(actionIntent(
            "get_my_ticket_status", "{\"ticketNumber\":\"T-1001\"}"));
        orchestrationService.orchestrate("Read T-1001", expiringConversation, PRINCIPAL);

        for (int turn = 1; turn <= 5; turn++) {
            generationProvider.response(actionIntent("create_support_ticket", "{}"));
            orchestrationService.orchestrate("Follow-up " + turn, expiringConversation, PRINCIPAL);
            if (turn == 1) {
                assertThat(generationProvider.lastPrompt())
                    .contains("PINNED TARGETS (previously pinned; not current UI selection)")
                    .contains("T-1001");
            }
        }
        assertThat(generationProvider.lastPrompt())
            .doesNotContain("PINNED TARGETS (previously pinned; not current UI selection)");

        String attachmentConversation = "memory-fresh-attachment";
        generationProvider.response(actionIntent(
            "get_my_ticket_status", "{\"ticketNumber\":\"T-1001\"}"));
        orchestrationService.orchestrate("Read T-1001", attachmentConversation, PRINCIPAL);

        OrchestrationAttachment fresh = OrchestrationAttachment.builder()
            .id("T-2002")
            .vectorSpace("support-ticket")
            .contentText("A freshly selected support ticket")
            .source("course-test-selection")
            .build();
        generationProvider.response(actionIntent("create_support_ticket", "{}"));
        orchestrationService.orchestrate(
            "Use this selection", attachmentConversation, List.of(fresh), PRINCIPAL);

        assertThat(generationProvider.lastPrompt())
            .contains("ATTACHMENTS (user context; pinned targets)")
            .contains("T-2002")
            .doesNotContain("PINNED TARGETS (previously pinned; not current UI selection)");
    }

    @Test
    void reopeningHistoryDoesNotMutateOrDeleteTheConversation() throws Exception {
        String conversationId = "memory-reopen";
        generationProvider.response(actionIntent(
            "get_my_ticket_status", "{\"ticketNumber\":\"T-1001\"}"));
        orchestrationService.orchestrate("Read T-1001", conversationId, PRINCIPAL);

        mockMvc.perform(get("/api/assistant/conversations/{conversationId}", conversationId)
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.turns.length()").value(1));
        mockMvc.perform(get("/api/assistant/conversations/{conversationId}", conversationId)
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.turns.length()").value(1));

        assertThat(chatSessionService.getSession(conversationId, OWNER).getTurns()).hasSize(1);
    }

    @Test
    void invalidConversationIdIsRejectedAtTheHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/assistant/orchestrate")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"Read my ticket","conversationId":"not allowed"}
                    """))
            .andExpect(status().isBadRequest());
    }

    private String actionIntent(String action, String paramsJson) {
        return """
            {
              "intents": [
                {
                  "type": "ACTION",
                  "intent": "%s",
                  "action": "%s",
                  "confidence": 0.99,
                  "actionParams": %s,
                  "requiresRetrieval": false,
                  "requiresGeneration": false
                }
              ],
              "orchestrationStrategy": "DIRECT_ACTION"
            }
            """.formatted(action, action, paramsJson);
    }

    private String confirmationIntent(String type) {
        String intent = type.equals("CONFIRMATION_POSITIVE") ? "confirm" : "reject";
        return """
            {
              "intents": [{"type":"%s","intent":"%s","confidence":0.99}],
              "orchestrationStrategy":"DIRECT_ACTION"
            }
            """.formatted(type, intent);
    }
}
