package dev.aifabric.course.support.knowledge;

public class ArticleNotFoundException extends RuntimeException {

    public ArticleNotFoundException(String id) {
        super("Knowledge article not found: " + id);
    }
}
