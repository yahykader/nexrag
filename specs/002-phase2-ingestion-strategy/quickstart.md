# Quickstart — Phase 2 Tests

**Date**: 2026-03-27
**Branch**: `002-phase2-ingestion-strategy`

## Prérequis

- Phase 1 complète et tous les tests verts (`./mvnw test` passe)
- Java 21 installé
- Aucune infrastructure externe requise (tout est mocqué)

## Lancer les tests Phase 2

```bash
# Depuis nex-rag/
cd nex-rag

# Tous les tests
./mvnw test

# Phase 2 uniquement (par package)
./mvnw test -Dtest="**/ingestion/strategy/**,**/ingestion/cache/**,**/ingestion/compression/**,**/ingestion/tracker/**,**/ingestion/analyzer/**,IngestionOrchestratorSpec"

# Une seule classe
./mvnw test -Dtest=TextChunkerSpec

# Une seule méthode
./mvnw test -Dtest=TextChunkerSpec#shouldProduceCorrectChunksWithOverlap

# Rapport de couverture (JaCoCo)
./mvnw test jacoco:report
# Rapport HTML : target/site/jacoco/index.html
```

## Structure des specs (pattern type)

```java
@DisplayName("Spec : NomClasse — description courte")
@ExtendWith(MockitoExtension.class)
class NomClasseSpec {

    @Mock
    private DependanceA dependanceA;

    @InjectMocks
    private NomClasse nomClasse;

    @Test
    @DisplayName("DOIT [action] quand [condition]")
    void shouldDoSomethingWhenCondition() {
        // Given
        when(dependanceA.methode()).thenReturn(valeur);

        // When
        var result = nomClasse.methode(parametre);

        // Then
        assertThat(result).isEqualTo(valeurAttendue);
    }
}
```

## Ordre de développement recommandé

### Groupe 1 — Utilitaires purs (sans dépendances)
1. `EmbeddingCompressorSpec` — classe stateless, aucun mock
2. `EmbeddingTextHasherSpec` — hachage SHA-256
3. `EmbeddingSerializerSpec` — sérialisation/désérialisation

### Groupe 2 — Cache
4. `EmbeddingCacheStoreSpec` — mock `RedisTemplate`
5. `EmbeddingCacheSpec` (si classe `EmbeddingCache` exposée)

### Groupe 3 — Tracker
6. `BatchInfoRegistrySpec`
7. `RollbackExecutorSpec` ← 100 % branch coverage obligatoire
8. `IngestionTrackerSpec` — assemble les composants du tracker

### Groupe 4 — Stratégies communes
9. `TextChunkerSpec`
10. `EmbeddingIndexerSpec`
11. `LibreOfficeConverterSpec`

### Groupe 5 — Stratégies par format
12. `PdfIngestionStrategySpec`
13. `DocxIngestionStrategySpec`
14. `XlsxIngestionStrategySpec`
15. `TextIngestionStrategySpec`
16. `TikaIngestionStrategySpec`
17. `IngestionConfigSpec`

### Groupe 6 — Analyzer image
18. `ImageConverterSpec`
19. `ImageSaverSpec`
20. `VisionFallbackGeneratorSpec`
21. `VisionAnalyzerSpec`
22. `ImageIngestionStrategySpec`

### Groupe 7 — Orchestrateur (dépend de tout)
23. `IngestionOrchestratorSpec`

## Vérification couverture

```bash
./mvnw test jacoco:report
open target/site/jacoco/index.html
```

Seuils attendus (constitution Principe IV) :
- `ingestion/strategy/` : ≥ 80 % lignes + branches
- `ingestion/cache/` : ≥ 80 % lignes + branches
- `ingestion/compression/` : ≥ 80 % lignes + branches
- `ingestion/tracker/RollbackExecutor` : **100 %** branches
- `ingestion/tracker/` (reste) : ≥ 80 % lignes + branches
- `ingestion/analyzer/` : ≥ 80 % lignes + branches
- `IngestionOrchestrator` : ≥ 80 % lignes + branches

## Commit de phase

```bash
git add src/test/java/com/exemple/nexrag/service/rag/ingestion/
git commit -m "test(phase-2): add ingestion strategy/cache/tracker specs — Phase 2 complete"
```

Format obligatoire : `test(phase-N): add <ClassName>Spec — <brief description>`
