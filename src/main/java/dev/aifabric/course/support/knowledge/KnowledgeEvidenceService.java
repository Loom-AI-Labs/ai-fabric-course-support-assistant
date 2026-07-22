package dev.aifabric.course.support.knowledge;

import ai.fabric.config.AIEntityConfigurationLoader;
import ai.fabric.core.AICoreService;
import ai.fabric.dto.AIEntityConfig;
import ai.fabric.dto.AISearchRequest;
import ai.fabric.dto.AISearchResponse;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.service.AICapabilityService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeEvidenceService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final KnowledgeArticleRepository articleRepository;
    private final AICapabilityService capabilityService;
    private final AIEntityConfigurationLoader configurationLoader;
    private final AICoreService aiCoreService;
    private final VectorDatabaseService vectorDatabaseService;
    private final ObjectMapper objectMapper;

    public KnowledgeEvidenceService(KnowledgeArticleRepository articleRepository,
                                    AICapabilityService capabilityService,
                                    AIEntityConfigurationLoader configurationLoader,
                                    AICoreService aiCoreService,
                                    VectorDatabaseService vectorDatabaseService,
                                    ObjectMapper objectMapper) {
        this.articleRepository = articleRepository;
        this.capabilityService = capabilityService;
        this.configurationLoader = configurationLoader;
        this.aiCoreService = aiCoreService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.objectMapper = objectMapper;
    }

    public IndexResponse indexAll() {
        List<KnowledgeArticle> articles = articleRepository.findAllByOrderByIdAsc();
        articles.forEach(this::replaceVector);
        return new IndexResponse(articles.size(), indexedCount());
    }

    public SearchResponse search(String query) {
        AISearchResponse response = aiCoreService.performSearch(AISearchRequest.builder()
            .query(query)
            .entityType(KnowledgeArticle.ENTITY_TYPE)
            .limit(5)
            .threshold(0.25)
            .build());

        List<Evidence> evidence = safeResults(response).stream()
            .map(this::toEvidence)
            .toList();
        return new SearchResponse(query, evidence.size(), evidence);
    }

    @Transactional
    public PublicArticle update(String id, UpdateArticleRequest request) {
        KnowledgeArticle article = articleRepository.findById(id)
            .orElseThrow(() -> new ArticleNotFoundException(id));
        article.setTitle(request.title());
        article.setBody(request.body());
        articleRepository.saveAndFlush(article);
        replaceVector(article);
        return PublicArticle.from(article);
    }

    @Transactional
    public void delete(String id) {
        KnowledgeArticle article = articleRepository.findById(id)
            .orElseThrow(() -> new ArticleNotFoundException(id));
        removeVectorIfPresent(id);
        articleRepository.delete(article);
        if (vectorDatabaseService.vectorExists(KnowledgeArticle.ENTITY_TYPE, id)) {
            throw new EvidenceOperationException("Vector still exists after deleting article " + id);
        }
    }

    public void clear() {
        vectorDatabaseService.clearVectorsByEntityType(KnowledgeArticle.ENTITY_TYPE);
        if (indexedCount() != 0) {
            throw new EvidenceOperationException("Knowledge evidence could not be cleared");
        }
    }

    public long indexedCount() {
        return vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE);
    }

    private void replaceVector(KnowledgeArticle article) {
        removeVectorIfPresent(article.getId());
        AIEntityConfig config = configurationLoader.getEntityConfig(KnowledgeArticle.ENTITY_TYPE);
        if (config == null) {
            throw new EvidenceOperationException("Missing AI entity configuration for " + KnowledgeArticle.ENTITY_TYPE);
        }
        capabilityService.indexForSearch(article, config);
        if (!vectorDatabaseService.vectorExists(KnowledgeArticle.ENTITY_TYPE, article.getId())) {
            throw new EvidenceOperationException("AI Fabric did not index article " + article.getId());
        }
    }

    private void removeVectorIfPresent(String id) {
        if (vectorDatabaseService.vectorExists(KnowledgeArticle.ENTITY_TYPE, id)
            && !vectorDatabaseService.removeVector(KnowledgeArticle.ENTITY_TYPE, id)) {
            throw new EvidenceOperationException("AI Fabric did not remove article vector " + id);
        }
    }

    private List<Map<String, Object>> safeResults(AISearchResponse response) {
        return response != null && response.getResults() != null ? response.getResults() : List.of();
    }

    private Evidence toEvidence(Map<String, Object> result) {
        String id = stringValue(result.getOrDefault("id", result.get("entityId")));
        double score = numberValue(result.getOrDefault("score", result.get("similarity")));
        return new Evidence(id, score, stringValue(result.get("content")), metadata(result.get("metadata")));
    }

    private Map<String, Object> metadata(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, item) -> copy.put(String.valueOf(key), item));
            return Map.copyOf(copy);
        }
        if (value instanceof String json && !json.isBlank()) {
            try {
                return Map.copyOf(objectMapper.readValue(json, MAP_TYPE));
            } catch (Exception exception) {
                throw new EvidenceOperationException("Vector metadata is not valid JSON", exception);
            }
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private double numberValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    public record IndexResponse(int indexedArticles, long indexedVectors) { }

    public record SearchResponse(String query, int resultCount, List<Evidence> evidence) { }

    public record Evidence(String evidenceId, double score, String content, Map<String, Object> metadata) { }

    public record UpdateArticleRequest(@NotBlank String title, @NotBlank String body) { }

    public record PublicArticle(String id, String title, String body, String category,
                                String tenantId, String status) {
        public static PublicArticle from(KnowledgeArticle article) {
            return new PublicArticle(article.getId(), article.getTitle(), article.getBody(), article.getCategory(),
                article.getTenantId(), article.getStatus());
        }
    }
}
