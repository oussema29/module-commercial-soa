# Guide de code : Customer (Baha Farhati)

Ce guide permet de réaliser les étapes **B1** et **B2** sans erreur et sans avoir à réfléchir : il donne l'arborescence, le **code complet de chaque fichier**, les routes avec leurs corps de requête et de réponse, le flux, les tests et les commandes de vérification.

> **Le code ci-dessous a été compilé et testé** (Java 21, Spring Boot 3.3.5) : après l'étape B1, `mvn clean test` donne **17 tests au vert** ; après B2, **24 tests au vert**. Les tests tournent sur une base H2 en mémoire (mode MySQL) ; l'essai avec la vraie base MySQL se fait avec `docker compose up -d` (voir « Comment valider » de chaque fiche).

## Objectif, en bref

**À la fin de B1 et B2, le service Customer fonctionne** : on peut créer des clients et des catégories, rechercher, changer le statut d'un client (prospect, client, inactif), ajouter des contacts et des interactions (appels, emails, réunions). Deux de ses routes sont lues par les autres services : Quotation vérifie un client avant de faire un devis, Campaign demande la liste des clients d'une catégorie pour leur écrire.

**Comment lire ce guide sans se perdre**
- Lire **les sections 2, 3 et 4** (arborescence, flux, erreurs) : c'est la compréhension, 5 minutes.
- Pour chaque étape : lire le tableau « Fichiers à créer » et les **routes** (section « Les routes de l'étape »).
- Les **blocs de code** sont à **copier tels quels** dans les fichiers : inutile de les lire ligne par ligne. Les lire seulement pour comprendre comment une règle est codée.
- Finir par la section « Vérification » : `mvn clean test`, puis l'essai dans Swagger.

## 1. Comment utiliser ce guide

1. Avoir terminé l'étape **B0** (le squelette `customer-service` démarre et `mvn clean test` est vert).
2. Pour chaque fichier ci-dessous : créer le fichier **au chemin indiqué** (en partant de la racine du dépôt) et y coller le code **tel quel**. Les noms de paquets sont déjà corrects.
3. Quand tous les fichiers d'une étape sont créés : `cd code/customer-service` puis `mvn clean test`. Résultat attendu : `BUILD SUCCESS`.
4. Essayer les routes avec Swagger : démarrer `mvn spring-boot:run` puis ouvrir <http://localhost:8081/swagger-ui.html>.
5. Cocher les tâches de la fiche (`B1_...md`, `B2_...md`), faire valider par le trinôme, puis commit et push (voir `../README.md`).

## 2. Arborescence finale du service

```
code/customer-service/
├── pom.xml                                      (déjà fait en B0)
└── src/
    ├── main/
    │   ├── java/com/example/customer/
    │   │   ├── CustomerServiceApplication.java  (B0)
    │   │   ├── exception/                       (B0 : ApiException, ErrorResponse, GlobalExceptionHandler)
    │   │   ├── entity/       B1 : TypeClient, StatutClient, CategorieClient, Client
    │   │   │                 B2 : TypeInteraction, Contact, Interaction
    │   │   ├── repository/   B1 : CategorieRepository, ClientRepository
    │   │   │                 B2 : ContactRepository, InteractionRepository
    │   │   ├── dto/          B1 : PageResponse, CategorieSaisie, CategorieReponse, ClientCreation, ClientModification,
    │   │   │                      ChangementStatut, ClientReponse, Destinataire
    │   │   │                 B2 : ContactSaisie, ContactReponse, InteractionSaisie, InteractionReponse
    │   │   ├── service/      B1 : CategorieService, ClientService
    │   │   │                 B2 : ContactService, InteractionService
    │   │   └── controller/   B1 : CategorieController, ClientController
    │   │                     B2 : ContactController, InteractionController
    │   └── resources/application.yml            (B0, une ligne à ajouter en B2)
    └── test/
        ├── java/com/example/customer/           B0 : CustomerServiceApplicationTests
        │                                        B1 : CategorieApiTests, ClientApiTests
        │                                        B2 : ContactApiTests, InteractionApiTests
        └── resources/application-test.yml       (B0)
```


## 3. Le flux : comment une requête traverse le service

Toute route suit le même chemin, de gauche à droite :

```
Requête HTTP --> Controller --> Service --> Repository --> Base MySQL (customer_db)
                  (route,        (règles      (requêtes
                  validation)     métier)      SQL)
Réponse HTTP <-- Controller <-- Service <-- Repository
```

**Exemple : `POST /clients`**
1. `ClientController.creer` reçoit le JSON, le convertit en `ClientCreation` et le valide (`@Valid`). Un champ invalide donne **422** (géré par `GlobalExceptionHandler`).
2. `ClientService.creer` met l'email en minuscules sans espaces et regarde dans la base si un client l'utilise déjà : si oui, **409**.
3. S'il y a un `categorieId`, le service vérifie que la catégorie existe : sinon **404**.
4. Le service crée le `Client` (statut `PROSPECT` par défaut) et le fait enregistrer par `ClientRepository`.
5. Le contrôleur répond **201** avec un `ClientReponse`.

**Qui appelle Customer** (les autres services lisent, n'écrivent jamais) :

| Appelant | Route lue | Quand |
|---|---|---|
| Quotation (étape S2) | `GET /clients/{id}` | À la création d'un devis : vérifier le client et copier son nom et son email |
| Campaign (étape A3) | `GET /clients/destinataires?categorieId=...` | Au lancement d'une campagne : trouver les clients à qui écrire |

Les deux routes **ne doivent rien exposer de plus** que ce qui est prévu au contrat. Customer **reçoit** aussi l'événement `ProspectGenere` (étape E1, plus tard), qui crée un client `PROSPECT`.


## 4. Format commun des erreurs

Toute erreur a ce corps (c'est `GlobalExceptionHandler`, déjà fait en B0, qui le produit) :

```json
{ "code": 409, "message": "Un client avec cet email existe déjà", "timestamp": "2026-11-18T14:30:00Z" }
```

| Code | Quand |
|---|---|
| 400 | JSON illisible, identifiant mal formé dans l'URL, valeur d'énumération inconnue |
| 404 | Client, catégorie, contact ou interaction introuvable |
| 409 | Email déjà utilisé ; catégorie encore utilisée |
| 422 | Champ obligatoire absent ou invalide |

## 5. Étape B1 : catégories et clients

### 5.1 Fichiers à créer (20)

| # | Fichier | Rôle |
|---|---|---|
| 1 | `code/customer-service/src/main/java/com/example/customer/entity/CategorieClient.java` | La table des catégories de clients. |
| 2 | `code/customer-service/src/main/java/com/example/customer/entity/Client.java` | La table des clients et prospects. `emailNormalise` (email en minuscules, sans espaces) porte la contrainte d'unicité. |
| 3 | `code/customer-service/src/main/java/com/example/customer/entity/StatutClient.java` | Les trois statuts d'un client. |
| 4 | `code/customer-service/src/main/java/com/example/customer/entity/TypeClient.java` | Les deux types de client. |
| 5 | `code/customer-service/src/main/java/com/example/customer/repository/CategorieRepository.java` | Accès à la table des catégories (méthodes fournies par Spring Data). |
| 6 | `code/customer-service/src/main/java/com/example/customer/repository/ClientRepository.java` | Accès à la table des clients : contrôle d'unicité de l'email, catégorie utilisée, destinataires. |
| 7 | `code/customer-service/src/main/java/com/example/customer/dto/CategorieReponse.java` | Catégorie renvoyée par l'API. |
| 8 | `code/customer-service/src/main/java/com/example/customer/dto/CategorieSaisie.java` | Corps reçu pour créer ou modifier une catégorie. |
| 9 | `code/customer-service/src/main/java/com/example/customer/dto/ChangementStatut.java` | Corps de `PATCH /clients/{id}/statut`. |
| 10 | `code/customer-service/src/main/java/com/example/customer/dto/ClientCreation.java` | Corps de `POST /clients`. L'email est nettoyé (espaces enlevés) avant la validation. |
| 11 | `code/customer-service/src/main/java/com/example/customer/dto/ClientModification.java` | Corps de `PUT /clients/{id}` (sans le statut). |
| 12 | `code/customer-service/src/main/java/com/example/customer/dto/ClientReponse.java` | Client renvoyé par l'API (sans `emailNormalise`). |
| 13 | `code/customer-service/src/main/java/com/example/customer/dto/Destinataire.java` | Liste réduite lue par Campaign : seulement `clientId`, `nom`, `email`. |
| 14 | `code/customer-service/src/main/java/com/example/customer/dto/PageResponse.java` | Format commun des listes paginées `{contenu, page, taille, total}`. |
| 15 | `code/customer-service/src/main/java/com/example/customer/service/CategorieService.java` | Règles des catégories (suppression refusée si utilisée). |
| 16 | `code/customer-service/src/main/java/com/example/customer/service/ClientService.java` | Règles des clients : email unique, statut, recherche, destinataires. |
| 17 | `code/customer-service/src/main/java/com/example/customer/controller/CategorieController.java` | Routes `/categories`. |
| 18 | `code/customer-service/src/main/java/com/example/customer/controller/ClientController.java` | Routes `/clients`. |
| 19 | `code/customer-service/src/test/java/com/example/customer/CategorieApiTests.java` | Tests des routes `/categories` (MockMvc, base H2). |
| 20 | `code/customer-service/src/test/java/com/example/customer/ClientApiTests.java` | Tests des routes `/clients` : email unique, statut, recherche, destinataires. |

### 5.2 Code


#### `code/customer-service/src/main/java/com/example/customer/entity/CategorieClient.java`

*La table des catégories de clients.*

```java
package com.example.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class CategorieClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String nom;

    private String description;

    public UUID getId() { return id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
```

#### `code/customer-service/src/main/java/com/example/customer/entity/Client.java`

*La table des clients et prospects. `emailNormalise` (email en minuscules, sans espaces) porte la contrainte d'unicité.*

```java
package com.example.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Client {

    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeClient type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutClient statut = StatutClient.PROSPECT;

    @Column(nullable = false)
    private String nom;

    private String prenom;

    // Email tel que saisi
    @Column(nullable = false)
    private String email;

    // Email sans espaces et en minuscules : sert à garantir l'unicité (jamais exposé dans l'API)
    @Column(nullable = false, unique = true)
    private String emailNormalise;

    private String telephone;
    private String adresse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private CategorieClient categorie;

    // Simple référence à une campagne (autre service) : pas de clé étrangère
    private UUID campagneSourceId;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void avantCreation() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void avantModification() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public TypeClient getType() { return type; }
    public void setType(TypeClient type) { this.type = type; }

    public StatutClient getStatut() { return statut; }
    public void setStatut(StatutClient statut) { this.statut = statut; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getEmailNormalise() { return emailNormalise; }
    public void setEmailNormalise(String emailNormalise) { this.emailNormalise = emailNormalise; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public CategorieClient getCategorie() { return categorie; }
    public void setCategorie(CategorieClient categorie) { this.categorie = categorie; }

    public UUID getCampagneSourceId() { return campagneSourceId; }
    public void setCampagneSourceId(UUID campagneSourceId) { this.campagneSourceId = campagneSourceId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

#### `code/customer-service/src/main/java/com/example/customer/entity/StatutClient.java`

*Les trois statuts d'un client.*

```java
package com.example.customer.entity;

public enum StatutClient {
    PROSPECT, CLIENT, INACTIF
}
```

#### `code/customer-service/src/main/java/com/example/customer/entity/TypeClient.java`

*Les deux types de client.*

```java
package com.example.customer.entity;

public enum TypeClient {
    PARTICULIER, ENTREPRISE
}
```

#### `code/customer-service/src/main/java/com/example/customer/repository/CategorieRepository.java`

*Accès à la table des catégories (méthodes fournies par Spring Data).*

```java
package com.example.customer.repository;

import com.example.customer.entity.CategorieClient;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorieRepository extends JpaRepository<CategorieClient, UUID> {
}
```

#### `code/customer-service/src/main/java/com/example/customer/repository/ClientRepository.java`

*Accès à la table des clients : contrôle d'unicité de l'email, catégorie utilisée, destinataires.*

```java
package com.example.customer.repository;

import com.example.customer.entity.Client;
import com.example.customer.entity.StatutClient;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    boolean existsByEmailNormalise(String emailNormalise);

    boolean existsByEmailNormaliseAndIdNot(String emailNormalise, UUID id);

    // Spring Data lit "CategorieId" comme categorie.id
    boolean existsByCategorieId(UUID categorieId);

    Page<Client> findByCategorieIdAndStatut(UUID categorieId, StatutClient statut, Pageable pageable);
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/CategorieReponse.java`

*Catégorie renvoyée par l'API.*

```java
package com.example.customer.dto;

import com.example.customer.entity.CategorieClient;
import java.util.UUID;

public record CategorieReponse(UUID id, String nom, String description) {

    public static CategorieReponse de(CategorieClient c) {
        return new CategorieReponse(c.getId(), c.getNom(), c.getDescription());
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/CategorieSaisie.java`

*Corps reçu pour créer ou modifier une catégorie.*

```java
package com.example.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record CategorieSaisie(@NotBlank String nom, String description) {
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/ChangementStatut.java`

*Corps de `PATCH /clients/{id}/statut`.*

```java
package com.example.customer.dto;

import com.example.customer.entity.StatutClient;
import jakarta.validation.constraints.NotNull;

public record ChangementStatut(@NotNull StatutClient statut) {
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/ClientCreation.java`

*Corps de `POST /clients`. L'email est nettoyé (espaces enlevés) avant la validation.*

```java
package com.example.customer.dto;

import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Corps de POST /clients. Le statut est facultatif (PROSPECT par défaut ; CLIENT accepté). */
public record ClientCreation(
        @NotNull TypeClient type,
        StatutClient statut,
        @NotBlank String nom,
        String prenom,
        @NotBlank @Email String email,
        String telephone,
        String adresse,
        UUID categorieId) {

    // Les espaces autour de l'email sont enlevés avant la validation
    public ClientCreation {
        if (email != null) {
            email = email.trim();
        }
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/ClientModification.java`

*Corps de `PUT /clients/{id}` (sans le statut).*

```java
package com.example.customer.dto;

import com.example.customer.entity.TypeClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Corps de PUT /clients/{id}. Ne contient pas le statut (route dédiée). */
public record ClientModification(
        @NotNull TypeClient type,
        @NotBlank String nom,
        String prenom,
        @NotBlank @Email String email,
        String telephone,
        String adresse,
        UUID categorieId) {

    // Les espaces autour de l'email sont enlevés avant la validation
    public ClientModification {
        if (email != null) {
            email = email.trim();
        }
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/ClientReponse.java`

*Client renvoyé par l'API (sans `emailNormalise`).*

```java
package com.example.customer.dto;

import com.example.customer.entity.Client;
import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import java.time.Instant;
import java.util.UUID;

public record ClientReponse(
        UUID id,
        TypeClient type,
        StatutClient statut,
        String nom,
        String prenom,
        String email,
        String telephone,
        String adresse,
        UUID categorieId,
        UUID campagneSourceId,
        Instant createdAt,
        Instant updatedAt) {

    public static ClientReponse de(Client c) {
        return new ClientReponse(c.getId(), c.getType(), c.getStatut(), c.getNom(), c.getPrenom(), c.getEmail(),
                c.getTelephone(), c.getAdresse(),
                c.getCategorie() == null ? null : c.getCategorie().getId(),
                c.getCampagneSourceId(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/Destinataire.java`

*Liste réduite lue par Campaign : seulement `clientId`, `nom`, `email`.*

```java
package com.example.customer.dto;

import com.example.customer.entity.Client;
import java.util.UUID;

/** Liste réduite lue par Campaign : rien d'autre que l'identifiant, le nom et l'email. */
public record Destinataire(UUID clientId, String nom, String email) {

    public static Destinataire de(Client c) {
        return new Destinataire(c.getId(), c.getNom(), c.getEmail());
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/PageResponse.java`

*Format commun des listes paginées `{contenu, page, taille, total}`.*

```java
package com.example.customer.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Réponse paginée du contrat : { contenu, page, taille, total }. */
public record PageResponse<T>(List<T> contenu, int page, int taille, long total) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/service/CategorieService.java`

*Règles des catégories (suppression refusée si utilisée).*

```java
package com.example.customer.service;

import com.example.customer.dto.CategorieReponse;
import com.example.customer.dto.CategorieSaisie;
import com.example.customer.entity.CategorieClient;
import com.example.customer.exception.ApiException;
import com.example.customer.repository.CategorieRepository;
import com.example.customer.repository.ClientRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategorieService {

    private final CategorieRepository categories;
    private final ClientRepository clients;

    public CategorieService(CategorieRepository categories, ClientRepository clients) {
        this.categories = categories;
        this.clients = clients;
    }

    public CategorieReponse creer(CategorieSaisie saisie) {
        CategorieClient c = new CategorieClient();
        c.setNom(saisie.nom());
        c.setDescription(saisie.description());
        return CategorieReponse.de(categories.save(c));
    }

    @Transactional(readOnly = true)
    public List<CategorieReponse> lister() {
        return categories.findAll().stream().map(CategorieReponse::de).toList();
    }

    public CategorieReponse modifier(UUID id, CategorieSaisie saisie) {
        CategorieClient c = trouver(id);
        c.setNom(saisie.nom());
        c.setDescription(saisie.description());
        return CategorieReponse.de(c);
    }

    public void supprimer(UUID id) {
        CategorieClient c = trouver(id);
        if (clients.existsByCategorieId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Cette catégorie est utilisée par des clients");
        }
        categories.delete(c);
    }

    private CategorieClient trouver(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Catégorie introuvable"));
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/service/ClientService.java`

*Règles des clients : email unique, statut, recherche, destinataires.*

```java
package com.example.customer.service;

import com.example.customer.dto.ChangementStatut;
import com.example.customer.dto.ClientCreation;
import com.example.customer.dto.ClientModification;
import com.example.customer.dto.ClientReponse;
import com.example.customer.dto.Destinataire;
import com.example.customer.dto.PageResponse;
import com.example.customer.entity.CategorieClient;
import com.example.customer.entity.Client;
import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import com.example.customer.exception.ApiException;
import com.example.customer.repository.CategorieRepository;
import com.example.customer.repository.ClientRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService {

    private final ClientRepository clients;
    private final CategorieRepository categories;

    public ClientService(ClientRepository clients, CategorieRepository categories) {
        this.clients = clients;
        this.categories = categories;
    }

    public ClientReponse creer(ClientCreation saisie) {
        String emailNormalise = normaliser(saisie.email());
        if (clients.existsByEmailNormalise(emailNormalise)) {
            throw emailDejaUtilise();
        }
        Client c = new Client();
        c.setType(saisie.type());
        c.setStatut(saisie.statut() == null ? StatutClient.PROSPECT : saisie.statut());
        c.setNom(saisie.nom());
        c.setPrenom(saisie.prenom());
        c.setEmail(saisie.email().trim());
        c.setEmailNormalise(emailNormalise);
        c.setTelephone(saisie.telephone());
        c.setAdresse(saisie.adresse());
        c.setCategorie(trouverCategorie(saisie.categorieId()));
        return ClientReponse.de(clients.save(c));
    }

    @Transactional(readOnly = true)
    public ClientReponse consulter(UUID id) {
        return ClientReponse.de(trouver(id));
    }

    public ClientReponse modifier(UUID id, ClientModification saisie) {
        Client c = trouver(id);
        String emailNormalise = normaliser(saisie.email());
        if (clients.existsByEmailNormaliseAndIdNot(emailNormalise, id)) {
            throw emailDejaUtilise();
        }
        // Le statut et campagneSourceId ne changent jamais ici
        c.setType(saisie.type());
        c.setNom(saisie.nom());
        c.setPrenom(saisie.prenom());
        c.setEmail(saisie.email().trim());
        c.setEmailNormalise(emailNormalise);
        c.setTelephone(saisie.telephone());
        c.setAdresse(saisie.adresse());
        c.setCategorie(trouverCategorie(saisie.categorieId()));
        return ClientReponse.de(c);
    }

    public ClientReponse changerStatut(UUID id, ChangementStatut changement) {
        Client c = trouver(id);
        c.setStatut(changement.statut());
        return ClientReponse.de(c);
    }

    public void supprimer(UUID id) {
        clients.delete(trouver(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientReponse> rechercher(TypeClient type, StatutClient statut, UUID categorieId,
                                                  String q, int page, int taille) {
        Specification<Client> spec = (root, query, cb) -> cb.conjunction();
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (statut != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statut"), statut));
        }
        if (categorieId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categorie").get("id"), categorieId));
        }
        if (q != null && !q.isBlank()) {
            String motif = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nom")), motif),
                    cb.like(cb.lower(cb.coalesce(root.<String>get("prenom"), "")), motif),
                    cb.like(cb.lower(root.get("email")), motif)));
        }
        return PageResponse.of(clients.findAll(spec, pagination(page, taille)).map(ClientReponse::de));
    }

    /** Liste réduite lue par Campaign : seulement les clients de statut CLIENT de la catégorie. */
    @Transactional(readOnly = true)
    public PageResponse<Destinataire> destinataires(UUID categorieId, int page, int taille) {
        if (!categories.existsById(categorieId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Catégorie introuvable");
        }
        return PageResponse.of(clients
                .findByCategorieIdAndStatut(categorieId, StatutClient.CLIENT, pagination(page, taille))
                .map(Destinataire::de));
    }

    // ---- méthodes internes

    Client trouver(UUID id) {
        return clients.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Client introuvable"));
    }

    private CategorieClient trouverCategorie(UUID categorieId) {
        if (categorieId == null) {
            return null;
        }
        return categories.findById(categorieId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Catégorie introuvable"));
    }

    /** L'unicité ignore les majuscules et les espaces autour. */
    static String normaliser(String email) {
        return email.trim().toLowerCase();
    }

    private static ApiException emailDejaUtilise() {
        return new ApiException(HttpStatus.CONFLICT, "Un client avec cet email existe déjà");
    }

    private static Pageable pagination(int page, int taille) {
        int tailleOk = Math.min(Math.max(taille, 1), 100);
        return PageRequest.of(Math.max(page, 0), tailleOk, Sort.by("nom"));
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/controller/CategorieController.java`

*Routes `/categories`.*

```java
package com.example.customer.controller;

import com.example.customer.dto.CategorieReponse;
import com.example.customer.dto.CategorieSaisie;
import com.example.customer.service.CategorieService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categories")
public class CategorieController {

    private final CategorieService service;

    public CategorieController(CategorieService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategorieReponse> lister() {
        return service.lister();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategorieReponse creer(@Valid @RequestBody CategorieSaisie saisie) {
        return service.creer(saisie);
    }

    @PutMapping("/{id}")
    public CategorieReponse modifier(@PathVariable UUID id, @Valid @RequestBody CategorieSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/controller/ClientController.java`

*Routes `/clients`.*

```java
package com.example.customer.controller;

import com.example.customer.dto.ChangementStatut;
import com.example.customer.dto.ClientCreation;
import com.example.customer.dto.ClientModification;
import com.example.customer.dto.ClientReponse;
import com.example.customer.dto.Destinataire;
import com.example.customer.dto.PageResponse;
import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import com.example.customer.service.ClientService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientReponse creer(@Valid @RequestBody ClientCreation saisie) {
        return service.creer(saisie);
    }

    @GetMapping
    public PageResponse<ClientReponse> rechercher(
            @RequestParam(required = false) TypeClient type,
            @RequestParam(required = false) StatutClient statut,
            @RequestParam(required = false) UUID categorieId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int taille) {
        return service.rechercher(type, statut, categorieId, q, page, taille);
    }

    @GetMapping("/destinataires")
    public PageResponse<Destinataire> destinataires(
            @RequestParam UUID categorieId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int taille) {
        return service.destinataires(categorieId, page, taille);
    }

    @GetMapping("/{id}")
    public ClientReponse consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    @PutMapping("/{id}")
    public ClientReponse modifier(@PathVariable UUID id, @Valid @RequestBody ClientModification saisie) {
        return service.modifier(id, saisie);
    }

    @PatchMapping("/{id}/statut")
    public ClientReponse changerStatut(@PathVariable UUID id, @Valid @RequestBody ChangementStatut changement) {
        return service.changerStatut(id, changement);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
```

#### `code/customer-service/src/test/java/com/example/customer/CategorieApiTests.java`

*Tests des routes `/categories` (MockMvc, base H2).*

```java
package com.example.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CategorieApiTests {

    @Autowired
    MockMvc mvc;

    private String creerCategorie(String nom) throws Exception {
        String json = mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\",\"description\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    @Test
    void creerPuisLister() throws Exception {
        creerCategorie("VIP");
        mvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("VIP"));
    }

    @Test
    void sansNomEst422() throws Exception {
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void modifier() throws Exception {
        String id = creerCategorie("VIP");
        mvc.perform(put("/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Premium\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Premium"));
    }

    @Test
    void supprimerUneCategorieLibre() throws Exception {
        String id = creerCategorie("Libre");
        mvc.perform(delete("/categories/" + id)).andExpect(status().isNoContent());
        mvc.perform(put("/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void supprimerUneCategorieUtiliseeEst409() throws Exception {
        String id = creerCategorie("Utilisee");
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"a@atlas.tn\",\"categorieId\":\"" + id + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/categories/" + id)).andExpect(status().isConflict());
    }
}
```

#### `code/customer-service/src/test/java/com/example/customer/ClientApiTests.java`

*Tests des routes `/clients` : email unique, statut, recherche, destinataires.*

```java
package com.example.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class ClientApiTests {

    @Autowired
    MockMvc mvc;

    private String creerCategorie(String nom) throws Exception {
        String json = mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String creerClient(String nom, String email, String categorieId, String statut) throws Exception {
        String corps = "{\"type\":\"ENTREPRISE\",\"nom\":\"" + nom + "\",\"email\":\"" + email + "\""
                + (categorieId == null ? "" : ",\"categorieId\":\"" + categorieId + "\"")
                + (statut == null ? "" : ",\"statut\":\"" + statut + "\"") + "}";
        String json = mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void creationValideDonneUnProspect() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Société Atlas\",\"email\":\"contact@atlas.tn\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("PROSPECT"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void sansEmailOuEmailMalFormeEst422() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"pas-un-email\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emailEnDoubleSansTenirCompteDeLaCasseNiDesEspacesEst409() throws Exception {
        creerClient("Atlas", "Ali@Mail.tn", null, null);
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PARTICULIER\",\"nom\":\"Ali\",\"email\":\" ali@mail.tn \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void categorieInconnueEst404() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"a@a.tn\",\"categorieId\":\""
                                + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void modifierNeChangePasLeStatut() throws Exception {
        String id = creerClient("Atlas", "a@atlas.tn", null, "CLIENT");
        mvc.perform(put("/clients/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas 2\",\"email\":\"a@atlas.tn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Atlas 2"))
                .andExpect(jsonPath("$.statut").value("CLIENT"));
    }

    @Test
    void modifierAvecLEmailDUnAutreEst409() throws Exception {
        creerClient("Un", "un@x.tn", null, null);
        String id2 = creerClient("Deux", "deux@x.tn", null, null);
        mvc.perform(put("/clients/" + id2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Deux\",\"email\":\"UN@x.tn\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void changerLeStatut() throws Exception {
        String id = creerClient("Atlas", "a@atlas.tn", null, null);
        mvc.perform(patch("/clients/" + id + "/statut").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"CLIENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLIENT"));
        mvc.perform(patch("/clients/" + id + "/statut").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"INCONNU\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechercheAvecFiltresEtPagination() throws Exception {
        String vip = creerCategorie("VIP");
        creerClient("Alpha", "alpha@x.tn", vip, "CLIENT");
        creerClient("Beta", "beta@x.tn", vip, null);
        creerClient("Gamma", "gamma@x.tn", null, null);

        mvc.perform(get("/clients?statut=PROSPECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/clients?categorieId=" + vip))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/clients?q=ALPHA"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].nom").value("Alpha"));
        mvc.perform(get("/clients?page=0&taille=2"))
                .andExpect(jsonPath("$.contenu.length()").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.taille").value(2));
    }

    @Test
    void destinatairesSeulementLesClientsDeLaCategorie() throws Exception {
        String vip = creerCategorie("VIP");
        creerClient("Alpha", "alpha@x.tn", vip, "CLIENT");
        creerClient("Beta", "beta@x.tn", vip, null);          // PROSPECT : exclu
        creerClient("Gamma", "gamma@x.tn", null, "CLIENT");   // autre catégorie : exclu

        mvc.perform(get("/clients/destinataires?categorieId=" + vip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].nom").value("Alpha"))
                .andExpect(jsonPath("$.contenu[0].clientId").exists())
                .andExpect(jsonPath("$.contenu[0].email").value("alpha@x.tn"))
                .andExpect(jsonPath("$.contenu[0].telephone").doesNotExist());
    }

    @Test
    void destinatairesCategorieInconnueEst404EtCategorieVideDonneUneListeVide() throws Exception {
        mvc.perform(get("/clients/destinataires?categorieId=" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
        String vide = creerCategorie("Vide");
        mvc.perform(get("/clients/destinataires?categorieId=" + vide))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.contenu.length()").value(0));
    }

    @Test
    void consulterSupprimerEtIdentifiantMalForme() throws Exception {
        String id = creerClient("Atlas", "a@atlas.tn", null, null);
        mvc.perform(get("/clients/" + id)).andExpect(status().isOk());
        mvc.perform(delete("/clients/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/clients/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/clients/pas-un-uuid")).andExpect(status().isBadRequest());
    }
}
```


### 5.3 Les routes de l'étape B1

Les champs et codes d'erreur sont ceux du contrat `code/contrat_api/customer.yaml`.


##### `POST /categories`

Crée une catégorie de clients.

Corps de la requête :

```json
{ "nom": "VIP", "description": "Clients importants" }
```

Réponse **201** :

```json
{ "id": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f", "nom": "VIP", "description": "Clients importants" }
```

Erreurs : 422 si `nom` est absent.

##### `GET /categories`

Liste toutes les catégories (tableau JSON, sans pagination).

Réponse **200** :

```json
[ { "id": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f", "nom": "VIP", "description": "Clients importants" } ]
```

Erreurs : aucune.

##### `PUT /categories/{id}`

Modifie une catégorie.

Corps de la requête :

```json
{ "nom": "Premium", "description": "Clients premium" }
```

Réponse **200** :

```json
{ "id": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f", "nom": "Premium", "description": "Clients premium" }
```

Erreurs : 404 catégorie inconnue ; 422 sans nom.

##### `DELETE /categories/{id}`

Supprime une catégorie.

Réponse **204** :

(aucun corps)

Erreurs : 404 catégorie inconnue ; **409** si des clients l'utilisent encore.

##### `POST /clients`

Crée un client ou un prospect. Le statut est `PROSPECT` si le corps n'en donne pas (`CLIENT` est aussi accepté).

Corps de la requête :

```json
{
  "type": "ENTREPRISE",
  "nom": "Société Atlas",
  "email": "contact@atlas.tn",
  "telephone": "+216 20 123 456",
  "adresse": "Tunis",
  "categorieId": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f"
}
```

Réponse **201** :

```json
{
  "id": "9a2f7c31-84b6-4d10-b3e5-6c8a1d0e9f52",
  "type": "ENTREPRISE",
  "statut": "PROSPECT",
  "nom": "Société Atlas",
  "prenom": null,
  "email": "contact@atlas.tn",
  "telephone": "+216 20 123 456",
  "adresse": "Tunis",
  "categorieId": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f",
  "campagneSourceId": null,
  "createdAt": "2026-11-18T14:30:00Z",
  "updatedAt": "2026-11-18T14:30:00Z"
}
```

Erreurs : 404 catégorie inconnue ; **409** email déjà utilisé (majuscules et espaces ignorés) ; 422 champ obligatoire absent ou email mal formé ; 400 JSON illisible.

##### `GET /clients`

Recherche paginée. Tous les paramètres sont facultatifs.

Paramètres d'URL : `type` (PARTICULIER ou ENTREPRISE), `statut`, `categorieId`, `q` (texte cherché dans nom, prénom et email, sans tenir compte de la casse), `page` (0 par défaut), `taille` (20 par défaut, 100 au maximum).

Réponse **200** :

```json
{ "contenu": [ {
      "id": "9a2f7c31-84b6-4d10-b3e5-6c8a1d0e9f52",
      "type": "ENTREPRISE",
      "statut": "PROSPECT",
      "nom": "Société Atlas",
      "prenom": null,
      "email": "contact@atlas.tn",
      "telephone": "+216 20 123 456",
      "adresse": "Tunis",
      "categorieId": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f",
      "campagneSourceId": null,
      "createdAt": "2026-11-18T14:30:00Z",
      "updatedAt": "2026-11-18T14:30:00Z"
    } ], "page": 0, "taille": 20, "total": 1 }
```

Erreurs : 400 paramètre illisible.

##### `GET /clients/destinataires`

Liste **réduite** lue par Campaign : seulement les clients de statut `CLIENT` de la catégorie, avec leur identifiant, nom et email.

Paramètres d'URL : `categorieId` (obligatoire), `page`, `taille`.

Réponse **200** :

```json
{ "contenu": [ { "clientId": "9a2f7c31-84b6-4d10-b3e5-6c8a1d0e9f52", "nom": "Société Atlas", "email": "contact@atlas.tn" } ], "page": 0, "taille": 20, "total": 1 }
```

Erreurs : 404 catégorie inconnue ; une catégorie existante sans client renvoie `contenu: []`.

##### `GET /clients/{id}`

Consulte une fiche (lue par Quotation avant de créer un devis : le statut `INACTIF` y est visible).

Réponse **200** :

```json
{
  "id": "9a2f7c31-84b6-4d10-b3e5-6c8a1d0e9f52",
  "type": "ENTREPRISE",
  "statut": "PROSPECT",
  "nom": "Société Atlas",
  "prenom": null,
  "email": "contact@atlas.tn",
  "telephone": "+216 20 123 456",
  "adresse": "Tunis",
  "categorieId": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f",
  "campagneSourceId": null,
  "createdAt": "2026-11-18T14:30:00Z",
  "updatedAt": "2026-11-18T14:30:00Z"
}
```

Erreurs : 404 client inconnu ; 400 identifiant mal formé.

##### `PUT /clients/{id}`

Modifie une fiche. Ne change **jamais** le statut ni `campagneSourceId`.

Corps de la requête :

```json
{
  "type": "ENTREPRISE",
  "nom": "Société Atlas SARL",
  "email": "contact@atlas.tn",
  "telephone": "+216 20 123 456",
  "adresse": "Tunis",
  "categorieId": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f"
}
```

Réponse **200** :

```json
le client modifié (même format que ci-dessus)
```

Erreurs : 404 ; **409** email déjà pris par un autre client ; 422.

##### `PATCH /clients/{id}/statut`

Change le statut (décision du commercial ; le passage de PROSPECT à CLIENT n'est jamais automatique).

Corps de la requête :

```json
{ "statut": "CLIENT" }
```

Réponse **200** :

```json
le client avec son nouveau statut
```

Erreurs : 404 ; 400 si la valeur n'est pas PROSPECT, CLIENT ou INACTIF ; 422 si `statut` est absent.

##### `DELETE /clients/{id}`

Supprime une fiche (en usage normal on passe le client en `INACTIF`).

Réponse **204** :

(aucun corps)

Erreurs : 404.

### 5.4 Vérification

```
cd code/customer-service
mvn clean test
```

Attendu : `Tests run: 17, Failures: 0, Errors: 0` et `BUILD SUCCESS` (1 test de B0, 5 de catégories, 11 de clients).

Essai à la main (Swagger, <http://localhost:8081/swagger-ui.html>) :
1. `POST /categories` avec `{ "nom": "VIP", "description": "Clients importants" }` : noter l'`id`.
2. `POST /clients` avec cette `categorieId` et `"email": "contact@atlas.tn"` : 201, statut `PROSPECT`.
3. Refaire le même `POST` avec `"email": "CONTACT@atlas.tn"` : **409**.
4. `PATCH /clients/{id}/statut` avec `{ "statut": "CLIENT" }` : 200.
5. `GET /clients/destinataires?categorieId=<id>` : un seul élément avec `clientId`, `nom`, `email`.
6. `DELETE /categories/<id>` : **409** (catégorie utilisée).


## 6. Étape B2 : contacts et interactions

### 6.1 Modification de `application.yml`

Ajouter ce bloc **à la fin** de `src/main/resources/application.yml` (il donne l'auteur des interactions tant que le JWT n'est pas branché, étape E3) :

```yaml
app:
  # Tant que le JWT n'est pas branché, l'auteur d'une interaction est cette valeur fixe
  default-user-id: 00000000-0000-0000-0000-000000000001
```

### 6.2 Fichiers à créer (15)

| # | Fichier | Rôle |
|---|---|---|
| 1 | `code/customer-service/src/main/java/com/example/customer/entity/Contact.java` | La table des contacts. `@OnDelete(CASCADE)` : supprimer un client supprime ses contacts. |
| 2 | `code/customer-service/src/main/java/com/example/customer/entity/Interaction.java` | La table des interactions (appels, emails, réunions). |
| 3 | `code/customer-service/src/main/java/com/example/customer/entity/TypeInteraction.java` | Les trois types d'interaction. |
| 4 | `code/customer-service/src/main/java/com/example/customer/repository/ContactRepository.java` | Accès aux contacts d'un client. |
| 5 | `code/customer-service/src/main/java/com/example/customer/repository/InteractionRepository.java` | Accès aux interactions, de la plus récente à la plus ancienne. |
| 6 | `code/customer-service/src/main/java/com/example/customer/dto/ContactReponse.java` | Contact renvoyé par l'API. |
| 7 | `code/customer-service/src/main/java/com/example/customer/dto/ContactSaisie.java` | Corps reçu pour créer ou modifier un contact. |
| 8 | `code/customer-service/src/main/java/com/example/customer/dto/InteractionReponse.java` | Interaction renvoyée par l'API. |
| 9 | `code/customer-service/src/main/java/com/example/customer/dto/InteractionSaisie.java` | Corps reçu pour une interaction : pas de `auteurId`, il est fixé par le service. |
| 10 | `code/customer-service/src/main/java/com/example/customer/service/ContactService.java` | Règles des contacts. |
| 11 | `code/customer-service/src/main/java/com/example/customer/service/InteractionService.java` | Règles des interactions ; l'auteur vient de `app.default-user-id`. |
| 12 | `code/customer-service/src/main/java/com/example/customer/controller/ContactController.java` | Routes des contacts. |
| 13 | `code/customer-service/src/main/java/com/example/customer/controller/InteractionController.java` | Routes des interactions. |
| 14 | `code/customer-service/src/test/java/com/example/customer/ContactApiTests.java` | Tests des routes des contacts. |
| 15 | `code/customer-service/src/test/java/com/example/customer/InteractionApiTests.java` | Tests des interactions et de la suppression en cascade. |

### 6.3 Code


#### `code/customer-service/src/main/java/com/example/customer/entity/Contact.java`

*La table des contacts. `@OnDelete(CASCADE)` : supprimer un client supprime ses contacts.*

```java
package com.example.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
public class Contact {

    @Id
    private UUID id = UUID.randomUUID();

    // Quand le client est supprimé, la base supprime aussi ses contacts
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Client client;

    @Column(nullable = false)
    private String nom;

    private String prenom;
    private String fonction;
    private String email;
    private String telephone;

    public UUID getId() { return id; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getFonction() { return fonction; }
    public void setFonction(String fonction) { this.fonction = fonction; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
}
```

#### `code/customer-service/src/main/java/com/example/customer/entity/Interaction.java`

*La table des interactions (appels, emails, réunions).*

```java
package com.example.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
public class Interaction {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeInteraction type;

    @Column(nullable = false)
    private Instant date;

    @Column(length = 2000)
    private String description;

    // Identifiant de l'utilisateur qui a saisi l'interaction (fixé par le service)
    private UUID auteurId;

    public UUID getId() { return id; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public TypeInteraction getType() { return type; }
    public void setType(TypeInteraction type) { this.type = type; }

    public Instant getDate() { return date; }
    public void setDate(Instant date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getAuteurId() { return auteurId; }
    public void setAuteurId(UUID auteurId) { this.auteurId = auteurId; }
}
```

#### `code/customer-service/src/main/java/com/example/customer/entity/TypeInteraction.java`

*Les trois types d'interaction.*

```java
package com.example.customer.entity;

public enum TypeInteraction {
    APPEL, EMAIL, REUNION
}
```

#### `code/customer-service/src/main/java/com/example/customer/repository/ContactRepository.java`

*Accès aux contacts d'un client.*

```java
package com.example.customer.repository;

import com.example.customer.entity.Contact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    List<Contact> findByClientId(UUID clientId);
}
```

#### `code/customer-service/src/main/java/com/example/customer/repository/InteractionRepository.java`

*Accès aux interactions, de la plus récente à la plus ancienne.*

```java
package com.example.customer.repository;

import com.example.customer.entity.Interaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    // De la plus récente à la plus ancienne
    List<Interaction> findByClientIdOrderByDateDesc(UUID clientId);
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/ContactReponse.java`

*Contact renvoyé par l'API.*

```java
package com.example.customer.dto;

import com.example.customer.entity.Contact;
import java.util.UUID;

public record ContactReponse(UUID id, UUID clientId, String nom, String prenom, String fonction, String email,
                             String telephone) {

    public static ContactReponse de(Contact c) {
        return new ContactReponse(c.getId(), c.getClient().getId(), c.getNom(), c.getPrenom(), c.getFonction(),
                c.getEmail(), c.getTelephone());
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/ContactSaisie.java`

*Corps reçu pour créer ou modifier un contact.*

```java
package com.example.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactSaisie(@NotBlank String nom, String prenom, String fonction, @Email String email, String telephone) {
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/InteractionReponse.java`

*Interaction renvoyée par l'API.*

```java
package com.example.customer.dto;

import com.example.customer.entity.Interaction;
import com.example.customer.entity.TypeInteraction;
import java.time.Instant;
import java.util.UUID;

public record InteractionReponse(UUID id, UUID clientId, TypeInteraction type, Instant date, String description,
                                 UUID auteurId) {

    public static InteractionReponse de(Interaction i) {
        return new InteractionReponse(i.getId(), i.getClient().getId(), i.getType(), i.getDate(), i.getDescription(),
                i.getAuteurId());
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/dto/InteractionSaisie.java`

*Corps reçu pour une interaction : pas de `auteurId`, il est fixé par le service.*

```java
package com.example.customer.dto;

import com.example.customer.entity.TypeInteraction;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Pas de auteurId ici : il vient de l'utilisateur courant, jamais du corps de la requête. */
public record InteractionSaisie(@NotNull TypeInteraction type, @NotNull Instant date, String description) {
}
```

#### `code/customer-service/src/main/java/com/example/customer/service/ContactService.java`

*Règles des contacts.*

```java
package com.example.customer.service;

import com.example.customer.dto.ContactReponse;
import com.example.customer.dto.ContactSaisie;
import com.example.customer.entity.Client;
import com.example.customer.entity.Contact;
import com.example.customer.exception.ApiException;
import com.example.customer.repository.ClientRepository;
import com.example.customer.repository.ContactRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContactService {

    private final ContactRepository contacts;
    private final ClientRepository clients;

    public ContactService(ContactRepository contacts, ClientRepository clients) {
        this.contacts = contacts;
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public List<ContactReponse> lister(UUID clientId) {
        verifierClient(clientId);
        return contacts.findByClientId(clientId).stream().map(ContactReponse::de).toList();
    }

    public ContactReponse ajouter(UUID clientId, ContactSaisie saisie) {
        Client client = verifierClient(clientId);
        Contact c = new Contact();
        c.setClient(client);
        remplir(c, saisie);
        return ContactReponse.de(contacts.save(c));
    }

    public ContactReponse modifier(UUID id, ContactSaisie saisie) {
        Contact c = contacts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Contact introuvable"));
        remplir(c, saisie);
        return ContactReponse.de(c);
    }

    public void supprimer(UUID id) {
        Contact c = contacts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Contact introuvable"));
        contacts.delete(c);
    }

    private Client verifierClient(UUID clientId) {
        return clients.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Client introuvable"));
    }

    private void remplir(Contact c, ContactSaisie s) {
        c.setNom(s.nom());
        c.setPrenom(s.prenom());
        c.setFonction(s.fonction());
        c.setEmail(s.email());
        c.setTelephone(s.telephone());
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/service/InteractionService.java`

*Règles des interactions ; l'auteur vient de `app.default-user-id`.*

```java
package com.example.customer.service;

import com.example.customer.dto.InteractionReponse;
import com.example.customer.dto.InteractionSaisie;
import com.example.customer.entity.Client;
import com.example.customer.entity.Interaction;
import com.example.customer.exception.ApiException;
import com.example.customer.repository.ClientRepository;
import com.example.customer.repository.InteractionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InteractionService {

    private final InteractionRepository interactions;
    private final ClientRepository clients;
    // Tant que le JWT n'est pas branché (étape E3), l'auteur est une valeur fixe de application.yml
    private final UUID auteurParDefaut;

    public InteractionService(InteractionRepository interactions, ClientRepository clients,
                              @Value("${app.default-user-id}") UUID auteurParDefaut) {
        this.interactions = interactions;
        this.clients = clients;
        this.auteurParDefaut = auteurParDefaut;
    }

    @Transactional(readOnly = true)
    public List<InteractionReponse> lister(UUID clientId) {
        verifierClient(clientId);
        return interactions.findByClientIdOrderByDateDesc(clientId).stream().map(InteractionReponse::de).toList();
    }

    public InteractionReponse ajouter(UUID clientId, InteractionSaisie saisie) {
        Client client = verifierClient(clientId);
        Interaction i = new Interaction();
        i.setClient(client);
        i.setAuteurId(auteurParDefaut);
        remplir(i, saisie);
        return InteractionReponse.de(interactions.save(i));
    }

    public InteractionReponse modifier(UUID id, InteractionSaisie saisie) {
        Interaction i = trouver(id);
        remplir(i, saisie);   // l'auteur ne change pas
        return InteractionReponse.de(i);
    }

    public void supprimer(UUID id) {
        interactions.delete(trouver(id));
    }

    private Interaction trouver(UUID id) {
        return interactions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Interaction introuvable"));
    }

    private Client verifierClient(UUID clientId) {
        return clients.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Client introuvable"));
    }

    private void remplir(Interaction i, InteractionSaisie s) {
        i.setType(s.type());
        i.setDate(s.date());
        i.setDescription(s.description());
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/controller/ContactController.java`

*Routes des contacts.*

```java
package com.example.customer.controller;

import com.example.customer.dto.ContactReponse;
import com.example.customer.dto.ContactSaisie;
import com.example.customer.service.ContactService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContactController {

    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    @GetMapping("/clients/{clientId}/contacts")
    public List<ContactReponse> lister(@PathVariable UUID clientId) {
        return service.lister(clientId);
    }

    @PostMapping("/clients/{clientId}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactReponse ajouter(@PathVariable UUID clientId, @Valid @RequestBody ContactSaisie saisie) {
        return service.ajouter(clientId, saisie);
    }

    @PutMapping("/contacts/{id}")
    public ContactReponse modifier(@PathVariable UUID id, @Valid @RequestBody ContactSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @DeleteMapping("/contacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
```

#### `code/customer-service/src/main/java/com/example/customer/controller/InteractionController.java`

*Routes des interactions.*

```java
package com.example.customer.controller;

import com.example.customer.dto.InteractionReponse;
import com.example.customer.dto.InteractionSaisie;
import com.example.customer.service.InteractionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InteractionController {

    private final InteractionService service;

    public InteractionController(InteractionService service) {
        this.service = service;
    }

    @GetMapping("/clients/{clientId}/interactions")
    public List<InteractionReponse> lister(@PathVariable UUID clientId) {
        return service.lister(clientId);
    }

    @PostMapping("/clients/{clientId}/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public InteractionReponse ajouter(@PathVariable UUID clientId, @Valid @RequestBody InteractionSaisie saisie) {
        return service.ajouter(clientId, saisie);
    }

    @PutMapping("/interactions/{id}")
    public InteractionReponse modifier(@PathVariable UUID id, @Valid @RequestBody InteractionSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @DeleteMapping("/interactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
```

#### `code/customer-service/src/test/java/com/example/customer/ContactApiTests.java`

*Tests des routes des contacts.*

```java
package com.example.customer;

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
class ContactApiTests {

    @Autowired
    MockMvc mvc;

    private String creerClient() throws Exception {
        String json = mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"" + UUID.randomUUID() + "@atlas.tn\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void ajouterListerModifierSupprimer() throws Exception {
        String clientId = creerClient();
        String json = mvc.perform(post("/clients/" + clientId + "/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Ben Salah\",\"prenom\":\"Karim\",\"fonction\":\"Directeur\",\"email\":\"karim@atlas.tn\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andReturn().getResponse().getContentAsString();
        String contactId = JsonPath.read(json, "$.id");

        mvc.perform(get("/clients/" + clientId + "/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fonction").value("Directeur"));

        mvc.perform(put("/contacts/" + contactId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Ben Salah\",\"fonction\":\"Acheteur\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonction").value("Acheteur"));

        mvc.perform(delete("/contacts/" + contactId)).andExpect(status().isNoContent());
        mvc.perform(get("/clients/" + clientId + "/contacts")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void clientOuContactInconnuEst404() throws Exception {
        mvc.perform(get("/clients/" + UUID.randomUUID() + "/contacts")).andExpect(status().isNotFound());
        mvc.perform(put("/contacts/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/contacts/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void sansNomOuEmailInvalideEst422() throws Exception {
        String clientId = creerClient();
        mvc.perform(post("/clients/" + clientId + "/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prenom\":\"Karim\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/clients/" + clientId + "/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"X\",\"email\":\"pas-un-email\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
```

#### `code/customer-service/src/test/java/com/example/customer/InteractionApiTests.java`

*Tests des interactions et de la suppression en cascade.*

```java
package com.example.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.customer.repository.ContactRepository;
import com.example.customer.repository.InteractionRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InteractionApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    ContactRepository contacts;
    @Autowired
    InteractionRepository interactions;
    @Autowired
    EntityManager em;

    private String creerClient() throws Exception {
        String json = mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"" + UUID.randomUUID() + "@atlas.tn\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void auteurVientDeLaConfigurationMemeSiLeCorpsEnContientUn() throws Exception {
        String clientId = creerClient();
        mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPEL\",\"date\":\"2026-11-18T10:00:00Z\","
                                + "\"description\":\"Premier appel\",\"auteurId\":\"11111111-1111-1111-1111-111111111111\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.auteurId").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.type").value("APPEL"));
    }

    @Test
    void listeDeLaPlusRecenteALaPlusAncienne() throws Exception {
        String clientId = creerClient();
        for (String date : new String[]{"2026-11-01T10:00:00Z", "2026-11-20T10:00:00Z", "2026-11-10T10:00:00Z"}) {
            mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"EMAIL\",\"date\":\"" + date + "\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/clients/" + clientId + "/interactions"))
                .andExpect(jsonPath("$[0].date").value("2026-11-20T10:00:00Z"))
                .andExpect(jsonPath("$[2].date").value("2026-11-01T10:00:00Z"));
    }

    @Test
    void modifierSupprimerEtErreurs() throws Exception {
        String clientId = creerClient();
        String json = mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"REUNION\",\"date\":\"2026-11-18T10:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");

        mvc.perform(put("/interactions/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPEL\",\"date\":\"2026-11-19T10:00:00Z\",\"description\":\"Corrigé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("APPEL"))
                .andExpect(jsonPath("$.description").value("Corrigé"));

        mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPEL\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INCONNU\",\"date\":\"2026-11-18T10:00:00Z\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/interactions/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/interactions/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/clients/" + UUID.randomUUID() + "/interactions")).andExpect(status().isNotFound());
    }

    @Test
    void supprimerUnClientSupprimeSesContactsEtInteractions() throws Exception {
        String clientId = creerClient();
        mvc.perform(post("/clients/" + clientId + "/contacts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Ben Salah\"}")).andExpect(status().isCreated());
        mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"APPEL\",\"date\":\"2026-11-18T10:00:00Z\"}")).andExpect(status().isCreated());
        em.flush();
        assertThat(contacts.count()).isEqualTo(1);
        assertThat(interactions.count()).isEqualTo(1);

        mvc.perform(delete("/clients/" + clientId)).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(contacts.count()).isZero();
        assertThat(interactions.count()).isZero();
    }
}
```


### 6.4 Les routes de l'étape B2


##### `POST /clients/{id}/contacts`

Ajoute un contact à un client.

Corps de la requête :

```json
{ "nom": "Ben Salah", "prenom": "Karim", "fonction": "Directeur", "email": "karim@atlas.tn", "telephone": "+216 20 111 222" }
```

Réponse **201** :

```json
{
  "id": "5d1c0b7e-2f4a-4a8e-9b6c-3e7f8a9b0c1d",
  "clientId": "9a2f7c31-84b6-4d10-b3e5-6c8a1d0e9f52",
  "nom": "Ben Salah",
  "prenom": "Karim",
  "fonction": "Directeur",
  "email": "karim@atlas.tn",
  "telephone": "+216 20 111 222"
}
```

Erreurs : 404 client inconnu ; 422 sans `nom` ou email mal formé.

##### `GET /clients/{id}/contacts`

Liste les contacts d'un client (tableau JSON).

Réponse **200** :

```json
[ { ...contact... } ]
```

Erreurs : 404 client inconnu.

##### `PUT /contacts/{id}`

Modifie un contact (même corps que la création).

Corps de la requête :

```json
{ "nom": "Ben Salah", "fonction": "Acheteur" }
```

Réponse **200** :

```json
le contact modifié
```

Erreurs : 404 ; 422.

##### `DELETE /contacts/{id}`

Supprime un contact.

Réponse **204** :

(aucun corps)

Erreurs : 404.

##### `POST /clients/{id}/interactions`

Enregistre un appel, un email ou une réunion. `auteurId` est rempli par le service : même si le corps en contient un, il est ignoré.

Corps de la requête :

```json
{ "type": "APPEL", "date": "2026-11-18T10:00:00Z", "description": "Premier appel de présentation" }
```

Réponse **201** :

```json
{
  "id": "7e2d1c0b-3a5b-4b9f-8c7d-4f8a9b0c1d2e",
  "clientId": "9a2f7c31-84b6-4d10-b3e5-6c8a1d0e9f52",
  "type": "APPEL",
  "date": "2026-11-18T10:00:00Z",
  "description": "Premier appel de présentation",
  "auteurId": "00000000-0000-0000-0000-000000000001"
}
```

Erreurs : 404 client inconnu ; 422 `type` ou `date` absent ; 400 type inconnu.

##### `GET /clients/{id}/interactions`

Liste les interactions, de la plus récente à la plus ancienne (tableau JSON).

Réponse **200** :

```json
[ { ...interaction... } ]
```

Erreurs : 404 client inconnu.

##### `PUT /interactions/{id}`

Modifie une interaction (l'auteur ne change pas).

Corps de la requête :

```json
{ "type": "REUNION", "date": "2026-11-19T09:00:00Z", "description": "Réunion de suivi" }
```

Réponse **200** :

```json
l'interaction modifiée
```

Erreurs : 404 ; 422 ; 400.

##### `DELETE /interactions/{id}`

Supprime une interaction.

Réponse **204** :

(aucun corps)

Erreurs : 404.

### 6.5 Vérification

```
cd code/customer-service
mvn clean test
```

Attendu : `Tests run: 24, Failures: 0, Errors: 0` et `BUILD SUCCESS`.

Essai à la main (Swagger) avec un client créé en B1 :
1. `POST /clients/{id}/contacts` avec `{ "nom": "Ben Salah", "prenom": "Karim", "fonction": "Directeur", "email": "karim@atlas.tn" }` : 201.
2. `POST /clients/{id}/interactions` avec `{ "type": "APPEL", "date": "2026-11-18T10:00:00Z", "description": "Premier appel" }` : 201, `auteurId` rempli.
3. `GET /clients/{id}/interactions` : l'interaction apparaît.
4. `DELETE /clients/{id}` puis `GET /clients/{id}/contacts` : **404**.

## 7. Après B2

Customer est terminé, sauf la réception de l'événement `ProspectGenere` (étape E1, avec le trinôme). Les deux routes lues par les autres services (`GET /clients/{id}` et `GET /clients/destinataires`) sont prêtes : prévenir Oussema Souidi (Quotation, étape S2) et Oussema Arfaoui (Campaign, étape A3) pour qu'ils les essaient en réel.
