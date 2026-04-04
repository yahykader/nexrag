package com.exemple.nexrag.service.rag.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Intercepteur HTTP pour appliquer le Rate Limiting automatiquement.
 *
 * Principe SRP  : unique responsabilité → intercepter les requêtes et déléguer
 *                 la vérification à {@link RateLimitService}.
 * Clean code    : supprime l'auto-import redondant ({@code RateLimitService}
 *                 est dans le même package).
 *                 {@code checkRateLimit()} reste la seule méthode de routage.
 *                 OPTIONS (CORS preflight) court-circuité en première ligne.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ObjectMapper     objectMapper;

    // -------------------------------------------------------------------------
    // HandlerInterceptor
    // -------------------------------------------------------------------------

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // Autoriser OPTIONS (CORS preflight) sans vérification
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String userId = resolveUserId(request);
        RateLimitResult result = selectLimit(userId, request.getRequestURI(), request.getMethod());

        if (result.isAllowed()) {
            response.addHeader("X-RateLimit-Remaining",
                String.valueOf(result.getRemainingTokens()));
            return true;
        }

        rejectWithTooManyRequests(response, result);
        return false;
    }

    // -------------------------------------------------------------------------
    // Sélection de la limite selon l'endpoint
    // -------------------------------------------------------------------------

    private RateLimitResult selectLimit(String userId, String path, String method) {
        if (path.contains("/upload/batch"))                          return rateLimitService.checkBatchLimit(userId);
        if (path.contains("/upload"))                               return rateLimitService.checkUploadLimit(userId);
        if (path.contains("/search"))                               return rateLimitService.checkSearchLimit(userId);
        if ("DELETE".equals(method) && path.contains("/file/"))     return rateLimitService.checkDeleteLimit(userId);
        if ("DELETE".equals(method) && path.contains("/batch/"))    return rateLimitService.checkDeleteLimit(userId);
        if ("DELETE".equals(method) && path.contains("/files/"))    return rateLimitService.checkDeleteLimit(userId);
        return rateLimitService.checkDefaultLimit(userId);
    }

    // -------------------------------------------------------------------------
    // Résolution de l'identifiant utilisateur
    // -------------------------------------------------------------------------

    /**
     * Résout l'identifiant utilisateur depuis la requête.
     * Priorité : header X-User-Id → session → IP client.
     */
    private String resolveUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) return userId;

        if (request.getSession(false) != null) {
            Object sessionUserId = request.getSession().getAttribute("userId");
            if (sessionUserId != null) return sessionUserId.toString();
        }

        return resolveClientIp(request);
    }

    /**
     * Résout l'IP client en tenant compte des proxies.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;

        return request.getRemoteAddr();
    }

    // -------------------------------------------------------------------------
    // Réponse 429
    // -------------------------------------------------------------------------

    private void rejectWithTooManyRequests(HttpServletResponse response,
                                           RateLimitResult result) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader("X-RateLimit-Remaining", "0");
        response.addHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        response.addHeader("X-RateLimit-Reset",
            String.valueOf(System.currentTimeMillis() / 1000 + result.getRetryAfterSeconds()));

        String body = objectMapper.writeValueAsString(Map.of(
            "error",             "Too Many Requests",
            "message",           "Rate limit dépassé. Réessayez dans %ds.".formatted(result.getRetryAfterSeconds()),
            "retryAfterSeconds", result.getRetryAfterSeconds(),
            "timestamp",         System.currentTimeMillis()
        ));

        response.getWriter().write(body);
        response.getWriter().flush();
    }
}