# Guide de code : Quotation (Oussema Souidi)

Ce guide permet de réaliser les étapes **S1**, **S2** et **S3** sans erreur et sans avoir à réfléchir : il donne l'arborescence, le **code complet de chaque fichier**, les routes avec leurs corps de requête et de réponse, le flux, les tests et les commandes de vérification.

> **Le code ci-dessous a été compilé et testé** (Java 21, Spring Boot 3.3.5) : `mvn clean test` donne **8 tests au vert** après S1, **24** après S2 et **35** après S3. Les tests tournent sur une base H2 en mémoire (mode MySQL), et Customer est **simulé** dans les tests : ils ne dépendent d'aucun autre service. L'essai avec la vraie base MySQL et le vrai Customer se fait avec `docker compose up -d` (voir « Comment valider » de chaque fiche).

## Objectif, en bref

**À la fin de S1, S2 et S3, le service Quotation fonctionne** : on gère un catalogue de produits et les taux de TVA ; on crée un devis pour un client (Quotation va lire ce client chez Customer), on y ajoute des lignes et les montants HT, TVA et TTC sont calculés automatiquement ; on envoie le devis en PDF par email (simulé) avec un lien ; le client, sans compte, accepte ou refuse en cliquant ; les devis périmés expirent chaque nuit. Quand un devis lié à une campagne est accepté, un événement est préparé pour Campaign.

**Comment lire ce guide sans se perdre**
- Lire **les sections 2, 3 et 4** (arborescence, flux du devis, erreurs) : c'est la compréhension, 10 minutes.
- Pour chaque étape : lire le tableau « Fichiers à créer » et les **routes** (section « Les routes de l'étape »).
- Les **blocs de code** sont à **copier tels quels** dans les fichiers : inutile de les lire ligne par ligne. Les lire seulement pour comprendre comment une règle est codée.
- Finir par la section « Vérification » : `mvn clean test`, puis l'essai dans Swagger.

## 1. Comment utiliser ce guide

1. L'étape **S1** crée le service : créer le dossier `code/quotation-service/` puis, pour chaque fichier, le créer **au chemin indiqué** (en partant de la racine du dépôt) et y coller le code **tel quel**.
2. Quand un fichier est marqué **REMPLACE le précédent**, il remplace entièrement la version créée à une étape précédente.
3. Après chaque étape : `cd code/quotation-service` puis `mvn clean test`. Résultat attendu : `BUILD SUCCESS`.
4. Essayer les routes avec Swagger : `mvn spring-boot:run` puis ouvrir <http://localhost:8082/swagger-ui.html>.
5. Cocher les tâches de la fiche (`S1_...md`, `S2_...md`, `S3_...md`), faire valider par le trinôme, puis commit et push (voir `../README.md`).

## 2. Arborescence finale du service

```
code/quotation-service/
├── pom.xml                                  S1 (remplacé en S3 : bibliothèque PDF)
└── src/
    ├── main/
    │   ├── java/com/example/quotation/
    │   │   ├── QuotationServiceApplication.java        S1
    │   │   ├── exception/   ApiException, ErrorResponse, GlobalExceptionHandler    S1
    │   │   ├── config/      ClockConfig (S2), SchedulingConfig (S3)
    │   │   ├── entity/      S1 : Taxe, Produit
    │   │   │                S2 : StatutDevis, LigneDevis, Devis
    │   │   │                S3 : Document
    │   │   ├── repository/  S1 : TaxeRepository, ProduitRepository
    │   │   │                S2 : DevisRepository   (remplacé en S3)
    │   │   │                S3 : DocumentRepository
    │   │   ├── dto/         S1 : PageResponse, TaxeSaisie, TaxeReponse, ProduitSaisie, ProduitReponse
    │   │   │                S2 : DevisCreation, DevisModification, LigneSaisie, LigneModification, LigneReponse, DevisReponse
    │   │   │                S3 : DevisPublic
    │   │   ├── client/      S2 : ClientCustomer, CustomerClient
    │   │   ├── messaging/   S3 : DevisAccepteEvent, EditeurEvenements, EditeurEvenementsLog
    │   │   ├── service/     S1 : TaxeService, ProduitService
    │   │   │                S2 : CalculateurDevis, ReferenceDevis, DevisService
    │   │   │                S3 : GenerateurPdf, EmailException, EnvoiEmail, JetonReponse, FichierPdf,
    │   │   │                     EnvoiDevisService, ReponsePubliqueService, ExpirationService, ExpirationDevisTache
    │   │   └── controller/  S1 : TaxeController, ProduitController
    │   │                    S2 : DevisController
    │   │                    S3 : EnvoiController, PublicController
    │   └── resources/application.yml           S1 (remplacé en S2, puis en S3)
    └── test/
        ├── java/com/example/quotation/         S1 : QuotationServiceApplicationTests, CatalogueApiTests
        │                                       S2 : CalculateurDevisTest, DevisApiTests
        │                                       S3 : EnvoiEtReponseApiTests
        └── resources/application-test.yml      S1
```


## 3. Le flux : comment une requête traverse le service

Toute route suit le même chemin :

```
Requête HTTP --> Controller --> Service --> Repository --> Base MySQL (quotation_db)
Réponse HTTP <-- Controller <-- Service <-- Repository
```

### 3.1 Cycle de vie d'un devis

```
            envoyer                accepter
BROUILLON ----------> ENVOYE ----------------> ACCEPTE
    |                   |  \---- refuser -----> REFUSE
    |                   |  \---- (date passée, tâche du soir) --> EXPIRE
    \---- annuler -----> +----- annuler --------> ANNULE
```

Un devis n'est modifiable (champs et lignes) qu'en **BROUILLON** ; sinon **409**.

### 3.2 De la création à l'acceptation (flux complet)

| # | Qui | Quoi |
|---|---|---|
| 1 | Commercial | `POST /devis` avec un `clientId` |
| 2 | Quotation → Customer | `GET /clients/{id}` : Quotation vérifie que le client existe et n'est pas `INACTIF`, et copie son nom et son email. **Si Customer ne répond pas : 503, rien n'est créé** |
| 3 | Commercial | `POST /devis/{id}/lignes` (une ou plusieurs fois) : le service copie le prix du catalogue et recalcule HT, TVA, TTC |
| 4 | Commercial | `POST /devis/{id}/envoyer` : le service crée le jeton, génère le PDF, envoie l'email (simulé : écrit dans les logs) avec les liens, passe à `ENVOYE` |
| 5 | Client (sans compte) | Ouvre son lien : `GET /public/reponse/{token}` |
| 6 | Client | `POST /public/reponse/{token}/accepter` : `ACCEPTE` ; si le devis a un `campagneId`, l'événement `DevisAccepte` est publié |
| 7 | Campaign (plus tard, étape E1) | Reçoit l'événement et ajoute le montant au chiffre d'affaires de la campagne |

### 3.3 Règles à ne pas oublier

- **Calcul automatique** : à chaque ajout, modification ou suppression de ligne, `CalculateurDevis` recalcule tout. Les montants ne sont **jamais** saisis.
- **Copie** : le devis garde une copie du nom et de l'email du client, et la ligne une copie de la désignation, du prix et de la TVA. Si le client ou le produit change ensuite, le devis ne change pas.
- **Le jeton** n'existe que dans l'email du client : il n'est dans aucune réponse destinée aux employés.
- **Le lien du client** est valable jusqu'à la **fin du jour** de `dateValidite` (le jour même, il fonctionne encore).
- **Montants** : `BigDecimal` partout, jamais `double`. En JSON ce sont des nombres décimaux (`1250.500`).
- `campagneId` est facultatif et **n'est pas vérifié** par Quotation (elle n'appelle pas Campaign).


## 4. Format commun des erreurs

Toute erreur a ce corps (c'est `GlobalExceptionHandler` qui le produit) :

```json
{ "code": 409, "message": "Ce devis ne peut plus être accepté", "timestamp": "2026-11-18T14:30:00Z" }
```

| Code | Quand |
|---|---|
| 400 | JSON illisible, identifiant mal formé dans l'URL, valeur d'énumération inconnue |
| 404 | Devis, ligne, produit, taxe ou client introuvable ; jeton inconnu |
| 409 | Devis dans un état qui ne permet pas l'action ; réponse refusée (« Ce devis ne peut plus être accepté ») |
| 422 | Champ invalide, client inactif, date de validité incorrecte, email invalide |
| 503 | Customer indisponible, ou envoi d'email impossible |

## 5. Étape S1 : squelette, catalogue et taxes

### 5.1 Fichiers à créer (22)

| # | Fichier | Rôle |
|---|---|---|
| 1 | `code/quotation-service/pom.xml` | Dépendances Maven du service. |
| 2 | `code/quotation-service/src/main/resources/application.yml` | Configuration du service. |
| 3 | `code/quotation-service/src/main/java/com/example/quotation/exception/ApiException.java` | L'erreur que les services lancent : un code HTTP et un message. |
| 4 | `code/quotation-service/src/main/java/com/example/quotation/exception/ErrorResponse.java` | Corps JSON de toute erreur : `{code, message, timestamp}`. |
| 5 | `code/quotation-service/src/main/java/com/example/quotation/exception/GlobalExceptionHandler.java` | Transforme chaque exception en réponse d'erreur au format du contrat. |
| 6 | `code/quotation-service/src/main/java/com/example/quotation/entity/Produit.java` | La table du catalogue. |
| 7 | `code/quotation-service/src/main/java/com/example/quotation/entity/Taxe.java` | La table des taux de TVA. |
| 8 | `code/quotation-service/src/main/java/com/example/quotation/repository/ProduitRepository.java` | Accès aux produits. |
| 9 | `code/quotation-service/src/main/java/com/example/quotation/repository/TaxeRepository.java` | Accès aux taxes. |
| 10 | `code/quotation-service/src/main/java/com/example/quotation/dto/PageResponse.java` | Format commun des listes paginées `{contenu, page, taille, total}`. |
| 11 | `code/quotation-service/src/main/java/com/example/quotation/dto/ProduitReponse.java` | Produit renvoyé par l'API. |
| 12 | `code/quotation-service/src/main/java/com/example/quotation/dto/ProduitSaisie.java` | Corps reçu pour un produit (prix décimal, trois décimales au plus). |
| 13 | `code/quotation-service/src/main/java/com/example/quotation/dto/TaxeReponse.java` | Taxe renvoyée par l'API. |
| 14 | `code/quotation-service/src/main/java/com/example/quotation/dto/TaxeSaisie.java` | Corps reçu pour une taxe (taux de 0 à 100). |
| 15 | `code/quotation-service/src/main/java/com/example/quotation/service/ProduitService.java` | Règles du catalogue (taxe obligatoire et existante). |
| 16 | `code/quotation-service/src/main/java/com/example/quotation/service/TaxeService.java` | Règles des taxes. |
| 17 | `code/quotation-service/src/main/java/com/example/quotation/controller/ProduitController.java` | Routes `/produits`. |
| 18 | `code/quotation-service/src/main/java/com/example/quotation/controller/TaxeController.java` | Routes `/taxes`. |
| 19 | `code/quotation-service/src/main/java/com/example/quotation/QuotationServiceApplication.java` | Point d'entrée du service (port 8082). |
| 20 | `code/quotation-service/src/test/java/com/example/quotation/CatalogueApiTests.java` | Tests des routes `/taxes` et `/produits`. |
| 21 | `code/quotation-service/src/test/java/com/example/quotation/QuotationServiceApplicationTests.java` | Le contexte Spring démarre. |
| 22 | `code/quotation-service/src/test/resources/application-test.yml` | Configuration des tests : base H2 en mémoire, sans RabbitMQ. |

### 5.2 Code


#### `code/quotation-service/pom.xml`

*Dépendances Maven du service.*

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>

  <groupId>com.example</groupId>
  <artifactId>quotation-service</artifactId>
  <version>0.1.0</version>
  <name>quotation-service</name>
  <description>Module commercial : devis, catalogue, taxes (port 8082)</description>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-amqp</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.6.0</version></dependency>
    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>

    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
    </plugins>
  </build>
</project>
```

#### `code/quotation-service/src/main/resources/application.yml`

*Configuration du service.*

```yaml
server:
  port: 8082

spring:
  application:
    name: quotation-service
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3308/quotation_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:soa}
  jpa:
    hibernate:
      ddl-auto: update        # développement : le schéma suit les entités
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  jackson:
    serialization:
      write-dates-as-timestamps: false   # dates ISO 8601
    time-zone: UTC
  rabbitmq:
    host: ${RABBIT_HOST:localhost}
    port: 5672
    username: guest
    password: guest

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

