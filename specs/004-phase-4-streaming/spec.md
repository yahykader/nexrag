# Feature Specification: Phase 4 — Streaming (Conversational AI avec Réponses en Temps Réel)

**Feature Branch**: `004-phase-4-streaming`
**Created**: 2026-03-30
**Status**: Draft
**Input**: User description: "read nexrag-test-plan-speckit.md and create a specification for the PHASE 4 — Streaming"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Gestion de l'historique de conversation (Priority: P1)

En tant qu'utilisateur du chat, je veux que le système se souvienne des messages précédents de notre échange, afin que la réponse suivante tienne compte du contexte de la conversation et ne répète pas des informations déjà établies.

**Why this priority**: Sans persistance de contexte, chaque message serait traité comme une conversation indépendante, rendant le chat inutilisable pour des échanges multi-tours. C'est le fondement de toute expérience conversationnelle cohérente.

**Independent Test**: Peut être testé en créant une conversation, en ajoutant plusieurs messages, puis en vérifiant que l'historique retourné contient bien tous les tours dans le bon ordre — sans nécessiter de streaming ni de RAG.

**Acceptance Scenarios**:

1. **Given** aucune conversation n'existe pour un utilisateur, **When** il envoie son premier message, **Then** une nouvelle conversation est créée avec un identifiant unique et le message est enregistré dans l'historique.
2. **Given** une conversation active avec 5 messages, **When** un 6ème message est ajouté, **Then** l'historique contient les 6 messages dans l'ordre chronologique.
3. **Given** une conversation dont l'historique a atteint la limite configurée (ex. 10 messages), **When** un nouveau message est ajouté, **Then** le message le plus ancien est retiré et la fenêtre reste à la taille maximale.
4. **Given** une conversation dont le TTL a expiré, **When** le scheduler de nettoyage s'exécute, **Then** la conversation est supprimée sans laisser de données orphelines.
5. **Given** une conversation appartenant à l'utilisateur A, **When** l'utilisateur B tente d'y accéder avec le bon identifiant de conversation mais son propre `userId`, **Then** le système rejette la requête avec une erreur d'accès refusé sans révéler l'existence de la conversation.

---

### User Story 2 - Réception de la réponse token par token (Priority: P2)

En tant qu'utilisateur, je veux voir la réponse de l'IA s'afficher progressivement mot après mot, afin de percevoir une expérience fluide et réactive sans attendre la fin complète de la génération.

**Why this priority**: Le streaming token par token est la promesse principale de l'interface conversationnelle. Une réponse bloquante (attente de la réponse complète) dégrade fortement la perception de performance et l'expérience utilisateur.

**Independent Test**: Peut être testé en simulant un flux de tokens depuis un serveur externe fictif et en vérifiant que chaque token est émis sous forme d'événement distinct dans le bon ordre, puis qu'un événement de fin de flux est émis en dernier.

**Acceptance Scenarios**:

1. **Given** un flux de tokens disponible depuis le service de génération, **When** le client consomme ce flux, **Then** chaque token produit un événement de type TOKEN émis dans l'ordre de réception.
2. **Given** un flux de tokens en cours, **When** le service de génération signale la fin de la réponse, **Then** un événement de type DONE est émis et aucun autre événement de quelque type que ce soit ne suit — le flux est clos.
3. **Given** un flux actif, **When** le service de génération rencontre une erreur, **Then** un événement de type ERROR est émis avec un message descriptif, et l'application reste stable (pas d'exception non gérée).
4. **Given** que l'émetteur d'événements reçoit un événement ERROR, **When** il le publie vers le client, **Then** le client est notifié de l'erreur sans que le flux soit interrompu de façon brutale.

---

### User Story 3 - Réponse enrichie par le contexte documentaire (Priority: P3)

En tant qu'utilisateur ayant uploadé des documents, je veux que les réponses de l'IA soient fondées sur le contenu de ces documents, afin d'obtenir des réponses pertinentes et sourcées plutôt que des réponses génériques.

**Why this priority**: L'injection du contexte RAG est ce qui différencie NexRAG d'un simple chatbot. Sans cette étape, le streaming fonctionnerait mais perdrait tout son intérêt métier. Priorité P3 car dépend de la chaîne de récupération (Phase 3).

