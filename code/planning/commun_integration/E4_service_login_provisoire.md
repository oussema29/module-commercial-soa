# Étape E4 : Service de login provisoire (utilisateurs fictifs et émission de JWT)

| | |
|---|---|
| **Service** | Auth (service provisoire, port 8084) |
| **Responsable** | Tout le trinôme |
| **Ce que couvre l'étape** | Obtenir un jeton par un vrai login, pour démontrer la sécurité de bout en bout |
| **Dossier de code** | `code/auth-service` |
| **Prérequis** | E3 validée (les trois services vérifient déjà le JWT) |
| **État** | Fait : service de login provisoire, testé |

## 1. Objectif

Sans service qui émet des jetons, la sécurité de l'étape E3 ne peut pas être démontrée : personne ne peut se connecter. Ce petit service vérifie un email et un mot de passe et renvoie un JWT. Il est **provisoire** : il sera remplacé par le service Authentication de l'autre trinôme sans toucher aux trois services du module.

**Pas dans cette étape :**

- Une vraie base d'utilisateurs, l'inscription, la réinitialisation du mot de passe.
- Le renouvellement du jeton, la déconnexion, la révocation.
- Le verrouillage après des essais répétés, la double authentification.
- Ces fonctions relèvent du service Authentication (ou d'un serveur d'identité comme Keycloak).

## 2. Modèle de données

Aucune base de données. Quatre utilisateurs **fictifs** (un par rôle) sont déclarés dans `application.yml` : `id` (UUID), `email`, `mot-de-passe` (de démonstration), `role`. Au démarrage, chaque mot de passe est chiffré (BCrypt) ; seule l'empreinte est gardée en mémoire.

## 3. API à réaliser

Aucune route dans cette étape.

## 4. Règles métier à respecter

