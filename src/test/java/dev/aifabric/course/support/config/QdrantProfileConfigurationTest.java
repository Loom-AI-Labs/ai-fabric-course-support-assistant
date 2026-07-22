package dev.aifabric.course.support.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class QdrantProfileConfigurationTest {

    @Test
    void qdrantProfileSelectsThePublishedAdapterWithoutChangingEmbeddingsOrFallback() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
            .load("course-qdrant", new ClassPathResource("application-qdrant.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        assertThat(resolver.getProperty("ai.vector-db.type")).isEqualTo("qdrant");
        assertThat(resolver.getProperty("ai.providers.embedding-provider")).isEqualTo("onnx");
        assertThat(resolver.getProperty("ai.providers.enable-fallback", Boolean.class)).isFalse();
        assertThat(resolver.getProperty("ai.providers.qdrant.enabled", Boolean.class)).isTrue();
        assertThat(resolver.getProperty("ai.providers.qdrant.prefer-grpc", Boolean.class)).isFalse();
        assertThat(resolver.getProperty("ai.providers.qdrant.collection-prefix"))
            .isEqualTo("course_prod07__");
        assertThat(resolver.getProperty("course.release.runtime-mode")).isEqualTo("qdrant-local");
    }
}
