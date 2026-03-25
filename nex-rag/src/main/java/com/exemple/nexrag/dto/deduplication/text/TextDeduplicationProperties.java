package com.exemple.nexrag.dto.deduplication.text;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Positive;

/**
 * Propriétés de configuration pour la déduplication de textes.
 *
 * Principe SRP  : unique responsabilité → porter la configuration.
 * Principe DIP  : les services dépendent de cette abstraction, pas de @Value éparpillés.
 * Clean code    : élimine les 4 @Value inline dans le service et le log
 *                 dans le constructeur (les @Value ne sont pas encore injectés
 *                 au moment du constructeur).
 */
@Slf4j
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "deduplication.text")
public class TextDeduplicationProperties {

    /** Active ou désactive la déduplication de texte. */
    private boolean enabled = true;

    /** TTL des entrées Redis en jours. */
    @Positive
    private int ttlDays = 30;

    /**
     * Si {@code true}, les clés Redis sont scopées par batchId
     * (isolation entre batches).
     */
    private boolean batchIdScope = false;

    @PostConstruct
    public void log() {
        log.info("✅ TextDeduplication — enabled={}, ttlDays={}, batchIdScope={}",
            enabled, ttlDays, batchIdScope);
    }
}