#### `code/quotation-service/src/main/java/com/example/quotation/exception/ApiException.java`

*L'erreur que les services lancent : un code HTTP et un message.*

```java
package com.example.quotation.exception;

import org.springframework.http.HttpStatus;

/**
 * Erreur que le service lance quand une règle n'est pas respectée.
 * Exemple : throw new ApiException(HttpStatus.NOT_FOUND, "Devis introuvable");
 * Codes utilisés : 404 introuvable, 409 état incompatible, 422 donnée invalide, 503 service indisponible.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/exception/ErrorResponse.java`

*Corps JSON de toute erreur : `{code, message, timestamp}`.*

```java
package com.example.quotation.exception;

import java.time.Instant;

/** Le corps JSON renvoyé en cas d'erreur (le même dans les trois services) : { code, message, timestamp }. */
public record ErrorResponse(int code, String message, Instant timestamp) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/exception/GlobalExceptionHandler.java`

*Transforme chaque exception en réponse d'erreur au format du contrat.*

```java
package com.example.quotation.exception;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Transforme chaque exception en réponse { code, message, timestamp }. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return build(e.getStatus(), e.getMessage());
    }

    // Un champ manquant ou invalide dans le corps de la requête : 422
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + " ; " + b)
                .orElse("Donnée invalide");
        return build(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    // JSON illisible, identifiant mal formé dans l'URL ou paramètre manquant : 400
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return build(HttpStatus.BAD_REQUEST, "Requête mal formée");
    }

    // Deux personnes modifient le même devis en même temps : la seconde reçoit 409
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponse> handleConflict(Exception e) {
        LOG.warn("Conflit de données", e);
        return build(HttpStatus.CONFLICT, "Conflit de mise à jour, veuillez réessayer");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message, Instant.now()));
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/entity/Produit.java`

*La table du catalogue.*

```java
package com.example.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
public class Produit {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String nom;

    private String description;

    // Montant en dinars, trois décimales au plus
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal prix;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "taxe_id")
    private Taxe taxe;

    public UUID getId() { return id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrix() { return prix; }
    public void setPrix(BigDecimal prix) { this.prix = prix; }

    public Taxe getTaxe() { return taxe; }
    public void setTaxe(Taxe taxe) { this.taxe = taxe; }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/entity/Taxe.java`

*La table des taux de TVA.*

```java
package com.example.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
public class Taxe {

    @Id
    private UUID id = UUID.randomUUID();

    // Pourcentage de 0 à 100 (19 pour 19 %)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTVA;

    private String description;

    public UUID getId() { return id; }

    public BigDecimal getTauxTVA() { return tauxTVA; }
    public void setTauxTVA(BigDecimal tauxTVA) { this.tauxTVA = tauxTVA; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/repository/ProduitRepository.java`

*Accès aux produits.*

```java
package com.example.quotation.repository;

import com.example.quotation.entity.Produit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProduitRepository extends JpaRepository<Produit, UUID> {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/repository/TaxeRepository.java`

*Accès aux taxes.*

```java
package com.example.quotation.repository;

import com.example.quotation.entity.Taxe;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxeRepository extends JpaRepository<Taxe, UUID> {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/PageResponse.java`

*Format commun des listes paginées `{contenu, page, taille, total}`.*

```java
package com.example.quotation.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Réponse paginée du contrat : { contenu, page, taille, total }. */
public record PageResponse<T>(List<T> contenu, int page, int taille, long total) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Page demandée, avec une taille entre 1 et 100. */
    public static Pageable pageable(int page, int taille, Sort tri) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), 100), tri);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/ProduitReponse.java`

*Produit renvoyé par l'API.*

```java
package com.example.quotation.dto;

import com.example.quotation.entity.Produit;
import java.math.BigDecimal;
import java.util.UUID;

public record ProduitReponse(UUID id, String nom, String description, BigDecimal prix, UUID taxeId) {

    public static ProduitReponse de(Produit p) {
        return new ProduitReponse(p.getId(), p.getNom(), p.getDescription(), p.getPrix(), p.getTaxe().getId());
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/ProduitSaisie.java`

*Corps reçu pour un produit (prix décimal, trois décimales au plus).*

```java
package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ProduitSaisie(
        @NotBlank String nom,
        String description,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 3) BigDecimal prix,
        @NotNull UUID taxeId) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/TaxeReponse.java`

*Taxe renvoyée par l'API.*

```java
package com.example.quotation.dto;

import com.example.quotation.entity.Taxe;
import java.math.BigDecimal;
import java.util.UUID;

public record TaxeReponse(UUID id, BigDecimal tauxTVA, String description) {

    public static TaxeReponse de(Taxe t) {
        return new TaxeReponse(t.getId(), t.getTauxTVA(), t.getDescription());
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/TaxeSaisie.java`

*Corps reçu pour une taxe (taux de 0 à 100).*

```java
package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TaxeSaisie(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal tauxTVA,
        String description) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/ProduitService.java`

*Règles du catalogue (taxe obligatoire et existante).*

```java
package com.example.quotation.service;

import com.example.quotation.dto.PageResponse;
import com.example.quotation.dto.ProduitReponse;
import com.example.quotation.dto.ProduitSaisie;
import com.example.quotation.entity.Produit;
import com.example.quotation.entity.Taxe;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.ProduitRepository;
import com.example.quotation.repository.TaxeRepository;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProduitService {

    private final ProduitRepository produits;
    private final TaxeRepository taxes;

    public ProduitService(ProduitRepository produits, TaxeRepository taxes) {
        this.produits = produits;
        this.taxes = taxes;
    }

    public ProduitReponse creer(ProduitSaisie saisie) {
        Produit p = new Produit();
        remplir(p, saisie);
        return ProduitReponse.de(produits.save(p));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProduitReponse> lister(int page, int taille) {
        return PageResponse.of(produits.findAll(PageResponse.pageable(page, taille, Sort.by("nom")))
                .map(ProduitReponse::de));
    }

    @Transactional(readOnly = true)
    public ProduitReponse consulter(UUID id) {
        return ProduitReponse.de(trouver(id));
    }

    public ProduitReponse modifier(UUID id, ProduitSaisie saisie) {
        Produit p = trouver(id);
        remplir(p, saisie);
        return ProduitReponse.de(p);
    }

    /** Sans effet sur les devis existants : ils gardent leur copie du produit. */
    public void supprimer(UUID id) {
        produits.delete(trouver(id));
    }

    private Produit trouver(UUID id) {
        return produits.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Produit introuvable"));
    }

    private void remplir(Produit p, ProduitSaisie s) {
        Taxe taxe = taxes.findById(s.taxeId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Taxe introuvable"));
        p.setNom(s.nom());
        p.setDescription(s.description());
        p.setPrix(s.prix());
        p.setTaxe(taxe);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/TaxeService.java`

*Règles des taxes.*

```java
package com.example.quotation.service;

import com.example.quotation.dto.TaxeReponse;
import com.example.quotation.dto.TaxeSaisie;
import com.example.quotation.entity.Taxe;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.TaxeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaxeService {

    private final TaxeRepository taxes;

    public TaxeService(TaxeRepository taxes) {
        this.taxes = taxes;
    }

    public TaxeReponse creer(TaxeSaisie saisie) {
        Taxe t = new Taxe();
        t.setTauxTVA(saisie.tauxTVA());
        t.setDescription(saisie.description());
        return TaxeReponse.de(taxes.save(t));
    }

    @Transactional(readOnly = true)
    public List<TaxeReponse> lister() {
        return taxes.findAll().stream().map(TaxeReponse::de).toList();
    }

    public TaxeReponse modifier(UUID id, TaxeSaisie saisie) {
        Taxe t = taxes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Taxe introuvable"));
        t.setTauxTVA(saisie.tauxTVA());
        t.setDescription(saisie.description());
        return TaxeReponse.de(t);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/controller/ProduitController.java`

*Routes `/produits`.*

```java
package com.example.quotation.controller;

import com.example.quotation.dto.PageResponse;
import com.example.quotation.dto.ProduitReponse;
import com.example.quotation.dto.ProduitSaisie;
import com.example.quotation.service.ProduitService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/produits")
public class ProduitController {

    private final ProduitService service;

    public ProduitController(ProduitService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProduitReponse> lister(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int taille) {
        return service.lister(page, taille);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProduitReponse creer(@Valid @RequestBody ProduitSaisie saisie) {
        return service.creer(saisie);
    }

    @GetMapping("/{id}")
    public ProduitReponse consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    @PutMapping("/{id}")
    public ProduitReponse modifier(@PathVariable UUID id, @Valid @RequestBody ProduitSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/controller/TaxeController.java`

*Routes `/taxes`.*

```java
package com.example.quotation.controller;

import com.example.quotation.dto.TaxeReponse;
import com.example.quotation.dto.TaxeSaisie;
import com.example.quotation.service.TaxeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/taxes")
public class TaxeController {

    private final TaxeService service;

    public TaxeController(TaxeService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaxeReponse> lister() {
        return service.lister();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaxeReponse creer(@Valid @RequestBody TaxeSaisie saisie) {
        return service.creer(saisie);
    }

    @PutMapping("/{id}")
    public TaxeReponse modifier(@PathVariable UUID id, @Valid @RequestBody TaxeSaisie saisie) {
        return service.modifier(id, saisie);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/QuotationServiceApplication.java`

*Point d'entrée du service (port 8082).*

```java
package com.example.quotation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QuotationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuotationServiceApplication.class, args);
    }
}
```

#### `code/quotation-service/src/test/java/com/example/quotation/CatalogueApiTests.java`

*Tests des routes `/taxes` et `/produits`.*

```java
package com.example.quotation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogueApiTests {

    @Autowired
    MockMvc mvc;

    private String creerTaxe(String taux) throws Exception {
        String json = mvc.perform(post("/taxes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tauxTVA\":" + taux + ",\"description\":\"TVA\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String corpsProduit(String nom, String prix, String taxeId) {
        return "{\"nom\":\"" + nom + "\",\"description\":\"Forfait\",\"prix\":" + prix + ",\"taxeId\":\"" + taxeId + "\"}";
    }

    @Test
    void creerEtListerDesTaxes() throws Exception {
        creerTaxe("19");
        mvc.perform(get("/taxes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tauxTVA").value(19));
    }

    @Test
    void tauxHorsDeZeroACentEst422() throws Exception {
        mvc.perform(post("/taxes").contentType(MediaType.APPLICATION_JSON).content("{\"tauxTVA\":120}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/taxes").contentType(MediaType.APPLICATION_JSON).content("{\"tauxTVA\":-1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void modifierUneTaxe() throws Exception {
        String id = creerTaxe("19");
        mvc.perform(put("/taxes/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"tauxTVA\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tauxTVA").value(7));
        mvc.perform(put("/taxes/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"tauxTVA\":7}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void creerUnProduitAvecUnPrixDecimal() throws Exception {
        String taxeId = creerTaxe("19");
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON)
                        .content(corpsProduit("Étude de faisabilité", "1250.500", taxeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prix").value(1250.5))
                .andExpect(jsonPath("$.taxeId").value(taxeId));
    }

    @Test
    void prixNegatifOuPlusDeTroisDecimalesEst422() throws Exception {
        String taxeId = creerTaxe("19");
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON).content(corpsProduit("A", "-5", taxeId)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON).content(corpsProduit("A", "1.2345", taxeId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void taxeInconnueEst404() throws Exception {
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON)
                        .content(corpsProduit("A", "10", UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void listeConsulterModifierSupprimer() throws Exception {
        String taxeId = creerTaxe("19");
        String json = mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON)
                        .content(corpsProduit("Audit", "500", taxeId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON).content(corpsProduit("Zeta", "10", taxeId)))
                .andExpect(status().isCreated());

        mvc.perform(get("/produits?page=0&taille=1"))
                .andExpect(jsonPath("$.contenu.length()").value(1))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/produits/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Audit"));
        mvc.perform(put("/produits/" + id).contentType(MediaType.APPLICATION_JSON).content(corpsProduit("Audit 2", "600", taxeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Audit 2"));
        mvc.perform(delete("/produits/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/produits/" + id)).andExpect(status().isNotFound());
    }
}
```

#### `code/quotation-service/src/test/java/com/example/quotation/QuotationServiceApplicationTests.java`

*Le contexte Spring démarre.*

```java
package com.example.quotation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class QuotationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

#### `code/quotation-service/src/test/resources/application-test.yml`

*Configuration des tests : base H2 en mémoire, sans RabbitMQ.*

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:quotation;MODE=MySQL;DB_CLOSE_DELAY=-1
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
  rabbitmq:
    listener:
      simple:
        auto-startup: false
```


