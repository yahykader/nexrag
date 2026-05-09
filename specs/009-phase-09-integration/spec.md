# Feature Specification: PHASE 9 — Tests d'Intégration

**Feature Branch**: `009-phase-09-integration`  
**Created**: 2026-05-07  
**Status**: Draft  

## Clarifications

### Session 2026-05-07

- Q: Quels formats de documents sont testés en intégration bout-en-bout ? → A: Tous les formats supportés — PDF, DOCX, XLSX, images et texte brut.
- Q: Que retourne le système quand deux clients soumettent le même fichier simultanément ? → A: L'un réussit (SUCCESS), l'autre reçoit DUPLICATE — protection par déduplication atomique.
- Q: Quelle est la latence maximale acceptable pour le pipeline de récupération (requête → passages classés) ? → A: 3 secondes — cohérent avec le timeout par retriever défini en Phase 3.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Validation Ingestion Bout-en-Bout (Priority: P1)

En tant qu'ingénieur qualité, je veux valider le pipeline complet d'ingestion depuis le dépôt d'un document jusqu'à son stockage dans la base vectorielle, afin d'avoir la certitude que tous les composants fonctionnent correctement ensemble — et pas seulement en isolation.

**Why this priority**: L'ingestion est le socle du système RAG. Si les documents ne sont pas correctement ingérés et indexés, toutes les capacités de recherche et de génération échouent. Il s'agit du chemin d'intégration le plus critique.

**Independent Test**: Peut être testé de façon autonome en soumettant un document PDF via l'API, puis en vérifiant : (1) les vecteurs sont bien présents dans la base vectorielle, (2) une deuxième soumission du même document est détectée comme doublon, (3) le tout s'exécute en moins de 10 secondes.

**Acceptance Scenarios**:

1. **Given** un document valide (PDF 2 pages, DOCX, XLSX, image ou fichier texte), **When** il est soumis via l'endpoint d'upload, **Then** le système l'ingère en moins de 10 secondes, stocke au moins un vecteur dans la base vectorielle, et retourne un statut succès avec un identifiant de batch.
2. **Given** un document déjà ingéré, **When** le même fichier est soumis à nouveau, **Then** le système retourne le statut DUPLICATE sans recréer de vecteurs ni relancer le traitement.
3. **Given** un fichier contenant du contenu malveillant simulé, **When** il est soumis à l'ingestion, **Then** le système le rejette avec une erreur de détection de virus et ne stocke aucun vecteur.
4. **Given** deux clients soumettent le même fichier simultanément, **When** les deux requêtes arrivent en concurrence, **Then** exactement l'une reçoit le statut SUCCESS et l'autre reçoit DUPLICATE — aucun vecteur redondant n'est stocké.

---

### User Story 2 - Validation Pipeline RAG Complet (Priority: P2)

En tant qu'ingénieur qualité, je veux valider la chaîne complète de traitement d'une requête — depuis la question utilisateur jusqu'aux passages contextuels classés — afin de confirmer que la recherche sémantique fonctionne correctement de bout en bout.

**Why this priority**: La qualité de la récupération est la proposition de valeur centrale du système RAG. Valider ce pipeline de bout en bout garantit que la transformation de requête, la récupération multi-sources et le reclassement opèrent correctement ensemble.

**Independent Test**: Peut être testé de façon autonome en ingérant un document, puis en interrogeant le système avec une question dont la réponse est dans le document, et en vérifiant qu'au moins 3 passages pertinents sont retournés classés par pertinence décroissante.

**Acceptance Scenarios**:

1. **Given** un document ingéré contenant des informations précises, **When** une requête pertinente est soumise, **Then** au moins 3 passages issus du document sont récupérés et retournés en ordre décroissant de pertinence, en moins de 3 secondes.
2. **Given** deux requêtes successives dans la même session, **When** la deuxième requête est soumise, **Then** l'historique de la première interaction est préservé et accessible pour contextualiser la réponse.

---

### User Story 3 - Validation Streaming de Réponse (Priority: P3)

En tant qu'ingénieur qualité, je veux valider que l'assistant produit des réponses en streaming token par token, afin de confirmer que la livraison en temps réel fonctionne correctement sur l'ensemble du pipeline.

**Why this priority**: Le streaming est le mécanisme de livraison visible par l'utilisateur final pour toutes les réponses IA. Sans validation du streaming, l'expérience utilisateur ne peut pas être garantie.

