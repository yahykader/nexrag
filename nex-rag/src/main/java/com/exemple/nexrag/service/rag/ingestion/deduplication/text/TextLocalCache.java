package com.exemple.nexrag.service.rag.ingestion.deduplication.text;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache local en mémoire pour la déduplication de textes.
 *
 * Principe SRP : unique responsabilité → gérer le cache mémoire thread-safe.
 * Clean code   : extrait la gestion du {@link ConcurrentHashMap} du service
 *                pour isoler clairement les deux niveaux de cache (local vs Redis).
 *
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Component
public class TextLocalCache {

    private final Set<String> cache = ConcurrentHashMap.newKeySet();

    /**
     * Ajoute une clé au cache de manière atomique.
     *
     * @return {@code true} si la clé était absente (nouveau texte),
     *         {@code false} si elle était déjà présente (doublon)
     */
    public boolean addIfAbsent(String key) {
        return cache.add(key);
    }

    /**
     * Vérifie si une clé est présente dans le cache.
     */
    public boolean contains(String key) {
        return cache.contains(key);
    }

    /**
     * Ajoute une clé sans vérification (pré-chargement depuis Redis).
     */
    public void add(String key) {
        cache.add(key);
    }

    /**
     * Vide le cache local (à appeler en fin de batch).
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        log.debug("🗑️ [Cache] Cache local nettoyé : {} entrée(s)", size);
    }

    /**
     * Retourne le nombre d'entrées dans le cache local.
     */
    public int size() {
        return cache.size();
    }
}