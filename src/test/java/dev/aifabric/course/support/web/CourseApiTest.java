package dev.aifabric.course.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration.CourseTestGenerationProvider;
import ai.fabric.spi.RAGProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CourseTestAIConfiguration.class)
class CourseApiTest {

    private static final String ALEX_BEARER = "Bearer course-alex-local-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseTestGenerationProvider generationProvider;

    @Autowired
    private java.util.List<RAGProvider> ragProviders;

    @BeforeEach
    void reset() throws Exception {
        generationProvider.reset();
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    @Test
    void sourceDataAndVectorEvidenceHaveSeparateLifecycle() throws Exception {
        mockMvc.perform(post("/api/demo/seed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.articles").value(9))
            .andExpect(jsonPath("$.policies").value(2));

        mockMvc.perform(get("/api/demo/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkpoint").value("course-0.3.3-p03-prompt-overlays"))
            .andExpect(jsonPath("$.sourceRecords.articles").value(9))
            .andExpect(jsonPath("$.indexedVectors").value(0))
            .andExpect(jsonPath("$.capabilities.semanticSearch").value(true))
            .andExpect(jsonPath("$.capabilities.rag").value(true))
            .andExpect(jsonPath("$.capabilities.governedActions").value(true))
            .andExpect(jsonPath("$.capabilities.conversationMemory").value(true))
            .andExpect(jsonPath("$.capabilities.tenantSecurity").value(true))
            .andExpect(jsonPath("$.capabilities.piiProtection").value(true));

        mockMvc.perform(get("/api/knowledge/search")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .param("q", "I cannot sign in after too many attempts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCount").value(0));

        mockMvc.perform(post("/api/demo/index"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indexedArticles").value(9))
            .andExpect(jsonPath("$.indexedVectors").value(9));

        mockMvc.perform(get("/api/knowledge/search")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .param("q", "I cannot sign in after too many attempts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence[0].evidenceId").value("policy-account-lockout-01"))
            .andExpect(jsonPath("$.evidence[0].metadata.category").value("authentication-policy"))
            .andExpect(jsonPath("$.evidence[0].metadata.tenantId").doesNotExist())
            .andExpect(jsonPath("$.evidence[0].metadata.raw").doesNotExist())
            .andExpect(jsonPath("$.evidence[0].content").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("fraud review"))));
    }

    @Test
    void healthReportsBuildAndProviderPostureWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/demo/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("ai-fabric-course-support-assistant"))
            .andExpect(jsonPath("$.version").isNotEmpty())
            .andExpect(jsonPath("$.aiFabricVersion").value("0.3.3"))
            .andExpect(jsonPath("$.checkpoint").value("course-0.3.3-p03-prompt-overlays"))
            .andExpect(jsonPath("$.provider.mode").value("deterministic-test"))
            .andExpect(jsonPath("$.provider.orchestration").value("course-orchestration-test"))
            .andExpect(jsonPath("$.provider.orchestrationModel").value("course-test-orchestration"))
            .andExpect(jsonPath("$.provider.generation").value("course-generation-test"))
            .andExpect(jsonPath("$.provider.generationModel").value("course-test-generation"))
            .andExpect(jsonPath("$.provider.embedding").value("course-test"))
            .andExpect(jsonPath("$.provider.vector").value("memory"))
            .andExpect(jsonPath("$.provider.fallbackEnabled").value(false))
            .andExpect(jsonPath("$.openAiApiKey").doesNotExist())
            .andExpect(jsonPath("$.courseToken").doesNotExist());
    }

    @Test
    void updateAndDeleteReplaceTheEvidenceWithoutLeavingStaleVectors() throws Exception {
        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/index")).andExpect(status().isOk());

        mockMvc.perform(put("/api/knowledge/articles/article-billing-method")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Download a billing invoice",
                      "body": "Open Billing History and download the invoice or receipt for the selected renewal."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Download a billing invoice"));

        mockMvc.perform(get("/api/knowledge/search")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .param("q", "Where can I download my invoice receipt?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence[0].evidenceId").value("article-billing-method"))
            .andExpect(jsonPath("$.evidence[0].content").value(
                org.hamcrest.Matchers.containsString("download the invoice")))
            .andExpect(jsonPath("$.evidence[0].content").value(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("replacement method"))));

        mockMvc.perform(delete("/api/knowledge/articles/article-billing-method")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/demo/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceRecords.articles").value(8))
            .andExpect(jsonPath("$.indexedVectors").value(8));
    }

    @Test
    void ragRuntimeIsPresentAndNoEvidenceSkipsGeneration() throws Exception {
        org.assertj.core.api.Assertions.assertThat(ragProviders).hasSize(1);

        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());

        mockMvc.perform(post("/api/assistant/query")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"What should I do if failed sign-ins locked me out?"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NO_EVIDENCE"))
            .andExpect(jsonPath("$.answer").doesNotExist())
            .andExpect(jsonPath("$.evidence").isEmpty())
            .andExpect(jsonPath("$.diagnostics.retrievalSucceeded").value(true))
            .andExpect(jsonPath("$.diagnostics.generationAttempted").value(false));

        org.assertj.core.api.Assertions.assertThat(generationProvider.generationCalls()).isZero();
    }

    @Test
    void indexedEvidenceProducesGroundedAnswerWithValidatedCitation() throws Exception {
        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/index")).andExpect(status().isOk());

        mockMvc.perform(post("/api/assistant/query")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"What should I do if failed sign-ins locked me out?"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ANSWERED"))
            .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("fifteen minutes")))
            .andExpect(jsonPath("$.mode").value("EVIDENCE_GROUNDED"))
            .andExpect(jsonPath("$.evidence[0].id").value("policy-account-lockout-01"))
            .andExpect(jsonPath("$.evidence[0].category").value("authentication-policy"))
            .andExpect(jsonPath("$.diagnostics.generationAttempted").value(true))
            .andExpect(jsonPath("$.diagnostics.requestId").value("course-test-generation-request"));

        org.assertj.core.api.Assertions.assertThat(generationProvider.generationCalls()).isOne();
        org.assertj.core.api.Assertions.assertThat(generationProvider.lastPrompt())
            .contains("policy-account-lockout-01")
            .contains("after repeated failed sign-in attempts")
            .doesNotContain("fraud-review")
            .doesNotContain("staff-only");
    }

    @Test
    void providerFailureRemainsVisibleAndHasNoCannedAnswer() throws Exception {
        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/index")).andExpect(status().isOk());
        generationProvider.failNext();

        mockMvc.perform(post("/api/assistant/query")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"What should I do if failed sign-ins locked me out?"}
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("GENERATION_FAILED"))
            .andExpect(jsonPath("$.answer").doesNotExist())
            .andExpect(jsonPath("$.evidence").isEmpty())
            .andExpect(jsonPath("$.diagnostics.errorCode").value("LLM_GENERATION_FAILED"));
    }

    @Test
    void clearingVectorsPreservesSourceRowsAndReturnsToNoEvidence() throws Exception {
        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/index")).andExpect(status().isOk());

        mockMvc.perform(post("/api/demo/vectors/clear"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceRecords.articles").value(9))
            .andExpect(jsonPath("$.indexedVectors").value(0));

        mockMvc.perform(post("/api/assistant/query")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"What should I do if failed sign-ins locked me out?"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NO_EVIDENCE"))
            .andExpect(jsonPath("$.diagnostics.generationAttempted").value(false));

        org.assertj.core.api.Assertions.assertThat(generationProvider.generationCalls()).isZero();
    }
}
