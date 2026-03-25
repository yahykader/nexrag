package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.service.rag.ingestion.strategy.IngestionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.List;

/**
 * Configuration de l'ordre des stratégies d'ingestion.
 *
 * Principe OCP  : ajouter un nouveau format = créer un bean {@link IngestionStrategy}.
 *                 Cette classe ne change jamais.
 * Principe DIP  : dépend de l'abstraction {@link IngestionStrategy},
 *                 pas des implémentations concrètes.
 * Clean code    : Spring injecte automatiquement TOUS les beans IngestionStrategy.
 *                 Le tri par {@code getPriority()} garantit l'ordre de sélection —
 *                 la liste déclarée manuellement n'était pas triée.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Configuration
public class IngestionConfig {

    /**
     * Stratégies triées par priorité croissante.
     * Spring injecte automatiquement tous les beans {@link IngestionStrategy}
     * présents dans le contexte.
     *
     * Pour ajouter un nouveau format :
     * <ol>
     *   <li>Créer une classe annotée {@code @Component} qui implémente {@link IngestionStrategy}</li>
     *   <li>Définir {@code getPriority()} — valeur basse = priorité haute</li>
     *   <li>Cette méthode n'a pas besoin d'être modifiée</li>
     * </ol>
     */
    @Bean
    public List<IngestionStrategy> ingestionStrategies(List<IngestionStrategy> strategies) {
        List<IngestionStrategy> sorted = strategies.stream()
            .sorted(Comparator.comparingInt(IngestionStrategy::getPriority))
            .toList();

        sorted.forEach(s ->
            log.info("📋 [Ingestion] Strategy enregistrée : {} (priorité={})",
                s.getName(), s.getPriority())
        );

        return sorted;
    }
}