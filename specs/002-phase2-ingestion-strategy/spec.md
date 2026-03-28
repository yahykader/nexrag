# Feature Specification: Phase 2 — Ingestion : Stratégies, Cache & Orchestration

**Feature Branch**: `002-phase2-ingestion-strategy`
**Created**: 2026-03-27
**Status**: Draft
**Input**: User description: "PHASE 2 — Ingestion : Stratégies, Cache & Orchestration"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ingestion Multi-Format par Stratégie Adaptée (Priority: P1)

En tant que système d'ingestion, je veux appliquer automatiquement la stratégie d'extraction de texte la plus adaptée au type de fichier soumis (PDF, DOCX, XLSX, image, texte brut, ou format inconnu), afin d'extraire le contenu de manière optimale pour chaque format.

**Why this priority**: C'est le cœur fonctionnel de la Phase 2. Sans stratégies d'extraction correctes, aucune indexation vectorielle n'est possible. Chaque format de document représente un cas d'usage métier distinct.

**Independent Test**: Peut être testé de manière autonome en soumettant un fichier de chaque type supporté et en vérifiant que du texte exploitable est produit en sortie, indépendamment du cache ou du tracking de batch.

**Acceptance Scenarios**:

1. **Given** un fichier PDF de 3 pages, **When** la stratégie PDF est appliquée, **Then** au moins 3 chunks de texte sont produits (un par page minimum).
2. **Given** un fichier DOCX valide, **When** la stratégie DOCX est appliquée, **Then** le texte du document est extrait sans erreur.
3. **Given** un fichier DOCX corrompu, **When** la stratégie DOCX est appliquée, **Then** une erreur d'ingestion est levée avec le nom du fichier dans le message.
4. **Given** une image JPEG, **When** la stratégie Image est appliquée, **Then** l'analyseur visuel est sollicité pour produire une description textuelle.
5. **Given** un fichier XLSX avec plusieurs feuilles, **When** la stratégie XLSX est appliquée, **Then** les données de chaque feuille sont extraites.
6. **Given** un type MIME non reconnu, **When** le système sélectionne une stratégie, **Then** la stratégie de secours générique est utilisée.
7. **Given** un fichier texte brut encodé en UTF-8, **When** la stratégie texte est appliquée, **Then** le contenu est lu intégralement avec l'encodage correct.

---

### User Story 2 - Découpage et Indexation des Embeddings avec Cache (Priority: P2)

En tant que système d'ingestion, je veux découper le texte extrait en chunks configurables et indexer leurs représentations vectorielles, tout en évitant les appels redondants au service d'embedding grâce à un cache, afin d'optimiser les coûts et la performance d'indexation.

**Why this priority**: Le chunking et l'indexation transforment le texte brut en vecteurs recherchables. Le cache évite de re-calculer des embeddings déjà connus, ce qui réduit les coûts et la latence. Ce composant est le pont entre l'extraction et la recherche vectorielle.

**Independent Test**: Peut être testé de manière autonome en soumettant un texte donné deux fois et en vérifiant que le service d'embedding n'est appelé qu'une seule fois, et que les chunks produits respectent les paramètres de taille et de chevauchement configurés.

**Acceptance Scenarios**:

1. **Given** un texte de 1000 unités avec une taille de chunk de 200 et un chevauchement de 50, **When** le découpage est appliqué, **Then** au moins 6 chunks sont produits et chacun ne dépasse pas 200 unités.
2. **Given** deux chunks consécutifs produits par le découpage, **When** on compare la fin du premier et le début du second, **Then** les 50 dernières unités du premier se retrouvent au début du second.
3. **Given** un texte plus court que la taille de chunk configurée, **When** le découpage est appliqué, **Then** un seul chunk est retourné.
4. **Given** un texte dont l'embedding a déjà été calculé et mis en cache, **When** le même texte est soumis à nouveau, **Then** le service d'embedding externe n'est pas appelé une seconde fois.
5. **Given** la configuration de compression vectorielle activée, **When** un vecteur de 1536 dimensions est traité, **Then** le vecteur compressé possède 256 dimensions.

---

### User Story 3 - Suivi de Batch et Rollback en cas d'Erreur (Priority: P3)

En tant que système d'ingestion, je veux tracer chaque batch d'ingestion avec son état (démarré, terminé, échoué) et pouvoir annuler automatiquement un batch en erreur, afin de garantir la cohérence des données indexées et de ne jamais laisser d'embeddings orphelins dans le store vectoriel.

