# Étape E5 : Passerelle (API Gateway) : un seul point d'entrée

| | |
|---|---|
| **Service** | Gateway (port 8080) |
| **Responsable** | Tout le trinôme |
| **Ce que couvre l'étape** | Un seul point d'entrée pour tout le module : le routage vers les services, la retransmission du jeton et, en option, le refus des requêtes sans jeton valide |
| **Dossier de code** | `code/gateway-service` |
| **Prérequis** | E4 validée (login) ; E3 validée (JWT dans les services) |
| **État** | Code de la passerelle fait, testé ; bloc docker-compose et scénario complet à la fin |

## 1. Objectif

Sans passerelle, un client doit connaître quatre adresses (8081 à 8084). La passerelle donne **un seul point d'entrée** (`http://localhost:8080`) et route chaque requête vers le bon service d'après son chemin. Elle ne contient aucune règle métier.

**Pas dans cette étape :**

- Le contrôle des **rôles** : il reste dans chaque service, pour qu'un service appelé directement reste protégé.
- La répartition de charge, le service de découverte, la limitation du nombre de requêtes.
- Une interface web (aucune n'existe dans ce projet).

## 2. Modèle de données

Aucune base de données. Les routes sont déclarées dans `application.yml`.

## 3. API à réaliser

Aucune route dans cette étape.

## 4. Règles métier à respecter

- **Routage par préfixe de chemin** : `/clients`, `/categories`, `/contacts`, `/interactions` → Customer ; `/devis`, `/produits`, `/taxes`, `/public/reponse` → Quotation ; `/campagnes`, `/canaux`, `/contenus`, `/publics`, `/public/campagnes`, `/public/invitations` → Campaign ; `/login` → Auth. Les chemins sont les mêmes que ceux des contrats : la passerelle ne les change pas.
- La méthode, le corps et tous les en-têtes (dont `Authorization`) sont retransmis tels quels.
- **Deux modes**, propriété `app.security.mode` : `none` (défaut) : la passerelle route sans rien vérifier ; `jwt` : elle vérifie la **signature et l'expiration** du jeton et répond **401** sans le transmettre s'il est absent ou invalide. Dans les deux cas elle **ne contrôle pas les rôles** : un rôle non autorisé reçoit 403 **du service**, retransmis tel quel.
- `/public/**`, `/login`, la santé de la passerelle et les requêtes de pré-vérification CORS (`OPTIONS`) n'exigent jamais de jeton.
- **Erreurs au format du module** `{code, message, timestamp}` : chemin inconnu → 404 « Route inconnue » ; service arrêté (connexion refusée, nom introuvable, adresse injoignable) → **503** « Service indisponible » ; délai dépassé → 504 ; sans jeton valide (mode jwt) → 401.
- Un service arrêté ne gêne pas les autres : seules les routes de ce service répondent 503.
- **CORS** ouvert en développement (`allowedOriginPatterns: *`) pour qu'une future interface web puisse appeler la passerelle ; à restreindre en production.
- Les services restent **joignables directement** (8081 à 8084) et **restent protégés** : sans jeton ils répondent 401, avec un mauvais rôle 403. La passerelle n'est pas la seule barrière.

## 5. Tâches, dans l'ordre

- [ ] Créer `code/gateway-service/` : `pom.xml` (Spring Cloud Gateway, sécurité et serveur de ressources OAuth2 réactifs, actuator), `GatewayServiceApplication`.
- [ ] Écrire `application.yml` : port 8080, les quatre routes (adresses des services par variables `CUSTOMER_URL`, `QUOTATION_URL`, `CAMPAIGN_URL`, `AUTH_URL`), CORS, `app.security`.
- [ ] Écrire `ErreurPasserelle` : toute erreur au format du module, avec la correspondance entre erreur réseau et code (503, 504).
- [ ] Écrire `SecurityConfig` : chaîne `none` (tout passe) et chaîne `jwt` (vérification du jeton, routes publiques exceptées).
- [ ] Écrire les tests : routage (chaque chemin vers le bon service, méthodes et `Authorization` retransmis), pannes, sécurité jwt, correspondance des erreurs.
- [ ] Ajouter `Dockerfile` et le service `gateway-service` (profil `services`) dans `docker-compose.yml`.
- [ ] Ajouter l'option `--gateway` au scénario de bout en bout : toutes les requêtes passent par la passerelle.

## 6. Fichiers créés ou modifiés

- `code/gateway-service/pom.xml, Dockerfile, .dockerignore, src/main/resources/application.yml`
- `code/gateway-service/src/main/java/com/example/gateway/ : GatewayServiceApplication, ErreurPasserelle, SecurityConfig`
- `code/gateway-service/src/test/java/com/example/gateway/ : Fournisseurs (faux services), RoutageTests, PanneTests, SecuriteJwtTests, ErreurPasserelleTest`
- `code/docker-compose.yml, code/scenarios/scenario_complet.py (option `--gateway`)`

## 7. Tests à écrire

- Chacun des 20 chemins types arrive chez le bon service avec le même chemin ; les méthodes POST, PUT et DELETE sont retransmises ; l'en-tête `Authorization` arrive tel quel.
- Chemin inconnu → 404 `{code, message, timestamp}`.
- Service injoignable → 503 « Service indisponible », et les autres services répondent toujours.
- Mode jwt : sans jeton, jeton mal signé, expiré ou mal formé → 401 au format du module ; jeton valide → transmis ; **le rôle n'est pas contrôlé** (le service répondra) ; `/public/**` et `/login` sans jeton → transmis.
- Correspondance des erreurs réseau : connexion refusée, nom introuvable et « no route to host » donnent 503, un délai dépassé 504, une route inconnue 404.
- **De bout en bout, dans Docker, en mode jwt, tout à travers la passerelle (8080) avec un vrai login** : 28 vérifications correctes. En arrêtant Customer : 503 pour `/clients`, 200 pour `/devis`, et retour à 200 au redémarrage.

## 8. Points d'attention

- **Dans Docker, un service arrêté n'a plus d'adresse** : l'erreur n'est ni « connexion refusée » ni « nom introuvable » mais « no route to host ». Constaté en réel : la première version répondait 500 au lieu de 503. Il faut tester l'arrêt d'un vrai conteneur, pas seulement un port fermé.
- La passerelle ne remplace pas la sécurité des services : tout accès direct à un service doit rester refusé sans jeton valide.
- Elle vérifie seulement que le jeton est **valide** ; savoir si le rôle a le droit est le travail du service, qui connaît ses routes. Ne pas copier les droits dans la passerelle (ils divergeraient).
- Les adresses des services viennent de variables d'environnement : dans Docker ce sont les noms des conteneurs (`http://customer-service:8081`), sur le poste `localhost`.
- La passerelle est **réactive** (Spring WebFlux) alors que les services sont classiques (Spring MVC) : c'est normal, elles n'échangent que du HTTP.

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` dans `gateway-service` : 35 tests au vert.
2. `SECURITY_MODE=jwt JWT_SECRET=<32 caractères au moins> docker compose --profile services up -d --build`.
3. Un seul point d'entrée : `curl -X POST localhost:8080/login -H "Content-Type: application/json" -d '{"email":"marketing@exemple.tn","motDePasse":"marketing-demo-1"}'` → un jeton.
4. Avec ce jeton : `GET localhost:8080/campagnes` → 200 ; `GET localhost:8080/clients` → **403** (le rôle MARKETING n'a pas le droit : le refus vient du service Customer) ; sans jeton → **401** ; `GET localhost:8080/n-importe-quoi` → **404 « Route inconnue »**.
5. `docker stop customer-service`, puis `GET localhost:8080/clients` avec un jeton → **503** ; `GET localhost:8080/devis` → 200 ; `docker start customer-service`.
6. `python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service --gateway http://localhost:8080 --auth-url http://localhost:8080` → toutes les vérifications correctes.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-E5`, push (voir `README.md`).

Message de commit proposé :

```
feat(gateway): passerelle (routage, retransmission du jeton, vérification optionnelle de la signature) et scénario à travers elle
```
