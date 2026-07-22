package dev.aifabric.course.support.quality;

import dev.aifabric.course.support.identity.CourseAuthorizationService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import dev.aifabric.course.support.knowledge.KnowledgeEvidenceService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RagQualityService {

    private final KnowledgeEvidenceService evidenceService;
    private final CourseAuthorizationService authorizationService;
    private final RagQualityCatalog catalog;

    public RagQualityService(KnowledgeEvidenceService evidenceService,
                             CourseAuthorizationService authorizationService,
                             RagQualityCatalog catalog) {
        this.evidenceService = evidenceService;
        this.authorizationService = authorizationService;
        this.catalog = catalog;
    }

    public QualitySuite evaluateGoldenSuite(CoursePrincipal principal) {
        CoursePrincipal authorized = authorizationService.requireScope(principal, "support:read");
        List<CaseResult> results = catalog.questionsFor(authorized).stream()
            .map(question -> evaluate(question.toRequest(), authorized))
            .toList();
        long passedCases = results.stream().filter(CaseResult::passed).count();
        return new QualitySuite(
            "support-knowledge-golden-v1",
            passedCases == results.size(),
            results.size(),
            (int) passedCases,
            results.size() - (int) passedCases,
            results
        );
    }

    public CaseResult evaluate(EvaluationRequest request, CoursePrincipal principal) {
        CoursePrincipal authorized = authorizationService.requireScope(principal, "support:read");
        validate(request);
        KnowledgeEvidenceService.SearchResponse search = evidenceService.search(request.question(), authorized);
        List<String> observedIds = search.evidence().stream()
            .map(KnowledgeEvidenceService.Evidence::evidenceId)
            .toList();
        Set<String> observedSet = new LinkedHashSet<>(observedIds);
        List<String> missingIds = request.expectedEvidenceIds().stream()
            .filter(id -> !observedSet.contains(id))
            .toList();
        List<String> forbiddenIds = request.forbiddenEvidenceIds().stream()
            .filter(observedSet::contains)
            .toList();

        String normalizedContent = search.evidence().stream()
            .map(KnowledgeEvidenceService.Evidence::content)
            .reduce("", (left, right) -> left + "\n" + right)
            .toLowerCase(Locale.ROOT);
        List<String> missingFragments = request.requiredContentFragments().stream()
            .filter(fragment -> !normalizedContent.contains(fragment.toLowerCase(Locale.ROOT)))
            .toList();
        List<String> staleFragments = request.forbiddenContentFragments().stream()
            .filter(fragment -> normalizedContent.contains(fragment.toLowerCase(Locale.ROOT)))
            .toList();

        List<String> failures = new ArrayList<>();
        if (request.expectNoEvidence() && !observedIds.isEmpty()) {
            failures.add("UNEXPECTED_EVIDENCE");
        }
        if (!missingIds.isEmpty()) {
            failures.add("EXPECTED_EVIDENCE_MISSING");
        }
        if (!forbiddenIds.isEmpty()) {
            failures.add("FORBIDDEN_EVIDENCE_RETURNED");
        }
        if (!missingFragments.isEmpty()) {
            failures.add("REQUIRED_CONTENT_MISSING");
        }
        if (!staleFragments.isEmpty()) {
            failures.add("STALE_CONTENT_RETURNED");
        }

        return new CaseResult(
            request.caseId(),
            failures.isEmpty(),
            request.question(),
            request.expectedEvidenceIds(),
            observedIds,
            missingIds,
            forbiddenIds,
            missingFragments,
            staleFragments,
            List.copyOf(failures)
        );
    }

    private void validate(EvaluationRequest request) {
        if (request == null || request.question().isBlank() || request.caseId().isBlank()) {
            throw new IllegalArgumentException("caseId and question are required");
        }
        if (request.caseId().length() > 128 || request.question().length() > 2_000) {
            throw new IllegalArgumentException("caseId or question exceeds the quality request limit");
        }
        List<List<String>> lists = List.of(
            request.expectedEvidenceIds(),
            request.forbiddenEvidenceIds(),
            request.requiredContentFragments(),
            request.forbiddenContentFragments()
        );
        if (lists.stream().anyMatch(values -> values.size() > 20)
            || request.expectedEvidenceIds().stream().anyMatch(value -> value.length() > 128)
            || request.forbiddenEvidenceIds().stream().anyMatch(value -> value.length() > 128)
            || request.requiredContentFragments().stream().anyMatch(value -> value.length() > 256)
            || request.forbiddenContentFragments().stream().anyMatch(value -> value.length() > 256)) {
            throw new IllegalArgumentException("Quality expectations exceed the configured request limits");
        }
        if (request.expectNoEvidence() && !request.expectedEvidenceIds().isEmpty()) {
            throw new IllegalArgumentException("A no-evidence case cannot require evidence IDs");
        }
    }

    public record GoldenQuestion(
        String caseId,
        String question,
        List<String> expectedEvidenceIds,
        List<String> forbiddenEvidenceIds,
        List<String> requiredContentFragments,
        List<String> forbiddenContentFragments
    ) {
        EvaluationRequest toRequest() {
            return new EvaluationRequest(
                caseId,
                question,
                expectedEvidenceIds,
                forbiddenEvidenceIds,
                requiredContentFragments,
                forbiddenContentFragments,
                false
            );
        }
    }

    public record EvaluationRequest(
        String caseId,
        String question,
        List<String> expectedEvidenceIds,
        List<String> forbiddenEvidenceIds,
        List<String> requiredContentFragments,
        List<String> forbiddenContentFragments,
        Boolean expectNoEvidence
    ) {
        public EvaluationRequest {
            caseId = caseId == null ? "" : caseId.trim();
            question = question == null ? "" : question.trim();
            expectedEvidenceIds = safeList(expectedEvidenceIds);
            forbiddenEvidenceIds = safeList(forbiddenEvidenceIds);
            requiredContentFragments = safeList(requiredContentFragments);
            forbiddenContentFragments = safeList(forbiddenContentFragments);
            expectNoEvidence = Boolean.TRUE.equals(expectNoEvidence);
        }

        private static List<String> safeList(List<String> values) {
            return values == null ? List.of() : values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        }
    }

    public record CaseResult(
        String caseId,
        boolean passed,
        String question,
        List<String> expectedEvidenceIds,
        List<String> observedEvidenceIds,
        List<String> missingEvidenceIds,
        List<String> returnedForbiddenEvidenceIds,
        List<String> missingContentFragments,
        List<String> returnedStaleContentFragments,
        List<String> failureCodes
    ) {
    }

    public record QualitySuite(
        String suiteId,
        boolean passed,
        int totalCases,
        int passedCases,
        int failedCases,
        List<CaseResult> cases
    ) {
    }
}