### 5.3 Les routes de l'étape S1

Les champs et codes d'erreur sont ceux du contrat `code/contrat_api/quotation.yaml`.


##### `POST /taxes`

Crée un taux de TVA (pourcentage de 0 à 100).

Corps de la requête :

```json
{ "tauxTVA": 19, "description": "TVA standard" }
```

Réponse **201** :

```json
{ "id": "a1b2c3d4-0000-4000-8000-000000000001", "tauxTVA": 19, "description": "TVA standard" }
```

Erreurs : 422 taux absent ou hors de 0 à 100.

##### `GET /taxes`

Liste les taux (tableau JSON).

Réponse **200** :

```json
[ { "id": "...", "tauxTVA": 19, "description": "TVA standard" } ]
```

Erreurs : aucune.

##### `PUT /taxes/{id}`

Modifie un taux.

Corps de la requête :

```json
{ "tauxTVA": 7, "description": "TVA réduite" }
```

Réponse **200** :

```json
la taxe modifiée
```

Erreurs : 404 ; 422.

##### `POST /produits`

Crée un produit ou un service du catalogue. Le prix est un **nombre décimal** (trois décimales au plus).

Corps de la requête :

```json
{ "nom": "Étude de faisabilité", "description": "Forfait 5 jours", "prix": 1250.500, "taxeId": "<id d'une taxe>" }
```

Réponse **201** :

```json
{ "id": "...", "nom": "Étude de faisabilité", "description": "Forfait 5 jours", "prix": 1250.500, "taxeId": "..." }
```

Erreurs : 404 taxe inconnue ; 422 nom absent, prix négatif ou avec plus de trois décimales.

##### `GET /produits`

Liste paginée du catalogue.

Paramètres d'URL : `page` (0 par défaut), `taille` (20 par défaut, 100 au maximum).

Réponse **200** :

```json
{ "contenu": [ { ...produit... } ], "page": 0, "taille": 20, "total": 1 }
```

Erreurs : 400.

##### `GET /produits/{id}`

Consulte un produit.

Réponse **200** :

```json
le produit
```

Erreurs : 404.

##### `PUT /produits/{id}`

Modifie un produit (même corps que la création).

Corps de la requête :

```json
{ "nom": "Étude 2", "description": "Forfait 8 jours", "prix": 1800, "taxeId": "..." }
```

Réponse **200** :

```json
le produit modifié
```

Erreurs : 404 ; 422.

##### `DELETE /produits/{id}`

Supprime un produit. Sans effet sur les devis existants : ils gardent leur copie.

Réponse **204** :

(aucun corps)

Erreurs : 404.

### 5.4 Vérification

```
cd code/quotation-service
mvn clean test
```

Attendu : `Tests run: 8, Failures: 0, Errors: 0` et `BUILD SUCCESS` (1 test de démarrage, 7 de catalogue).

