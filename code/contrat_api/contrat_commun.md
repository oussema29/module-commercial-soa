# Contrat d'API commun – module Clients, Devis, Campagnes (Groupe B)

Ce document fixe les règles que **tous les services du module respectent**, et que les autres trinômes peuvent reprendre pour s'intégrer. Le détail des routes est dans les trois fichiers OpenAPI : `customer.yaml` (port 8081), `quotation.yaml` (8082), `campaign.yaml` (8083). Les événements sont dans `evenements.md`.

Des exemples de requêtes et de réponses sont intégrés aux schémas principaux.

Pour lire un contrat : coller le contenu d'un `.yaml` dans <https://editor.swagger.io>. Chaque service Spring Boot devra exposer le même contrat avec springdoc-openapi.

## 1. Format

| Élément | Règle |
|---|---|
| Transport | REST, JSON (`application/json`, UTF-8), sauf les PDF (`application/pdf`) |
| Noms de champs | `camelCase` **sans accent** : `dateDebut`, `montantPrevu`, `repondueLe` |
| Énumérations | MAJUSCULES **sans accent** : `ENVOYE`, `TERMINEE`, `FIDELISATION` |
| Identifiants | UUID (chaîne). Les tables de liaison internes ont un entier qui n'apparaît jamais dans l'API |
| Dates | ISO 8601 : `2026-11-18` (date) ou `2026-11-18T14:30:00Z` (date et heure, UTC) |
| Montants | **Nombre décimal** en dinars, trois décimales au plus : `1250.500`. Côté Java : `BigDecimal`, jamais `double`. Pas de champ `devise` |
| Pourcentages | Nombres : `19` pour 19 % (taux de TVA, remises de 0 à 100) |
| Champ absent | Une valeur facultative sans contenu est `null`, jamais une chaîne vide |

## 2. Erreurs

Toutes les erreurs ont le même corps :

```json
{ "code": 404, "message": "Client not found", "timestamp": "2026-11-18T14:30:00Z" }
```

