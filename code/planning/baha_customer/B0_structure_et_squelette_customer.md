# Étape B0 : Structure du code et squelette Customer

| | |
|---|---|
| **Service** | Customer (et infrastructure commune) |
| **Responsable** | Baha Farhati (le `docker-compose.yml` est partagé par tout le trinôme) |
| **Ce que couvre l'étape** | Base technique : dossier de code, bases de données, structure d'un service |
| **Dossier de code** | `code/customer-service` |
| **Code fourni** | Non : à écrire à partir de cette fiche |
| **Prérequis** | Java 21 ou plus, Maven 3, Docker |
| **État** | Fait ; en attente de validation du trinôme |

## 1. Objectif

Poser le dossier `code/`, l'infrastructure de développement (3 bases MySQL et RabbitMQ) et un premier service Customer qui démarre, avec la structure classique d'un projet Spring Boot (`controller`, `service`, `repository`, `entity`, `dto`, `exception`).

**Pas dans cette étape :**

- Aucune entité ni aucune route métier (étape B1).
- Quotation et Campaign (étape S1).
- Rôles et authentification : ils viennent à l'étape E3, avec le JWT.

## 2. Modèle de données

Aucune table. Le schéma est généré à partir des entités (`ddl-auto: update`) à partir de l'étape B1.

## 3. API à réaliser

Aucune route dans cette étape.

## 4. Règles métier à respecter

- Structure d'un service : le paquet de base `com.example.<service>` contient la classe `<Service>Application` et six dossiers : `controller` (routes HTTP), `service` (règles métier), `repository` (accès à la base), `entity` (tables), `dto` (objets envoyés et reçus par l'API), `exception` (erreurs).
- Erreurs : corps `{ code, message, timestamp }` (`ErrorResponse`) ; un service lance `ApiException(statut, message)` et `GlobalExceptionHandler` la transforme en réponse. 404 introuvable, 409 état de la ressource, 422 donnée invalide, 400 requête mal formée, 503 service dépendant indisponible.
- JSON `camelCase` sans accent ; dates ISO 8601 (pas de timestamps numériques).
- Tout paramètre qui change d'une machine à l'autre se surcharge par variable d'environnement (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBIT_HOST`).
- Pas d'authentification pour le moment : aucune vérification de rôle. Elle est ajoutée à l'étape E3.

## 5. Tâches, dans l'ordre

- [x] Créer `code/` avec `.gitignore` et `docker-compose.yml` (customer-db 3307, quotation-db 3308, campaign-db 3309, rabbitmq 5672 et console 15672).
- [x] Créer `customer-service/pom.xml` : Spring Boot 3.3.5, Java 21, web, data-jpa, validation, amqp, actuator, springdoc-openapi 2.6.0, mysql-connector-j ; H2 et spring-boot-starter-test en test.
- [x] Créer `CustomerServiceApplication` (port 8081) et les dossiers `controller`, `service`, `repository`, `entity`, `dto`, `exception`.
- [x] Créer `ErrorResponse`, `ApiException` et `GlobalExceptionHandler` (`@RestControllerAdvice`) dans `exception`.
- [x] Écrire `application.yml` (MySQL, Jackson ISO, RabbitMQ, actuator `health` et `info`).
- [x] Écrire `application-test.yml` (H2 en mode MySQL, écouteurs RabbitMQ désactivés) et `CustomerServiceApplicationTests` avec `@ActiveProfiles("test")`.
- [x] `mvn clean test` : 1 test au vert.
- [x] Écrire `code/README.md` (arborescence, conventions, plan des étapes).

## 6. Fichiers créés ou modifiés

- `code/docker-compose.yml`
- `code/.gitignore`
- `code/README.md`
- `code/customer-service/pom.xml`
- `code/customer-service/src/main/java/com/example/customer/CustomerServiceApplication.java`
- `code/customer-service/src/main/java/com/example/customer/exception/ (ApiException, ErrorResponse, GlobalExceptionHandler)`
- `code/customer-service/src/main/resources/application.yml`
- `code/customer-service/src/test/resources/application-test.yml`
- `code/customer-service/src/test/java/com/example/customer/CustomerServiceApplicationTests.java`

## 7. Tests à écrire

- Le contexte Spring démarre (H2, sans RabbitMQ).

## 8. Points d'attention

- Le JDK installé est le 23 ; la cible de compilation est Java 21 (`java.version`).
- Hibernate 6 enregistre un `UUID` en `BINARY(16)` sur MySQL : les identifiants ne sont pas lisibles à l'œil dans la base, mais l'API les affiche normalement.
- Sans `application-test.yml` séparé, un fichier `application.yml` de test remplacerait celui du service (au lieu de le compléter) : c'est pourquoi on utilise un profil.
- `ddl-auto: update` convient au développement. Pour un déploiement réel on passerait à des migrations (Flyway), hors périmètre.

## 9. Comment valider (à faire par le trinôme)

```
cd code/customer-service
mvn clean test          # attendu : Tests run: 1, Failures: 0, BUILD SUCCESS

cd ..
docker compose up -d    # attendu : 4 conteneurs ; console RabbitMQ sur http://localhost:15672 (guest / guest)
cd customer-service
mvn spring-boot:run     # attendu : « Started CustomerServiceApplication »
```

Puis dans un navigateur ou avec curl : `http://localhost:8081/actuator/health` doit répondre `{"status":"UP"}`.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-B0`, push (voir `README.md`).

Message de commit proposé :

```
chore: structure du dossier code, docker-compose et squelette customer-service
```
