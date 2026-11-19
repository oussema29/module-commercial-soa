# Étape B1 : Customer : catégories et clients

| | |
|---|---|
| **Service** | Customer (sous-service 1.1 Fiches clients) |
| **Responsable** | Baha Farhati |
| **Ce que couvre l'étape** | Fiches clients et prospects (créer, modifier, rechercher, supprimer), email unique, statuts, catégories, liste de destinataires pour Campaign |
| **Dossier de code** | `code/customer-service` |
| **Code fourni** | `GUIDE_CODE_CUSTOMER.md (section 5)` : arborescence, code complet de chaque fichier, routes, tests |
| **Prérequis** | B0 validée |
| **État** | À faire |

## 1. Objectif

Gérer les catégories de clients et les fiches clients : création, modification, recherche paginée, changement de statut, suppression par l'administrateur, et la liste réduite de destinataires que Campaign lira au lancement.

**Pas dans cette étape :**

- Contacts et interactions (étape B2).
- Réception de `ProspectGenere` (étape E1) : mais le champ `campagneSourceId` existe dès maintenant.
- Table `EvenementTraite` (étape E1).

## 2. Modèle de données

**CategorieClient** : `id` (UUID), `nom` (obligatoire), `description`.

**Client** :

| Champ | Type | Note |
|---|---|---|
| `id` | UUID | généré par le service |
| `type` | enum `PARTICULIER` / `ENTREPRISE` | obligatoire |
| `statut` | enum `PROSPECT` / `CLIENT` / `INACTIF` | `PROSPECT` par défaut |
| `nom` | chaîne | obligatoire (nom du particulier ou de la société) |
| `prenom` | chaîne | facultatif (particulier) |
| `email` | chaîne | obligatoire, tel que saisi |
| `emailNormalise` | chaîne | `email` sans espaces autour et en minuscules ; **contrainte d'unicité** ; jamais exposé dans l'API |
| `telephone`, `adresse` | chaînes | facultatifs |
| `categorie` | lien vers `CategorieClient` | facultatif ; clé étrangère interne à `customer_db` |
| `campagneSourceId` | UUID | facultatif ; simple référence à une campagne (autre service), **sans clé étrangère** |
| `createdAt`, `updatedAt` | date-heure UTC | remplis par le service |

`Client` et `CategorieClient` sont dans la même base : la clé étrangère entre elles est permise.

## 3. API à réaliser

Source de vérité : le fichier OpenAPI du service dans `contrat_api/`. Les champs ci-dessous en sont extraits.

La colonne « Rôles » est le contrat : elle est **appliquée à l'étape E3** (JWT). D'ici là, aucune route ne vérifie le rôle.

**customer.yaml**

| Méthode | Chemin | Rôles | Succès | Erreurs | Rôle de la route |
|---|---|---|---|---|---|
| GET | `/categories` | ADMIN, COMMERCIAL, MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les catégories |
| POST | `/categories` | ADMIN | 201 | 400, 401, 403, 404, 422 | Créer une catégorie |
| PUT | `/categories/{id}` | ADMIN | 200 | 400, 401, 403, 404, 422 | Modifier une catégorie |
| DELETE | `/categories/{id}` | ADMIN | 204 | 400, 401, 403, 404, 409 | Supprimer une catégorie |
| POST | `/clients` | ADMIN, COMMERCIAL | 201 | 400, 401, 403, 404, 409, 422 | Créer un client ou un prospect |
| GET | `/clients` | ADMIN, COMMERCIAL, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister et rechercher les clients |
| GET | `/clients/destinataires` | ADMIN, COMMERCIAL, MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Liste réduite des clients d'une catégorie (pour Campaign) |
| GET | `/clients/{id}` | ADMIN, COMMERCIAL, RESPONSABLE | 200 | 400, 401, 403, 404 | Consulter une fiche |
| PUT | `/clients/{id}` | ADMIN, COMMERCIAL | 200 | 400, 401, 403, 404, 409, 422 | Modifier une fiche (sans le statut) |
| DELETE | `/clients/{id}` | ADMIN | 204 | 400, 401, 403, 404 | Supprimer un client (en usage normal : passer en INACTIF) |
| PATCH | `/clients/{id}/statut` | ADMIN, COMMERCIAL | 200 | 400, 401, 403, 404, 422 | Changer le statut (décision du commercial) |

