# Code du module commercial (Clients, Devis, Campagnes)

Trois services Spring Boot indépendants, chacun avec sa base MySQL, plus RabbitMQ. Les règles viennent de `contrat_api/` (contrats OpenAPI, `contrat_commun.md`, `evenements.md`). Le détail de chaque étape est dans `planning/`, une fiche par incrément.

## Où est quoi

```
code/
├── docker-compose.yml      3 bases MySQL + RabbitMQ (pas les services Java)
├── customer-service/       Baha Farhati        port 8081  base customer_db  (MySQL 3307)
├── quotation-service/      Oussema Souidi      port 8082  base quotation_db (MySQL 3308)
├── campaign-service/       Oussema Arfaoui     port 8083  base campaign_db  (MySQL 3309)
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

```
cd code
docker compose up -d                          # bases et RabbitMQ (console : http://localhost:15672, guest / guest)
cd customer-service
mvn spring-boot:run                           # http://localhost:8081
mvn test                                      # tests : base H2 en mémoire, sans Docker
```

Prérequis : Java 21 ou plus, Maven 3, Docker. Les identifiants MySQL (`root` / `soa`) sont **de développement** : données fictives uniquement.

## Conventions (identiques dans les trois services)

- **Erreurs** : corps `{ code, message, timestamp }`. Un service lance `new ApiException(HttpStatus.NOT_FOUND, "Client introuvable")` : 404 introuvable, 409 état incompatible, 422 donnée invalide, 503 service dépendant indisponible. Un champ invalide dans le corps donne 422 ; un JSON illisible ou un UUID mal formé donne 400.
- **JSON** : `camelCase` sans accent ; dates ISO 8601 ; montants `BigDecimal`.
- **Configuration** : ce qui change d'une machine à l'autre (URL de base, mot de passe, hôte RabbitMQ) se surcharge par variable d'environnement (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBIT_HOST`).
- **Schéma** : `ddl-auto: update` en développement ; les tables suivent les entités.
- **Tests** : profil `test` (`application-test.yml`), base H2 en mode MySQL.
- **Sécurité** : aucune pour le moment. Les rôles et le JWT sont ajoutés en dernier (incrément 11).

## Répartition et étapes

Un membre = un service = un dossier = une base. Le détail (qui fait quoi, ordre de travail, points d'intégration, une fiche par étape) est dans `planning/README.md`.

| Membre | Service | Étapes |
|---|---|---|
| Baha Farhati | Customer | B0 structure et squelette · B1 catégories et clients · B2 contacts et interactions |
| Oussema Souidi | Quotation | S1 squelette, catalogue, taxes · S2 devis et calcul · S3 envoi, PDF, réponse publique |
| Oussema Arfaoui | Campaign | A1 squelette · A2 campagnes, publics, canaux, contenus, budget · A3 lancement, invitations, réponses · A4 performance, ROI |
| Tous | Intégration | E1 événements RabbitMQ · E2 bout en bout et Docker · E3 JWT et rôles |

Chaque étape se termine par `mvn clean test` au vert et un essai à la main avec Swagger (`http://localhost:808x/swagger-ui.html`), puis par la validation du trinôme.

Avancement : **B0 fait** (en attente de validation) ; les autres étapes sont à faire.
