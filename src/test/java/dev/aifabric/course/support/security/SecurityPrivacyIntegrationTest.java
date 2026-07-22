package dev.aifabric.course.support.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.fabric.access.AIAccessControlService;
import ai.fabric.access.policy.EntityAccessPolicy;
import ai.fabric.chat.domain.ChatSession;
import ai.fabric.chat.service.ChatSessionService;
import ai.fabric.dto.AIAccessControlRequest;
import ai.fabric.dto.AIAccessControlResponse;
import ai.fabric.dto.AIAccessSubjectContext;
import ai.fabric.dto.VectorRecord;
import ai.fabric.rag.VectorDatabaseService;
import dev.aifabric.course.support.assistant.SupportOrchestrationResponse;
import dev.aifabric.course.support.assistant.SupportOrchestrationService;
import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import dev.aifabric.course.support.message.SupportMessage;
import dev.aifabric.course.support.message.SupportMessageRepository;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration.CourseTestGenerationProvider;
import java.time.Clock;
import java.util.List;
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
class SecurityPrivacyIntegrationTest {

    private static final String ALEX_BEARER = "Bearer course-alex-local-token";
    private static final String RILEY_BEARER = "Bearer course-riley-local-token";
    private static final CoursePrincipal ALEX = new CoursePrincipal(
        CourseDataService.COURSE_CUSTOMER,
        CourseDataService.COURSE_TENANT,
        "security-course-session"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseTestGenerationProvider generationProvider;

    @Autowired
    private SupportMessageRepository messageRepository;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @Autowired
    private SupportOrchestrationService orchestrationService;

    @Autowired
    private ChatSessionService chatSessionService;

    @BeforeEach
    void resetAndIndex() throws Exception {
        generationProvider.reset();
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/index")).andExpect(status().isOk());
    }

    @Test
    void protectedApisRequireAServerVerifiedBearerIdentity() throws Exception {
        mockMvc.perform(get("/api/knowledge/articles"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/knowledge/articles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-course-token"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/knowledge/articles")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .header("X-User-Id", CourseDataService.SECOND_CUSTOMER)
                .header("X-Tenant-Id", CourseDataService.SECOND_TENANT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == 'article-vpn-blue')]").exists())
            .andExpect(jsonPath("$[?(@.id == 'article-vpn-red')]").doesNotExist())
            .andExpect(jsonPath("$[?(@.id == 'article-payroll-red-restricted')]").doesNotExist())
            .andExpect(jsonPath("$[0].tenantId").doesNotExist());
    }

    @Test
    void retrievalIsTenantFilteredAndRestrictedEvidenceNeverLeavesTheBoundary() throws Exception {
        String alexResponse = mockMvc.perform(get("/api/knowledge/search")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .param("q", "How do I restore VPN access and refresh my certificate?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.evidence[0].evidenceId").value("article-vpn-blue"))
            .andExpect(jsonPath("$.evidence[0].metadata.tenantId").doesNotExist())
            .andExpect(jsonPath("$.evidence[0].metadata.raw").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        assertThat(alexResponse)
            .doesNotContain("article-vpn-red")
            .doesNotContain("article-payroll-red-restricted")
            .doesNotContain("replacement device");

        String rileyResponse = mockMvc.perform(get("/api/knowledge/search")
                .header(HttpHeaders.AUTHORIZATION, RILEY_BEARER)
                .param("q", "How do I restore VPN access and enroll my replacement device?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCount").value(1))
            .andExpect(jsonPath("$.evidence[0].evidenceId").value("article-vpn-red"))
            .andExpect(jsonPath("$.evidence[0].metadata.tenantId").doesNotExist())
            .andExpect(jsonPath("$.evidence[0].metadata.raw").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        assertThat(rileyResponse)
            .doesNotContain("article-vpn-blue")
            .doesNotContain("article-payroll-red-restricted")
            .doesNotContain("refresh their device certificate");
    }

    @Test
    void explicitMessageIntakeStoresAndIndexesOnlyRedactedText() throws Exception {
        String rawEmail = "alex.private@example.com";
        String rawSsn = "123-45-6789";

        String response = mockMvc.perform(post("/api/support/messages")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Contact " + rawEmail + " about SSN " + rawSsn + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.piiDetected").value(true))
            .andExpect(jsonPath("$.detectedTypes").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(rawEmail).doesNotContain(rawSsn);
        SupportMessage stored = messageRepository.findAll().getFirst();
        assertThat(stored.getSafeContent())
            .doesNotContain(rawEmail)
            .doesNotContain(rawSsn)
            .contains("***");

        VectorRecord vector = vectorDatabaseService
            .getVectorByEntity(SupportMessage.ENTITY_TYPE, stored.getId())
            .orElseThrow();
        assertThat(vector.getContent()).doesNotContain(rawEmail).doesNotContain(rawSsn);
        assertThat(String.valueOf(vector.getMetadata())).doesNotContain(rawEmail).doesNotContain(rawSsn);

        String listed = mockMvc.perform(get("/api/support/messages")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(listed).doesNotContain(rawEmail).doesNotContain(rawSsn);
    }

    @Test
    void directRagRedactsInputBeforeTheProviderAndSanitizesGeneratedOutput() throws Exception {
        String rawInputEmail = "question.owner@example.com";
        String rawOutputEmail = "agent.private@example.com";
        String rawOutputSsn = "987-65-4321";
        generationProvider.response("""
            {"answer":"Contact agent.private@example.com and quote 987-65-4321.","citationIds":["policy-account-lockout-01"]}
            """);

        String response = mockMvc.perform(post("/api/assistant/query")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"My email is " + rawInputEmail
                    + " and failed sign-ins locked my account\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ANSWERED"))
            .andReturn().getResponse().getContentAsString();

        assertThat(generationProvider.lastPrompt()).doesNotContain(rawInputEmail);
        assertThat(response)
            .doesNotContain(rawInputEmail)
            .doesNotContain(rawOutputEmail)
            .doesNotContain(rawOutputSsn)
            .contains("***");
    }

    @Test
    void orchestrationHistoryStoresTheRedactedTurnRatherThanRawPii() {
        String rawEmail = "history.owner@example.com";
        String conversationId = "security-redacted-history";
        generationProvider.response("""
            {
              "intents": [{
                "type":"INFORMATION",
                "intent":"account_help",
                "confidence":0.99,
                "requiresRetrieval":false,
                "requiresGeneration":false
              }],
              "orchestrationStrategy":"DIRECT_GENERATION"
            }
            """);

        SupportOrchestrationResponse response = orchestrationService.orchestrate(
            "My email is " + rawEmail + "; explain account recovery.",
            conversationId,
            ALEX
        );

        assertThat(response.result()).isNotNull();
        assertThat(generationProvider.lastPrompt()).doesNotContain(rawEmail);
        ChatSession session = chatSessionService.getSession(conversationId, ALEX.userId());
        assertThat(session.getTurns()).hasSize(1);
        assertThat(session.getTurns().getFirst().getUserQuery())
            .doesNotContain(rawEmail)
            .contains("***");
    }

    @Test
    void accessPolicyDeniesMissingScopeAndFailsClosedWhenTheHookThrows() {
        AIAccessSubjectContext allowedSubject = subject(List.of("support:read"));
        AIAccessControlRequest allowedRequest = request(allowedSubject);
        AIAccessControlService configured = new AIAccessControlService(
            Clock.systemUTC(), coursePolicy());

        assertThat(configured.checkAccess(allowedRequest).getAccessGranted()).isTrue();
        assertThat(configured.checkAccess(request(subject(List.of()))).getAccessGranted()).isFalse();

        EntityAccessPolicy brokenPolicy = (authContext, entity) -> {
            throw new IllegalStateException("policy dependency unavailable");
        };
        AIAccessControlResponse failedClosed = new AIAccessControlService(
            Clock.systemUTC(), brokenPolicy).checkAccess(allowedRequest);

        assertThat(failedClosed.getAccessGranted()).isFalse();
        assertThat(failedClosed.getSuccess()).isFalse();
    }

    private EntityAccessPolicy coursePolicy() {
        return (authContext, entity) -> authContext != null
            && CourseDataService.COURSE_TENANT.equals(authContext.getTenantId())
            && authContext.getGrantedScopes().contains("support:read")
            && "rag:intent".equals(entity.get("resourceId"))
            && "READ".equals(entity.get("operationType"));
    }

    private AIAccessSubjectContext subject(List<String> scopes) {
        return AIAccessSubjectContext.builder()
            .subjectId(CourseDataService.COURSE_CUSTOMER)
            .sessionId("security-access-session")
            .subjectType("END_USER")
            .tenantId(CourseDataService.COURSE_TENANT)
            .grantedScopes(scopes)
            .build();
    }

    private AIAccessControlRequest request(AIAccessSubjectContext subject) {
        return AIAccessControlRequest.builder()
            .requestId("security-access-request")
            .authContext(subject)
            .resourceId("rag:intent")
            .operationType("READ")
            .build();
    }
}
