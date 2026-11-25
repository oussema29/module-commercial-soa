# Code du module commercial (Clients, Devis, Campagnes)

Trois services Spring Boot indépendants, chacun avec sa base MySQL, plus RabbitMQ. Les règles viennent de `contrat_api/` (contrats OpenAPI, `contrat_commun.md`, `evenements.md`). Le détail de chaque étape est dans `planning/`, une fiche par incrément.

## Où est quoi

```
code/
├── docker-compose.yml      3 bases MySQL + RabbitMQ (pas les services Java)
├── customer-service/       Baha Farhati        port 8081  base customer_db  (MySQL 3307)
├── quotation-service/      Oussema Souidi      port 8082  base quotation_db (MySQL 3308)
├── campaign-service/       Oussema Arfaoui     port 8083  base campaign_db  (MySQL 3309)
├── auth-service/           (provisoire)        port 8084  pas de base : utilisateurs fictifs, émet des JWT
├── gateway-service/        (tout le trinôme)   port 8080  passerelle : UN SEUL point d'entrée, route vers les quatre services
└── README.md
```

Chaque service est un projet Maven autonome : aucun code partagé, un service ne dépend jamais du code d'un autre.

## Structure d'un service

Un projet Spring Boot classique :

```
customer-service/src/main/java/com/example/customer/
├── CustomerServiceApplication.java    point d'entrée
├── controller/    les routes HTTP
├── service/       les règles métier
├── repository/    l'accès à la base
├── entity/        les tables
├── dto/           les objets envoyés et reçus par l'API
└── exception/     les erreurs (ApiException, ErrorResponse, GlobalExceptionHandler)
```

Le contrôleur appelle le service, le service appelle le repository. Un contrôleur n'appelle jamais un repository directement.

## Lancer

Tout dans Docker (bases, RabbitMQ et les cinq services : Customer, Quotation, Campaign, Auth et passerelle ; 6 minutes la première fois) :

```
cd code
docker compose --profile services up -d --build
python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service
```

Ou les services sur le poste (seulement l'infrastructure dans Docker) :

```
cd code
docker compose up -d                          # bases et RabbitMQ (console : http://localhost:15672, guest / guest)
cd customer-service
mvn spring-boot:run                           # http://localhost:8081 ; Swagger : /swagger-ui.html
mvn clean test                                # tests : base H2 en mémoire, sans Docker
```

Ports : **passerelle 8080 (point d'entrée)**, Customer 8081, Quotation 8082, Campaign 8083, Auth 8084. Les services restent joignables directement et restent protégés. Prérequis : Java 21 ou plus, Maven 3, Docker, Python 3 (scénario).

**Avec l'authentification** (JWT, un rôle par route) :

```
SECURITY_MODE=jwt JWT_SECRET=<32 caractères au moins> docker compose --profile services up -d --build     # Git Bash / Linux / macOS (JWT_SECRET est facultatif : un secret de développement existe)
# PowerShell : $env:SECURITY_MODE="jwt"; docker compose --profile services up -d --build
curl -X POST localhost:8084/login -H "Content-Type: application/json" -d '{"email":"marketing@exemple.tn","motDePasse":"marketing-demo-1"}'      # renvoie un jeton
python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service --auth-url http://localhost:8084
# ou tout à travers la passerelle, un seul point d'entrée :
python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service --gateway http://localhost:8080 --auth-url http://localhost:8080
```

Sans `SECURITY_MODE=jwt` (valeur par défaut : `none`), aucune authentification : toutes les routes sont ouvertes. Le **service de login (`auth-service`) est provisoire** : quatre utilisateurs fictifs, un par rôle (`admin@exemple.tn` / `admin-demo-1`, `commercial@exemple.tn` / `commercial-demo-1`, `marketing@exemple.tn` / `marketing-demo-1`, `responsable@exemple.tn` / `responsable-demo-1`), des mots de passe chiffrés (BCrypt), un jeton d'une heure. Il sera remplacé par le service Authentication de l'autre trinôme ; les trois autres services ne dépendent pas de lui.

Les identifiants MySQL (`root` / `soa`) et le secret JWT par défaut sont **de développement** : données fictives uniquement.

## Conventions (identiques dans les trois services)

- **Erreurs** : corps `{ code, message, timestamp }`. Un service lance `new ApiException(HttpStatus.NOT_FOUND, "Client introuvable")` : 404 introuvable, 409 état incompatible, 422 donnée invalide, 503 service dépendant indisponible. Un champ invalide dans le corps donne 422 ; un JSON illisible ou un UUID mal formé donne 400.
- **JSON** : `camelCase` sans accent ; dates ISO 8601 ; montants `BigDecimal`.
- **Configuration** : ce qui change d'une machine à l'autre (URL de base, mot de passe, hôte RabbitMQ) se surcharge par variable d'environnement (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBIT_HOST`).
- **Schéma** : `ddl-auto: update` en développement ; les tables suivent les entités.
- **Tests** : profil `test` (`application-test.yml`), base H2 en mode MySQL.
- **Sécurité** : deux modes, `app.security.mode=none` (défaut) ou `jwt`. En mode `jwt`, chaque route d'employé exige un JWT valide et le rôle du contrat (401 sans jeton, 403 sinon) ; les routes `/public/**` sont toujours ouvertes. Le rôle est lu dans le champ `role` du JWT, l'identifiant de l'utilisateur dans `sub`.
- **Événements** : RabbitMQ, échange `module-commercial` ; émetteur et destinataire déclarent la file du destinataire ; consommateurs idempotents (table `EvenementTraite`) ; accusé de réception manuel.

## Répartition et étapes

Un membre = un service = un dossier = une base. Le détail (qui fait quoi, ordre de travail, points d'intégration, une fiche par étape) est dans `planning/README.md`.

| Membre | Service | Étapes |
|---|---|---|
| Baha Farhati | Customer | B0 structure et squelette · B1 catégories et clients · B2 contacts et interactions |
| Oussema Souidi | Quotation | S1 squelette, catalogue, taxes · S2 devis et calcul · S3 envoi, PDF, réponse publique |
| Oussema Arfaoui | Campaign | A1 squelette · A2 campagnes, publics, canaux, contenus, budget · A3 lancement, invitations, réponses · A4 performance, ROI |
| Tous | Intégration | E1 événements RabbitMQ · E2 bout en bout et Docker · E3 JWT et rôles · E4 service de login provisoire · E5 passerelle |

Chaque étape se termine par `mvn clean test` au vert et un essai à la main avec Swagger (`http://localhost:808x/swagger-ui.html`), puis par la validation du trinôme.

Avancement : **toutes les étapes (B0 à E5) ont été réalisées lors de l'essai complet** (branche `essai-complet`) et sont à valider ; les erreurs trouvées sont dans `planning/problems.md`. Les scénarios joués sont dans `planning/commun_integration/scenarios.md`.
