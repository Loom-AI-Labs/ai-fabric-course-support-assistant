package dev.aifabric.course.support.knowledge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, String> {

    List<KnowledgeArticle> findAllByOrderByIdAsc();

    List<KnowledgeArticle> findByTenantIdAndVisibleToUserTrueOrderByIdAsc(String tenantId);

    Optional<KnowledgeArticle> findByIdAndTenantId(String id, String tenantId);
}