**Why this priority**: La fiabilité transactionnelle est essentielle pour un système de production. Sans rollback, une erreur en cours d'ingestion laisserait des données partielles et corromprait les résultats de recherche. C'est un garde-fou critique mais qui s'active uniquement en cas d'anomalie.

**Independent Test**: Peut être testé de manière autonome en simulant une erreur pendant l'ingestion et en vérifiant que les embeddings partiels sont supprimés et que le batch est marqué comme échoué.

**Acceptance Scenarios**:

1. **Given** un batch en cours d'ingestion qui rencontre une erreur dans la stratégie d'extraction, **When** l'erreur est détectée, **Then** l'orchestrateur arrête le traitement, déclenche le rollback, et le batch est marqué FAILED.
2. **Given** un batch marqué FAILED, **When** le rollback est exécuté, **Then** tous les embeddings associés à ce batch sont supprimés du store vectoriel.
3. **Given** un batch en cours d'exécution, **When** chaque étape du pipeline est franchie, **Then** un événement de progression est émis vers les abonnés en temps réel.
4. **Given** les métadonnées d'un batch, **When** on consulte le registre, **Then** le nom du fichier, la taille et l'horodatage sont accessibles.

---

### Edge Cases

- Que se passe-t-il si le service d'analyseur visuel est indisponible lors du traitement d'une image ?
- Comment le système gère-t-il un fichier Excel vide (aucune donnée dans les feuilles) ?
- Que se passe-t-il si le cache des embeddings est plein ou inaccessible ?
- Comment le rollback se comporte-t-il si certains embeddings ont déjà été supprimés (idempotence) ?
- **Texte extrait vide** : Si une stratégie d'ingestion retourne un contenu vide (0 chunk), le système lève une `EmptyContentException`, le batch passe en état FAILED et le rollback est déclenché automatiquement — cohérent avec le comportement de toute autre erreur d'ingestion.

## Requirements *(mandatory)*

### Functional Requirements

**Stratégies d'ingestion**

- **FR-001**: Le système DOIT extraire le texte de chaque page d'un fichier PDF via la stratégie dédiée PDF.
- **FR-002**: Le système DOIT convertir un fichier DOCX puis en extraire le texte via la stratégie dédiée DOCX.
- **FR-003**: Le système DOIT extraire les données tabulaires de chaque feuille d'un fichier XLSX via la stratégie dédiée.
- **FR-004**: Le système DOIT déléguer l'extraction de contenu d'une image à un analyseur visuel spécialisé.
- **FR-005**: Le système DOIT lire le contenu d'un fichier texte brut en détectant automatiquement son encodage.
- **FR-006**: Le système DOIT utiliser une stratégie de secours générique pour tout type de fichier non explicitement supporté.
- **FR-007**: Le sélecteur de stratégies DOIT choisir automatiquement la stratégie appropriée en fonction du type MIME du fichier, sans modification de l'orchestrateur.

**Chunking et indexation**

- **FR-008**: Le système DOIT découper le texte en chunks de taille configurable avec un chevauchement configurable.
- **FR-009**: Le système DOIT calculer et stocker les représentations vectorielles des chunks dans le store vectoriel. En cas d'indisponibilité du service d'embedding, le système DOIT réessayer jusqu'à 3 fois avec un délai croissant (backoff) avant de marquer le batch FAILED et de déclencher le rollback.
- **FR-010**: Le système DOIT interroger le cache d'embeddings avant tout appel au service d'embedding externe.
- **FR-011**: Le système DOIT mettre en cache les embeddings calculés pour éviter les recalculs sur du contenu identique.
- **FR-012**: Le système DOIT compresser les vecteurs selon la configuration de quantification active.

**Orchestration et tracking**

- **FR-013**: L'orchestrateur DOIT coordonner la séquence complète : validation → scan antivirus → stratégie → découpage → indexation → tracking.
- **FR-014**: Le système DOIT enregistrer l'état de chaque batch (STARTED, COMPLETED, FAILED) dans un registre.
- **FR-015**: Le système DOIT supprimer les embeddings d'un batch en cas d'échec (rollback automatique). Les cas déclenchant l'échec incluent : erreur dans la stratégie d'extraction, contenu extrait vide (`EmptyContentException`), et toute exception non récupérable durant l'indexation.
- **FR-016**: Le registre de batch DOIT conserver les métadonnées associées : nom du fichier, taille, horodatage.
- **FR-017**: L'orchestrateur DOIT émettre exactement une notification de progression par étape du pipeline (validation, scan antivirus, extraction, indexation, tracking) — soit 5 notifications maximum par batch.

