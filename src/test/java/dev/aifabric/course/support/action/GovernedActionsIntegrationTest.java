package dev.aifabric.course.support.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.PendingActionStore;
import ai.fabric.intent.orchestration.OrchestrationResultType;
import dev.aifabric.course.support.assistant.SupportOrchestrationResponse;
import dev.aifabric.course.support.assistant.SupportOrchestrationService;
import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration.CourseTestGenerationProvider;
import dev.aifabric.course.support.ticket.SupportTicketRepository;
import dev.aifabric.course.support.ticket.SupportTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CourseTestAIConfiguration.class)
class GovernedActionsIntegrationTest {

    private static final String CONVERSATION = "course-action-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseDataService dataService;

    @Autowired
    private CourseTestGenerationProvider generationProvider;

    @Autowired
    private AIActionRegistry actionRegistry;

    @Autowired
    private PendingActionStore pendingActionStore;

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private SupportTicketService ticketService;

    @Autowired
    private SupportOrchestrationService orchestrationService;

    @BeforeEach
    void reset() {
        generationProvider.reset();
        pendingActionStore.clearPendingActions(CONVERSATION, CourseDataService.COURSE_CUSTOMER);
        dataService.seed();
    }

    @Test
    void registryPublishesOnlyUserOwnedActionParameters() {
        AIActionMetaData read = actionRegistry.findMetadata("get_my_ticket_status").orElseThrow();
        assertThat(read.getAccessMode().name()).isEqualTo("READ");
        assertThat(read.isConfirmationRequired()).isFalse();
        assertThat(read.isReadActionResolutionEligible()).isTrue();
        assertThat(read.getParameters()).containsOnlyKeys("ticketNumber");
        assertThat(read.getRequiredParameters()).containsExactly("ticketNumber");

        AIActionMetaData write = actionRegistry.findMetadata("create_support_ticket").orElseThrow();
        assertThat(write.getAccessMode().name()).isEqualTo("WRITE_ONLY");
        assertThat(write.isConfirmationRequired()).isTrue();
        assertThat(write.getParameters()).containsOnlyKeys("subject", "description", "priority");
        assertThat(write.getRequiredParameters()).containsExactlyInAnyOrder("subject", "description");
        assertThat(write.getParameters()).doesNotContainKeys(
            "userId", "tenantId", "customerId", "conversationId", "sessionId");

        AIActionMetaData escalation = actionRegistry.findMetadata("escalate_support_ticket").orElseThrow();
        assertThat(escalation.getAccessMode().name()).isEqualTo("WRITE_ONLY");
        assertThat(escalation.isConfirmationRequired()).isTrue();
        assertThat(escalation.getParameters()).containsOnlyKeys("ticketNumber");
        assertThat(escalation.getRequiredParameters()).containsExactly("ticketNumber");
        assertThat(escalation.getParameters()).doesNotContainKeys(
            "userId", "tenantId", "customerId", "conversationId", "sessionId");
    }

    @Test
    void missingRequiredDescriptionClarifiesWithoutPendingOrMutation() throws Exception {
        generationProvider.response(actionIntent(
            "create_support_ticket",
            "{\"subject\":\"Account locked\",\"priority\":\"HIGH\"}"
        ));

        mockMvc.perform(post("/api/assistant/orchestrate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("Create an urgent account locked ticket", CONVERSATION)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId").value(CONVERSATION))
            .andExpect(jsonPath("$.result.type").value("CLARIFICATION_REQUIRED"))
            .andExpect(jsonPath("$.result.data.action").value("create_support_ticket"))
            .andExpect(jsonPath("$.result.data.missingRequiredParameters[0]").value("description"));

        assertThat(ticketRepository.count()).isOne();
        assertThat(pendingActionStore.peekPendingAction(CONVERSATION, CourseDataService.COURSE_CUSTOMER)).isEmpty();
    }