**Independent Test**: Peut être testé de façon autonome en soumettant une requête et en capturant le flux de réponse — en vérifiant que des tokens de contenu arrivent avant le signal de fin de flux.

**Acceptance Scenarios**:

1. **Given** une requête valide avec du contenu pertinent ingéré, **When** l'endpoint de streaming est appelé, **Then** la réponse contient au moins un token de contenu livré avant le signal de fin de flux.
2. **Given** une condition d'erreur survient pendant la génération, **When** l'endpoint de streaming est appelé, **Then** un événement d'erreur est émis sans crash serveur, et les requêtes suivantes s'exécutent normalement.

---

### User Story 4 - Validation du Rate Limiting en Conditions Réelles (Priority: P4)

En tant qu'ingénieur qualité, je veux valider que le rate limiting applique correctement les quotas dans des conditions distribuées réelles, afin d'avoir la certitude que le système est protégé contre les abus en production.

**Why this priority**: Le rate limiting protège la stabilité du système. Le valider avec un vrai système distribué confirme que l'application des quotas fonctionne au-delà des tests unitaires mockés.

**Independent Test**: Peut être testé de façon autonome en envoyant des requêtes dépassant le quota configuré, puis en vérifiant les réponses HTTP 429 avec les headers de retry appropriés.

**Acceptance Scenarios**:

1. **Given** l'endpoint d'upload est limité à 10 requêtes/minute, **When** une 11ème requête est soumise dans la même minute depuis le même client, **Then** le système retourne HTTP 429 avec les headers `Retry-After` et `X-RateLimit-Remaining: 0`.
2. **Given** le service de quotas distribué devient temporairement indisponible, **When** une requête arrive, **Then** le système la laisse passer (fail-open) sans retourner d'erreur au client.

---

### User Story 5 - Validation Pipeline Complet Ingestion→Requête→Réponse (Priority: P5)

En tant qu'ingénieur qualité, je veux exécuter un test de régression couvrant le flux complet — depuis l'ingestion d'un document jusqu'à la réponse streaming — afin de garantir qu'aucune régression n'affecte l'ensemble du système.

**Why this priority**: Les tests en silos ne suffisent pas à détecter les régressions d'intégration entre couches. Un test de bout en bout couvrant l'ensemble du flux offre la couverture de régression la plus complète.

**Independent Test**: Peut être testé de façon autonome en exécutant la séquence : upload d'un document → attente de l'ingestion → envoi d'une requête pertinente → vérification de la réponse streaming avec contenu issu du document.

**Acceptance Scenarios**:

1. **Given** aucun document préalablement ingéré, **When** un document est uploadé puis une requête est soumise portant sur son contenu, **Then** la réponse streaming inclut des passages tirés du document et se termine par un signal de fin.
2. **Given** un pipeline complet opérationnel, **When** tous les tests d'intégration sont exécutés en séquence, **Then** chaque test démarre avec un état propre sans résidus des tests précédents.

---

### Edge Cases

- Que se passe-t-il si le scanner antivirus est indisponible pendant l'ingestion — l'ingestion est-elle bloquée ou autorisée ?
- Comment le pipeline se comporte-t-il lorsque le service IA externe retourne une erreur en milieu de flux ?
- Que se passe-t-il quand la base vectorielle ne contient aucun document correspondant à la requête ?
- ~~Comment le système gère-t-il l'ingestion simultanée du même fichier par deux clients concurrents ?~~ → Résolu : exactement un SUCCESS et un DUPLICATE, déduplication atomique garantie (voir US-1 scénario 4).
- Que se passe-t-il si le cache distribué est vide (cold start) lors d'une requête d'embedding ?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système DOIT valider le pipeline d'ingestion complet pour tous les formats supportés (PDF, DOCX, XLSX, images, texte brut) avec de l'infrastructure réelle (base vectorielle, cache distribué, scanner antivirus) — aucun composant d'infrastructure ne peut être mocké.
- **FR-002**: Le système DOIT valider qu'un document soumis deux fois — y compris en soumission simultanée par deux clients concurrents — produit exactement un SUCCESS et un DUPLICATE, sans créer de vecteurs redondants.
- **FR-003**: Le système DOIT valider qu'une requête portant sur du contenu ingéré retourne au moins 3 passages pertinents classés par pertinence décroissante.
- **FR-004**: Le système DOIT valider que les réponses en streaming livrent des tokens de contenu avant le signal de fin de flux.
- **FR-005**: Le système DOIT valider que l'historique de conversation persiste et est récupérable entre deux requêtes successives d'une même session.
- **FR-006**: Le système DOIT valider l'application du rate limiting avec un vrai gestionnaire de quotas distribué — les limites par endpoint doivent être respectées.
- **FR-007**: Le système DOIT valider que les appels au service IA externe utilisent des réponses simulées pré-configurées — aucun frais d'API réel ne doit être engagé pendant les tests.
- **FR-008**: Le système DOIT valider que le scanner antivirus bloque les fichiers malveillants et laisse passer les fichiers sains.
- **FR-009**: Chaque test d'intégration DOIT être isolé — chaque test commence avec un état propre, sans résidus des tests précédents.
- **FR-010**: Le système DOIT valider le flux complet bout-en-bout : upload de document → stockage de vecteurs → requête → récupération de passages → réponse streaming.

