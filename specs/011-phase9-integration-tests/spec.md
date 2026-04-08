# Feature Specification: PHASE 9 — Tests d'Intégration NexRAG

**Feature Branch**: `011-phase9-integration-tests`  
**Created**: 2026-04-06  
**Status**: Draft  
**Input**: User description: "PHASE 9 — Tests d'Intégration: validation du pipeline complet d'ingestion, retrieval et streaming RAG avec Testcontainers"

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Validation du pipeline d'ingestion bout-en-bout (Priority: P1)

En tant que testeur, je veux valider que le pipeline complet d'ingestion fonctionne de bout en bout — depuis la réception d'un document jusqu'à son stockage dans la base vectorielle — afin de garantir la cohérence de l'ensemble du système avant une mise en production.

**Why this priority**: L'ingestion est le point d'entrée de toute la plateforme. Si le pipeline d'ingestion est défaillant, aucune fonctionnalité RAG n'est utilisable. Ce test valide la chaîne critique : réception du document → analyse antivirale → découpage → génération d'embeddings → stockage vectoriel → déduplication.

**Independent Test**: Peut être testé indépendamment en soumettant un document de chaque format supporté (PDF, DOCX, XLSX, image) à l'API d'ingestion et en vérifiant que les embeddings sont bien présents dans la base vectorielle et que la déduplication fonctionne lors d'un second envoi du même fichier.

**Acceptance Scenarios**:

1. **Given** un document de chaque format supporté (PDF, DOCX, XLSX, image) est prêt à être soumis, **When** l'utilisateur envoie ce document via l'API d'ingestion, **Then** le document est ingéré avec succès en moins de 10 secondes pour chaque format.
2. **Given** le document a été ingéré, **When** on interroge la base vectorielle, **Then** au moins un embedding est enregistré pour ce document (nombre de résultats > 0).
3. **Given** le même PDF a déjà été ingéré, **When** l'utilisateur soumet à nouveau le même fichier, **Then** le système retourne un statut DUPLICATE sans relancer le pipeline d'ingestion.

---

### User Story 2 — Validation du pipeline RAG complet (Priority: P2)

En tant que testeur, je veux valider la chaîne complète de traitement d'une requête — depuis la saisie de la question jusqu'à la réponse en streaming — afin de garantir la qualité et la cohérence des réponses générées par le système.

**Why this priority**: Le pipeline RAG est la finalité de la plateforme. Il dépend de l'ingestion (P1) mais valide des aspects spécifiques : la pertinence du retrieval, la continuité du streaming, et le maintien de l'historique de conversation.

**Independent Test**: Peut être testé indépendamment (après un document ingéré en P1) en envoyant une requête pertinente et en vérifiant que plusieurs passages sont retournés et que la réponse commence à être streamée avant la fin du traitement.

**Acceptance Scenarios**:

1. **Given** un document pertinent a été ingéré, **When** l'utilisateur pose une question liée au contenu de ce document, **Then** au moins 3 passages du document sont inclus dans le contexte de réponse.
2. **Given** une requête est en cours de traitement, **When** la réponse commence à être générée, **Then** des tokens de réponse sont transmis à l'utilisateur avant que la génération ne soit totalement terminée.
3. **Given** l'utilisateur a posé une première question et reçu une réponse, **When** l'utilisateur pose une question de suivi dans la même session, **Then** le système prend en compte l'historique de la conversation pour formuler sa réponse.

---

### User Story 3 — Validation du limiteur de débit en conditions réelles (Priority: P3)

En tant que testeur, je veux valider que les limites de débit sont appliquées correctement en conditions d'intégration — avec une vraie infrastructure — afin de garantir la protection du système en production.

**Why this priority**: Le rate limiting est un mécanisme de protection critique mais il n'est pertinent de le tester en intégration qu'une fois les pipelines principaux validés.

**Independent Test**: Peut être testé indépendamment en envoyant un nombre de requêtes supérieur au seuil autorisé et en vérifiant que les requêtes excédentaires sont refusées avec le code de statut approprié.

**Acceptance Scenarios**:

1. **Given** un utilisateur envoie des requêtes en dessous du seuil autorisé, **When** chaque requête est traitée, **Then** toutes les requêtes reçoivent une réponse normale.
2. **Given** un utilisateur dépasse le seuil de débit autorisé, **When** la requête excédentaire arrive, **Then** le système refuse la requête et indique que la limite a été atteinte.

---

### Edge Cases