**Independent Test**: Peut être testé en fournissant un contexte RAG simulé (mock) et en vérifiant que l'orchestrateur transmet ce contexte au service de génération avant d'initier le streaming — sans nécessiter de vrai pipeline RAG actif.

**Acceptance Scenarios**:

1. **Given** une requête utilisateur et un contexte RAG disponible, **When** l'orchestrateur de streaming traite la requête, **Then** le contexte documentaire est injecté dans le prompt envoyé au service de génération avant que le streaming commence.
2. **Given** que l'orchestrateur a injecté le contexte RAG et que le flux de tokens est en cours, **When** l'événement DONE est reçu, **Then** `StreamingOrchestrator` reconstruit la réponse complète en concaténant les tokens accumulés et la sauvegarde dans l'historique via `ConversationManager`.
3. **Given** que le contexte RAG est vide (aucun document pertinent trouvé), **When** l'orchestrateur traite la requête, **Then** le streaming continue normalement avec une réponse sans contexte documentaire, sans erreur.

---

### Edge Cases

- Que se passe-t-il si le service de génération ne répond pas ? Après 30 secondes sans token reçu, le système émet un événement ERROR et libère les ressources du flux.
- Que se passe-t-il si la connexion cliente est coupée en milieu de streaming ? Le système doit libérer les ressources associées sans laisser de flux actif orphelin.
- Que se passe-t-il si l'identifiant de conversation fourni n'existe pas ? Le système doit retourner une erreur explicite plutôt que créer silencieusement une nouvelle conversation.
- Que se passe-t-il si un utilisateur tente d'accéder à la conversation d'un autre utilisateur (bon ID, mauvais `userId`) ? Le système doit rejeter la requête avec une erreur d'accès refusé, sans révéler l'existence de la conversation.
- Que se passe-t-il si deux requêtes arrivent simultanément sur la même conversation ? L'historique ne doit pas être corrompu (accès concurrent sécurisé).
- Que se passe-t-il si le flux de tokens est vide (réponse sans contenu) ? Un événement DONE doit être émis même si aucun TOKEN n'a précédé.
- Que se passe-t-il si la limite d'historique est configurée à 0 ou une valeur négative ? Le système doit appliquer une valeur minimale par défaut ou rejeter la configuration invalide au démarrage.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système DOIT créer une nouvelle conversation avec un identifiant unique, associée au `userId` de l'utilisateur initiateur. Cet identifiant est retourné au client pour les requêtes suivantes.
- **FR-002**: Le système DOIT maintenir l'historique complet des messages (tours utilisateur et assistant) au sein d'une conversation.
- **FR-003**: Le système DOIT tronquer l'historique de conversation en retirant les messages les plus anciens lorsque la fenêtre maximale configurée est atteinte.
- **FR-004**: Le système DOIT permettre de récupérer et de supprimer une conversation par son identifiant, à condition que le `userId` fourni corresponde au propriétaire. Toute tentative d'accès avec un `userId` non correspondant DOIT être rejetée avec une erreur explicite.
- **FR-005**: Le système DOIT nettoyer automatiquement les conversations inactives après expiration de leur durée de vie, en s'appuyant sur le TTL natif de Redis (aucun scheduler applicatif nécessaire).
- **FR-006**: Le système DOIT consommer la réponse du service de génération en mode flux, sans attendre la réponse complète.
- **FR-007**: Le système DOIT émettre un événement distinct pour chaque token reçu du service de génération.
- **FR-008**: Le système DOIT émettre un événement de type DONE à la fin d'un flux normal, ou un événement de type ERROR en cas d'anomalie survenant avant la fin (y compris timeout après 30 secondes sans token reçu). DONE est terminal absolu : une fois émis, le flux est clos et aucun autre événement ne peut suivre. Aucune exception non gérée ne doit se propager hors du composant.
- **FR-009**: Le système DOIT injecter le contexte documentaire issu du pipeline RAG dans le prompt avant d'initier le streaming.
- **FR-010**: `StreamingOrchestrator` DOIT coordonner dans l'ordre suivant : récupération RAG → ajout du message utilisateur à l'historique → appel au service de génération → émission des tokens → à réception de DONE, reconstruction de la réponse complète et sauvegarde dans l'historique. `EventEmitter` se limite à la propagation des événements vers le client sans accumulation ni persistance.

### Key Entities