### Objets échangés

**Categorie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `nom` | string | oui |  |
| `description` | string | non |  |

**CategorieSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `nom` | string | oui |  |
| `description` | string | non |  |

**ClientCreation**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `type` | string | oui | PARTICULIER / ENTREPRISE |
| `statut` | string | non | PROSPECT / CLIENT |
| `nom` | string | oui |  |
| `prenom` | string | non |  |
| `email` | string (email) | oui |  |
| `telephone` | string | non |  |
| `adresse` | string | non |  |
| `categorieId` | string (uuid) | non |  |

**Client**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `type` | string | oui | PARTICULIER / ENTREPRISE |
| `statut` | string | oui | PROSPECT / CLIENT / INACTIF |
| `nom` | string | oui |  |
| `prenom` | string | non | peut être null |
| `email` | string (email) | oui | Obligatoire, unique, comparé sans majuscules ni espaces |
| `telephone` | string | non | peut être null |
| `adresse` | string | non | peut être null |
| `categorieId` | string (uuid) | non | peut être null |
| `campagneSourceId` | string (uuid) | non | peut être null ; Campagne d'arrivée, fixée à la création |
| `createdAt` | string (date-time) | non |  |
| `updatedAt` | string (date-time) | non |  |

**Destinataire**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `clientId` | string (uuid) | oui |  |
| `nom` | string | oui | Nom complet à afficher |
| `email` | string (email) | oui |  |

**ClientModification** : Le statut ne se change pas ici : voir PATCH /clients/{id}/statut. campagneSourceId ne change jamais.

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `type` | string | oui | PARTICULIER / ENTREPRISE |
| `nom` | string | oui |  |
| `prenom` | string | non |  |
| `email` | string (email) | oui |  |
| `telephone` | string | non |  |
| `adresse` | string | non |  |
| `categorieId` | string (uuid) | non |  |

**ChangementStatut**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `statut` | string | oui | PROSPECT / CLIENT / INACTIF |

## 4. Règles métier à respecter

- **Email** : obligatoire, valide, unique **sans tenir compte des majuscules ni des espaces** (`Ali@Mail.tn ` et `ali@mail.tn` sont le même email) → 409 « Un client avec cet email existe déjà », à la création comme à la modification.
- **Création** : statut `PROSPECT` si le corps n'en donne pas ; `CLIENT` est aussi accepté. Si `categorieId` est donné, la catégorie doit exister (sinon 404).
- **Modification** (`PUT`) : ne change **jamais** le statut (route dédiée) ni `campagneSourceId`.
- **Statut** (`PATCH /clients/{id}/statut`) : décision du commercial ; le contrat ne limite pas les transitions (PROSPECT → CLIENT → INACTIF, et retour possible). Valeur inconnue → 400 (JSON illisible) ; `statut` absent → 422. Le passage de PROSPECT à CLIENT n'est **jamais automatique**.
- **Suppression d'un client** : ADMIN seulement, 204 ; en usage normal on met le client INACTIF.
- **Catégorie** : `nom` obligatoire (422 sinon) ; `DELETE` → 409 si au moins un client l'utilise.
- **Recherche** (`GET /clients`) : filtres facultatifs `type`, `statut`, `categorieId` et `q` (texte cherché dans nom, prénom et email, sans tenir compte de la casse) ; réponse paginée `{contenu, page, taille, total}`, taille 20 par défaut, 100 au maximum.
- **Destinataires** (`GET /clients/destinataires?categorieId=`) : seulement les clients de **statut CLIENT** de la catégorie ; chaque élément ne contient que `id`, `nom`, `email` ; paginée ; 404 si la catégorie n'existe pas ; liste vide si elle existe sans client.

## 5. Tâches, dans l'ordre