- Un document contenant un virus est détecté lors de l'analyse antivirale : le système rejette immédiatement le document avec un statut d'erreur explicite VIRUS_DETECTED, sans poursuivre le pipeline d'ingestion. Ce scénario est couvert par un test d'intégration dédié en PHASE 9.
- Comment le système se comporte-t-il si le service de stockage vectoriel est temporairement indisponible pendant l'ingestion ?
- Que se passe-t-il si une requête de streaming est interrompue avant la fin de la génération ?
- Comment le système gère-t-il un document vide ou corrompu soumis à l'ingestion ?
- Que se passe-t-il si l'historique de conversation est trop long pour tenir dans le contexte du modèle ?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système DOIT valider le pipeline d'ingestion complet pour chaque format supporté (PDF, DOCX, XLSX, image), depuis la réception du document jusqu'à son stockage dans la base vectorielle, en passant par l'analyse antivirale, le découpage en chunks et la génération d'embeddings.
- **FR-002**: Le système DOIT détecter et rejeter les documents déjà ingérés précédemment, en retournant un statut de duplication, sans relancer le traitement.
- **FR-003**: Le système DOIT valider que les embeddings générés sont correctement persistés et requêtables après ingestion.
- **FR-004**: Le système DOIT valider le pipeline de retrieval en s'assurant qu'une requête pertinente retourne un minimum de 3 passages du document ingéré.
- **FR-005**: Le système DOIT valider que la génération de réponse fonctionne en mode streaming, c'est-à-dire que des fragments de réponse sont transmis progressivement avant la fin complète du traitement.
- **FR-006**: Le système DOIT maintenir et utiliser l'historique des échanges dans une session de conversation lors du traitement de questions de suivi.
- **FR-007**: Le système DOIT appliquer les limites de débit configurées et rejeter les requêtes excédentaires avec un statut approprié.
- **FR-008**: Les services externes (API de génération d'embeddings) DOIVENT être simulés pendant les tests pour éviter des appels réels et garantir la reproductibilité des résultats.
- **FR-009**: Les services d'infrastructure (base vectorielle, cache distribué, service antivirale) DOIVENT être provisionnés une seule fois pour toute la suite de tests d'intégration (instance partagée), avec nettoyage des données entre les classes de tests (par truncation ou espace de nommage dédié), et arrêt à la fin de la suite complète.
- **FR-010**: Le système DOIT rejeter tout document pour lequel une menace virale est détectée lors de l'analyse antivirale, en retournant un statut d'erreur explicite VIRUS_DETECTED, sans déclencher les étapes suivantes du pipeline d'ingestion. Ce comportement DOIT être validé par un test d'intégration dédié.

### Key Entities

- **Document ingéré**: Représente un fichier soumis au système, avec ses métadonnées, ses chunks de texte découpés et ses vecteurs d'embedding associés dans la base vectorielle.
- **Session de conversation**: Représente un fil de dialogue entre un utilisateur et le système, conservant l'historique des questions et réponses pour permettre les questions de suivi contextuelles.
- **Résultat de retrieval**: Représente les passages de documents sélectionnés par le moteur de recherche vectorielle en réponse à une requête, avec leur score de pertinence.
- **Réponse streamée**: Représente la réponse générée par le modèle de langage, transmise progressivement sous forme de fragments successifs jusqu'à completion.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un document de chaque format supporté (PDF, DOCX, XLSX, image) est entièrement ingéré (analyse + découpage + stockage vectoriel) en moins de 10 secondes par document dans l'environnement de test.
- **SC-002**: 100% des documents ingérés ont leurs embeddings correctement persistés et sont requêtables immédiatement après l'ingestion.
- **SC-003**: La déduplication détecte et rejette 100% des tentatives de réingestion d'un fichier déjà traité.
- **SC-004**: Une requête pertinente retourne au minimum 3 passages distincts issus du document ingéré en moins de 2 secondes.
- **SC-005**: Les premiers fragments de réponse en streaming sont reçus dans un délai inférieur à 3 secondes après la soumission de la requête, pour 100% des requêtes traitées.
- **SC-006**: L'historique de conversation est correctement maintenu et influencé dans 100% des échanges de suivi dans une même session.
- **SC-007**: 100% des requêtes dépassant le seuil de débit autorisé sont refusées avec le code de statut approprié.
- **SC-008**: La suite de tests d'intégration complète s'exécute et produit des résultats reproductibles dans un environnement isolé, sans dépendances vers des services externes réels.

## Clarifications

### Session 2026-04-06

- Q: Stratégie d'isolation des tests d'intégration → A: Conteneurs partagés pour toute la suite (un seul démarrage) — nettoyage des données entre classes de tests par truncation ou namespace dédié.
- Q: Cibles de performance pour le retrieval et le streaming → A: Retrieval < 2s / Premier token streamé < 3s.
- Q: Comportement attendu lors d'une détection de virus → A: Rejet immédiat avec statut VIRUS_DETECTED, sans poursuivre le pipeline — cas couvert par un test d'intégration dédié en PHASE 9.
- Q: Périmètre des types de documents testés en intégration → A: Tous les formats supportés (PDF, DOCX, XLSX, image) — couverture maximale.
- Q: Budget de durée maximale de la suite en CI/CD → A: < 10 minutes pour la suite complète.

## Assumptions

- Les tests d'intégration s'exécutent dans un environnement isolé provisionné automatiquement, distinct de l'environnement de production ou de développement.
- Les services d'infrastructure requis (base vectorielle, cache distribué, service antivirale) sont disponibles via des conteneurs provisionnés à la demande pour la durée des tests.
- L'API externe de génération d'embeddings et de complétion est simulée (bouchonnée) pendant les tests pour garantir la reproductibilité et éviter des coûts opérationnels.
- Les tests d'intégration sont exécutés après la validation des tests unitaires des phases précédentes (phases 1 à 8).
- Des fichiers de test représentatifs pour chaque format supporté (PDF, DOCX, XLSX, image) sont disponibles dans les ressources de test pour valider le pipeline d'ingestion.
- Le pipeline de rate limiting est configuré avec des seuils identifiables pour permettre de tester les deux branches (sous le seuil et au-dessus du seuil).
- La suite de tests d'intégration complète s'exécute en moins de 10 minutes en pipeline CI/CD, garantissant un cycle de feedback rapide.
