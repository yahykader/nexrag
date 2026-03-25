package com.exemple.nexrag.service.rag.ingestion.cache;

/**
 * Contrat de nettoyage pour tout cache Redis du système d'ingestion.
 *
 * Principe OCP : ajouter un nouveau cache = implémenter cette interface.
 *                {@link RedisCacheCleanupService} ne change jamais.
 * Principe DIP : {@link RedisCacheCleanupService} dépend de cette abstraction,
 *                pas des implémentations concrètes.
 * Principe ISP : interface fine — deux méthodes ciblées, rien de superflu.
 *
 * @author ayahyaoui
 * @version 1.0
 */
public interface CacheCleanable {

    /**
     * Supprime toutes les entrées cache associées à un batch spécifique.
     *
     * @param batchId identifiant du batch à nettoyer
     */
    void removeBatch(String batchId);

    /**
     * Supprime l'intégralité des entrées cache (tous les batches).
     */
    void clearAll();
}