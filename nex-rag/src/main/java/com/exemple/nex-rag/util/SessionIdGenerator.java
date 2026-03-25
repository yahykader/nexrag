package com.exemple.nexrag.util;

import com.exemple.nexrag.constant.SseConstants;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Générateur d'identifiants de session SSE.
 *
 * Principe SRP : unique responsabilité → générer des identifiants de session.
 * Clean code   : extrait la logique hors du controller.
 */
@Component
public class SessionIdGenerator {

    /**
     * Génère un identifiant de session unique et court.
     *
     * @return identifiant de la forme {@code session_<16 chars>}
     */
    public String generate() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return SseConstants.SESSION_PREFIX
            + uuid.substring(0, SseConstants.SESSION_ID_RANDOM_LENGTH);
    }
}