- **Conversation**: Session identifiée de façon unique, persistée dans Redis avec TTL natif, associée à un `userId` propriétaire. Contient l'historique ordonné des messages (auteur + contenu), une date de création et une date d'expiration gérée par Redis. La taille de l'historique est bornée par une valeur configurable. Toute opération sur la conversation requiert que le `userId` demandeur corresponde au propriétaire.
- **Message**: Un tour de conversation, caractérisé par son auteur (utilisateur ou assistant), son contenu textuel et son horodatage.
- **StreamingEvent**: Événement unitaire émis durant la génération d'une réponse. Porte un type (TOKEN, DONE, ERROR) et une charge utile optionnelle (contenu du token ou description d'erreur).
- **StreamingRequest**: Requête entrante contenant la question de l'utilisateur et l'identifiant de la conversation associée.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Les tests unitaires des 4 classes cibles (ConversationManager, EventEmitter, StreamingOrchestrator, OpenAiStreamingClient) atteignent une couverture de ligne et de branche ≥ 80 % chacune.
- **SC-002**: Chaque test unitaire s'exécute en moins de 500 ms sans aucun appel réseau réel (toutes les dépendances externes sont simulées).
- **SC-003**: 100 % des scénarios d'acceptation définis dans les User Stories 1, 2 et 3 sont couverts par au moins un test automatisé.
- **SC-004**: Les événements TOKEN sont émis dans le même ordre que les tokens reçus, vérifié par assertion sur séquence ordonnée dans les tests.
- **SC-005**: Un événement DONE est systématiquement présent en dernier dans tout flux terminé normalement, vérifié dans les tests de bout de flux.
- **SC-006**: Aucune exception non catchée ne se propage hors du composant en cas d'erreur ou de timeout du service de génération, vérifié par test d'injection d'erreur et test de dépassement de timeout (30 s simulés).
- **SC-008**: En cas d'absence de réponse du service de génération, un événement ERROR est émis dans un délai ≤ 30 secondes (simulé par mock temporel dans les tests).
- **SC-007**: La troncature de l'historique est déterministe : après ajout du N+1ème message avec une fenêtre de N, le message le plus ancien est retiré et la taille reste exactement N.

## Clarifications

### Session 2026-03-30

- Q: Où est persisté l'état de conversation (ConversationState) ? → A: Redis (TTL natif, survit aux redémarrages, partagé entre instances — mocké avec Mockito en tests unitaires).
- Q: DONE est-il un état terminal absolu pour le flux d'événements ? → A: Oui — aucun événement de quelque type que ce soit (TOKEN, ERROR, autre) ne peut suivre DONE ; le flux est immédiatement clos.
- Q: Quel composant est responsable d'accumuler les tokens et de sauvegarder la réponse complète dans l'historique ? → A: `StreamingOrchestrator` — il accumule les tokens, reconstruit la réponse à la réception de DONE, puis la persiste via `ConversationManager`.
- Q: L'accès aux conversations est-il restreint par utilisateur ? → A: Oui — chaque conversation est associée à un `userId` ; `ConversationManager` rejette toute opération dont le `userId` ne correspond pas au propriétaire de la conversation.
- Q: Quel est le timeout maximal du flux de génération avant émission d'un ERROR ? → A: 30 secondes — aligné sur les circuit breakers Resilience4j existants du projet.

## Assumptions

- Les tests unitaires de cette phase n'effectuent aucun appel réseau réel : WireMock est utilisé pour simuler le service externe de génération, Mockito pour toutes les autres dépendances internes.
- La configuration de la fenêtre d'historique (`maxHistory`) est un paramètre Spring Boot injecté — les tests peuvent la passer directement au constructeur ou via mock.
- Le pipeline RAG (Phase 3) est considéré comme opérationnel et est simulé par un mock dans les tests de cette phase.
- La livraison des événements SSE vers le client HTTP est hors périmètre de cette phase de tests unitaires ; seule l'émission logique des événements est testée.
- L'expiration des conversations est gérée par le TTL Redis natif. Les tests unitaires vérifient la logique de sauvegarde avec TTL via un mock du `ConversationRepository` — aucun timer réel n'est nécessaire.
- Les tests suivent les conventions de nommage du projet : `[ClasseTestée]Spec.java`, localisés dans `src/test/java/com/exemple/nexrag/service/rag/streaming/`.
