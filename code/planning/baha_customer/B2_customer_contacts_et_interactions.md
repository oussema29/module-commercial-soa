# Étape B2 : Customer : contacts et interactions

| | |
|---|---|
| **Service** | Customer (sous-services 1.2 Contacts et 1.3 Interactions) |
| **Responsable** | Baha Farhati |
| **Ce que couvre l'étape** | Contacts d'un client et interactions (appels, emails, réunions) |
| **Dossier de code** | `code/customer-service` |
| **Code fourni** | `GUIDE_CODE_CUSTOMER.md (section 6)` : arborescence, code complet de chaque fichier, routes, tests |
| **Prérequis** | B1 validée |
| **État** | À faire |

## 1. Objectif

Rattacher des contacts (personnes d'une entreprise) et des interactions (appels, emails, réunions) à une fiche client. Terminer ainsi les routes de Customer, sauf la réception de `ProspectGenere`.

**Pas dans cette étape :**

- Réception de `ProspectGenere`, qui crée aussi un contact pour une entreprise (étape E1).

## 2. Modèle de données

**Contact** : `id` (UUID), `client` (lien vers `Client`, clé étrangère interne), `nom` (obligatoire), `prenom`, `fonction`, `email`, `telephone`.

**Interaction** : `id` (UUID), `client` (lien vers `Client`), `type` (enum `APPEL` / `EMAIL` / `REUNION`, obligatoire), `date` (date-heure UTC, obligatoire), `description` (le résumé), `auteurId` (UUID de l'utilisateur, **jamais dans le corps** ; valeur fixe `app.default-user-id` (application.yml) jusqu'au JWT de l'étape E3).

## 3. API à réaliser

Source de vérité : le fichier OpenAPI du service dans `contrat_api/`. Les champs ci-dessous en sont extraits.

La colonne « Rôles » est le contrat : elle est **appliquée à l'étape E3** (JWT). D'ici là, aucune route ne vérifie le rôle.

**customer.yaml**

| Méthode | Chemin | Rôles | Succès | Erreurs | Rôle de la route |
|---|---|---|---|---|---|
| GET | `/clients/{id}/contacts` | ADMIN, COMMERCIAL, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les contacts d'un client |
| POST | `/clients/{id}/contacts` | ADMIN, COMMERCIAL | 201 | 400, 401, 403, 404, 422 | Ajouter un contact |
| PUT | `/contacts/{id}` | ADMIN, COMMERCIAL | 200 | 400, 401, 403, 404, 422 | Modifier un contact |
| DELETE | `/contacts/{id}` | ADMIN, COMMERCIAL | 204 | 400, 401, 403, 404 | Supprimer un contact |
| GET | `/clients/{id}/interactions` | ADMIN, COMMERCIAL, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les interactions d'un client |
| POST | `/clients/{id}/interactions` | ADMIN, COMMERCIAL | 201 | 400, 401, 403, 404, 422 | Enregistrer une interaction |
| PUT | `/interactions/{id}` | ADMIN, COMMERCIAL | 200 | 400, 401, 403, 404, 422 | Modifier une interaction |
| DELETE | `/interactions/{id}` | ADMIN, COMMERCIAL | 204 | 400, 401, 403, 404 | Supprimer une interaction |

### Objets échangés

**Contact**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `clientId` | string (uuid) | oui |  |
| `nom` | string | oui |  |
| `prenom` | string | non |  |
| `fonction` | string | non |  |
| `email` | string (email) | non |  |
| `telephone` | string | non |  |

**ContactSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `nom` | string | oui |  |
| `prenom` | string | non |  |
| `fonction` | string | non |  |
| `email` | string (email) | non |  |
| `telephone` | string | non |  |

**Interaction**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `clientId` | string (uuid) | oui |  |
| `type` | string | oui | APPEL / EMAIL / REUNION |
| `date` | string (date-time) | oui |  |
| `description` | string | non |  |
| `auteurId` | string (uuid) | non | Pris dans le contexte utilisateur, jamais saisi |

**InteractionSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `type` | string | oui | APPEL / EMAIL / REUNION |
| `date` | string (date-time) | oui |  |
| `description` | string | non |  |

## 4. Règles métier à respecter

- Client inconnu (dans l'URL) → 404 sur toutes les routes `/clients/{id}/...`.
- Contact ou interaction inconnu → 404.
- `auteurId` est fixé à la **création** (valeur de `app.default-user-id`, voir l'étape E3) ; il n'est ni demandé ni modifiable ensuite.
- Un contact peut avoir un email différent de celui du client ; son email n'a **pas** de contrainte d'unicité.
- Les listes de contacts et d'interactions renvoient directement un tableau JSON (pas de pagination, voir `contrat_commun.md`) ; les interactions sont triées de la plus récente à la plus ancienne.
- Un client INACTIF garde ses contacts et interactions (consultables) ; la modification reste permise.
- Supprimer un client supprime aussi ses contacts et ses interactions dans la même transaction.

## 5. Tâches, dans l'ordre

- [ ] Contacts : `Contact` (entity), `ContactRepository`, `ContactService`, `ContactController`, `ContactSaisie` et `ContactReponse` (dto).
- [ ] Interactions : `Interaction` et `TypeInteraction` (entity), `InteractionRepository`, `InteractionService`, `InteractionController`, `InteractionSaisie` et `InteractionReponse` (dto).
- [ ] Brancher la suppression en cascade côté `ClientService` (ou `CascadeType.REMOVE`) et le test correspondant.
- [ ] Remplir `auteurId` depuis `app.default-user-id` (jamais depuis le corps).
- [ ] Ajouter les tests de la section 7 (« Tests à écrire ») et vérifier dans Swagger.

## 6. Fichiers créés ou modifiés

- `entity/ : Contact, Interaction, TypeInteraction ; repository/ : ContactRepository, InteractionRepository ; service/ : ContactService, InteractionService`
- `controller/ : ContactController, InteractionController ; dto/ : ContactSaisie, ContactReponse, InteractionSaisie, InteractionReponse (sous `com/example/customer/`)`
- `src/test/java/com/example/customer/ContactApiTests.java, InteractionApiTests.java`

## 7. Tests à écrire

- Tests d'API avec MockMvc (`@SpringBootTest` + `@AutoConfigureMockMvc` + profil `test`, base H2 en mémoire).
- Ajouter un contact à un client → 201 ; lister → 1 élément ; modifier → 200 ; supprimer → 204.
- Client inconnu → 404 ; contact inconnu → 404.
- Interaction créée avec le corps `{type, date, description}` : `auteurId` de la réponse vaut `app.default-user-id`, même si le corps en contient un.
- Type d'interaction inconnu → 400 ; `type` ou `date` absent → 422.
- Suppression d'un client → ses contacts et interactions disparaissent.

## 8. Points d'attention

- Ne jamais accepter `auteurId` dans le corps : le contrat le déclare absent de `InteractionSaisie`.
- `type` enum invalide : Jackson lève une erreur de lecture (donc 400 par défaut). Si l'on veut 422, la convertir dans le gestionnaire d'erreurs ; l'important est que le comportement soit celui du contrat et testé.
- Pour ne pas charger tous les contacts d'un coup, éviter les listes `@OneToMany` bidirectionnelles inutiles : les repositories filtrent par `clientId`.

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` : tout vert.
2. Dans Swagger (`http://localhost:8081/swagger-ui.html`), avec une fiche créée à l'étape B1 :
   - `POST /clients/{id}/contacts` avec `{"nom": "Ben Salah", "prenom": "Karim", "fonction": "Directeur", "email": "karim@atlas.tn"}` → 201.
   - `POST /clients/{id}/interactions` avec `{"type": "APPEL", "date": "2026-11-18T10:00:00Z", "description": "Premier appel de présentation"}` → 201, avec `auteurId` rempli.
   - `GET /clients/{id}/interactions` → l'interaction apparaît.
3. `DELETE /clients/{id}` puis `GET /clients/{id}/contacts` → 404.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-B2`, push (voir `README.md`).

Message de commit proposé :

```
feat(customer): contacts et interactions rattachés à une fiche client
```
