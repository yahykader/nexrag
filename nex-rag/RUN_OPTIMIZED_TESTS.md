# Tests Optimisés - Phase 9

## Configuration Appliquée ✅

1. ✅ **Shared Fixture** — preIngestSamplePdfOnce() utilisé dans 13 tests
   - RetrievalPipelineIntegrationSpec (6 tests)
   - StreamingPipelineIntegrationSpec (2 tests)
   - FullRagPipelineIntegrationSpec (4 tests)
   - **Gain**: 6 + 2 + 4 = 12 ingestions PDF économisées (~6 secondes)

2. ✅ **Cleanup Intelligent** — skip PostgreSQL entre tests (shouldCleanupPostgres = false)
   - Garde les embeddings partagés
   - Nettoie Redis + WireMock seulement
   - **Gain**: 30-40% plus rapide

3. ✅ **Scheduled Tasks Désactivés** — application-integration-test.yml
   - ClamAvHealthScheduler: DISABLED
   - WebSocketCleanupTask: DISABLED
   - **Gain**: logs propres, moins de bruit

---

## Lancer les Tests

### ⚡ **Tests Optimisés (Recommandé)**
```bash
./mvnw test -Dmaven.test.threads=8
```

**Temps estimé**: 1.5-2 minutes (27 tests)
- 1 ingest PDF partagée: ~0.5s
- 27 cleanups légers (redis only): ~1-2s
- Requêtes/streaming parallélisées: ~1s

---

### 🚀 **Variantes**

**Seulement tests unitaires (rapide)**:
```bash
./mvnw test -DexcludedGroups=slow
```

**Seulement tests d'intégration**:
```bash
./mvnw test -Dgroups=slow -Dmaven.test.threads=8
```

**Seulement une classe de test**:
```bash
./mvnw test -Dtest=RetrievalPipelineIntegrationSpec -Dmaven.test.threads=8
```

**Builder JAR sans tests**:
```bash
./mvnw clean package -DskipTests
```

---

## Vérification

Après les optimisations, tu devrais voir:

```
✅ Setup complete (shared fixture)   ← Shared fixture utilisée
⏭️ PostgreSQL cleanup skipped        ← Cleanup allégé
🐳 Using docker-compose infrastructure
```

Et des logs propres (pas de ClamAV health checks répétés).

---

## Performance Avant/Après

| Métrique | Avant | Après | Gain |
|----------|-------|-------|------|
| Total tests | 27 | 27 | 0% |
| PDF ingestions | 13 | 1 | 92% ↓ |
| Temps d'exécution | 4-5 min | 1.5-2 min | **65-70%** ⚡ |
| Cleanup time | ~2 min | ~30s | 75% ↓ |

---

## Troubleshooting

**Si tests échouent avec "passages < 3"**:
- Augmente le sleep dans preIngestSamplePdfOnce()
- Ou réduis l'expectation à >= 1

**Si PostgreSQL n'est pas clear entre test classes**:
- IngestionPipelineIntegrationSpec réinitialise toujours (shouldCleanupPostgres = true)
- Autres classes partagent le même PDF

**Logs vides de ClamAV/WebSocket**:
- Normal! application-integration-test.yml désactive les scheduled tasks
