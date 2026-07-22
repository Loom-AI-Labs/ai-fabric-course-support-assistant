package dev.aifabric.course.support.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.fabric.indexing.IndexingStatus;
import ai.fabric.migration.domain.MigrationJob;
import ai.fabric.migration.domain.MigrationStatus;
import ai.fabric.migration.repository.MigrationJobRepository;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.repository.IndexingQueueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aifabric.course.support.knowledge.KnowledgeArticle;
import dev.aifabric.course.support.knowledge.KnowledgeArticleRepository;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
class KnowledgeMigrationIntegrationTest {

    private static final String ALEX_BEARER = "Bearer course-alex-local-token";
    private static final String RILEY_BEARER = "Bearer course-riley-local-token";
    private static final String MIGRATIONS = "/api/admin/migrations/knowledge-articles";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MigrationJobRepository jobRepository;
    @Autowired private IndexingQueueRepository queueRepository;
    @Autowired private KnowledgeArticleRepository articleRepository;
    @Autowired private VectorDatabaseService vectorDatabaseService;

    @BeforeEach
    void reset() throws Exception {
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
        queueRepository.deleteAll();
        jobRepository.deleteAll();
        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
    }

    @Test
    void boundedBackfillIsRetrievableTenantSafeAndIdempotent() throws Exception {
        assertThat(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE)).isZero();

        String firstJobId = start("""
            {"batchSize": 3, "rateLimit": 0, "reindexExisting": false}
            """);
        awaitJob(firstJobId, Set.of(MigrationStatus.COMPLETED), Duration.ofSeconds(5));
        awaitQueueDrainAndVectors(9, Duration.ofSeconds(5));

        mockMvc.perform(get(MIGRATIONS + "/" + firstJobId)
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.totalSourceRows").value(9))
            .andExpect(jsonPath("$.processedSourceRows").value(9))
            .andExpect(jsonPath("$.failedRows").value(0))
            .andExpect(jsonPath("$.currentIndexedVectors").value(9))
            .andExpect(jsonPath("$.pendingQueueEntries").value(0))
            .andExpect(jsonPath("$.processingQueueEntries").value(0))
            .andExpect(jsonPath("$.indexingCaughtUp").value(true))
            .andExpect(jsonPath("$.fullSourceVectorCoverage").value(true))
            .andExpect(jsonPath("$.createdBy").value("customer-alex"))
            .andExpect(jsonPath("$.skipAccounting").value(
                org.hamcrest.Matchers.containsString("does not expose an exact per-job skipped count")));

        mockMvc.perform(get("/api/knowledge/search")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .param("q", "I cannot sign in after too many attempts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence[0].evidenceId").value("policy-account-lockout-01"));

        mockMvc.perform(get("/api/knowledge/search")
                .header(HttpHeaders.AUTHORIZATION, RILEY_BEARER)
                .param("q", "recover VPN access"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence[0].evidenceId").value("article-vpn-red"));

        assertThat(queueRepository.findAll())
            .allSatisfy(entry -> assertThat(entry.getPayload())
                .doesNotContain("fraud review")
                .doesNotContain("staff-only")
                .doesNotContain("Payment processor references are private"));

        long queueEntriesBeforeRerun = queueRepository.count();
        String rerunJobId = start("""
            {"batchSize": 4, "rateLimit": 0, "reindexExisting": false}
            """);
        awaitJob(rerunJobId, Set.of(MigrationStatus.COMPLETED), Duration.ofSeconds(5));

        assertThat(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE)).isEqualTo(9);
        assertThat(queueRepository.count()).isEqualTo(queueEntriesBeforeRerun);
        assertThat(jobRepository.findById(rerunJobId).orElseThrow().getProcessedEntities()).isEqualTo(9);
    }

    @Test
    void adminBoundaryAndLifecycleTransitionsFailClosed() throws Exception {
        mockMvc.perform(post(MIGRATIONS)
                .header(HttpHeaders.AUTHORIZATION, RILEY_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());

        List<KnowledgeArticle> retained = articleRepository.findAllByOrderByIdAsc().stream().limit(2).toList();
        articleRepository.deleteAll();
        articleRepository.saveAllAndFlush(retained);

        String pausedJobId = start("""
            {"batchSize": 1, "rateLimit": 30, "reindexExisting": false}
            """);
        mockMvc.perform(post(MIGRATIONS + "/" + pausedJobId + "/pause")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAUSED"));

        awaitJob(pausedJobId, Set.of(MigrationStatus.PAUSED), Duration.ofSeconds(4));
        mockMvc.perform(post(MIGRATIONS + "/" + pausedJobId + "/resume")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk());
        awaitJob(pausedJobId, Set.of(MigrationStatus.COMPLETED), Duration.ofSeconds(8));
        awaitQueueDrainAndVectors(2, Duration.ofSeconds(5));

        mockMvc.perform(post(MIGRATIONS + "/" + pausedJobId + "/pause")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isConflict());

        mockMvc.perform(get(MIGRATIONS + "/missing-job")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isNotFound());

        vectorDatabaseService.clearVectorsByEntityType(KnowledgeArticle.ENTITY_TYPE);
        queueRepository.deleteAll();
        String cancelledJobId = start("""
            {"batchSize": 1, "rateLimit": 600, "reindexExisting": false}
            """);
        mockMvc.perform(post(MIGRATIONS + "/" + cancelledJobId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        awaitJob(cancelledJobId, Set.of(MigrationStatus.CANCELLED), Duration.ofSeconds(4));
        Thread.sleep(250);
    }

    @Test
    void filtersSelectStableEntityIdsWithoutClaimingFullCoverage() throws Exception {
        String jobId = start("""
            {
              "batchSize": 2,
              "rateLimit": 0,
              "entityIds": ["article-account-lockout"]
            }
            """);
        awaitJob(jobId, Set.of(MigrationStatus.COMPLETED), Duration.ofSeconds(5));
        awaitQueueDrainAndVectors(1, Duration.ofSeconds(5));

        mockMvc.perform(get(MIGRATIONS + "/" + jobId)
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processedSourceRows").value(9))
            .andExpect(jsonPath("$.currentIndexedVectors").value(1))
            .andExpect(jsonPath("$.indexingCaughtUp").value(true))
            .andExpect(jsonPath("$.fullSourceVectorCoverage").doesNotExist());
    }

    private String start(String json) throws Exception {
        String body = mockMvc.perform(post(MIGRATIONS)
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entityType").value(KnowledgeArticle.ENTITY_TYPE))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        return response.path("jobId").asText();
    }

    private MigrationJob awaitJob(String jobId, Set<MigrationStatus> statuses, Duration timeout)
        throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        MigrationJob last = null;
        while (Instant.now().isBefore(deadline)) {
            last = jobRepository.findById(jobId).orElse(null);
            if (last != null && statuses.contains(last.getStatus())) {
                return last;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("Job " + jobId + " did not reach " + statuses + "; last="
            + (last != null ? last.getStatus() : "missing"));
    }

    private void awaitQueueDrainAndVectors(long expectedVectors, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            long pending = queueRepository.countByStatus(IndexingStatus.PENDING);
            long processing = queueRepository.countByStatus(IndexingStatus.PROCESSING);
            long vectors = vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE);
            if (pending == 0 && processing == 0 && vectors == expectedVectors) {
                return;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("Indexing did not drain to " + expectedVectors + " vectors");
    }
}
