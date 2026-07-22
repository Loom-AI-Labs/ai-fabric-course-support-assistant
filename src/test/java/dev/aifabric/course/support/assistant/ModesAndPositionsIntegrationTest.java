package dev.aifabric.course.support.assistant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration.CourseTestGenerationProvider;
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
class ModesAndPositionsIntegrationTest {

    private static final String AUTHORIZATION = "Bearer course-alex-local-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseDataService dataService;

    @Autowired
    private CourseTestGenerationProvider generationProvider;

    @BeforeEach
    void reset() {
        generationProvider.reset();
        dataService.seed();
    }

    @Test
    void knowledgePositionMapsToRetrievalOnlyModeAndBlocksWriteIntent() throws Exception {
        generationProvider.response(createTicketIntent());

        mockMvc.perform(orchestrationRequest("knowledge", null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.type").value("CLARIFICATION_REQUIRED"))
            .andExpect(jsonPath("$.result.data.reason").value("ACTIONS_DISABLED_BY_POLICY"))
            .andExpect(jsonPath("$.result.metadata.orchestrationPolicy.mode").value("support_assistant"))
            .andExpect(jsonPath("$.result.metadata.orchestrationPolicy.position").value("knowledge"))
            .andExpect(jsonPath("$.result.metadata.orchestrationPolicy.actionsEnabled").value(false))
            .andExpect(jsonPath("$.result.metadata.orchestrationPolicy.retrievalEnabled").value(true));
    }

    @Test
    void ticketPositionMapsToResolverModeAndAllowsGovernedConfirmation() throws Exception {
        generationProvider.response(createTicketIntent());

        mockMvc.perform(orchestrationRequest("ticket", null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.type").value("CONFIRMATION_REQUIRED"))
            .andExpect(jsonPath("$.result.data.action").value("create_support_ticket"))
            .andExpect(jsonPath("$.result.metadata.orchestrationPolicy.mode").value("support_resolver"))
            .andExpect(jsonPath("$.result.metadata.orchestrationPolicy.position").value("ticket"))
            .andExpect(jsonPath("$.result.metadata.orchestrationPolicy.actionsEnabled").value(true));
    }

    @Test
    void explicitApprovedModeWinsOverThePositionMap() throws Exception {
        generationProvider.response(createTicketIntent());

        mockMvc.perform(orchestrationRequest("ticket", "support_assistant"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.data.reason").value("ACTIONS_DISABLED_BY_POLICY"))
            .andExpect(jsonPath("$.result.metadata.orchestrationPolicy.mode").value("support_assistant"))
            .andExpect(jsonPath("$.result.metadata.orchestrationPolicy.modeSource").value("REQUEST_MODE"));
    }

    @Test
    void unknownModeIsRejectedByStrictCoreRouting() throws Exception {
        generationProvider.response(createTicketIntent());

        mockMvc.perform(orchestrationRequest("ticket", "untrusted_admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.type").value("ERROR"))
            .andExpect(jsonPath("$.result.message").value("Unsupported mode: untrusted_admin"));
    }

    @Test
    void unknownPositionIsRejectedAtTheApplicationBoundary() throws Exception {
        mockMvc.perform(orchestrationRequest("admin-console", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Unsupported support position: admin-console"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder orchestrationRequest(
        String position, String mode
    ) {
        String modeJson = mode == null ? "" : ",\"mode\":\"" + mode + "\"";
        return post("/api/assistant/orchestrate")
            .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"message":"Create a ticket about account recovery","conversationId":"prod-02-routing","position":"%s"%s}
                """.formatted(position, modeJson));
    }

    private String createTicketIntent() {
        return """
            {
              "intents": [{
                "type": "ACTION",
                "intent": "create_support_ticket",
                "action": "create_support_ticket",
                "confidence": 0.99,
                "actionParams": {
                  "subject": "Account recovery",
                  "description": "Recovery email did not arrive"
                },
                "requiresRetrieval": false,
                "requiresGeneration": false
              }],
              "orchestrationStrategy": "DIRECT_ACTION"
            }
            """;
    }
}