- **Une seule route** : `POST /login` (sans identité). Corps : `{ "email": "...", "motDePasse": "..." }`. Réponse 200 : `{ "jeton": "...", "type": "Bearer", "expireDans": 3600, "role": "MARKETING", "utilisateurId": "<uuid>" }`. Contrat : `contrat_api/auth.yaml`.
- Le jeton est un JWT signé **HS256** avec le même secret que les trois services (`JWT_SECRET`, 32 caractères au moins). Il contient `sub` (identifiant de l'utilisateur), `role` et `exp` (1 heure par défaut).
- Mauvais mot de passe **ou** email inconnu : le même refus **401 « Identifiants invalides »** (on ne révèle pas si l'email existe). Le temps de réponse est le même dans les deux cas.
- L'email se compare sans tenir compte de la casse ni des espaces autour.
- Email ou mot de passe absent : 422 ; JSON illisible : 400. Corps d'erreur : `{code, message, timestamp}`.
- Les mots de passe ne sont **jamais** renvoyés, ni en clair ni sous forme d'empreinte, et jamais gardés en clair.
- Les trois autres services **ne dépendent pas** de ce service : ils vérifient seulement la signature et le rôle du jeton. Le service peut être arrêté sans effet sur eux, tant que les jetons déjà émis restent valides.
- Utilisateurs de démonstration : `admin@exemple.tn` / `admin-demo-1` (ADMIN) ; `commercial@exemple.tn` / `commercial-demo-1` (COMMERCIAL) ; `marketing@exemple.tn` / `marketing-demo-1` (MARKETING) ; `responsable@exemple.tn` / `responsable-demo-1` (RESPONSABLE).

## 5. Tâches, dans l'ordre

- [ ] Créer `code/auth-service/` : `pom.xml` (web, validation, actuator, springdoc, `spring-security-crypto` pour BCrypt, `nimbus-jose-jwt` pour la signature), `AuthServiceApplication`, erreurs au format du contrat.
- [ ] Écrire `config/AuthProperties` (lit `app.auth` : durée du jeton et liste des utilisateurs) et `config/CryptageConfig` (BCrypt, horloge).
- [ ] Écrire `service/UtilisateurService` (chiffre les mots de passe au démarrage ; `authentifier` ; même refus pour email inconnu et mauvais mot de passe) et `service/JetonService` (fabrique le JWT).
- [ ] Écrire `dto/LoginRequest`, `dto/LoginReponse` et `controller/LoginController` (`POST /login`).
- [ ] Écrire `application.yml` (port 8084, secret JWT, les quatre utilisateurs fictifs) et `AuthApiTests`.
- [ ] Ajouter `Dockerfile` et le service `auth-service` (profil `services`) dans `docker-compose.yml`, avec le même `JWT_SECRET` que les trois autres.
- [ ] Écrire `contrat_api/auth.yaml`.
- [ ] Ajouter l'option `--auth-url` au scénario de bout en bout : les jetons sont obtenus par un vrai login, un par rôle.

## 6. Fichiers créés ou modifiés

- `code/auth-service/pom.xml, Dockerfile, .dockerignore, src/main/resources/application.yml`
- `code/auth-service/src/main/java/com/example/auth/ : AuthServiceApplication ; config/ (AuthProperties, CryptageConfig) ; service/ (Utilisateur, UtilisateurService, JetonService) ; dto/ (LoginRequest, LoginReponse) ; controller/LoginController ; exception/`
- `code/auth-service/src/test/java/com/example/auth/AuthApiTests.java`
- `code/contrat_api/auth.yaml`
- `code/docker-compose.yml, code/scenarios/scenario_complet.py`

## 7. Tests à écrire

- Chacun des quatre utilisateurs obtient un jeton ; sa signature est valide avec le secret partagé ; `sub` est son identifiant, `role` son rôle, l'expiration est dans environ une heure.
- Un jeton vérifié avec un **autre** secret est invalide.
- Mauvais mot de passe et email inconnu : même 401 « Identifiants invalides ».
- L'email avec majuscules et espaces est accepté.
- Champ absent ou vide : 422 ; JSON illisible : 400.
- La réponse ne contient ni mot de passe ni empreinte ; l'empreinte BCrypt commence par `$2`, ne contient pas le mot de passe, et deux empreintes du même mot de passe sont différentes (sel).
- **De bout en bout, dans Docker, services en mode jwt** : le scénario complet avec des jetons obtenus par login → 28 vérifications correctes ; le jeton du MARKETING est accepté par Campaign (200) et refusé par Customer et Quotation (403) ; sans jeton ou avec un jeton falsifié → 401.

## 8. Points d'attention

- **Ce service est un bouchon.** Les mots de passe de démonstration sont dans `application.yml` parce qu'ils sont fictifs ; aucun vrai mot de passe ni vrai secret ne doit y figurer. Le secret se règle par la variable `JWT_SECRET`.
- Quand le service Authentication de l'autre trinôme existera, on l'utilise à la place : les trois services du module ne changent pas, à condition que ses jetons aient `role` et `sub` (le nom du champ du rôle se règle par `app.security.role-claim`) et soient signés avec le même secret.
- Pour un usage réel, un serveur d'identité comme Keycloak apporterait : une vraie base d'utilisateurs, la double authentification, le renouvellement et la révocation des jetons, et une signature à clé publique (les services n'auraient plus de secret à partager). Il alourdit l'installation : choix du groupe.
- Aucune protection contre les essais répétés de mot de passe : à ajouter dans un vrai service d'authentification.

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` dans `auth-service` : 7 tests au vert.
2. `SECURITY_MODE=jwt JWT_SECRET=<32 caractères au moins> docker compose --profile services up -d --build`.
3. `curl -X POST localhost:8084/login -H "Content-Type: application/json" -d '{"email":"marketing@exemple.tn","motDePasse":"marketing-demo-1"}'` → un jeton, rôle MARKETING. Avec un mauvais mot de passe → 401 « Identifiants invalides ».
4. Avec ce jeton : `GET localhost:8083/campagnes` → 200 ; `GET localhost:8081/clients` → 403 (le marketing ne lit pas les fiches clients) ; sans jeton → 401.
5. `python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service --auth-url http://localhost:8084` → toutes les vérifications correctes.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-E4`, push (voir `README.md`).

Message de commit proposé :

```
feat(auth): service de login provisoire (utilisateurs fictifs, BCrypt, émission de JWT) et scénario avec vrai login
```