### Key Entities

- **Stratégie d'ingestion**: Composant responsable de l'extraction de texte pour un type de fichier donné. Associée à un type MIME, retourne une liste de segments textuels.
- **Chunk**: Segment de texte découpé à partir du contenu extrait, caractérisé par une taille maximale et un chevauchement avec les chunks adjacents.
- **Vecteur d'embedding**: Représentation numérique d'un chunk, pouvant être compressée. Stocké dans le store vectoriel avec référence au batch et au fichier source.
- **Cache d'embedding**: Stockage intermédiaire associant une empreinte de texte à son vecteur calculé. Les entrées expirent automatiquement via un TTL configurable (défaut : 7 jours) ; aucune invalidation manuelle n'est requise.
- **Batch d'ingestion**: Unité de traitement regroupant tous les chunks et embeddings produits pour un fichier donné. Possède un identifiant, un état, et des métadonnées.
- **Registre de rollback**: Catalogue des embeddings créés dans un batch, permettant leur suppression atomique en cas d'échec.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % des formats de fichiers supportés (PDF, DOCX, XLSX, image, texte brut) produisent du texte exploitable sans intervention manuelle.
- **SC-002**: Un contenu textuel identique soumis deux fois ne déclenche qu'un seul appel au service d'embedding externe (taux de réutilisation du cache ≥ 100 % pour les doublons exacts).
- **SC-003**: Un batch échoué ne laisse aucun embedding orphelin dans le store vectoriel après rollback (cohérence à 100 %).
- **SC-004**: Le découpage d'un texte respecte les paramètres configurés (taille de chunk, chevauchement) avec une précision de ±1 unité pour les cas limites.
- **SC-005**: La compression vectorielle réduit la dimension des vecteurs d'au moins 80 % par rapport à la dimension originale lorsqu'elle est activée.
- **SC-006**: Chaque étape du pipeline d'ingestion est tracée : exactement 5 notifications de progression sont émises par batch (une par étape : validation, scan, extraction, indexation, tracking).
- **SC-007**: 80 % du code des modules couverts par cette phase (stratégies, cache, compression, tracker) est couvert par les tests unitaires.

## Clarifications

### Session 2026-03-27

- Q: Lorsqu'une stratégie d'ingestion retourne un contenu vide, que doit faire le système ? → A: Lever une `EmptyContentException` — le batch passe FAILED et le rollback est déclenché (Option B).
- Q: En cas d'indisponibilité du service d'embedding pendant l'indexation, que doit faire l'orchestrateur ? → A: Réessayer avec backoff limité (max 3 tentatives), puis FAILED + rollback (Option B).
- Q: À quelle granularité les notifications de progression doivent-elles être émises ? → A: Une notification par étape du pipeline (validation, scan, extraction, indexation, tracking) — 5 max par batch (Option A).
- Q: Les entrées du cache d'embeddings expirent-elles automatiquement ou sont-elles permanentes ? → A: Expiration automatique via TTL configurable (7 jours par défaut) (Option A).

## Assumptions

- Les tests unitaires couvrent chaque classe indépendamment ; les dépendances externes (service d'embedding, store vectoriel, analyseur visuel) sont simulées par des mocks.
- La stratégie de secours générique (Tika) est toujours disponible comme dernier recours ; aucune erreur ne doit être levée pour un type MIME inconnu.
- La compression vectorielle est optionnelle et activée par configuration ; en l'absence de configuration, les vecteurs sont stockés à leur dimension originale.
- Le cache d'embeddings utilise une empreinte du contenu textuel comme clé ; deux textes identiques partagent toujours la même entrée de cache. Les entrées expirent automatiquement après 7 jours (TTL configurable).
- L'analyseur visuel peut être indisponible ; dans ce cas, un texte de substitution est produit sans bloquer l'ingestion.
- Les tests de la Phase 2 s'appuient sur les fondations de la Phase 1 (validation, déduplication, sécurité) qui sont supposées fonctionnelles.
- Aucun appel réseau réel n'est effectué dans les tests unitaires ; WireMock peut être utilisé pour les interactions HTTP simulées.
