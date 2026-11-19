# Étape A2 : Campaign : campagnes, publics, canaux, contenus et budget

| | |
|---|---|
| **Service** | Campaign (sous-services 3.1 Campagnes, 3.2 Ciblage et canaux, 3.3 Contenus, 3.4 Performance : budget) |
| **Responsable** | Oussema Arfaoui |
| **Ce que couvre l'étape** | Campagnes et cycle de vie, publics, canaux, contenus, budget prévu |
| **Dossier de code** | `code/campaign-service` |
| **Prérequis** | A1 validée |
| **État** | Fait (Oussema Arfaoui) : testé |

## 1. Objectif

Construire une campagne en BROUILLON : la créer, définir ses publics, ses canaux, ses contenus et son budget prévu ; suspendre, reprendre et arrêter selon le cycle de vie. Le lancement (qui lit Customer) vient à l'étape suivante (A3).

**Pas dans cette étape :**

- `lancer`, invitations, réponses publiques (étape A3).
- Mesures, performance, CPC, ROI, rapport PDF, tâche de fin de campagne (étape A4).
- Vérification de la catégorie d'un public auprès de Customer (voir « Points d'attention »).

## 2. Modèle de données

**Campagne** : `id` (UUID), `nom` (obligatoire), `objectif` (`ACQUISITION` / `FIDELISATION` / `VENTES`, descriptif), `dateDebut`, `dateFin` (`dateFin` ≥ `dateDebut`), `statut` (`BROUILLON`, `ACTIVE`, `SUSPENDUE`, `TERMINEE`), `creePar` (valeur fixe `app.default-user-id` jusqu'au JWT).

**PerformanceCampagne** (une seule ligne par campagne, **créée avec la campagne**) : `id`, `campagne` (clé étrangère interne), `montantPrevu` (vide tant que non défini), `montantDepense` (0), `impressions` (0), `clics` (0), `prospectsGeneres` (0), `devisAcceptes` (0), `chiffreAffairesAttribue` (0). Le CPC, le ROI et `clientsInteresses` ne sont **pas** stockés.

**PublicCible** : `id`, `campagne`, `type` (`CLIENTS_EXISTANTS` / `PUBLIC_EXTERNE`), `categorieClientId` (UUID externe, obligatoire pour `CLIENTS_EXISTANTS`), `ageMin`, `ageMax`, `localisation`, `centresInteret`, `description` (critères du public externe).

**CanalPublicitaire** : `id`, `nom` (libellé libre), `type` (`FACEBOOK` / `INSTAGRAM` / `GOOGLE_ADS` / `EMAIL`).

**CampagneCanal** : `id` (**entier auto-incrémenté**, interne, jamais exposé), `campagne`, `canal`, couple unique.

**ContenuPublicitaire** : `id`, `campagne`, `titre`, `texte`, `imageUrl`, `videoUrl` (un lien, jamais une pièce jointe), `lien`.

**ContenuProduit** : `id` (entier interne), `contenu`, `produitId` (UUID externe, Quotation), `produitNom` (**copie**).

## 3. API à réaliser

Source de vérité : le fichier OpenAPI du service dans `contrat_api/`. Les champs ci-dessous en sont extraits.

La colonne « Rôles » est le contrat : elle est **appliquée à l'étape E3** (JWT). D'ici là, aucune route ne vérifie le rôle.

**campaign.yaml**

| Méthode | Chemin | Rôles | Succès | Erreurs | Rôle de la route |
|---|---|---|---|---|---|
| POST | `/campagnes` | MARKETING | 201 | 400, 401, 403, 404, 422 | Créer une campagne (BROUILLON) |
| GET | `/campagnes` | COMMERCIAL, MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les campagnes |
| GET | `/campagnes/{id}` | COMMERCIAL, MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Consulter une campagne |
| PUT | `/campagnes/{id}` | MARKETING | 200 | 400, 401, 403, 404, 409, 422 | Modifier une campagne |
| DELETE | `/campagnes/{id}` | MARKETING | 204 | 400, 401, 403, 404, 409 | Supprimer une campagne BROUILLON |
| POST | `/campagnes/{id}/suspendre` | MARKETING | 200 | 400, 401, 403, 404, 409 | Suspendre (ACTIVE vers SUSPENDUE) |
| POST | `/campagnes/{id}/reprendre` | MARKETING | 200 | 400, 401, 403, 404, 409 | Reprendre (SUSPENDUE vers ACTIVE) |
| POST | `/campagnes/{id}/arreter` | MARKETING | 200 | 400, 401, 403, 404, 409 | Terminer définitivement (vers TERMINEE) |
| GET | `/campagnes/{id}/publics` | MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les publics |
| POST | `/campagnes/{id}/publics` | MARKETING | 201 | 400, 401, 403, 404, 409, 422 | Définir un public |
| DELETE | `/publics/{id}` | MARKETING | 204 | 400, 401, 403, 404, 409 | Supprimer un public |
| GET | `/canaux` | ADMIN, MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les canaux |
| POST | `/canaux` | ADMIN | 201 | 400, 401, 403, 404, 422 | Créer un canal |
| POST | `/campagnes/{id}/canaux` | MARKETING | 201 | 400, 401, 403, 404, 409, 422 | Associer un canal |
| DELETE | `/campagnes/{id}/canaux/{canalId}` | MARKETING | 204 | 400, 401, 403, 404, 409 | Retirer un canal |
| GET | `/campagnes/{id}/contenus` | MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les contenus |
| POST | `/campagnes/{id}/contenus` | MARKETING | 201 | 400, 401, 403, 404, 422 | Ajouter un contenu |
| PUT | `/contenus/{id}` | MARKETING | 200 | 400, 401, 403, 404, 422 | Modifier un contenu |
| DELETE | `/contenus/{id}` | MARKETING | 204 | 400, 401, 403, 404 | Supprimer un contenu |
| POST | `/contenus/{id}/produits` | MARKETING | 201 | 400, 401, 403, 404, 422 | Ajouter un produit mis en avant |
| DELETE | `/contenus/{id}/produits/{produitId}` | MARKETING | 204 | 400, 401, 403, 404 | Retirer un produit du contenu |
| GET | `/campagnes/{id}/budget` | MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Consulter le budget |
| PUT | `/campagnes/{id}/budget` | MARKETING | 200 | 400, 401, 403, 404, 422 | Définir le budget prévu |

### Objets échangés

**CampagneSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `nom` | string | oui |  |
| `objectif` | string | oui | ACQUISITION / FIDELISATION / VENTES |
| `dateDebut` | string (date) | oui |  |
| `dateFin` | string (date) | oui | Postérieure ou égale à dateDebut |

**Campagne**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `nom` | string | oui |  |
| `objectif` | string | oui | ACQUISITION / FIDELISATION / VENTES |
| `dateDebut` | string (date) | oui |  |
| `dateFin` | string (date) | oui |  |
| `statut` | string | oui | BROUILLON / ACTIVE / SUSPENDUE / TERMINEE |
| `creePar` | string (uuid) | non |  |

**PublicCible**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `campagneId` | string (uuid) | oui |  |
| `type` | string | oui | CLIENTS_EXISTANTS / PUBLIC_EXTERNE |
| `categorieClientId` | string (uuid) | non | peut être null |
| `ageMin` | integer | non | peut être null |
| `ageMax` | integer | non | peut être null |
| `localisation` | string | non | peut être null |
| `centresInteret` | string | non | peut être null |
| `description` | string | non | peut être null |

**PublicCibleSaisie** : CLIENTS_EXISTANTS : categorieClientId obligatoire, aucun critère de profil. PUBLIC_EXTERNE : critères de profil, pas de categorieClientId. Sinon 422.

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `type` | string | oui | CLIENTS_EXISTANTS / PUBLIC_EXTERNE |
| `categorieClientId` | string (uuid) | non |  |
| `ageMin` | integer | non |  |
| `ageMax` | integer | non |  |
| `localisation` | string | non |  |
| `centresInteret` | string | non |  |
| `description` | string | non |  |

**Canal**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `nom` | string | oui |  |
| `type` | string | oui | FACEBOOK / INSTAGRAM / GOOGLE_ADS / EMAIL |

**CanalSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `nom` | string | oui |  |
| `type` | string | oui | FACEBOOK / INSTAGRAM / GOOGLE_ADS / EMAIL |

**CanalAssociation**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `canalId` | string (uuid) | oui |  |

**Contenu**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `campagneId` | string (uuid) | oui |  |
| `titre` | string | oui |  |
| `texte` | string | non |  |
| `imageUrl` | string | non | peut être null |
| `videoUrl` | string | non | peut être null ; Un lien, jamais une pièce jointe |
| `lien` | string | non | peut être null |
| `produits` | liste de ProduitMisEnAvant | non |  |

**ProduitMisEnAvant**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `produitId` | string (uuid) | oui |  |
| `produitNom` | string | oui | Copie du nom au moment de l'ajout |

**ContenuSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `titre` | string | oui |  |
| `texte` | string | non |  |
| `imageUrl` | string | non |  |
| `videoUrl` | string | non |  |
| `lien` | string | non |  |

**Budget**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `montantPrevu` | Montant | non | peut être null ; Vide tant que non défini |
| `montantDepense` | Montant | oui |  |

**BudgetSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `montantPrevu` | Montant | oui | 0 accepté |

## 4. Règles métier à respecter

- **Création** : statut `BROUILLON` ; `dateFin` ≥ `dateDebut` (422 sinon) ; la ligne `PerformanceCampagne` est créée dans la **même transaction**, avec `montantPrevu` vide.
- **Cycle de vie** : `suspendre` ACTIVE → SUSPENDUE ; `reprendre` SUSPENDUE → ACTIVE (409 aussi si `dateFin` est dépassée) ; `arreter` ACTIVE ou SUSPENDUE → TERMINEE, définitif ; toute autre transition → 409. (Aucune de ces routes n'appelle un autre service.)
- **Modifier** (`PUT`) : champs de la campagne **seulement en BROUILLON** (409 sinon) ; les contenus restent modifiables à tout moment.
- **Supprimer** : BROUILLON seulement (409 sinon) ; supprime aussi performance, publics, canaux associés et contenus.
- **Publics** : ajout et suppression **seulement en BROUILLON** (409 sinon) ; un public ne se modifie pas (on supprime et on recrée). `CLIENTS_EXISTANTS` n'a que `categorieClientId` (obligatoire) ; `PUBLIC_EXTERNE` n'a que les critères de profil (âge, localisation, centres d'intérêt) ; mélanger → 422. Plusieurs publics, de types différents, sont permis.
- **Canaux** : un canal ne se modifie ni ne se supprime (des campagnes le référencent) ; association et retrait **seulement en BROUILLON** ; associer deux fois le même canal → 409 ; canal inconnu → 404.
- **Cohérence canal / public** : `CLIENTS_EXISTANTS` exige un canal `EMAIL` ; `PUBLIC_EXTERNE` exige un canal de diffusion large (`FACEBOOK`, `INSTAGRAM` ou `GOOGLE_ADS`). Elle est **vérifiée au lancement** (étape A3) : ici on écrit seulement la fonction `verifierCoherence(campagne)` et ses tests.
- **Contenus** : ajout, modification et suppression à tout moment ; la vidéo est un lien ; les produits mis en avant sont **copiés** (`produitId` et `produitNom` envoyés par l'interface, Campaign ne rappelle pas Quotation).
- **Budget** : `PUT /campagnes/{id}/budget` ne modifie que `montantPrevu` (≥ 0, 0 accepté) ; `GET` renvoie prévu et dépensé. Le dépensé ne change que par `PUT /mesures` (étape A4).

## 5. Tâches, dans l'ordre

- [ ] Campagnes : `Campagne`, `PerformanceCampagne`, `StatutCampagne`, `ObjectifCampagne` (entity), repositories, `CampagneService`, `CampagneController`, objets d'échange (dto).
- [ ] Écrire `CycleDeVie` (méthodes `suspendre`, `reprendre`, `arreter` sur l'entité, avec l'horloge injectable pour `dateFin`).
- [ ] Ciblage : `PublicCible`, `CanalPublicitaire`, `CampagneCanal` (entity), repositories, services, contrôleurs, et `CoherenceCanalPublic` (service).
- [ ] Contenus : `ContenuPublicitaire`, `ContenuProduit` (entity), repository, service, contrôleur.
- [ ] Budget : `BudgetService` et `BudgetController` (lecture, définition du prévu).
- [ ] Vérifier que `DELETE` supprime tout en cascade dans une seule transaction.
- [ ] Tests de la section 7 (« Tests à écrire ») ; essai dans Swagger (`http://localhost:8083/swagger-ui.html`).

## 6. Fichiers créés ou modifiés

- `entity/ : Campagne, StatutCampagne, ObjectifCampagne, PerformanceCampagne, PublicCible, CanalPublicitaire, CampagneCanal, ContenuPublicitaire, ContenuProduit (sous `com/example/campaign/`)`
- `repository/ : un repository par entité ; service/ : CampagneService, PublicCibleService, CanalService, ContenuService, BudgetService, CoherenceCanalPublic`
- `controller/ : CampagneController, PublicCibleController, CanalController, ContenuController, BudgetController ; dto/ : objets d'échange`
- `src/test/java/com/example/campaign/ : CampagneApiTests, CycleDeVieTest, PublicCibleApiTests, CanalApiTests, ContenuApiTests, BudgetApiTests, CoherenceCanalPublicTest`

## 7. Tests à écrire

- Tests d'API avec MockMvc (`@SpringBootTest` + `@AutoConfigureMockMvc` + profil `test`, base H2 en mémoire).
- Création → 201, `BROUILLON`, ligne de performance présente (budget prévu vide).
- `dateFin` avant `dateDebut` → 422.
- Cycle de vie : toutes les transitions autorisées et au moins une refusée par état (par exemple `suspendre` sur BROUILLON → 409) ; `reprendre` après `dateFin` → 409 (horloge fixée).
- `PUT` d'une campagne ACTIVE → 409 ; `PUT` d'une campagne BROUILLON → 200.
- `DELETE` : BROUILLON → 204 et plus aucune ligne liée en base ; ACTIVE → 409.
- Public `CLIENTS_EXISTANTS` sans `categorieClientId` → 422 ; public `PUBLIC_EXTERNE` avec `categorieClientId` → 422 ; ajout sur une campagne ACTIVE → 409 ; suppression sur une campagne ACTIVE → 409.
- Canal : association deux fois → 409 ; retrait sur une campagne ACTIVE → 409.
- `CoherenceCanalPublic` : EXISTANTS + EMAIL → ok ; EXISTANTS sans EMAIL → refusé ; EXTERNE + FACEBOOK → ok ; EXTERNE + EMAIL seul → refusé ; deux publics de types différents exigent les deux canaux.
- Contenus : modification d'un contenu d'une campagne ACTIVE → 200 ; ajout puis retrait d'un produit mis en avant.
- Budget : `PUT` 0 → 200 ; négatif → 422 ; `GET` renvoie prévu et dépensé.

## 8. Points d'attention

- La campagne est **BROUILLON** tant qu'elle n'est pas lancée : toute la configuration se fait à ce stade. C'est ce qui rend inutile de recalculer des invitations déjà envoyées.
- Ne pas stocker CPC, ROI ni `clientsInteresses` : ils se calculent à la lecture (étape A4).
- Le contrôle de `categorieClientId` auprès de Customer (créer un public) est **prévu pour plus tard** : il demandera une lecture de `GET /categories` de Customer et un 422 si la catégorie n'existe pas, 503 si Customer est indisponible. À décider à ce moment avec le trinôme ; l'étape A3 le fait de toute façon au lancement (`GET /clients/destinataires` répond 404 si la catégorie n'existe pas, transformé en 422).
- `CampagneCanal` et `ContenuProduit` ont un identifiant **entier** interne qui n'apparaît jamais dans l'API.
- Horloge injectable pour toutes les règles de date (`reprendre`, plus tard `lancer` et la tâche de fin).
- Quand on ajoute ou retire un enfant (public, contenu, produit) dans une collection de la campagne, appeler `flush()` : sans cela, dans un test où tout se passe dans une seule transaction, l'enfant n'est pas retrouvable par son identifiant avant la fin de la transaction (en production chaque requête a sa transaction et le problème ne se voit pas).

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` dans `campaign-service` : tout vert.
2. Dans Swagger de Campaign (`http://localhost:8083/swagger-ui.html`), :
   - `POST /canaux` `{"nom": "Email principal", "type": "EMAIL"}`.
   - `POST /campagnes` `{"nom": "Offre fin d'année", "objectif": "FIDELISATION", "dateDebut": "2026-12-01", "dateFin": "2026-12-31"}` → 201.
   - `POST /campagnes/{id}/canaux`, `POST /campagnes/{id}/publics` (`CLIENTS_EXISTANTS` avec une catégorie), `POST /campagnes/{id}/contenus`, `PUT /campagnes/{id}/budget` `{"montantPrevu": 500}`.
   - `GET /campagnes/{id}/budget` → prévu 500, dépensé 0.
3. `DELETE /campagnes/{id}` → 204 ; `GET /campagnes/{id}` → 404.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-A2`, push (voir `README.md`).

Message de commit proposé :

```
feat(campaign): campagnes, publics, canaux, contenus et budget prévu
```