    @Test
    void rejectionAndConfirmationConsumePendingWorkExactlyOnce() throws Exception {
        generationProvider.response(completeCreateIntent());
        requestAction("Create a high priority ticket about missing recovery emails", CONVERSATION)
            .andExpect(jsonPath("$.result.type").value("CONFIRMATION_REQUIRED"))
            .andExpect(jsonPath("$.result.data.action").value("create_support_ticket"))
            .andExpect(jsonPath("$.result.data.confirmationRequired").value(true));

        assertThat(ticketRepository.count()).isOne();
        assertThat(pendingActionStore.peekPendingAction(CONVERSATION, CourseDataService.COURSE_CUSTOMER))
            .get().extracting(pending -> pending.action()).isEqualTo("create_support_ticket");

        generationProvider.response(confirmationIntent("CONFIRMATION_NEGATIVE"));
        requestAction("no", CONVERSATION)
            .andExpect(jsonPath("$.result.type").value("INFORMATION_PROVIDED"));
        assertThat(ticketRepository.count()).isOne();
        assertThat(pendingActionStore.peekPendingAction(CONVERSATION, CourseDataService.COURSE_CUSTOMER)).isEmpty();

        generationProvider.response(completeCreateIntent());
        requestAction("Create the support ticket", CONVERSATION)
            .andExpect(jsonPath("$.result.type").value("CONFIRMATION_REQUIRED"));

        generationProvider.response(confirmationIntent("CONFIRMATION_POSITIVE"));
        requestAction("yes", CONVERSATION)
            .andExpect(jsonPath("$.result.type").value("ACTION_EXECUTED"))
            .andExpect(jsonPath("$.result.success").value(true))
            .andExpect(jsonPath("$.result.data.actionResult.data.ticketNumber").exists())
            .andExpect(jsonPath("$.result.data.actionResult.data.status").value("OPEN"))
            .andExpect(jsonPath("$.result.data.actionResult.data.priority").value("HIGH"));

        assertThat(ticketRepository.count()).isEqualTo(2);
        assertThat(pendingActionStore.peekPendingAction(CONVERSATION, CourseDataService.COURSE_CUSTOMER)).isEmpty();

        generationProvider.response(confirmationIntent("CONFIRMATION_POSITIVE"));
        requestAction("yes", CONVERSATION)
            .andExpect(jsonPath("$.result.type").value("INFORMATION_PROVIDED"))
            .andExpect(jsonPath("$.result.message").value("There is no pending action to confirm."));
        assertThat(ticketRepository.count()).isEqualTo(2);
    }

    @Test
    void readActionReturnsOnlyTheCurrentCustomersTicketProjection() throws Exception {
        generationProvider.response(actionIntent(
            "get_my_ticket_status",
            "{\"ticketNumber\":\"T-1001\"}"
        ));

        requestAction("What is the status of T-1001?", CONVERSATION)
            .andExpect(jsonPath("$.result.type").value("ACTION_EXECUTED"))
            .andExpect(jsonPath("$.result.success").value(true))
            .andExpect(jsonPath("$.result.data.actionResult.data.ticketNumber").value("T-1001"))
            .andExpect(jsonPath("$.result.data.actionResult.data.status").value("OPEN"))
            .andExpect(jsonPath("$.result.data.actionResult.data.priority").value("MEDIUM"))
            .andExpect(jsonPath("$.result.data.actionResult.data.description").doesNotExist())
            .andExpect(jsonPath("$.result.data.actionResult.data.tenantId").doesNotExist())
            .andExpect(jsonPath("$.result.data.actionResult.data.customerId").doesNotExist());
    }

    @Test
    void missingIdentityAndCrossTenantContextAreDeniedBeforeExecution() {
        generationProvider.response(completeCreateIntent());
        SupportOrchestrationResponse anonymous = orchestrationService.orchestrate(
            "Create a support ticket",
            "anonymous-action-test",
            CoursePrincipal.anonymous("anonymous-session")
        );
        assertThat(anonymous.result().getType()).isEqualTo(OrchestrationResultType.ERROR);
        assertThat(anonymous.result().getErrorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(ticketRepository.count()).isOne();

        generationProvider.response(actionIntent(
            "get_my_ticket_status",
            "{\"ticketNumber\":\"T-1001\"}"
        ));
        SupportOrchestrationResponse wrongTenant = orchestrationService.orchestrate(
            "Read T-1001",
            "wrong-tenant-action-test",
            new CoursePrincipal(CourseDataService.COURSE_CUSTOMER, "tenant-red", "wrong-tenant-session")
        );
        assertThat(wrongTenant.result().getType()).isEqualTo(OrchestrationResultType.ACTION_DENIED);
        assertThat(ticketRepository.count()).isOne();

        assertThatThrownByTenantScopeRecheck();
    }

    private void assertThatThrownByTenantScopeRecheck() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            ticketService.getForCurrentCustomer(
                "T-1001", CourseDataService.COURSE_CUSTOMER, "tenant-red")))
            .isInstanceOf(dev.aifabric.course.support.ticket.TicketAccessDeniedException.class);
    }

    private org.springframework.test.web.servlet.ResultActions requestAction(String message, String conversationId)
        throws Exception {
        return mockMvc.perform(post("/api/assistant/orchestrate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(message, conversationId)))
            .andExpect(status().isOk());
    }

    private String request(String message, String conversationId) {
        return "{\"message\":\"" + message + "\",\"conversationId\":\"" + conversationId + "\"}";
    }

    private String completeCreateIntent() {
        return actionIntent(
            "create_support_ticket",
            "{\"subject\":\"Account locked\",\"description\":\"Recovery emails never arrive\",\"priority\":\"HIGH\"}"
        );
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
