package dev.aifabric.course.support.quality;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
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
class RagQualityIntegrationTest {

    private static final String ALEX = "Bearer course-alex-local-token";
    private static final String RILEY = "Bearer course-riley-local-token";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void seedAndIndex() throws Exception {
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/index")).andExpect(status().isOk());
    }

    @Test
    void goldenSuitesAssertExpectedEvidenceAndTenantExclusions() throws Exception {
        mockMvc.perform(get("/api/quality/rag/golden")
                .header(HttpHeaders.AUTHORIZATION, ALEX))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suiteId").value("support-knowledge-golden-v1"))
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.totalCases").value(3))
            .andExpect(jsonPath("$.failedCases").value(0))
            .andExpect(jsonPath("$.cases[0].expectedEvidenceIds", hasItem("policy-account-lockout-01")))
            .andExpect(jsonPath("$.cases[0].observedEvidenceIds", hasItem("policy-account-lockout-01")))
            .andExpect(jsonPath("$.cases[2].observedEvidenceIds", hasItem("article-vpn-blue")))
            .andExpect(jsonPath("$.cases[2].observedEvidenceIds", not(hasItem("article-vpn-red"))));

        mockMvc.perform(get("/api/quality/rag/golden")
                .header(HttpHeaders.AUTHORIZATION, RILEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.totalCases").value(1))
            .andExpect(jsonPath("$.cases[0].observedEvidenceIds", hasItem("article-vpn-red")))
            .andExpect(jsonPath("$.cases[0].observedEvidenceIds", not(hasItem("article-vpn-blue"))))
            .andExpect(jsonPath("$.cases[0].observedEvidenceIds", not(hasItem("article-payroll-red-restricted"))));
    }

    @Test
    void noSourceAndInsufficientContextAreDifferentVisibleOutcomes() throws Exception {
        mockMvc.perform(post("/api/demo/vectors/clear")).andExpect(status().isOk());

        mockMvc.perform(post("/api/quality/rag/evaluate")
                .header(HttpHeaders.AUTHORIZATION, ALEX)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "caseId":"empty-index",
                      "question":"How do I recover access?",
                      "expectNoEvidence":true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.observedEvidenceIds").isEmpty());

        mockMvc.perform(post("/api/demo/index")).andExpect(status().isOk());
        mockMvc.perform(post("/api/quality/rag/evaluate")
                .header(HttpHeaders.AUTHORIZATION, ALEX)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "caseId":"missing-retention-policy",
                      "question":"How long are audit logs retained?",
                      "expectedEvidenceIds":["article-audit-retention"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passed").value(false))
            .andExpect(jsonPath("$.missingEvidenceIds[0]").value("article-audit-retention"))
            .andExpect(jsonPath("$.failureCodes", hasItem("EXPECTED_EVIDENCE_MISSING")));
    }

    @Test
    void updatedEvidencePassesFreshnessChecksAndRejectsTheStaleText() throws Exception {
        mockMvc.perform(put("/api/knowledge/articles/article-billing-method")
                .header(HttpHeaders.AUTHORIZATION, ALEX)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"Download a billing invoice",
                      "body":"Open Billing History and download the invoice for the selected renewal."
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/quality/rag/evaluate")
                .header(HttpHeaders.AUTHORIZATION, ALEX)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "caseId":"fresh-billing-copy",
                      "question":"Where can I download my invoice?",
                      "expectedEvidenceIds":["article-billing-method"],
                      "requiredContentFragments":["download the invoice"],
                      "forbiddenContentFragments":["replacement method"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.observedEvidenceIds", hasItem("article-billing-method")))
            .andExpect(jsonPath("$.returnedStaleContentFragments").isEmpty());
    }

    @Test
    void promptRegressionChecksStructureAndFallbackWithoutPublishingPromptBodies() throws Exception {
        mockMvc.perform(get("/api/quality/prompts")
                .header(HttpHeaders.AUTHORIZATION, ALEX))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.supportAnswerVersion").value("v1-course-support"))
            .andExpect(jsonPath("$.baseFallbackVersion").value("v1"))
            .andExpect(jsonPath("$.querySlotPresent").value(true))
            .andExpect(jsonPath("$.contextSlotPresent").value(true))
            .andExpect(jsonPath("$.template").doesNotExist());
    }

    @Test
    void invalidQualityContractIsRejectedInsteadOfSilentlyTruncated() throws Exception {
        mockMvc.perform(post("/api/quality/rag/evaluate")
                .header(HttpHeaders.AUTHORIZATION, ALEX)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "caseId":"contradictory-case",
                      "question":"How do I recover access?",
                      "expectedEvidenceIds":["policy-account-lockout-01"],
                      "expectNoEvidence":true
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("A no-evidence case cannot require evidence IDs"));
    }
}
