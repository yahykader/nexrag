package com.exemple.nexrag.config;

import com.exemple.nexrag.service.rag.interceptor.RateLimitResult;
import com.exemple.nexrag.service.rag.interceptor.RateLimitService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Configuration de test pour fournir un mock de {@link RateLimitService}.
 *
 * Problème : {@code @WebMvcTest} charge {@code WebMvcConfig} qui enregistre
 * {@code RateLimitInterceptor}. L'intercepteur appelle {@code rateLimitService
 * .checkXxxLimit(userId)} — sans ce mock configuré, Mockito retourne {@code null}
 * par défaut, ce qui provoque une {@code NullPointerException} dans l'intercepteur
 * et un statut 500 sur tous les endpoints testés.
 *
 * Fix : toutes les méthodes {@code check*()} retournent {@link RateLimitResult#allowed(long)}
 * pour laisser passer les requêtes de test sans vérification réelle.
 */
@TestConfiguration
public class TestWebConfig {

    @Bean
    public RateLimitService rateLimitService() {
        RateLimitService mock = Mockito.mock(RateLimitService.class);

        RateLimitResult allowed = RateLimitResult.allowed(100);

        when(mock.checkUploadLimit(anyString())).thenReturn(allowed);
        when(mock.checkBatchLimit(anyString())).thenReturn(allowed);
        when(mock.checkDeleteLimit(anyString())).thenReturn(allowed);
        when(mock.checkSearchLimit(anyString())).thenReturn(allowed);
        when(mock.checkDefaultLimit(anyString())).thenReturn(allowed);

        return mock;
    }
}