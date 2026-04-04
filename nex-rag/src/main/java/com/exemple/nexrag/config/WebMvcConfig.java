package com.exemple.nexrag.config;

import com.exemple.nexrag.service.rag.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration Spring MVC.
 *
 * Principe SRP  : unique responsabilité → enregistrer les intercepteurs HTTP.
 * Clean code    : supprime le code CORS commenté — du code mort n'appartient
 *                 pas au code source (utiliser Git pour l'historique).
 *                 Corrige les paths {@code /v1/} → {@code /api/v1/}.
 *
 * @author ayhyaoui
 * @version 2.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns(
                "/api/v1/ingestion/**",
                "/api/v1/crud/**"
            )
            .excludePathPatterns(
                "/api/v1/ingestion/health",
                "/api/v1/ingestion/health/detailed",
                "/api/v1/ingestion/strategies"
            );

        log.info("✅ RateLimitInterceptor enregistré sur /api/v1/ingestion/**, /api/v1/crud/**");
    }
}