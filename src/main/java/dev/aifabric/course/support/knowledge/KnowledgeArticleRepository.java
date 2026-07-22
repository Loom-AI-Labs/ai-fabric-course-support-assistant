package dev.aifabric.course.support.knowledge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, String> {

    List<KnowledgeArticle> findAllByOrderByIdAsc();
}
