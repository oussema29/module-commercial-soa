# Étape E3 : Authentification JWT et contrôle des rôles

| | |
|---|---|
| **Service** | Les trois services |
| **Responsable** | Tout le trinôme |
| **Ce que couvre l'étape** | Authentification JWT et contrôle des rôles |
| **Dossier de code** | `code/ (les trois services)` |
| **Prérequis** | E2 validée ; service Authentication de l'autre trinôme disponible |
| **État** | Réalisé avec un secret JWT partagé ; le service Authentication de l'autre trinôme n'existe pas encore |

## 1. Objectif

Ajouter en dernier la sécurité : valider le JWT et refuser (401 ou 403) selon le rôle de chaque route. Jusqu'ici, avec `app.security.mode=none`, tout est ouvert.

**Pas dans cette étape :**

- Réaliser le service Authentication : il appartient à un autre trinôme.

## 2. Modèle de données

Aucune nouvelle table.

## 3. API à réaliser

Aucune route dans cette étape.

## 4. Règles métier à respecter

- Quatre rôles : `ADMIN`, `COMMERCIAL`, `MARKETING`, `RESPONSABLE`. Le rôle autorisé de chaque route est dans la colonne « Rôles » des fiches et dans `x-roles` des fichiers `contrat_api/*.yaml`.
- **Deux modes**, propriété `app.security.mode` : `none` (défaut, développement et tests : aucune authentification) ; `jwt` (chaque route d'employé exige un JWT valide, en-tête `Authorization: Bearer ...`).
- En mode `jwt` : pas de jeton, jeton mal signé ou expiré → **401** ; rôle non autorisé → **403** ; le corps de l'erreur est celui du contrat `{code, message, timestamp}`.
- Les droits sont vérifiés **par URL, avant la validation du corps de la requête** (une ligne `requestMatchers(...).hasAnyRole(...)` par route dans `SecurityConfig`, reprise des `x-roles`). Un rôle non autorisé reçoit donc toujours 403, jamais 422.
- Les routes `/public/**` et `/actuator/health` (et Swagger) n'exigent **aucune** identité.
- L'identifiant de l'utilisateur est le champ `sub` du JWT : il remplit `auteurId` (interactions) et `creePar` (devis, campagnes). Sans JWT, c'est la valeur fixe `app.default-user-id`. Jamais lu dans le corps d'une requête.
- Un service qui en appelle un autre **retransmet le JWT** reçu (Quotation → Customer, Campaign → Customer).
- Le champ du JWT qui contient le rôle se règle par `app.security.role-claim` (défaut `role`) ; si l'autre trinôme a d'autres noms de rôles, on les convertit à un seul endroit (`SecurityConfig`).
- La signature est vérifiée avec un secret partagé (HS256, `JWT_SECRET`, 32 caractères au moins), **jamais écrit dans git** : variable d'environnement.

## 5. Tâches, dans l'ordre

- [ ] Se mettre d'accord avec le trinôme Authentication sur : le champ du JWT qui contient le rôle, ses valeurs, le champ de l'identifiant utilisateur (`sub`), et le secret ou la clé de signature.
- [ ] Ajouter `spring-boot-starter-security` et `spring-boot-starter-oauth2-resource-server` dans les trois `pom.xml`.
- [ ] Créer dans chaque service : `config/SecurityConfig` (deux chaînes de filtres, `none` et `jwt`, erreurs 401 et 403 au format du contrat ; les droits de chaque route y sont écrits **explicitement**, une ligne par route, reprise des `x-roles`) et `security/UtilisateurCourant`.
- [ ] Remplacer la valeur fixe par `UtilisateurCourant` dans `InteractionService` (Customer), `DevisService` (Quotation) et `CampagneService` (Campaign).
- [ ] Retransmettre le JWT dans `CustomerClient` (Quotation et Campaign).
- [ ] Ajouter à `application.yml` le bloc `app.security` (`mode`, `jwt-secret`, `role-claim`) et, dans `docker-compose.yml`, `SECURITY_MODE` et `JWT_SECRET`.
- [ ] Écrire `SecuriteJwtTests` (401, 403, auteur = `sub`) et `MatriceRolesTests` (chaque route avec chaque rôle, générée à partir du contrat, plus « sans jeton → 401 »).
- [ ] Rejouer le scénario de bout en bout en mode `jwt` (`--jwt-secret`).

## 6. Fichiers créés ou modifiés

- `<service>/config/SecurityConfig.java ; <service>/security/UtilisateurCourant.java (dans chacun des trois services)`
- `<service>/pom.xml, application.yml`
- `Customer : InteractionService ; Quotation : DevisService, CustomerClient ; Campaign : CampagneService, CustomerClient`
- `tests : SecuriteJwtTests, MatriceRolesTests dans chaque service`
- `code/docker-compose.yml, code/scenarios/scenario_complet.py (option `--jwt-secret`)`

## 7. Tests à écrire

- Sans jeton → 401 avec le corps `{code: 401, ...}` ; jeton signé avec un autre secret, expiré ou mal formé → 401.
- **Matrice complète** : pour chaque route et chacun des quatre rôles, un rôle autorisé n'est jamais refusé (ni 401 ni 403) et un rôle non autorisé reçoit 403 ; chaque route protégée sans jeton → 401 ; chaque route publique sans jeton → jamais 401 ni 403.
- Exemples : `COMMERCIAL` ne peut pas `DELETE /clients/{id}` ; `MARKETING` peut `GET /clients/destinataires` mais pas `GET /clients` ; `RESPONSABLE` lit mais ne crée rien ; `COMMERCIAL` consulte les campagnes mais ne les crée pas.
- `auteurId` d'une interaction, `creePar` d'un devis et d'une campagne = `sub` du jeton.
- Le mode `none` reste celui des autres tests : ils passent sans jeton.

## 8. Points d'attention

- Ne pas protéger seulement par `@PreAuthorize` sur les méthodes des contrôleurs : Spring valide le corps de la requête **avant** d'appeler la méthode, donc un rôle non autorisé recevrait 422 au lieu de 403 (constaté pendant l'essai). C'est pourquoi les droits sont posés sur les URL.
- Ne jamais mettre une clé de signature réelle dans git : variable d'environnement `JWT_SECRET`. Le secret de développement par défaut ne doit servir qu'en local.
- Cet incrément dépend d'une équipe extérieure pour les vrais jetons : sans le service Authentication, le module reste complet et démontrable (mode `none`, ou jetons fabriqués avec le secret partagé).
- Les pages publiques restent publiques : leur sécurité repose sur le jeton d'URL (une amélioration possible, hors périmètre : limiter le nombre de requêtes par adresse IP et ajouter un captcha).

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` dans les trois services : tout vert (les matrices de rôles comptent plusieurs centaines de cas).
2. `SECURITY_MODE=jwt JWT_SECRET=<secret de 32 caractères au moins> docker compose --profile services up -d --build`.
3. `curl -i localhost:8081/clients` sans jeton → **401** avec `{"code":401,...}`.
4. `python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service --jwt-secret <le même secret>` → toutes les vérifications correctes : le scénario appelle chaque route avec le rôle qui convient, et le JWT est retransmis de Quotation et de Campaign vers Customer.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-E3`, push (voir `README.md`).

Message de commit proposé :

```
feat: authentification JWT et contrôle des rôles sur toutes les routes
```
