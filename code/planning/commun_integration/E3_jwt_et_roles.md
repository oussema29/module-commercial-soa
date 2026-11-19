# Étape E3 : Authentification JWT et contrôle des rôles

| | |
|---|---|
| **Service** | Les trois services |
| **Responsable** | Tout le trinôme |
| **Ce que couvre l'étape** | Authentification JWT et contrôle des rôles |
| **Dossier de code** | `code/ (les trois services)` |
| **Code fourni** | Non : à écrire à partir de cette fiche |
| **Prérequis** | E2 validée ; service Authentication de l'autre trinôme disponible |
| **État** | À faire (dépend d'un autre trinôme) |

## 1. Objectif

Ajouter en dernier la sécurité : lire le JWT émis par le service Authentication, et refuser (403) les rôles non autorisés sur chaque route. Jusqu'ici, tout est ouvert.

**Pas dans cette étape :**

- Réaliser le service Authentication : il appartient à un autre trinôme.

## 2. Modèle de données

Aucune nouvelle table.

## 3. API à réaliser

Aucune route dans cette étape.

## 4. Règles métier à respecter

- Quatre rôles : `ADMIN`, `COMMERCIAL`, `MARKETING`, `RESPONSABLE`. Le rôle autorisé de chaque route est dans la colonne « Rôles » des fiches et dans `x-roles` des fichiers `contrat_api/*.yaml`.
- Sans JWT valide sur une route d'employé : 401 ; avec un rôle non autorisé : 403.
- Les routes `/public/**` et `/actuator/health` ne demandent **aucune** identité.
- `auteurId` et `creePar` (qui prenaient `app.default-user-id`) viennent désormais de l'identifiant de l'utilisateur dans le JWT ; ils ne sont jamais lus dans le corps d'une requête.
- Un service qui en appelle un autre retransmet le JWT reçu (Quotation → Customer, Campaign → Customer).
- Si l'autre trinôme utilise d'autres noms de rôles, on les convertit en un seul endroit (une petite table de correspondance) : aucune route ne change.
- Le service Authentication et la Gateway sont réalisés ailleurs ; ici on ne fait que s'y connecter.

## 5. Tâches, dans l'ordre

- [ ] Se mettre d'accord avec le trinôme Authentication sur : le champ du JWT qui contient le rôle, ses valeurs possibles, le champ qui contient l'identifiant de l'utilisateur, et la clé ou l'adresse de validation de la signature.
- [ ] Ajouter `spring-boot-starter-security` et `spring-boot-starter-oauth2-resource-server` dans chaque service ; configurer la validation du JWT (`SecurityConfig`).
- [ ] Convertir le champ « rôle » du JWT en rôle Spring (`ROLE_ADMIN`…).
- [ ] Protéger chaque route selon sa colonne « Rôles » (`@PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL')")` sur les méthodes des contrôleurs).
- [ ] Laisser `/public/**` et `/actuator/health` sans authentification.
- [ ] Remplacer `app.default-user-id` par l'identifiant lu dans le JWT.
- [ ] Retransmettre le JWT dans les appels entre services.
- [ ] Tests : jeton valide, expiré, mal signé, absent, rôle insuffisant.

## 6. Fichiers créés ou modifiés

- `<service>/config/SecurityConfig.java (dans chacun des trois services)`
- `<service>/controller/* (annotations de rôles)`
- `<service>/pom.xml, application.yml (clé de validation du JWT)`

## 7. Tests à écrire

- Sans jeton → 401 ; jeton expiré ou mal signé → 401 ; rôle insuffisant → 403 ; bon rôle → 200.
- Exemples : `COMMERCIAL` ne peut pas `DELETE /clients/{id}` (403) ; `MARKETING` ne peut pas `GET /clients` (403) mais peut `GET /clients/destinataires` ; `RESPONSABLE` lit mais ne crée rien.
- Routes `/public/**` : accessibles sans jeton.
- L'appel Quotation → Customer retransmet le jeton (vérifier côté Customer qui est l'utilisateur).
- `creePar` d'une campagne créée est l'identifiant du jeton.

## 8. Points d'attention

- Ne jamais mettre une clé de signature réelle dans git : variable d'environnement.
- Cette étape est le seul qui dépend d'une équipe extérieure : s'il n'est pas prêt à temps, le module reste complet et démontrable sans authentification.
- Les pages publiques restent publiques : leur sécurité repose sur le jeton d'URL (une amélioration possible, hors périmètre : limiter le nombre de requêtes par adresse IP et ajouter un captcha).

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` dans les trois services : tout vert.
2. Avec un jeton fourni par Authentication (ou un jeton de test signé avec la clé convenue) : une route d'employé sans jeton → 401 ; avec le rôle MARKETING, `POST /campagnes` → 201 et `creePar` est l'identifiant du jeton ; avec le rôle COMMERCIAL → 403.
3. `GET /public/reponse/<jeton>` fonctionne sans jeton.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-E3`, push (voir `README.md`).

Message de commit proposé :

```
feat: authentification JWT et contrôle des rôles sur toutes les routes
```
