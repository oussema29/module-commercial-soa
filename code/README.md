# Code du module commercial (Clients, Devis, Campagnes)

Trois services Spring Boot indépendants, chacun avec sa base MySQL, plus RabbitMQ. Les règles viennent de `contrat_api/` ; le détail de chaque étape est dans `planning/`.

## Où est quoi

```
code/
├── docker-compose.yml      bases MySQL, RabbitMQ et (profil « services ») les services Java
├── customer-service/       Baha Farhati        port 8081
├── quotation-service/      Oussema Souidi      port 8082
├── campaign-service/       Oussema Arfaoui     port 8083
├── auth-service/           provisoire          port 8084   login, émet des JWT
├── gateway-service/        tout le trinôme     port 8080   point d'entrée unique
└── contrat_api/  planning/
```

Chaque service est un projet Maven autonome : aucun code partagé.

## Structure d'un service

```
src/main/java/com/example/customer/
├── CustomerServiceApplication.java    point d'entrée
├── controller/    routes HTTP
├── service/       règles métier
├── repository/    accès à la base
├── entity/        tables
├── dto/           objets de l'API
└── exception/     erreurs (ApiException, ErrorResponse, GlobalExceptionHandler)
```

Le contrôleur appelle le service, le service appelle le repository.

## Lancer

Prérequis : Java 21, Maven 3, Docker.

```
cd code
docker compose up -d                    # bases et RabbitMQ (console : http://localhost:15672, guest / guest)
cd customer-service
mvn spring-boot:run                     # http://localhost:8081 ; Swagger : /swagger-ui.html
mvn clean test                          # tests du service
```

Les tests tournent sur une base H2 en mémoire, sans Docker.

Tout dans Docker, services compris :

```
docker compose --profile services up -d --build
```

## Authentification

Par défaut, aucune authentification. Avec `SECURITY_MODE=jwt` (et `JWT_SECRET` d'au moins 32 caractères), chaque route d'employé exige un JWT et le rôle prévu au contrat ; les routes `/public/**` restent ouvertes. Le jeton s'obtient par `POST /login` sur `auth-service` (utilisateurs fictifs, provisoire).

## Conventions (identiques dans les trois services)

- **Erreurs** : `{ code, message, timestamp }` ; 404 introuvable, 409 état incompatible, 422 donnée invalide, 503 service indisponible.
- **JSON** : `camelCase` sans accent, dates ISO 8601, montants `BigDecimal`.
- **Configuration** : surchargée par variable d'environnement (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBIT_HOST`).
- **Événements** : RabbitMQ, échange `module-commercial`, consommateurs idempotents.

## Répartition

Un membre = un service = un dossier = une base. Détail dans `planning/README.md`.

| Membre | Service | Étapes |
|---|---|---|
| Baha Farhati | Customer | B0 squelette · B1 catégories et clients · B2 contacts et interactions |
| Oussema Souidi | Quotation | S1 catalogue, taxes · S2 devis et calcul · S3 envoi, PDF, réponse publique |
| Oussema Arfaoui | Campaign | A1 squelette · A2 campagnes, publics, canaux, contenus, budget · A3 lancement, invitations, réponses · A4 performance, ROI |
| Tous | Intégration | E1 événements · E2 Docker · E3 JWT et rôles · E4 login · E5 passerelle |

Avancement sur `main` : **Campaign, le login et la passerelle sont faits** ; Customer (B1, B2), Quotation (S1 à S3) et l'assemblage final restent à faire.