- [ ] Dans `entity` : `Client`, `CategorieClient`, enums `TypeClient` et `StatutClient`.
- [ ] Dans `repository` : `CategorieRepository` et `ClientRepository` (méthodes : `existsByEmailNormalise`, `existsByEmailNormaliseAndIdNot`, `existsByCategorieId`, recherche avec `Specification` pour `type`, `statut`, `categorieId`, `q`, et requête des destinataires).
- [ ] Créer les objets d'échange dans `dto` : `ClientCreation`, `ClientModification`, `ChangementStatut`, `ClientReponse`, `Destinataire`, `CategorieSaisie`, `CategorieReponse`, avec la validation (`@NotBlank`, `@Email`, `@NotNull`), et `PageResponse<T>` `{contenu, page, taille, total}`.
- [ ] Dans `service` : `CategorieService` et `ClientService` avec les règles de la section 4 (« Règles métier ») (la normalisation de l'email est faite à un seul endroit).
- [ ] Dans `controller` : `CategorieController` et `ClientController` (codes 201, 200, 204 comme dans le contrat).
- [ ] Écrire la configuration springdoc pour que Swagger affiche les routes (`/swagger-ui.html`).
- [ ] Écrire les tests de la section 7 (« Tests à écrire ») (MockMvc, base H2).
- [ ] Vérifier avec MySQL réel (Docker) puis cocher les tâches, mettre l'état de la fiche à jour.

## 6. Fichiers créés ou modifiés

- `entity/ : Client, CategorieClient, TypeClient, StatutClient ; repository/ : ClientRepository, CategorieRepository ; service/ : ClientService, CategorieService`
- `controller/ : ClientController, CategorieController ; dto/ : les objets d'échange et PageResponse (tous sous `com/example/customer/`)`
- `src/test/java/com/example/customer/ClientApiTests.java, CategorieApiTests.java`

## 7. Tests à écrire

- Tests d'API avec MockMvc (`@SpringBootTest` + `@AutoConfigureMockMvc` + profil `test`, base H2 en mémoire).
- Création d'un client valide → 201, statut `PROSPECT`, `id` renvoyé ; sans email → 422 ; email mal formé → 422.
- Deux clients avec `Ali@Mail.tn ` et `ali@mail.tn` → le second reçoit 409.
- Création avec `categorieId` inconnu → 404.
- `PUT` ne change pas le statut ; `PATCH` statut → 200 ; valeur inconnue → 400.
- Recherche : filtres `statut`, `categorieId`, `q` (casse ignorée) ; pagination (`page`, `taille`, `total`).
- Destinataires : seuls les clients de statut CLIENT de la catégorie ; catégorie inconnue → 404 ; catégorie vide → liste vide ; la réponse ne contient que `id`, `nom`, `email`.
- Catégorie : sans nom → 422 ; suppression d'une catégorie utilisée → 409 ; suppression d'une catégorie libre → 204.
- Un `id` mal formé dans l'URL → 400.

## 8. Points d'attention

- L'unicité de l'email doit être garantie **aussi par la base** (contrainte unique sur `emailNormalise`), pas seulement par un test dans le service : deux requêtes simultanées pourraient passer le test. Attraper la violation de contrainte et la convertir en 409.
- `GET /clients/destinataires` est la **seule** route lue par Campaign : elle ne doit rien exposer d'autre (pas de téléphone, ni d'adresse). C'est ce qui permet au marketing de lancer une campagne sans lire les fiches.
- `GET /clients/{id}` est lue par Quotation : elle doit renvoyer le statut (Quotation refuse les clients INACTIF).
- Le contrôle de la catégorie citée par un public de campagne (Campaign) viendra plus tard : il utilisera `GET /categories`.
- Pagination : refuser `taille` supérieure à 100 en la ramenant à 100, ou en répondant 400 ; choisir une des deux et l'écrire dans le test.

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` : tout vert.
2. `docker compose up -d`, puis `mvn spring-boot:run` dans `customer-service`.
3. Ouvrir `http://localhost:8081/swagger-ui.html` et essayer dans l'ordre :
   - `POST /categories` avec `{"nom": "VIP", "description": "Clients importants"}` → 201.
   - `POST /clients` avec `{"type": "ENTREPRISE", "nom": "Société Atlas", "email": "contact@atlas.tn", "categorieId": "<id de VIP>"}` → 201, statut `PROSPECT`.
   - Refaire le même `POST` avec `"email": " CONTACT@atlas.tn"` → **409**.
   - `PATCH /clients/{id}/statut` avec `{"statut": "CLIENT"}` → 200.
   - `GET /clients/destinataires?categorieId=<id de VIP>` → un seul élément avec `id`, `nom`, `email`.
   - `DELETE /categories/<id de VIP>` → **409** (catégorie utilisée).


## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-B1`, push (voir `README.md`).

Message de commit proposé :

```
feat(customer): catégories et clients (CRUD, recherche, statut, destinataires)
```