| Code | Sens |
|---|---|
| 200, 201, 204 | Succès (201 à la création, 204 sans contenu) |
| 400 | Requête mal formée (JSON invalide, paramètre illisible) |
| 401 | Non authentifié |
| 403 | Rôle non autorisé pour cette route |
| 404 | Ressource introuvable |
| 409 | L'objet est dans un état qui ne permet pas l'action (devis déjà envoyé, campagne non active, doublon d'email) |
| 422 | La donnée est lisible mais invalide, ou une règle métier n'est pas respectée |
| 503 | Un service dépendant est indisponible, ou l'envoi d'email a échoué ; la cause est dans `message` |

Règle pour distinguer 409 et 422 : **409** parle de l'état de la ressource, **422** parle de la donnée envoyée.

## 3. Listes paginées

Toute liste de ressources acceptant un filtre est paginée : `?page=0&taille=20` (taille maximale 100). Réponse :

```json
{ "contenu": [ ... ], "page": 0, "taille": 20, "total": 134 }
```

Certaines listes courtes (contacts d'un client, catégories, taxes, canaux, publics, contenus) renvoient directement un tableau JSON.

## 4. Identité et rôles

- Le service lit l'utilisateur courant dans un seul composant « contexte utilisateur ». En développement il renvoie un utilisateur et un rôle par défaut ; plus tard il lira le JWT `Authorization: Bearer ...`. Le contrat déclare donc `bearerAuth`, sans qu'il soit obligatoire pour l'instant.
- Quatre rôles : `ADMIN`, `COMMERCIAL`, `MARKETING`, `RESPONSABLE`. Le rôle autorisé de **chaque route** est dans l'extension `x-roles` de son opération ; un autre rôle reçoit 403. Les routes `/public/**` portent `x-roles: [PUBLIC]` : pas d'identité, elles s'authentifient par un jeton dans l'URL.
- Un service qui en appelle un autre **retransmet l'identité** reçue. Les routes publiques ne font aucun appel synchrone : elles publient seulement un événement.
- `auteurId` et `creePar` viennent du contexte utilisateur et ne sont jamais saisis dans un corps de requête.

**Obtenir un jeton.** Aujourd'hui, par le service de login **provisoire** (`auth.yaml`, `POST /login`, quatre utilisateurs fictifs) ; plus tard, par le service Authentication de l'autre trinôme. Les trois services du module ne dépendent d'aucun des deux : ils vérifient seulement le jeton.

**Contenu du JWT (hypothèse retenue, à confirmer avec le service Authentication).** Signature HS256 avec un secret partagé ; le **rôle** est dans le champ `role` (le nom du champ se règle par `app.security.role-claim`) ; l'**identifiant de l'utilisateur** est le champ `sub`, un UUID. Sans jeton valide : 401 ; rôle non autorisé : 403, avec le corps d'erreur de la section 2. Les droits sont vérifiés avant la validation du corps de la requête : un rôle non autorisé reçoit toujours 403. Mode `app.security.mode=none` (défaut en développement) : aucune authentification.

## 5. Règles d'intégration entre services

| Lien | Règle |
|---|---|
| Lectures | Synchrones, REST, lecture seule : Quotation lit `GET /clients/{id}` (une fois, à la création d'un devis) ; Campaign lit `GET /clients/destinataires?categorieId=…` (au lancement et au renvoi) |
| Écritures | Jamais directes : toujours par événement RabbitMQ (`evenements.md`) |
| Panne d'un service lu | Le service appelant répond **503** avec un message clair, sans rien créer ; il se rétablit seul au retour du service lu |
| Copie plutôt que rappel | Un devis copie le client et les prix ; une publicité copie le nom du produit ; un événement porte toutes les données utiles |
| Aucun accès à la base d'un autre service | Un identifiant venant d'ailleurs est une simple référence, sans clé étrangère |
| Envoi d'email | Une méthode `envoyerEmail(destinataire, sujet, texte, pièce jointe facultative)` par service qui envoie ; simulée (logs) pour l'instant |

## 6. Décisions prises en écrivant ce contrat

Ces choix ne figuraient pas dans la conception initiale ; ils sont tranchés ici pour que les contrats soient exécutables. **Validées par le trinôme.**

1. **Noms JSON et énumérations sans accent** (`dateValidite`, `ENVOYE`) : les accents dans les noms de champs gênent les outils et les autres langages. Les documents de conception gardent les accents pour la lecture ; la correspondance est directe (`dateValidité` ↔ `dateValidite`).
2. **Montants en nombres décimaux JSON** (`1250.500`), à trois décimales au plus. Le service lit et calcule en `BigDecimal` ; le risque d'arrondi n'existe que chez un client JavaScript, négligeable pour ces montants.
3. **Remises et TVA en pourcentage** (0 à 100), y compris `remiseGlobale`.
4. **Le jeton n'est jamais exposé** par les routes des employés : ni `tokenReponse` dans `Devis`, ni `jeton` dans `Invitation`. Il n'existe que dans l'email du client.
5. **Suppression d'une catégorie** (`DELETE /categories/{id}`) : 409 si des clients l'utilisent encore. La conception prévoit seulement que le client n'est pas supprimé en usage normal.
6. **`GET /clients/destinataires`** : 404 si la catégorie n'existe pas (Campaign le transforme en 422 au lancement) ; liste vide si la catégorie existe sans client. Cela reprend la décision de contrôle de la catégorie.
7. **Création d'un client** : le statut initial est `PROSPECT` par défaut ; le commercial peut aussi créer directement un `CLIENT`. Le statut se change ensuite uniquement par `PATCH /clients/{id}/statut`.
8. **Le second clic** sur « Je suis intéressé » répond **409** « Vous avez déjà répondu », comme un devis déjà traité ; l'interface affiche un message clair, pas une erreur brute.
9. **Publics et canaux** : on peut en ajouter ou supprimer seulement tant que la campagne est BROUILLON (409 sinon), car les invitations ne sont créées qu'au lancement. Les **contenus** restent modifiables à tout moment (corriger un texte n'affecte pas les invitations déjà envoyées).
10. **Événement `ProspectGenere`** : le type du contact s'appelle `typeClient` (et non `type`, déjà pris par l'enveloppe de l'événement). Noms d'échange, de clés et de files RabbitMQ proposés dans `evenements.md`.
11. **Modification d'une campagne** (`PUT /campagnes/{id}`) : les champs de la campagne (nom, objectif, dates) ne se modifient qu'en BROUILLON, 409 sinon. Le statut ne change que par `lancer`, `suspendre`, `reprendre` et `arreter`.

## 7. Ce qu'il reste à préciser

- Noms de rôles (`x-roles`) et contenu du JWT (champs `role` et `sub`, secret ou clé de signature) à confronter avec le service Authentication d'un autre trinôme.
- Choix des noms d'échange et de files RabbitMQ à partager avec les autres trinômes qui voudraient consommer `DevisAccepte`.
