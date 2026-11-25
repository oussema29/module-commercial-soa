# Étape A1 : Campaign : squelette du service

| | |
|---|---|
| **Service** | Campaign |
| **Responsable** | Oussema Arfaoui |
| **Ce que couvre l'étape** | Base technique de Campaign |
| **Dossier de code** | `code/campaign-service` |
| **Prérequis** | Aucun autre service : Campaign démarre seul (la structure de dossiers est décrite en B0) |
| **État** | Réalisé lors de l'essai complet (branche `essai-complet`) ; à valider par le trinôme |

## 1. Objectif

Créer le service Campaign avec la même structure que Customer, qui démarre seul, avec ses erreurs au format du contrat et sa propre base.

**Pas dans cette étape :**

- Entités et routes (étape A2).
- Les services Customer et Quotation : d'autres membres les réalisent.

## 2. Modèle de données

Aucune table.

## 3. API à réaliser

Aucune route dans cette étape.

## 4. Règles métier à respecter

- Structure : paquet `com.example.campaign` avec `CampaignServiceApplication` et les dossiers `controller`, `service`, `repository`, `entity`, `dto`, `exception` (dont `ApiException`, `ErrorResponse`, `GlobalExceptionHandler` et `PageResponse`, recopiés de Customer : **aucun code partagé entre services**).
- Port 8083 ; base `campaign_db` (MySQL 3309, déjà dans `docker-compose.yml`).
- Pas d'authentification pour le moment (étape E3).

## 5. Tâches, dans l'ordre

- [ ] Créer `campaign-service/pom.xml` (mêmes dépendances que Customer, plus la génération de PDF à l'étape A4).
- [ ] Créer `CampaignServiceApplication` et les six dossiers.
- [ ] Recopier `ApiException`, `ErrorResponse`, `GlobalExceptionHandler` et `PageResponse` (paquet `com.example.campaign`).
- [ ] Écrire `application.yml` (port 8083, base 3309, RabbitMQ, Jackson ISO, actuator) et `application-test.yml` (H2).
- [ ] Écrire `CampaignServiceApplicationTests` (le contexte démarre).
- [ ] Mettre à jour `code/README.md` si besoin.

## 6. Fichiers créés ou modifiés

- `code/campaign-service/pom.xml`
- `code/campaign-service/src/main/java/com/example/campaign/CampaignServiceApplication.java`
- `code/campaign-service/src/main/java/com/example/campaign/exception/ (ApiException, ErrorResponse, GlobalExceptionHandler)`
- `code/campaign-service/src/main/java/com/example/campaign/dto/PageResponse.java`
- `code/campaign-service/src/main/resources/application.yml`
- `code/campaign-service/src/test/resources/application-test.yml`
- `code/campaign-service/src/test/java/com/example/campaign/CampaignServiceApplicationTests.java`

## 7. Tests à écrire

- Le contexte Spring démarre (H2, sans RabbitMQ).

## 8. Points d'attention

- La duplication des classes d'erreur entre services est volontaire : chaque service doit pouvoir être construit et déployé seul.
- Ne pas toucher aux dossiers `customer-service/` et `quotation-service/` : ils appartiennent aux autres membres.

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` dans `campaign-service` : tout vert.
2. `docker compose up -d`, puis `mvn spring-boot:run` : `http://localhost:8083/actuator/health` répond `UP`.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-A1`, push (voir `README.md`).

Message de commit proposé :

```
chore(campaign): squelette du service campaign
```