Essai à la main : `docker compose up -d` (dans `code/`), `mvn spring-boot:run`, puis dans Swagger (<http://localhost:8082/swagger-ui.html>) :
1. `POST /taxes` avec `{ "tauxTVA": 19, "description": "TVA standard" }`.
2. `POST /produits` avec `{ "nom": "Étude de faisabilité", "description": "Forfait 5 jours", "prix": 1250.500, "taxeId": "<id>" }`.
3. `GET /produits` : le produit apparaît dans la liste paginée.


## 6. Étape S2 : devis, lignes et calcul

### 6.1 Fichiers à créer ou remplacer (20)

| # | Fichier | Rôle |
|---|---|---|
| 1 | `code/quotation-service/src/main/resources/application.yml` (REMPLACE le précédent) | Configuration du service. |
| 2 | `code/quotation-service/src/main/java/com/example/quotation/config/ClockConfig.java` | Fournit l'horloge du service : toute date « d'aujourd'hui » passe par elle (remplaçable dans un test). |
| 3 | `code/quotation-service/src/main/java/com/example/quotation/entity/Devis.java` | La table des devis. Contient aussi la copie du client et le jeton du lien (étape S3). |
| 4 | `code/quotation-service/src/main/java/com/example/quotation/entity/LigneDevis.java` | La table des lignes. Désignation, prix et TVA sont des COPIES du catalogue. |
| 5 | `code/quotation-service/src/main/java/com/example/quotation/entity/StatutDevis.java` | Les six statuts d'un devis. |
| 6 | `code/quotation-service/src/main/java/com/example/quotation/repository/DevisRepository.java` | Accès aux devis. |
| 7 | `code/quotation-service/src/main/java/com/example/quotation/dto/DevisCreation.java` | Corps de `POST /devis`. |
| 8 | `code/quotation-service/src/main/java/com/example/quotation/dto/DevisModification.java` | Corps de `PUT /devis/{id}`. |
| 9 | `code/quotation-service/src/main/java/com/example/quotation/dto/DevisReponse.java` | Devis renvoyé aux employés (sans le jeton). |
| 10 | `code/quotation-service/src/main/java/com/example/quotation/dto/LigneModification.java` | Corps de `PUT /devis/{id}/lignes/{ligneId}`. |
| 11 | `code/quotation-service/src/main/java/com/example/quotation/dto/LigneReponse.java` | Ligne renvoyée par l'API. |
| 12 | `code/quotation-service/src/main/java/com/example/quotation/dto/LigneSaisie.java` | Corps de `POST /devis/{id}/lignes`. |
| 13 | `code/quotation-service/src/main/java/com/example/quotation/client/ClientCustomer.java` | Ce que Quotation lit d'un client chez Customer. |
| 14 | `code/quotation-service/src/main/java/com/example/quotation/client/CustomerClient.java` | Appel à Customer (`GET /clients/{id}`), avec délais courts et traduction des erreurs (404, 503). |
| 15 | `code/quotation-service/src/main/java/com/example/quotation/service/CalculateurDevis.java` | Calcule les montants HT, TVA et TTC. Aucun accès à la base : facile à tester seul. |
| 16 | `code/quotation-service/src/main/java/com/example/quotation/service/DevisService.java` | Règles des devis : création avec lecture de Customer, lignes, calcul, annulation, liste. |
| 17 | `code/quotation-service/src/main/java/com/example/quotation/service/ReferenceDevis.java` | Fabrique la référence `DEV-2026-0001`. |
| 18 | `code/quotation-service/src/main/java/com/example/quotation/controller/DevisController.java` | Routes `/devis` (sans l'envoi ni le PDF). |
| 19 | `code/quotation-service/src/test/java/com/example/quotation/CalculateurDevisTest.java` | Test unitaire du calcul des montants (sans Spring ni base). |
| 20 | `code/quotation-service/src/test/java/com/example/quotation/DevisApiTests.java` | Tests des routes `/devis` (Customer simulé). |

### 6.2 Code


#### `code/quotation-service/src/main/resources/application.yml` **(REMPLACE le précédent)**

*Configuration du service.*

```yaml
server:
  port: 8082

spring:
  application:
    name: quotation-service
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3308/quotation_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:soa}
  jpa:
    hibernate:
      ddl-auto: update        # développement : le schéma suit les entités
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  jackson:
    serialization:
      write-dates-as-timestamps: false   # dates ISO 8601
    time-zone: UTC
  rabbitmq:
    host: ${RABBIT_HOST:localhost}
    port: 5672
    username: guest
    password: guest

app:
  # Adresse du service Customer (lecture du client à la création d'un devis)
  customer-url: ${CUSTOMER_URL:http://localhost:8081}
  # Tant que le JWT n'est pas branché (étape E3), l'auteur d'un devis est cette valeur fixe
  default-user-id: 00000000-0000-0000-0000-000000000001

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

#### `code/quotation-service/src/main/java/com/example/quotation/config/ClockConfig.java`

*Fournit l'horloge du service : toute date « d'aujourd'hui » passe par elle (remplaçable dans un test).*

```java
package com.example.quotation.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // Toute date « d'aujourd'hui » passe par cette horloge : on peut la remplacer dans un test
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/entity/Devis.java`

*La table des devis. Contient aussi la copie du client et le jeton du lien (étape S3).*

```java
package com.example.quotation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class Devis {

    @Id
    private UUID id = UUID.randomUUID();

    // Évite que deux personnes modifient le même devis en même temps
    @Version
    private Long version;

    @Column(nullable = false, unique = true)
    private String reference;

    private LocalDate date;
    private LocalDate dateValidite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDevis statut = StatutDevis.BROUILLON;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal remiseGlobale = BigDecimal.ZERO;

    // Montants CALCULÉS par CalculateurDevis : jamais saisis
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal totalHT = BigDecimal.ZERO;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal totalTVA = BigDecimal.ZERO;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal totalTTC = BigDecimal.ZERO;

    // Référence au client (autre service, pas de clé étrangère) et copie de ses coordonnées
    private UUID clientId;
    private String clientNom;
    private String clientEmail;

    // Facultatif ; non vérifié par ce service
    private UUID campagneId;

    // Jeton du lien envoyé au client (étape S3) : jamais exposé aux employés
    @Column(unique = true)
    private String tokenReponse;

    private Instant repondueLe;
    private UUID creePar;

    @OneToMany(mappedBy = "devis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<LigneDevis> lignes = new ArrayList<>();

    public UUID getId() { return id; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalDate getDateValidite() { return dateValidite; }
    public void setDateValidite(LocalDate dateValidite) { this.dateValidite = dateValidite; }

    public StatutDevis getStatut() { return statut; }
    public void setStatut(StatutDevis statut) { this.statut = statut; }

    public BigDecimal getRemiseGlobale() { return remiseGlobale; }
    public void setRemiseGlobale(BigDecimal remiseGlobale) { this.remiseGlobale = remiseGlobale; }

    public BigDecimal getTotalHT() { return totalHT; }
    public void setTotalHT(BigDecimal totalHT) { this.totalHT = totalHT; }

    public BigDecimal getTotalTVA() { return totalTVA; }
    public void setTotalTVA(BigDecimal totalTVA) { this.totalTVA = totalTVA; }

    public BigDecimal getTotalTTC() { return totalTTC; }
    public void setTotalTTC(BigDecimal totalTTC) { this.totalTTC = totalTTC; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getClientNom() { return clientNom; }
    public void setClientNom(String clientNom) { this.clientNom = clientNom; }

    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    public UUID getCampagneId() { return campagneId; }
    public void setCampagneId(UUID campagneId) { this.campagneId = campagneId; }

    public String getTokenReponse() { return tokenReponse; }
    public void setTokenReponse(String tokenReponse) { this.tokenReponse = tokenReponse; }

    public Instant getRepondueLe() { return repondueLe; }
    public void setRepondueLe(Instant repondueLe) { this.repondueLe = repondueLe; }

    public UUID getCreePar() { return creePar; }
    public void setCreePar(UUID creePar) { this.creePar = creePar; }

    public List<LigneDevis> getLignes() { return lignes; }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/entity/LigneDevis.java`

*La table des lignes. Désignation, prix et TVA sont des COPIES du catalogue.*

```java
package com.example.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
public class LigneDevis {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devis_id")
    private Devis devis;

    // Rang de la ligne dans le devis (pour garder l'ordre d'ajout)
    private int position;

    // Référence au produit du catalogue ; les champs suivants en sont des COPIES
    private UUID produitId;

    @Column(nullable = false)
    private String designation;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal prixUnitaire;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTVA;

    private int quantite;

    // Remise de la ligne en pourcentage (0 à 100)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal remise = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal totalLigneHT = BigDecimal.ZERO;

    public UUID getId() { return id; }

    public Devis getDevis() { return devis; }
    public void setDevis(Devis devis) { this.devis = devis; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public UUID getProduitId() { return produitId; }
    public void setProduitId(UUID produitId) { this.produitId = produitId; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public BigDecimal getPrixUnitaire() { return prixUnitaire; }
    public void setPrixUnitaire(BigDecimal prixUnitaire) { this.prixUnitaire = prixUnitaire; }

    public BigDecimal getTauxTVA() { return tauxTVA; }
    public void setTauxTVA(BigDecimal tauxTVA) { this.tauxTVA = tauxTVA; }

    public int getQuantite() { return quantite; }
    public void setQuantite(int quantite) { this.quantite = quantite; }

    public BigDecimal getRemise() { return remise; }
    public void setRemise(BigDecimal remise) { this.remise = remise; }

    public BigDecimal getTotalLigneHT() { return totalLigneHT; }
    public void setTotalLigneHT(BigDecimal totalLigneHT) { this.totalLigneHT = totalLigneHT; }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/entity/StatutDevis.java`

*Les six statuts d'un devis.*

```java
package com.example.quotation.entity;

public enum StatutDevis {
    BROUILLON, ENVOYE, ACCEPTE, REFUSE, EXPIRE, ANNULE
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/repository/DevisRepository.java`

*Accès aux devis.*

```java
package com.example.quotation.repository;

import com.example.quotation.entity.Devis;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DevisRepository extends JpaRepository<Devis, UUID>, JpaSpecificationExecutor<Devis> {

    long countByReferenceStartingWith(String prefixe);
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/DevisCreation.java`

*Corps de `POST /devis`.*

```java
package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DevisCreation(
        @NotNull UUID clientId,
        @NotNull LocalDate dateValidite,
        UUID campagneId,
        @DecimalMin("0") @DecimalMax("100") BigDecimal remiseGlobale) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/DevisModification.java`

*Corps de `PUT /devis/{id}`.*

```java
package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Corps de PUT /devis/{id}. clientEmail sert à corriger l'adresse d'envoi (la fiche Customer n'est pas modifiée). */
public record DevisModification(
        @NotNull LocalDate dateValidite,
        UUID campagneId,
        @DecimalMin("0") @DecimalMax("100") BigDecimal remiseGlobale,
        @Email String clientEmail) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/DevisReponse.java`

*Devis renvoyé aux employés (sans le jeton).*

```java
package com.example.quotation.dto;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Vue d'un devis pour les employés. Le jeton du lien client n'y figure jamais. */
public record DevisReponse(
        UUID id,
        String reference,
        LocalDate date,
        LocalDate dateValidite,
        StatutDevis statut,
        BigDecimal remiseGlobale,
        BigDecimal totalHT,
        BigDecimal totalTVA,
        BigDecimal totalTTC,
        UUID clientId,
        String clientNom,
        String clientEmail,
        UUID campagneId,
        Instant repondueLe,
        UUID creePar,
        List<LigneReponse> lignes) {

    public static DevisReponse de(Devis d) {
        return new DevisReponse(d.getId(), d.getReference(), d.getDate(), d.getDateValidite(), d.getStatut(),
                d.getRemiseGlobale(), d.getTotalHT(), d.getTotalTVA(), d.getTotalTTC(), d.getClientId(),
                d.getClientNom(), d.getClientEmail(), d.getCampagneId(), d.getRepondueLe(), d.getCreePar(),
                d.getLignes().stream().map(LigneReponse::de).toList());
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/LigneModification.java`

*Corps de `PUT /devis/{id}/lignes/{ligneId}`.*

```java
package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LigneModification(
        @NotNull @Min(1) Integer quantite,
        @DecimalMin("0") @DecimalMax("100") BigDecimal remise) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/LigneReponse.java`

*Ligne renvoyée par l'API.*

```java
package com.example.quotation.dto;

import com.example.quotation.entity.LigneDevis;
import java.math.BigDecimal;
import java.util.UUID;

public record LigneReponse(UUID id, UUID produitId, String designation, BigDecimal prixUnitaire, BigDecimal tauxTVA,
                           int quantite, BigDecimal remise, BigDecimal totalLigneHT) {

    public static LigneReponse de(LigneDevis l) {
        return new LigneReponse(l.getId(), l.getProduitId(), l.getDesignation(), l.getPrixUnitaire(), l.getTauxTVA(),
                l.getQuantite(), l.getRemise(), l.getTotalLigneHT());
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/LigneSaisie.java`

*Corps de `POST /devis/{id}/lignes`.*

```java
package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record LigneSaisie(
        @NotNull UUID produitId,
        @NotNull @Min(1) Integer quantite,
        @DecimalMin("0") @DecimalMax("100") BigDecimal remise) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/client/ClientCustomer.java`

*Ce que Quotation lit d'un client chez Customer.*

```java
package com.example.quotation.client;

import java.util.UUID;

/** Ce que Quotation lit d'un client chez Customer (les autres champs de la réponse sont ignorés). */
public record ClientCustomer(UUID id, String nom, String prenom, String email, String statut) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/client/CustomerClient.java`

*Appel à Customer (`GET /clients/{id}`), avec délais courts et traduction des erreurs (404, 503).*

```java
package com.example.quotation.client;

import com.example.quotation.exception.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Lecture d'un client chez Customer (lien I1). Lecture seule : on n'écrit jamais chez Customer. */
@Component
public class CustomerClient {

    private final RestClient rest;

    public CustomerClient(@Value("${app.customer-url}") String url) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(2));
        fabrique.setReadTimeout(Duration.ofSeconds(2));
        this.rest = RestClient.builder().baseUrl(url).requestFactory(fabrique).build();
    }

    public ClientCustomer trouver(UUID clientId) {
        try {
            return rest.get().uri("/clients/{id}", clientId).retrieve().body(ClientCustomer.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Client introuvable");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service Customer indisponible");
        }
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/CalculateurDevis.java`

*Calcule les montants HT, TVA et TTC. Aucun accès à la base : facile à tester seul.*

```java
package com.example.quotation.service;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.LigneDevis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Calcule les montants d'un devis. Aucun accès à la base : on lui donne un devis, il remplit les totaux.
 *
 * totalLigneHT = prixUnitaire x quantite x (1 - remise/100)
 * totalHT      = somme(totalLigneHT) x (1 - remiseGlobale/100)
 * totalTVA     = somme(totalLigneHT x tauxTVA/100) x (1 - remiseGlobale/100)
 * totalTTC     = totalHT + totalTVA
 * Tout est arrondi à 3 décimales (HALF_UP).
 */
@Component
public class CalculateurDevis {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    public void recalculer(Devis devis) {
        BigDecimal sommeHT = BigDecimal.ZERO;
        BigDecimal sommeTVA = BigDecimal.ZERO;

        for (LigneDevis ligne : devis.getLignes()) {
            BigDecimal facteurRemise = BigDecimal.ONE.subtract(ligne.getRemise().divide(CENT, 10, RoundingMode.HALF_UP));
            BigDecimal ht = ligne.getPrixUnitaire()
                    .multiply(BigDecimal.valueOf(ligne.getQuantite()))
                    .multiply(facteurRemise)
                    .setScale(3, RoundingMode.HALF_UP);
            ligne.setTotalLigneHT(ht);
            sommeHT = sommeHT.add(ht);
            sommeTVA = sommeTVA.add(ht.multiply(ligne.getTauxTVA()).divide(CENT, 10, RoundingMode.HALF_UP));
        }

        BigDecimal facteurGlobal = BigDecimal.ONE.subtract(devis.getRemiseGlobale().divide(CENT, 10, RoundingMode.HALF_UP));
        BigDecimal totalHT = sommeHT.multiply(facteurGlobal).setScale(3, RoundingMode.HALF_UP);
        BigDecimal totalTVA = sommeTVA.multiply(facteurGlobal).setScale(3, RoundingMode.HALF_UP);
        devis.setTotalHT(totalHT);
        devis.setTotalTVA(totalTVA);
        devis.setTotalTTC(totalHT.add(totalTVA));
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/DevisService.java`

*Règles des devis : création avec lecture de Customer, lignes, calcul, annulation, liste.*

```java
package com.example.quotation.service;

import com.example.quotation.client.ClientCustomer;
import com.example.quotation.client.CustomerClient;
import com.example.quotation.dto.DevisCreation;
import com.example.quotation.dto.DevisModification;
import com.example.quotation.dto.DevisReponse;
import com.example.quotation.dto.LigneModification;
import com.example.quotation.dto.LigneSaisie;
import com.example.quotation.dto.PageResponse;
import com.example.quotation.entity.Devis;
import com.example.quotation.entity.LigneDevis;
import com.example.quotation.entity.Produit;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.DevisRepository;
import com.example.quotation.repository.ProduitRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DevisService {

    private final DevisRepository devis;
    private final ProduitRepository produits;
    private final CustomerClient customer;
    private final CalculateurDevis calculateur;
    private final ReferenceDevis references;
    private final Clock clock;
    private final UUID auteurParDefaut;

    public DevisService(DevisRepository devis, ProduitRepository produits, CustomerClient customer,
                        CalculateurDevis calculateur, ReferenceDevis references, Clock clock,
                        @Value("${app.default-user-id}") UUID auteurParDefaut) {
        this.devis = devis;
        this.produits = produits;
        this.customer = customer;
        this.calculateur = calculateur;
        this.references = references;
        this.clock = clock;
        this.auteurParDefaut = auteurParDefaut;
    }

    public DevisReponse creer(DevisCreation saisie) {
        // 1. On lit le client chez Customer AVANT d'écrire quoi que ce soit : s'il est en panne, rien n'est créé
        ClientCustomer client = customer.trouver(saisie.clientId());
        if ("INACTIF".equals(client.statut())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Ce client est inactif : aucun devis possible");
        }
        LocalDate aujourdhui = LocalDate.now(clock);
        if (saisie.dateValidite().isBefore(aujourdhui)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La date de validité doit être postérieure ou égale à la date du devis");
        }
        // 2. On copie les coordonnées du client : le devis reste utilisable si Customer tombe plus tard
        Devis d = new Devis();
        d.setReference(references.suivante());
        d.setDate(aujourdhui);
        d.setDateValidite(saisie.dateValidite());
        d.setClientId(client.id() != null ? client.id() : saisie.clientId());
        d.setClientNom(client.prenom() == null || client.prenom().isBlank()
                ? client.nom() : client.prenom() + " " + client.nom());
        d.setClientEmail(client.email());
        d.setCampagneId(saisie.campagneId());
        d.setRemiseGlobale(saisie.remiseGlobale() == null ? BigDecimal.ZERO : saisie.remiseGlobale());
        d.setCreePar(auteurParDefaut);
        calculateur.recalculer(d);
        return DevisReponse.de(devis.save(d));
    }

    @Transactional(readOnly = true)
    public DevisReponse consulter(UUID id) {
        return DevisReponse.de(trouver(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DevisReponse> lister(UUID clientId, StatutDevis statut, UUID campagneId, int page, int taille) {
        Specification<Devis> spec = (root, query, cb) -> cb.conjunction();
        if (clientId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clientId"), clientId));
        }
        if (statut != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statut"), statut));
        }
        if (campagneId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("campagneId"), campagneId));
        }
        return PageResponse.of(devis.findAll(spec, PageResponse.pageable(page, taille, Sort.by("reference").descending()))
                .map(DevisReponse::de));
    }

    public DevisReponse modifier(UUID id, DevisModification saisie) {
        Devis d = trouverBrouillon(id);
        if (saisie.dateValidite().isBefore(d.getDate())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La date de validité doit être postérieure ou égale à la date du devis");
        }
        d.setDateValidite(saisie.dateValidite());
        d.setCampagneId(saisie.campagneId());
        if (saisie.remiseGlobale() != null) {
            d.setRemiseGlobale(saisie.remiseGlobale());
        }
        if (saisie.clientEmail() != null) {
            d.setClientEmail(saisie.clientEmail());
        }
        calculateur.recalculer(d);
        return DevisReponse.de(d);
    }

    public DevisReponse ajouterLigne(UUID id, LigneSaisie saisie) {
        Devis d = trouverBrouillon(id);
        Produit produit = produits.findById(saisie.produitId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Produit introuvable"));
        LigneDevis ligne = new LigneDevis();
        ligne.setDevis(d);
        ligne.setPosition(d.getLignes().size());
        ligne.setProduitId(produit.getId());
        // COPIE du catalogue : si le produit change ensuite, cette ligne ne change pas
        ligne.setDesignation(produit.getNom());
        ligne.setPrixUnitaire(produit.getPrix());
        ligne.setTauxTVA(produit.getTaxe().getTauxTVA());
        ligne.setQuantite(saisie.quantite());
        ligne.setRemise(saisie.remise() == null ? BigDecimal.ZERO : saisie.remise());
        d.getLignes().add(ligne);
        calculateur.recalculer(d);
        return DevisReponse.de(d);
    }

    public DevisReponse modifierLigne(UUID id, UUID ligneId, LigneModification saisie) {
        Devis d = trouverBrouillon(id);
        LigneDevis ligne = trouverLigne(d, ligneId);
        ligne.setQuantite(saisie.quantite());
        ligne.setRemise(saisie.remise() == null ? BigDecimal.ZERO : saisie.remise());
        calculateur.recalculer(d);
        return DevisReponse.de(d);
    }

    public DevisReponse supprimerLigne(UUID id, UUID ligneId) {
        Devis d = trouverBrouillon(id);
        d.getLignes().remove(trouverLigne(d, ligneId));
        calculateur.recalculer(d);
        return DevisReponse.de(d);
    }

    /** Annulation possible avant la réponse du client : BROUILLON ou ENVOYE. */
    public DevisReponse annuler(UUID id) {
        Devis d = trouver(id);
        if (d.getStatut() != StatutDevis.BROUILLON && d.getStatut() != StatutDevis.ENVOYE) {
            throw new ApiException(HttpStatus.CONFLICT, "Ce devis ne peut plus être annulé");
        }
        d.setStatut(StatutDevis.ANNULE);
        return DevisReponse.de(d);
    }

    // ---- méthodes internes

    Devis trouver(UUID id) {
        return devis.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Devis introuvable"));
    }

    private Devis trouverBrouillon(UUID id) {
        Devis d = trouver(id);
        if (d.getStatut() != StatutDevis.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Seul un devis en brouillon peut être modifié");
        }
        return d;
    }

    private LigneDevis trouverLigne(Devis d, UUID ligneId) {
        return d.getLignes().stream().filter(l -> l.getId().equals(ligneId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ligne introuvable"));
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/ReferenceDevis.java`

*Fabrique la référence `DEV-2026-0001`.*

```java
package com.example.quotation.service;

import com.example.quotation.repository.DevisRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Fabrique la référence lisible d'un devis : DEV-2026-0042 (numéro à 4 chiffres, recommencé chaque année). */
@Component
public class ReferenceDevis {

    private final DevisRepository devis;
    private final Clock clock;

    public ReferenceDevis(DevisRepository devis, Clock clock) {
        this.devis = devis;
        this.clock = clock;
    }

    public String suivante() {
        String prefixe = "DEV-" + LocalDate.now(clock).getYear() + "-";
        long numero = devis.countByReferenceStartingWith(prefixe) + 1;
        return prefixe + String.format("%04d", numero);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/controller/DevisController.java`

*Routes `/devis` (sans l'envoi ni le PDF).*

```java
package com.example.quotation.controller;

import com.example.quotation.dto.DevisCreation;
import com.example.quotation.dto.DevisModification;
import com.example.quotation.dto.DevisReponse;
import com.example.quotation.dto.LigneModification;
import com.example.quotation.dto.LigneSaisie;
import com.example.quotation.dto.PageResponse;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.service.DevisService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devis")
public class DevisController {

    private final DevisService service;

    public DevisController(DevisService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DevisReponse creer(@Valid @RequestBody DevisCreation saisie) {
        return service.creer(saisie);
    }

    @GetMapping
    public PageResponse<DevisReponse> lister(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) StatutDevis statut,
            @RequestParam(required = false) UUID campagneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int taille) {
        return service.lister(clientId, statut, campagneId, page, taille);
    }

    @GetMapping("/{id}")
    public DevisReponse consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    @PutMapping("/{id}")
    public DevisReponse modifier(@PathVariable UUID id, @Valid @RequestBody DevisModification saisie) {
        return service.modifier(id, saisie);
    }

    @PostMapping("/{id}/lignes")
    @ResponseStatus(HttpStatus.CREATED)
    public DevisReponse ajouterLigne(@PathVariable UUID id, @Valid @RequestBody LigneSaisie saisie) {
        return service.ajouterLigne(id, saisie);
    }

    @PutMapping("/{id}/lignes/{ligneId}")
    public DevisReponse modifierLigne(@PathVariable UUID id, @PathVariable UUID ligneId,
                                      @Valid @RequestBody LigneModification saisie) {
        return service.modifierLigne(id, ligneId, saisie);
    }

    @DeleteMapping("/{id}/lignes/{ligneId}")
    public DevisReponse supprimerLigne(@PathVariable UUID id, @PathVariable UUID ligneId) {
        return service.supprimerLigne(id, ligneId);
    }

    @PostMapping("/{id}/annuler")
    public DevisReponse annuler(@PathVariable UUID id) {
        return service.annuler(id);
    }
}
```

#### `code/quotation-service/src/test/java/com/example/quotation/CalculateurDevisTest.java`

*Test unitaire du calcul des montants (sans Spring ni base).*

```java
package com.example.quotation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.LigneDevis;
import com.example.quotation.service.CalculateurDevis;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Test unitaire pur : pas de Spring, pas de base. */
class CalculateurDevisTest {

    private final CalculateurDevis calculateur = new CalculateurDevis();

    private static LigneDevis ligne(String prix, int quantite, String remise, String tva) {
        LigneDevis l = new LigneDevis();
        l.setPrixUnitaire(new BigDecimal(prix));
        l.setQuantite(quantite);
        l.setRemise(new BigDecimal(remise));
        l.setTauxTVA(new BigDecimal(tva));
        return l;
    }

    @Test
    void uneLigneAvecRemiseEtTva() {
        Devis d = new Devis();
        d.getLignes().add(ligne("100.000", 2, "10", "19"));
        calculateur.recalculer(d);
        assertThat(d.getLignes().get(0).getTotalLigneHT()).isEqualByComparingTo("180.000");
        assertThat(d.getTotalHT()).isEqualByComparingTo("180.000");
        assertThat(d.getTotalTVA()).isEqualByComparingTo("34.200");
        assertThat(d.getTotalTTC()).isEqualByComparingTo("214.200");
    }

    @Test
    void deuxLignesDeTvaDifferenteEtRemiseGlobale() {
        Devis d = new Devis();
        d.getLignes().add(ligne("100.000", 1, "0", "19"));
        d.getLignes().add(ligne("50.000", 2, "0", "7"));
        d.setRemiseGlobale(new BigDecimal("10"));
        calculateur.recalculer(d);
        // HT : 200 x 0,9 = 180 ; TVA : (19 + 7) x 0,9 = 23,4
        assertThat(d.getTotalHT()).isEqualByComparingTo("180.000");
        assertThat(d.getTotalTVA()).isEqualByComparingTo("23.400");
        assertThat(d.getTotalTTC()).isEqualByComparingTo("203.400");
    }

    @Test
    void arrondiATroisDecimales() {
        Devis d = new Devis();
        d.getLignes().add(ligne("100.000", 1, "33.333", "0"));
        calculateur.recalculer(d);
        assertThat(d.getTotalHT()).isEqualByComparingTo("66.667");
    }

    @Test
    void devisSansLigneTotauxANul() {
        Devis d = new Devis();
        calculateur.recalculer(d);
        assertThat(d.getTotalHT()).isEqualByComparingTo("0");
        assertThat(d.getTotalTVA()).isEqualByComparingTo("0");
        assertThat(d.getTotalTTC()).isEqualByComparingTo("0");
    }
}
```

#### `code/quotation-service/src/test/java/com/example/quotation/DevisApiTests.java`

*Tests des routes `/devis` (Customer simulé).*

```java
package com.example.quotation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.quotation.client.ClientCustomer;
import com.example.quotation.client.CustomerClient;
import com.example.quotation.entity.Produit;
import com.example.quotation.entity.Taxe;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.ProduitRepository;
import com.example.quotation.repository.TaxeRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DevisApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TaxeRepository taxes;
    @Autowired
    ProduitRepository produits;

    // Customer est SIMULÉ : les tests ne dépendent jamais du vrai service
    @MockBean
    CustomerClient customer;

    final UUID clientId = UUID.randomUUID();
    final String demain = LocalDate.now().plusDays(10).toString();

    @BeforeEach
    void clientParDefaut() {
        when(customer.trouver(any())).thenReturn(
                new ClientCustomer(clientId, "Société Atlas", null, "contact@atlas.tn", "CLIENT"));
    }

    private String creerDevis() throws Exception {
        String json = mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private Produit creerProduit(String prix, String tva) {
        Taxe t = new Taxe();
        t.setTauxTVA(new BigDecimal(tva));
        taxes.save(t);
        Produit p = new Produit();
        p.setNom("Étude");
        p.setPrix(new BigDecimal(prix));
        p.setTaxe(t);
        return produits.save(p);
    }

    @Test
    void creationCopieLesCoordonneesDuClient() throws Exception {
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.clientNom").value("Société Atlas"))
                .andExpect(jsonPath("$.clientEmail").value("contact@atlas.tn"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("DEV-" + LocalDate.now().getYear() + "-")))
                .andExpect(jsonPath("$.totalTTC").value(0))
                .andExpect(jsonPath("$.tokenReponse").doesNotExist());
    }

    @Test
    void lesReferencesSAugmentent() throws Exception {
        creerDevis();
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.endsWith("-0002")));
    }

    @Test
    void clientInactifEst422() throws Exception {
        when(customer.trouver(any())).thenReturn(new ClientCustomer(clientId, "Atlas", null, "a@a.tn", "INACTIF"));
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void clientIntrouvableEst404() throws Exception {
        when(customer.trouver(any())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Client introuvable"));
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerEnPanneDonne503EtNeCreeRien() throws Exception {
        when(customer.trouver(any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service Customer indisponible"));
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
        mvc.perform(get("/devis")).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void dateDeValiditeDansLePasseEst422() throws Exception {
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ajouterUneLigneCopieLeCatalogueEtCalculeLesTotaux() throws Exception {
        Produit p = creerProduit("100.000", "19");
        String id = creerDevis();
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":2,\"remise\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.lignes[0].designation").value("Étude"))
                .andExpect(jsonPath("$.lignes[0].totalLigneHT").value(180))
                .andExpect(jsonPath("$.totalHT").value(180))
                .andExpect(jsonPath("$.totalTVA").value(34.2))
                .andExpect(jsonPath("$.totalTTC").value(214.2));

        // Le prix du catalogue change : la ligne du devis garde sa copie
        p.setPrix(new BigDecimal("999.000"));
        produits.save(p);
        mvc.perform(get("/devis/" + id))
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(100))
                .andExpect(jsonPath("$.totalTTC").value(214.2));
    }

    @Test
    void produitInconnuEst404EtQuantiteNulleEst422() throws Exception {
        Produit p = creerProduit("100", "19");
        String id = creerDevis();
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + UUID.randomUUID() + "\",\"quantite\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":0}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":1,\"remise\":150}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void modifierEtSupprimerUneLigneRecalculentLesTotaux() throws Exception {
        Produit p = creerProduit("100.000", "19");
        String id = creerDevis();
        String json = mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ligneId = JsonPath.read(json, "$.lignes[0].id");

        mvc.perform(put("/devis/" + id + "/lignes/" + ligneId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantite\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHT").value(300));

        mvc.perform(delete("/devis/" + id + "/lignes/" + ligneId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes.length()").value(0))
                .andExpect(jsonPath("$.totalTTC").value(0));
        mvc.perform(delete("/devis/" + id + "/lignes/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void modifierLeDevisEnBrouillon() throws Exception {
        String id = creerDevis();
        String autre = LocalDate.now().plusDays(30).toString();
        mvc.perform(put("/devis/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateValidite\":\"" + autre + "\",\"remiseGlobale\":5,\"clientEmail\":\"nouveau@atlas.tn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateValidite").value(autre))
                .andExpect(jsonPath("$.remiseGlobale").value(5))
                .andExpect(jsonPath("$.clientEmail").value("nouveau@atlas.tn"));
    }

    @Test
    void unDevisAnnuleNeSeModifiePlusEtNeSAnnuleQuUneFois() throws Exception {
        Produit p = creerProduit("100", "19");
        String id = creerDevis();
        mvc.perform(post("/devis/" + id + "/annuler")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ANNULE"));
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/devis/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/devis/" + id + "/annuler")).andExpect(status().isConflict());
    }

    @Test
    void listeAvecFiltres() throws Exception {
        String a = creerDevis();
        creerDevis();
        mvc.perform(post("/devis/" + a + "/annuler")).andExpect(status().isOk());

        mvc.perform(get("/devis")).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/devis?statut=ANNULE")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/devis?clientId=" + clientId)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/devis?clientId=" + UUID.randomUUID())).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/devis?page=0&taille=1")).andExpect(jsonPath("$.contenu.length()").value(1));
        mvc.perform(get("/devis/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
```


### 6.3 Les routes de l'étape S2


##### `POST /devis`

Crée un devis BROUILLON. Le service lit le client chez Customer **avant d'écrire quoi que ce soit** et copie son nom et son email.

Corps de la requête :

```json
{ "clientId": "9a2f7c31-84b6-4d10-b3e5-6c8a1d0e9f52", "dateValidite": "2026-12-31", "campagneId": null, "remiseGlobale": 0 }
```

Réponse **201** :

```json
{
  "id": "e41a8b07-6d93-4f2c-a5e8-3b9c0d1f2a47",
  "reference": "DEV-2026-0001",
  "date": "2026-11-18",
  "dateValidite": "2026-12-31",
  "statut": "BROUILLON",
  "remiseGlobale": 0,
  "totalHT": 2250.900,
  "totalTVA": 427.671,
  "totalTTC": 2678.571,
  "clientId": "9a2f7c31-84b6-4d10-b3e5-6c8a1d0e9f52",
  "clientNom": "Société Atlas",
  "clientEmail": "contact@atlas.tn",
  "campagneId": null,
  "repondueLe": null,
  "creePar": "00000000-0000-0000-0000-000000000001",
  "lignes": [
    {
      "id": "c2d3e4f5-0a1b-4c2d-8e3f-4a5b6c7d8e9f",
      "produitId": "d4e5f6a7-1b2c-4d3e-9f4a-5b6c7d8e9f0a",
      "designation": "Étude de faisabilité",
      "prixUnitaire": 1250.500,
      "tauxTVA": 19,
      "quantite": 2,
      "remise": 10,
      "totalLigneHT": 2250.900
    }
  ]
}
```

Erreurs : 404 client inconnu ; 422 client `INACTIF` ou `dateValidite` dans le passé ; **503** Customer indisponible (rien n'est créé).

##### `GET /devis`

Liste paginée (c'est aussi l'historique des devis d'un client).

Paramètres d'URL : `clientId`, `statut`, `campagneId`, `page`, `taille` (tous facultatifs).

Réponse **200** :

```json
{ "contenu": [ { ...devis... } ], "page": 0, "taille": 20, "total": 1 }
```

Erreurs : 400.

##### `GET /devis/{id}`

Consulte un devis avec ses lignes et ses montants.

Réponse **200** :

```json
le devis
```

Erreurs : 404.

##### `PUT /devis/{id}`

Modifie un devis, **seulement s'il est BROUILLON**. `clientEmail` permet de corriger l'adresse d'envoi sans toucher à la fiche Customer.

Corps de la requête :

```json
{ "dateValidite": "2026-12-31", "campagneId": null, "remiseGlobale": 5, "clientEmail": "nouveau@atlas.tn" }
```

Réponse **200** :

```json
le devis modifié
```

Erreurs : 404 ; **409** devis non BROUILLON ; 422 date de validité avant la date du devis, email mal formé.

##### `POST /devis/{id}/lignes`

Ajoute une ligne. Le service **copie** désignation, prix et TVA du catalogue puis recalcule les montants. Renvoie le devis complet.

Corps de la requête :

```json
{ "produitId": "<id d'un produit>", "quantite": 2, "remise": 10 }
```

Réponse **201** :

```json
le devis complet (voir ci-dessus)
```

Erreurs : 404 devis ou produit inconnu ; **409** devis non BROUILLON ; 422 quantité inférieure à 1 ou remise hors de 0 à 100.

##### `PUT /devis/{id}/lignes/{ligneId}`

Modifie la quantité et la remise d'une ligne ; recalcule.

Corps de la requête :

```json
{ "quantite": 3, "remise": 0 }
```

Réponse **200** :

```json
le devis complet
```

Erreurs : 404 ; 409 ; 422.

##### `DELETE /devis/{id}/lignes/{ligneId}`

Supprime une ligne ; recalcule.

Réponse **200** :

```json
le devis complet
```

Erreurs : 404 ; 409.

##### `POST /devis/{id}/annuler`

Annule un devis avant la réponse du client (BROUILLON ou ENVOYE).

Réponse **200** :

```json
le devis, statut `ANNULE`
```

Erreurs : 404 ; **409** déjà ACCEPTE, REFUSE, EXPIRE ou ANNULE.

### 6.4 Vérification

```
cd code/quotation-service
mvn clean test
```

Attendu : `Tests run: 24, Failures: 0, Errors: 0` et `BUILD SUCCESS`. Les tests simulent Customer : il n'est **pas** nécessaire qu'il soit démarré.

Essai réel (une fois l'étape B1 de Baha validée) : démarrer Customer (8081) et Quotation (8082), créer un client dans Customer, une taxe et un produit dans Quotation, puis :
1. `POST /devis` avec le `clientId` : 201, `clientNom` et `clientEmail` copiés.
2. `POST /devis/{id}/lignes` avec `{ "produitId": "<id>", "quantite": 2, "remise": 10 }` : vérifier les totaux à la calculatrice.
3. **Panne** : arrêter Customer puis refaire `POST /devis` : **503**, et `GET /devis` ne contient aucun nouveau devis.


## 7. Étape S3 : PDF, envoi, réponse publique et expiration

### 7.1 Fichiers à créer ou remplacer (22)

| # | Fichier | Rôle |
|---|---|---|
| 1 | `code/quotation-service/pom.xml` (REMPLACE le précédent) | Dépendances Maven du service. |
| 2 | `code/quotation-service/src/main/resources/application.yml` (REMPLACE le précédent) | Configuration du service. |
| 3 | `code/quotation-service/src/main/java/com/example/quotation/config/SchedulingConfig.java` | Active les tâches planifiées. |
| 4 | `code/quotation-service/src/main/java/com/example/quotation/entity/Document.java` | Trace du PDF généré et envoyé. |
| 5 | `code/quotation-service/src/main/java/com/example/quotation/repository/DevisRepository.java` (REMPLACE le précédent) | Accès aux devis. |
| 6 | `code/quotation-service/src/main/java/com/example/quotation/repository/DocumentRepository.java` | Accès aux documents. |
| 7 | `code/quotation-service/src/main/java/com/example/quotation/dto/DevisPublic.java` | Ce que le client voit en ouvrant son lien. |
| 8 | `code/quotation-service/src/main/java/com/example/quotation/messaging/DevisAccepteEvent.java` | Contenu de l'événement `DevisAccepte`. |
| 9 | `code/quotation-service/src/main/java/com/example/quotation/messaging/EditeurEvenements.java` | Interface de publication des événements (remplacée à l'étape E1 par RabbitMQ). |
| 10 | `code/quotation-service/src/main/java/com/example/quotation/messaging/EditeurEvenementsLog.java` | Version provisoire : écrit l'événement dans les logs. |
| 11 | `code/quotation-service/src/main/java/com/example/quotation/service/EmailException.java` | Erreur lancée quand l'envoi d'un email échoue. |
| 12 | `code/quotation-service/src/main/java/com/example/quotation/service/EnvoiDevisService.java` | Envoi d'un devis : vérifications, jeton, PDF, email, statut ENVOYE. |
| 13 | `code/quotation-service/src/main/java/com/example/quotation/service/EnvoiEmail.java` | UNIQUE point d'envoi d'email : simulé (écrit dans les logs). |
| 14 | `code/quotation-service/src/main/java/com/example/quotation/service/ExpirationDevisTache.java` | Tâche quotidienne (1 h du matin) qui appelle l'expiration. |
| 15 | `code/quotation-service/src/main/java/com/example/quotation/service/ExpirationService.java` | Passe à EXPIRE les devis dont la date de validité est passée. |
| 16 | `code/quotation-service/src/main/java/com/example/quotation/service/FichierPdf.java` | Nom et contenu d'un PDF. |
| 17 | `code/quotation-service/src/main/java/com/example/quotation/service/GenerateurPdf.java` | Fabrique le PDF d'un devis (bibliothèque OpenPDF). |
| 18 | `code/quotation-service/src/main/java/com/example/quotation/service/JetonReponse.java` | Fabrique le jeton aléatoire du lien envoyé au client. |
| 19 | `code/quotation-service/src/main/java/com/example/quotation/service/ReponsePubliqueService.java` | Réponse du client par son lien : consulter, accepter, refuser. |
| 20 | `code/quotation-service/src/main/java/com/example/quotation/controller/EnvoiController.java` | Routes `POST /devis/{id}/envoyer` et `GET /devis/{id}/pdf`. |
| 21 | `code/quotation-service/src/main/java/com/example/quotation/controller/PublicController.java` | Routes publiques `/public/reponse/{token}` (sans identité). |
| 22 | `code/quotation-service/src/test/java/com/example/quotation/EnvoiEtReponseApiTests.java` | Tests de l'envoi, du PDF, de la réponse publique et de l'expiration. |

### 7.2 Code


#### `code/quotation-service/pom.xml` **(REMPLACE le précédent)**

*Dépendances Maven du service.*

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>

  <groupId>com.example</groupId>
  <artifactId>quotation-service</artifactId>
  <version>0.1.0</version>
  <name>quotation-service</name>
  <description>Module commercial : devis, catalogue, taxes (port 8082)</description>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-amqp</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.6.0</version></dependency>
    <dependency><groupId>com.github.librepdf</groupId><artifactId>openpdf</artifactId><version>1.3.43</version></dependency>
    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>

    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
    </plugins>
  </build>
</project>
```

#### `code/quotation-service/src/main/resources/application.yml` **(REMPLACE le précédent)**

*Configuration du service.*

```yaml
server:
  port: 8082

spring:
  application:
    name: quotation-service
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3308/quotation_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:soa}
  jpa:
    hibernate:
      ddl-auto: update        # développement : le schéma suit les entités
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  jackson:
    serialization:
      write-dates-as-timestamps: false   # dates ISO 8601
    time-zone: UTC
  rabbitmq:
    host: ${RABBIT_HOST:localhost}
    port: 5672
    username: guest
    password: guest

app:
  # Adresse du service Customer (lecture du client à la création d'un devis)
  customer-url: ${CUSTOMER_URL:http://localhost:8081}
  # Tant que le JWT n'est pas branché (étape E3), l'auteur d'un devis est cette valeur fixe
  default-user-id: 00000000-0000-0000-0000-000000000001
  # Adresse de l'interface web : les liens du devis envoyé au client commencent par elle
  interface-url: ${INTERFACE_URL:http://localhost:3000}
  email:
    # Mettre true pour simuler une panne d'envoi (essais à la main)
    echec-simule: false

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

#### `code/quotation-service/src/main/java/com/example/quotation/config/SchedulingConfig.java`

*Active les tâches planifiées.*

```java
package com.example.quotation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/entity/Document.java`

*Trace du PDF généré et envoyé.*

```java
package com.example.quotation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;

/** Trace du PDF généré et envoyé pour un devis. */
@Entity
public class Document {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devis_id")
    private Devis devis;

    private String format = "PDF";

    @Lob
    private byte[] contenu;

    private Instant genereLe;
    private Instant envoyeLe;
    private String destinataire;

    public UUID getId() { return id; }

    public Devis getDevis() { return devis; }
    public void setDevis(Devis devis) { this.devis = devis; }

    public String getFormat() { return format; }

    public byte[] getContenu() { return contenu; }
    public void setContenu(byte[] contenu) { this.contenu = contenu; }

    public Instant getGenereLe() { return genereLe; }
    public void setGenereLe(Instant genereLe) { this.genereLe = genereLe; }

    public Instant getEnvoyeLe() { return envoyeLe; }
    public void setEnvoyeLe(Instant envoyeLe) { this.envoyeLe = envoyeLe; }

    public String getDestinataire() { return destinataire; }
    public void setDestinataire(String destinataire) { this.destinataire = destinataire; }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/repository/DevisRepository.java` **(REMPLACE le précédent)**

*Accès aux devis.*

```java
package com.example.quotation.repository;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DevisRepository extends JpaRepository<Devis, UUID>, JpaSpecificationExecutor<Devis> {

    long countByReferenceStartingWith(String prefixe);

    Optional<Devis> findByTokenReponse(String tokenReponse);

    List<Devis> findByStatutAndDateValiditeBefore(StatutDevis statut, LocalDate date);
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/repository/DocumentRepository.java`

*Accès aux documents.*

```java
package com.example.quotation.repository;

import com.example.quotation.entity.Document;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/dto/DevisPublic.java`

*Ce que le client voit en ouvrant son lien.*

```java
package com.example.quotation.dto;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ce que le client voit en ouvrant son lien : rien d'interne (ni identifiants, ni jeton). */
public record DevisPublic(String reference, LocalDate date, LocalDate dateValidite, StatutDevis statut,
                          String clientNom, List<LigneReponse> lignes, BigDecimal totalHT, BigDecimal totalTVA,
                          BigDecimal totalTTC) {

    public static DevisPublic de(Devis d) {
        return new DevisPublic(d.getReference(), d.getDate(), d.getDateValidite(), d.getStatut(), d.getClientNom(),
                d.getLignes().stream().map(LigneReponse::de).toList(), d.getTotalHT(), d.getTotalTVA(),
                d.getTotalTTC());
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/messaging/DevisAccepteEvent.java`

*Contenu de l'événement `DevisAccepte`.*

```java
package com.example.quotation.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contenu de l'événement DevisAccepte (voir contrat_api/evenements.md). */
public record DevisAccepteEvent(UUID eventId, UUID devisId, String reference, UUID clientId, UUID campagneId,
                                BigDecimal montantHT, BigDecimal montantTTC, Instant date) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/messaging/EditeurEvenements.java`

*Interface de publication des événements (remplacée à l'étape E1 par RabbitMQ).*

```java
package com.example.quotation.messaging;

/**
 * Publication des événements de Quotation. Aujourd'hui : écriture dans les logs seulement.
 * À l'étape E1, une autre implémentation enverra le message à RabbitMQ, sans changer le reste du code.
 */
public interface EditeurEvenements {

    void devisAccepte(DevisAccepteEvent evenement);
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/messaging/EditeurEvenementsLog.java`

*Version provisoire : écrit l'événement dans les logs.*

```java
package com.example.quotation.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EditeurEvenementsLog implements EditeurEvenements {

    private static final Logger LOG = LoggerFactory.getLogger(EditeurEvenementsLog.class);

    @Override
    public void devisAccepte(DevisAccepteEvent evenement) {
        LOG.info("[ÉVÉNEMENT SIMULÉ] DevisAccepte {}", evenement);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/EmailException.java`

*Erreur lancée quand l'envoi d'un email échoue.*

```java
package com.example.quotation.service;

/** L'envoi d'un email a échoué. */
public class EmailException extends RuntimeException {

    public EmailException(String message) {
        super(message);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/EnvoiDevisService.java`

*Envoi d'un devis : vérifications, jeton, PDF, email, statut ENVOYE.*

```java
package com.example.quotation.service;

import com.example.quotation.dto.DevisReponse;
import com.example.quotation.entity.Devis;
import com.example.quotation.entity.Document;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.DevisRepository;
import com.example.quotation.repository.DocumentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EnvoiDevisService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final DevisRepository devis;
    private final DocumentRepository documents;
    private final GenerateurPdf generateurPdf;
    private final EnvoiEmail envoiEmail;
    private final Clock clock;
    private final String interfaceUrl;

    public EnvoiDevisService(DevisRepository devis, DocumentRepository documents, GenerateurPdf generateurPdf,
                             EnvoiEmail envoiEmail, Clock clock, @Value("${app.interface-url}") String interfaceUrl) {
        this.devis = devis;
        this.documents = documents;
        this.generateurPdf = generateurPdf;
        this.envoiEmail = envoiEmail;
        this.clock = clock;
        this.interfaceUrl = interfaceUrl;
    }

    public DevisReponse envoyer(UUID id) {
        Devis d = trouver(id);
        if (d.getStatut() != StatutDevis.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Seul un devis en brouillon peut être envoyé");
        }
        if (d.getClientEmail() == null || !EMAIL.matcher(d.getClientEmail()).matches()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "L'email du client est invalide");
        }
        if (d.getDateValidite().isBefore(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "La date de validité est dépassée");
        }

        d.setTokenReponse(JetonReponse.generer());
        byte[] pdf = generateurPdf.generer(d);
        try {
            envoiEmail.envoyer(d.getClientEmail(), "Votre devis " + d.getReference(), texteEmail(d), pdf);
        } catch (EmailException e) {
            // Le devis reste BROUILLON (la transaction est annulée) ; le commercial corrige l'email et recommence
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Envoi impossible : " + e.getMessage());
        }

        d.setStatut(StatutDevis.ENVOYE);
        Document trace = new Document();
        trace.setDevis(d);
        trace.setContenu(pdf);
        trace.setGenereLe(Instant.now(clock));
        trace.setEnvoyeLe(Instant.now(clock));
        trace.setDestinataire(d.getClientEmail());
        documents.save(trace);
        return DevisReponse.de(d);
    }

    @Transactional(readOnly = true)
    public FichierPdf pdf(UUID id) {
        Devis d = trouver(id);
        return new FichierPdf(d.getReference() + ".pdf", generateurPdf.generer(d));
    }

    private Devis trouver(UUID id) {
        return devis.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Devis introuvable"));
    }

    private String texteEmail(Devis d) {
        String lien = interfaceUrl + "/devis/reponse/" + d.getTokenReponse();
        return "Bonjour " + d.getClientNom() + ",\n\n"
                + "Votre devis " + d.getReference() + " est joint à ce message (valable jusqu'au "
                + d.getDateValidite() + ").\n\n"
                + "Accepter : " + lien + "?choix=accepter\n"
                + "Refuser : " + lien + "?choix=refuser\n";
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/EnvoiEmail.java`

*UNIQUE point d'envoi d'email : simulé (écrit dans les logs).*

```java
package com.example.quotation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * UNIQUE point d'envoi d'email du service. Aujourd'hui l'envoi est SIMULÉ (écrit dans les logs).
 * Quand le service Notification existera, il suffira de remplacer le corps de envoyer().
 */
@Component
public class EnvoiEmail {

    private static final Logger LOG = LoggerFactory.getLogger(EnvoiEmail.class);

    private final boolean echecSimule;

    public EnvoiEmail(@Value("${app.email.echec-simule:false}") boolean echecSimule) {
        this.echecSimule = echecSimule;
    }

    public void envoyer(String destinataire, String sujet, String texte, byte[] pieceJointe) {
        if (echecSimule) {
            throw new EmailException("Boîte du destinataire indisponible (échec simulé)");
        }
        LOG.info("[EMAIL SIMULÉ] à={} sujet={} pièceJointe={} octets\n{}",
                destinataire, sujet, pieceJointe == null ? 0 : pieceJointe.length, texte);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/ExpirationDevisTache.java`

*Tâche quotidienne (1 h du matin) qui appelle l'expiration.*

```java
package com.example.quotation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tâche quotidienne (1 h du matin) : expire les devis dont la date de validité est passée. */
@Component
public class ExpirationDevisTache {

    private static final Logger LOG = LoggerFactory.getLogger(ExpirationDevisTache.class);

    private final ExpirationService service;

    public ExpirationDevisTache(ExpirationService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void executer() {
        int nombre = service.expirerLesDevisEchus();
        LOG.info("Expiration des devis : {} devis expiré(s)", nombre);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/ExpirationService.java`

*Passe à EXPIRE les devis dont la date de validité est passée.*

```java
package com.example.quotation.service;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.repository.DevisRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExpirationService {

    private final DevisRepository devis;
    private final Clock clock;

    public ExpirationService(DevisRepository devis, Clock clock) {
        this.devis = devis;
        this.clock = clock;
    }

    /** Passe à EXPIRE les devis ENVOYE dont la date de validité est strictement passée. Renvoie leur nombre. */
    public int expirerLesDevisEchus() {
        List<Devis> echus = devis.findByStatutAndDateValiditeBefore(StatutDevis.ENVOYE, LocalDate.now(clock));
        echus.forEach(d -> d.setStatut(StatutDevis.EXPIRE));
        return echus.size();
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/FichierPdf.java`

*Nom et contenu d'un PDF.*

```java
package com.example.quotation.service;

public record FichierPdf(String nom, byte[] contenu) {
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/GenerateurPdf.java`

*Fabrique le PDF d'un devis (bibliothèque OpenPDF).*

```java
package com.example.quotation.service;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.LigneDevis;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Component;

/** Fabrique le PDF d'un devis (mise en page simple : le but est de démontrer la génération). */
@Component
public class GenerateurPdf {

    public byte[] generer(Devis d) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        // Le nom complet évite la confusion avec notre entité Document
        com.lowagie.text.Document pdf = new com.lowagie.text.Document();
        try {
            PdfWriter.getInstance(pdf, sortie);
            pdf.open();
            pdf.add(new Paragraph("Devis " + d.getReference()));
            pdf.add(new Paragraph("Client : " + d.getClientNom() + " (" + d.getClientEmail() + ")"));
            pdf.add(new Paragraph("Date : " + d.getDate() + " - valable jusqu'au " + d.getDateValidite()));
            pdf.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(5);
            for (String titre : new String[]{"Désignation", "Prix unitaire", "Quantité", "Remise %", "Total HT"}) {
                table.addCell(titre);
            }
            for (LigneDevis l : d.getLignes()) {
                table.addCell(l.getDesignation());
                table.addCell(l.getPrixUnitaire().toPlainString());
                table.addCell(String.valueOf(l.getQuantite()));
                table.addCell(l.getRemise().toPlainString());
                table.addCell(l.getTotalLigneHT().toPlainString());
            }
            pdf.add(table);

            pdf.add(new Paragraph(" "));
            pdf.add(new Paragraph("Remise globale : " + d.getRemiseGlobale().toPlainString() + " %"));
            pdf.add(new Paragraph("Total HT : " + d.getTotalHT().toPlainString()));
            pdf.add(new Paragraph("TVA : " + d.getTotalTVA().toPlainString()));
            pdf.add(new Paragraph("Total TTC : " + d.getTotalTTC().toPlainString()));
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du PDF impossible", e);
        } finally {
            pdf.close();
        }
        return sortie.toByteArray();
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/JetonReponse.java`

*Fabrique le jeton aléatoire du lien envoyé au client.*

```java
package com.example.quotation.service;

import java.security.SecureRandom;
import java.util.Base64;

/** Jeton du lien envoyé au client : 32 octets aléatoires, impossibles à deviner. */
public final class JetonReponse {

    private static final SecureRandom ALEA = new SecureRandom();

    private JetonReponse() {
    }

    public static String generer() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/service/ReponsePubliqueService.java`

*Réponse du client par son lien : consulter, accepter, refuser.*

```java
package com.example.quotation.service;

import com.example.quotation.dto.DevisPublic;
import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.exception.ApiException;
import com.example.quotation.messaging.DevisAccepteEvent;
import com.example.quotation.messaging.EditeurEvenements;
import com.example.quotation.repository.DevisRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Réponse du client par son lien : aucune identité, le jeton fait foi. */
@Service
@Transactional
public class ReponsePubliqueService {

    private final DevisRepository devis;
    private final EditeurEvenements evenements;
    private final Clock clock;

    public ReponsePubliqueService(DevisRepository devis, EditeurEvenements evenements, Clock clock) {
        this.devis = devis;
        this.evenements = evenements;
        this.clock = clock;
    }

    /** Renvoie toujours le devis avec son statut ; ne modifie rien. */
    @Transactional(readOnly = true)
    public DevisPublic consulter(String token) {
        return DevisPublic.de(trouver(token));
    }

    public DevisPublic accepter(String token) {
        Devis d = trouver(token);
        verifierQuOnPeutRepondre(d);
        d.setStatut(StatutDevis.ACCEPTE);
        d.setRepondueLe(Instant.now(clock));
        // L'événement n'est publié que si le devis vient d'une campagne
        if (d.getCampagneId() != null) {
            evenements.devisAccepte(new DevisAccepteEvent(UUID.randomUUID(), d.getId(), d.getReference(),
                    d.getClientId(), d.getCampagneId(), d.getTotalHT(), d.getTotalTTC(), d.getRepondueLe()));
        }
        return DevisPublic.de(d);
    }

    public DevisPublic refuser(String token) {
        Devis d = trouver(token);
        verifierQuOnPeutRepondre(d);
        d.setStatut(StatutDevis.REFUSE);
        d.setRepondueLe(Instant.now(clock));
        return DevisPublic.de(d);
    }

    private Devis trouver(String token) {
        return devis.findByTokenReponse(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lien invalide"));
    }

    /** Répondre n'est possible que sur un devis ENVOYE, jusqu'à la fin du jour de sa date de validité. */
    private void verifierQuOnPeutRepondre(Devis d) {
        boolean dateDepassee = LocalDate.now(clock).isAfter(d.getDateValidite());
        if (d.getStatut() != StatutDevis.ENVOYE || dateDepassee) {
            throw new ApiException(HttpStatus.CONFLICT, "Ce devis ne peut plus être accepté");
        }
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/controller/EnvoiController.java`

*Routes `POST /devis/{id}/envoyer` et `GET /devis/{id}/pdf`.*

```java
package com.example.quotation.controller;

import com.example.quotation.dto.DevisReponse;
import com.example.quotation.service.EnvoiDevisService;
import com.example.quotation.service.FichierPdf;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devis")
public class EnvoiController {

    private final EnvoiDevisService service;

    public EnvoiController(EnvoiDevisService service) {
        this.service = service;
    }

    @PostMapping("/{id}/envoyer")
    public DevisReponse envoyer(@PathVariable UUID id) {
        return service.envoyer(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        FichierPdf fichier = service.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fichier.nom() + "\"")
                .body(fichier.contenu());
    }
}
```

#### `code/quotation-service/src/main/java/com/example/quotation/controller/PublicController.java`

*Routes publiques `/public/reponse/{token}` (sans identité).*

```java
package com.example.quotation.controller;

import com.example.quotation.dto.DevisPublic;
import com.example.quotation.service.ReponsePubliqueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Routes sans identité : le client répond avec le jeton de son lien. */
@RestController
@RequestMapping("/public/reponse")
public class PublicController {

    private final ReponsePubliqueService service;

    public PublicController(ReponsePubliqueService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    public DevisPublic consulter(@PathVariable String token) {
        return service.consulter(token);
    }

    @PostMapping("/{token}/accepter")
    public DevisPublic accepter(@PathVariable String token) {
        return service.accepter(token);
    }

    @PostMapping("/{token}/refuser")
    public DevisPublic refuser(@PathVariable String token) {
        return service.refuser(token);
    }
}
```

#### `code/quotation-service/src/test/java/com/example/quotation/EnvoiEtReponseApiTests.java`

*Tests de l'envoi, du PDF, de la réponse publique et de l'expiration.*

```java
package com.example.quotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.quotation.client.ClientCustomer;
import com.example.quotation.client.CustomerClient;
import com.example.quotation.entity.Devis;
import com.example.quotation.entity.Produit;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.entity.Taxe;
import com.example.quotation.messaging.DevisAccepteEvent;
import com.example.quotation.messaging.EditeurEvenements;
import com.example.quotation.repository.DevisRepository;
import com.example.quotation.repository.ProduitRepository;
import com.example.quotation.repository.TaxeRepository;
import com.example.quotation.service.EmailException;
import com.example.quotation.service.EnvoiEmail;
import com.example.quotation.service.ExpirationService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EnvoiEtReponseApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TaxeRepository taxes;
    @Autowired
    ProduitRepository produits;
    @Autowired
    DevisRepository devisRepo;
    @Autowired
    ExpirationService expiration;

    // Les services voisins et l'envoi d'email sont SIMULÉS dans les tests
    @MockBean
    CustomerClient customer;
    @MockBean
    EnvoiEmail email;
    @MockBean
    EditeurEvenements evenements;

    final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void clientParDefaut() {
        when(customer.trouver(any())).thenReturn(
                new ClientCustomer(clientId, "Société Atlas", null, "contact@atlas.tn", "CLIENT"));
    }

    /** Crée un devis avec une ligne ; campagneId peut être null. */
    private String creerDevis(UUID campagneId) throws Exception {
        Taxe t = new Taxe();
        t.setTauxTVA(new BigDecimal("19"));
        taxes.save(t);
        Produit p = new Produit();
        p.setNom("Étude");
        p.setPrix(new BigDecimal("1000.000"));
        p.setTaxe(t);
        produits.save(p);

        String corps = "{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + LocalDate.now() + "\""
                + (campagneId == null ? "" : ",\"campagneId\":\"" + campagneId + "\"") + "}";
        String json = mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":1}"))
                .andExpect(status().isCreated());
        return id;
    }

    private String envoyer(String id) throws Exception {
        mvc.perform(post("/devis/" + id + "/envoyer")).andExpect(status().isOk());
        return devisRepo.findById(UUID.fromString(id)).orElseThrow().getTokenReponse();
    }

    @Test
    void envoyerPasseLeDevisAEnvoyeSansExposerLeJeton() throws Exception {
        String id = creerDevis(null);
        mvc.perform(post("/devis/" + id + "/envoyer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ENVOYE"))
                .andExpect(jsonPath("$.tokenReponse").doesNotExist());
        verify(email).envoyer(any(), any(), any(), any());
        assertThat(devisRepo.findById(UUID.fromString(id)).orElseThrow().getTokenReponse()).isNotBlank();
    }

    @Test
    void envoyerUnDevisDejaEnvoyeEst409() throws Exception {
        String id = creerDevis(null);
        envoyer(id);
        mvc.perform(post("/devis/" + id + "/envoyer")).andExpect(status().isConflict());
    }

    @Test
    void emailInvalideOuDateDepasseeEst422() throws Exception {
        Devis mauvaisEmail = new Devis();
        mauvaisEmail.setReference("DEV-TEST-0001");
        mauvaisEmail.setDate(LocalDate.now());
        mauvaisEmail.setDateValidite(LocalDate.now().plusDays(5));
        mauvaisEmail.setClientEmail("pas-un-email");
        devisRepo.save(mauvaisEmail);
        mvc.perform(post("/devis/" + mauvaisEmail.getId() + "/envoyer")).andExpect(status().isUnprocessableEntity());

        Devis perime = new Devis();
        perime.setReference("DEV-TEST-0002");
        perime.setDate(LocalDate.now().minusDays(10));
        perime.setDateValidite(LocalDate.now().minusDays(1));
        perime.setClientEmail("contact@atlas.tn");
        devisRepo.save(perime);
        mvc.perform(post("/devis/" + perime.getId() + "/envoyer")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void echecDEnvoiDonne503EtLeDevisResteBrouillon() throws Exception {
        String id = creerDevis(null);
        doThrow(new EmailException("Boîte pleine")).when(email).envoyer(any(), any(), any(), any());
        mvc.perform(post("/devis/" + id + "/envoyer"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Boîte pleine")));
        mvc.perform(get("/devis/" + id)).andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    void lePdfSeTelecharge() throws Exception {
        String id = creerDevis(null);
        byte[] pdf = mvc.perform(get("/devis/" + id + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void leClientConsulteSonDevisSansIdentite() throws Exception {
        String id = creerDevis(null);
        String token = envoyer(id);
        mvc.perform(get("/public/reponse/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ENVOYE"))
                .andExpect(jsonPath("$.clientNom").value("Société Atlas"))
                .andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.tokenReponse").doesNotExist());
        mvc.perform(get("/public/reponse/jeton-inconnu")).andExpect(status().isNotFound());
    }

    @Test
    void accepterUneSeuleFoisEtPublierLEvenementSiCampagne() throws Exception {
        UUID campagneId = UUID.randomUUID();
        String id = creerDevis(campagneId);
        String token = envoyer(id);

        mvc.perform(post("/public/reponse/" + token + "/accepter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTE"));

        ArgumentCaptor<DevisAccepteEvent> capture = ArgumentCaptor.forClass(DevisAccepteEvent.class);
        verify(evenements, times(1)).devisAccepte(capture.capture());
        DevisAccepteEvent e = capture.getValue();
        assertThat(e.campagneId()).isEqualTo(campagneId);
        assertThat(e.clientId()).isEqualTo(clientId);
        assertThat(e.montantHT()).isEqualByComparingTo("1000.000");
        assertThat(e.montantTTC()).isEqualByComparingTo("1190.000");

        mvc.perform(post("/public/reponse/" + token + "/accepter"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ce devis ne peut plus être accepté"));
        mvc.perform(post("/public/reponse/" + token + "/refuser")).andExpect(status().isConflict());
        verify(evenements, times(1)).devisAccepte(any());
    }

    @Test
    void aucunEvenementSiLeDevisNaPasDeCampagne() throws Exception {
        String token = envoyer(creerDevis(null));
        mvc.perform(post("/public/reponse/" + token + "/accepter")).andExpect(status().isOk());
        verify(evenements, never()).devisAccepte(any());
    }

    @Test
    void refuserPuisPlusRien() throws Exception {
        String token = envoyer(creerDevis(UUID.randomUUID()));
        mvc.perform(post("/public/reponse/" + token + "/refuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSE"));
        mvc.perform(post("/public/reponse/" + token + "/accepter")).andExpect(status().isConflict());
        verify(evenements, never()).devisAccepte(any());
    }

    @Test
    void lendemainDeLaDateDeValiditeLeLienNeMarchePlusEtLeDevisExpire() throws Exception {
        String id = creerDevis(null);   // dateValidite = aujourd'hui : le jour même, le lien fonctionne encore
        String token = envoyer(id);
        Devis d = devisRepo.findById(UUID.fromString(id)).orElseThrow();
        d.setDateValidite(LocalDate.now().minusDays(1));   // on simule « le lendemain »

        mvc.perform(post("/public/reponse/" + token + "/accepter"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ce devis ne peut plus être accepté"));

        assertThat(expiration.expirerLesDevisEchus()).isEqualTo(1);
        assertThat(d.getStatut()).isEqualTo(StatutDevis.EXPIRE);
    }

    @Test
    void laTacheDExpirationNeTouchePasLesBrouillons() throws Exception {
        String id = creerDevis(null);
        Devis d = devisRepo.findById(UUID.fromString(id)).orElseThrow();
        d.setDateValidite(LocalDate.now().minusDays(1));
        assertThat(expiration.expirerLesDevisEchus()).isZero();
        assertThat(d.getStatut()).isEqualTo(StatutDevis.BROUILLON);
    }
}
```


### 7.3 Les routes de l'étape S3


##### `POST /devis/{id}/envoyer`

Génère le PDF, crée le jeton, envoie l'email (simulé) avec les liens Accepter et Refuser, et passe le devis à `ENVOYE`. Le jeton n'est **jamais** dans la réponse.

Réponse **200** :

```json
le devis, statut `ENVOYE`
```

Erreurs : 404 ; **409** devis non BROUILLON ; **422** email du client invalide ou date de validité dépassée ; **503** échec d'envoi (le devis reste BROUILLON, la cause est dans `message`).

##### `GET /devis/{id}/pdf`

Télécharge le PDF du devis (`Content-Type: application/pdf`).

Réponse **200** :

(aucun corps)

Erreurs : 404.

##### `GET /public/reponse/{token}`

**Sans identité.** Le client ouvre son lien : renvoie toujours le devis avec son statut, sans rien modifier.

Réponse **200** :

```json
{
  "reference": "DEV-2026-0001",
  "date": "2026-11-18",
  "dateValidite": "2026-12-31",
  "statut": "ENVOYE",
  "clientNom": "Société Atlas",
  "lignes": [ { ...ligne... } ],
  "totalHT": 2250.900,
  "totalTVA": 427.671,
  "totalTTC": 2678.571
}
```

Erreurs : 404 jeton inconnu.

##### `POST /public/reponse/{token}/accepter`

**Sans identité.** Le client accepte. Statut `ACCEPTE` ; si le devis a un `campagneId`, l'événement `DevisAccepte` est publié (simulé jusqu'à E1).

Réponse **200** :

```json
le devis public, statut `ACCEPTE`
```

Erreurs : 404 jeton inconnu ; **409** « Ce devis ne peut plus être accepté » (déjà traité, annulé, expiré, ou le jour de `dateValidite` est passé).

##### `POST /public/reponse/{token}/refuser`

**Sans identité.** Le client refuse. Statut `REFUSE`, aucun événement.

Réponse **200** :

```json
le devis public, statut `REFUSE`
```

Erreurs : 404 ; **409** « Ce devis ne peut plus être accepté » (mêmes cas).

### 7.4 Vérification

```
cd code/quotation-service
mvn clean test
```

Attendu : `Tests run: 35, Failures: 0, Errors: 0` et `BUILD SUCCESS`.

Essai à la main (Swagger, avec un devis BROUILLON créé en S2) :
1. `POST /devis/{id}/envoyer` : 200. Dans la console du service apparaît l'email simulé, avec deux liens qui se terminent par `/devis/reponse/<jeton>?choix=accepter` et `?choix=refuser`. **Copier ce jeton.**
2. `GET /public/reponse/<jeton>` : 200, statut `ENVOYE`.
3. `POST /public/reponse/<jeton>/accepter` : 200, statut `ACCEPTE` ; refaire : **409 « Ce devis ne peut plus être accepté »**.
4. `GET /devis/{id}/pdf` : un PDF s'ouvre.
5. Si le devis a un `campagneId`, la console affiche une ligne `[ÉVÉNEMENT SIMULÉ] DevisAccepte ...` (le vrai envoi vers RabbitMQ vient à l'étape E1).
6. Pour simuler une panne d'envoi : mettre `app.email.echec-simule: true` dans `application.yml` et relancer : `POST /devis/{id}/envoyer` répond **503** et le devis reste BROUILLON.

## 8. Après S3

Quotation est terminé, sauf le branchement réel de l'événement `DevisAccepte` sur RabbitMQ (étape E1, avec le trinôme : remplacer `EditeurEvenementsLog` par une version qui envoie le message, sans toucher à `ReponsePubliqueService`).
