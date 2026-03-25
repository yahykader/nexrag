package com.exemple.nexrag.config;

import com.exemple.nexrag.config.properties.PgVectorProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration des stores d'embeddings PgVector.
 *
 * Principe SRP : unique responsabilité → créer les EmbeddingStore PgVector.
 * Principe DIP : dépend de l'abstraction {@link PgVectorProperties}.
 * Principe OCP : ajouter un nouveau store ne modifie pas les beans existants.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openai.enabled", havingValue = "true")
public class PgVectorStoreConfig {

    private static final String TABLE_TEXT  = "text_embeddings";
    private static final String TABLE_IMAGE = "image_embeddings";

    private final PgVectorProperties props;

    @Bean(name = "textEmbeddingStore")
    public EmbeddingStore<TextSegment> textEmbeddingStore() {
        log.info("📚 Création textEmbeddingStore — table={}", TABLE_TEXT);
        return buildStore(TABLE_TEXT);
    }

    @Bean(name = "imageEmbeddingStore")
    public EmbeddingStore<TextSegment> imageEmbeddingStore() {
        log.info("🖼️ Création imageEmbeddingStore — table={}", TABLE_IMAGE);
        return buildStore(TABLE_IMAGE);
    }

    // -------------------------------------------------------------------------
    // Fabrique interne
    // -------------------------------------------------------------------------

    private EmbeddingStore<TextSegment> buildStore(String tableName) {
        try {
            return PgVectorEmbeddingStore.builder()
                .host(props.getHost())
                .port(props.getPort())
                .database(props.getDatabase())
                .user(props.getUser())
                .password(props.getPassword())
                .table(tableName)
                .dimension(props.getDimension())
                .createTable(true)
                .dropTableFirst(false)
                .build();

        } catch (Exception e) {
            throw new IllegalStateException(
                "❌ Impossible de créer le store PgVector '" + tableName + "'. " +
                "Vérifiez que l'extension est activée : " +
                "CREATE EXTENSION IF NOT EXISTS vector;",
                e
            );
        }
    }
}