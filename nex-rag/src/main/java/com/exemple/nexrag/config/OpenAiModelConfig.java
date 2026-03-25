package com.exemple.nexrag.config;

import com.exemple.nexrag.config.properties.OpenAiProperties;
import com.exemple.nexrag.service.rag.embedding.MeteredEmbeddingModel;
import com.exemple.nexrag.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration des modèles OpenAI (Embedding + Chat).
 *
 * Principe SRP : unique responsabilité → instancier les beans OpenAI.
 * Principe DIP : dépend de l'abstraction {@link OpenAiProperties}.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openai.enabled", havingValue = "true")
public class OpenAiModelConfig {

    private final OpenAiProperties props;

    // -------------------------------------------------------------------------
    // Embedding
    // -------------------------------------------------------------------------

    /**
     * EmbeddingModel instrumenté avec {@link MeteredEmbeddingModel}.
     */
    @Bean
    public EmbeddingModel embeddingModel(RAGMetrics ragMetrics, EmbeddingCache cache) {
        log.info("🧠 Création EmbeddingModel — model={}, dimension={}",
            props.getEmbeddingModel(), "1536");

        EmbeddingModel base = OpenAiEmbeddingModel.builder()
            .apiKey(props.getApiKey())
            .modelName(props.getEmbeddingModel())
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .maxRetries(props.getMaxRetries())
            .logRequests(props.isLogRequests())
            .logResponses(props.isLogResponses())
            .build();

        return new MeteredEmbeddingModel(base, ragMetrics);
    }

    // -------------------------------------------------------------------------
    // Chat
    // -------------------------------------------------------------------------

    @Bean
    public ChatLanguageModel chatModel() {
        log.info("🤖 Création ChatLanguageModel — model={}, temperature={}",
            props.getChatModel(), props.getTemperature());

        return OpenAiChatModel.builder()
            .apiKey(props.getApiKey())
            .modelName(props.getChatModel())
            .temperature(props.getTemperature())
            .maxTokens(props.getMaxTokens())
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .maxRetries(props.getMaxRetries())
            .logRequests(props.isLogRequests())
            .logResponses(props.isLogResponses())
            .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatModel() {
        log.info("🌊 Création StreamingChatLanguageModel — model={}", props.getChatModel());

        return OpenAiStreamingChatModel.builder()
            .apiKey(props.getApiKey())
            .modelName(props.getChatModel())
            .temperature(props.getTemperature())
            .maxTokens(props.getMaxTokens())
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .logRequests(props.isLogRequests())
            .logResponses(props.isLogResponses())
            .build();
    }
}