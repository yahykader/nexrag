package com.exemple.nexrag.service.rag.ingestion.cache;

import com.exemple.nexrag.dto.cache.EmbeddingCacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration du cache Redis pour les embeddings.
 *
 * Principe SRP : unique responsabilité → créer et câbler le bean RedisTemplate.
 * Clean code   : le nom du bean est une constante — élimine la magic string
 *                {@code "embeddingCacheRedisTemplate"} dupliquée entre
 *                {@code @Bean} et les {@code @Qualifier} consommateurs.
 *
 * Note sur le sérialiseur :
 * ObjectMapper SANS {@code activateDefaultTyping} — stocke les tableaux de floats
 * directement ({@code [-0.03, 0.15, ...]}) sans métadonnées de type Jackson.
 * Résout l'erreur {@code "Could not resolve type id '-0.030514618'"}.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Configuration
@EnableCaching
@EnableConfigurationProperties(EmbeddingCacheProperties.class)
public class EmbeddingCacheConfig {

    /** Nom du bean RedisTemplate — à utiliser dans les @Qualifier consommateurs. */
    public static final String REDIS_TEMPLATE_BEAN = "embeddingCacheRedisTemplate";

    @Bean(name = REDIS_TEMPLATE_BEAN)
    public RedisTemplate<String, Object> embeddingCacheRedisTemplate(
            LettuceConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // ObjectMapper sans typage polymorphe — évite la corruption de désérialisation
        GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(new ObjectMapper());

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();

        log.info("✅ {} configuré (JSON sans type metadata)", REDIS_TEMPLATE_BEAN);
        return template;
    }
}