### Key Entities

- **Pipeline d'Ingestion**: La séquence d'étapes transformant un document uploadé en vecteurs indexés et recherchables (antivirus → déduplication → parsing → découpage → vectorisation → stockage).
- **Pipeline de Requête**: La séquence d'étapes transformant une question utilisateur en passages contextuels classés (transformation → routage → récupération multi-sources → reclassement → injection de contexte).
- **Pipeline de Streaming**: La séquence d'étapes transformant une requête et ses passages contextuels en réponse IA livrée token par token au client.
- **Environnement de Test d'Intégration**: Une infrastructure de test autonome et éphémère incluant base vectorielle, cache distribué, scanner antivirus, et service IA simulé.
- **Fixture de Test**: Un ensemble de petits documents de test (PDF 2 pages, DOCX, XLSX, image JPEG, fichier texte brut) utilisés comme données d'entrée reproductibles pour valider chaque format supporté.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Chaque format supporté (PDF, DOCX, XLSX, image, texte brut) est entièrement ingéré et ses vecteurs sont vérifiables dans la base en moins de 10 secondes par document de test.
- **SC-002**: Une deuxième soumission du même document est détectée comme doublon en moins de 2 secondes, sans création de nouvelles entrées vectorielles.
- **SC-003**: Une requête pertinente retourne au moins 3 passages classés issus d'un document préalablement ingéré, en moins de 3 secondes.
- **SC-004**: Le premier token de contenu d'une réponse streaming est livré en moins de 5 secondes, le flux se terminant par un signal de complétion.
- **SC-005**: Le rate limiting rejette les requêtes en excès (HTTP 429) dans la même fenêtre temporelle tout en servant normalement les requêtes dans les quotas.
- **SC-006**: Les 5 suites de tests d'intégration passent intégralement dans un environnement CI/CD propre sans accès réseau externe ni credentials API réels.
- **SC-007**: Le test de pipeline complet (ingestion → requête → streaming) se termine avec succès en moins de 30 secondes.
- **SC-008**: Aucun test ne laisse de données résiduelles affectant les tests suivants — l'isolation est maintenue à 100% entre les suites.

## Assumptions

- L'infrastructure de test (base vectorielle, cache distribué, scanner antivirus) est provisionnée automatiquement à chaque exécution de test — aucune infrastructure externe persistante n'est requise.
- Les réponses du service IA externe sont simulées avec des réponses pré-configurées — aucune clé API réelle ni accès réseau n'est nécessaire.
- Les tests d'intégration s'exécutent avec la même configuration applicative que la production, seuls les endpoints d'infrastructure étant redirigés vers les services de test.
- Le scanner antivirus est configuré pour reconnaître le fichier EICAR standard comme déclencheur de détection simulée.
- Les fixtures de test couvrent tous les formats supportés (PDF, DOCX, XLSX, image JPEG, texte brut) et sont de petite taille pour maintenir des temps d'exécution rapides.
- La fenêtre de réinitialisation des quotas de rate limiting est de 60 secondes — les tests prennent en compte ce délai.
- Les tests d'intégration peuvent prendre jusqu'à 60 secondes par suite en raison du démarrage des conteneurs — ce délai est acceptable en pipeline CI/CD.
- La couverture de code minimale exigée pour valider les tests d'intégration est de 80% sur les packages ciblés (ingestion, retrieval, streaming).
