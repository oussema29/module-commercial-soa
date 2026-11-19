# Étape S2 : Quotation : devis, lignes et calcul

| | |
|---|---|
| **Service** | Quotation (sous-services 2.1 Devis et 2.3 Calcul) |
| **Responsable** | Oussema Souidi |
| **Ce que couvre l'étape** | Création et modification des devis, lignes, calcul HT / TVA / TTC, annulation, historique par client ; lecture de Customer (lien I1) |
| **Dossier de code** | `code/quotation-service` |
| **Code fourni** | `GUIDE_CODE_QUOTATION.md (section 6)` : arborescence, code complet de chaque fichier, routes, tests |
| **Prérequis** | S1 validée. Customer est **simulé** pendant le développement (réponses du contrat `customer.yaml`) ; l'essai réel se fait quand B1 est validée |
| **État** | À faire |

## 1. Objectif

Créer et modifier des devis : vérification du client auprès de Customer (I1), copie de ses coordonnées, lignes copiées depuis le catalogue, recalcul automatique des montants HT, TVA et TTC, annulation et liste filtrée.

**Pas dans cette étape :**

- Envoi par email, PDF, réponse du client par lien, expiration planifiée (étape S3).
- Événement `DevisAccepte` (étape E1).

## 2. Modèle de données

**Devis** :

| Champ | Type | Note |
|---|---|---|
| `id` | UUID | |
| `reference` | chaîne | unique, lisible : `DEV-<année>-<numéro sur 4 chiffres>` (par exemple `DEV-2026-0042`) |
| `date` | date | jour de création |
| `dateValidite` | date | doit être ≥ `date` (422 sinon) |
| `statut` | enum | `BROUILLON` au départ ; ensuite `ENVOYE`, `ACCEPTE`, `REFUSE`, `EXPIRE`, `ANNULE` |
| `remiseGlobale` | pourcentage 0 à 100 | 0 par défaut |
| `totalHT`, `totalTVA`, `totalTTC` | `BigDecimal` | **calculés**, jamais saisis |
| `clientId` | UUID | référence à Customer, **sans clé étrangère** |
| `clientNom`, `clientEmail` | chaînes | **copiés** depuis Customer à la création |
| `campagneId` | UUID | facultatif ; **non vérifié** par Quotation |
| `tokenReponse` | chaîne | créé à l'étape S3 ; jamais exposé aux employés |
| `repondueLe` | date-heure | rempli à l'étape S3 |
| `creePar` | UUID | valeur fixe `app.default-user-id` jusqu'au JWT (étape E3) |

**LigneDevis** : `id`, `devis` (clé étrangère interne), `produitId`, `designation`, `prixUnitaire`, `tauxTVA` (**copies** du catalogue au moment de l'ajout), `quantite` (entier > 0), `remise` (pourcentage 0 à 100), `totalLigneHT`.

## 3. API à réaliser

Source de vérité : le fichier OpenAPI du service dans `contrat_api/`. Les champs ci-dessous en sont extraits.

La colonne « Rôles » est le contrat : elle est **appliquée à l'étape E3** (JWT). D'ici là, aucune route ne vérifie le rôle.

**quotation.yaml**

| Méthode | Chemin | Rôles | Succès | Erreurs | Rôle de la route |
|---|---|---|---|---|---|
| POST | `/devis` | COMMERCIAL | 201 | 400, 401, 403, 404, 422, 503 | Créer un devis (BROUILLON) |
| GET | `/devis` | COMMERCIAL, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les devis |
| GET | `/devis/{id}` | COMMERCIAL, RESPONSABLE | 200 | 400, 401, 403, 404 | Consulter un devis |
| PUT | `/devis/{id}` | COMMERCIAL | 200 | 400, 401, 403, 404, 409, 422 | Modifier un devis |
| POST | `/devis/{id}/lignes` | COMMERCIAL | 201 | 400, 401, 403, 404, 409, 422 | Ajouter une ligne |
| PUT | `/devis/{id}/lignes/{ligneId}` | COMMERCIAL | 200 | 400, 401, 403, 404, 409, 422 | Modifier une ligne |
| DELETE | `/devis/{id}/lignes/{ligneId}` | COMMERCIAL | 200 | 400, 401, 403, 404, 409 | Supprimer une ligne |
| POST | `/devis/{id}/annuler` | COMMERCIAL | 200 | 400, 401, 403, 404, 409 | Annuler un devis (BROUILLON ou ENVOYE) |

### Objets échangés

**DevisCreation** : Refusé (422) si le client est INACTIF ; 404 si le client n'existe pas ; 503 si Customer est indisponible.

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `clientId` | string (uuid) | oui |  |
| `dateValidite` | string (date) | oui | Postérieure ou égale à la date du devis |
| `campagneId` | string (uuid) | non | Facultatif, non vérifié par Quotation ; l'interface propose la liste des campagnes |
| `remiseGlobale` | number | non |  |

**Devis** : Les montants sont calculés par le service, jamais saisis. Le jeton de réponse n'est jamais exposé.

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `reference` | string | oui |  |
| `date` | string (date) | non |  |
| `dateValidite` | string (date) | non |  |
| `statut` | string | oui | BROUILLON / ENVOYE / ACCEPTE / REFUSE / EXPIRE / ANNULE |
| `remiseGlobale` | number | non |  |
| `totalHT` | Montant | non |  |
| `totalTVA` | Montant | non |  |
| `totalTTC` | Montant | oui |  |
| `clientId` | string (uuid) | oui |  |
| `clientNom` | string | non |  |
| `clientEmail` | string (email) | non |  |
| `campagneId` | string (uuid) | non | peut être null |
| `repondueLe` | string (date-time) | non | peut être null |
| `creePar` | string (uuid) | non |  |
| `lignes` | liste de LigneDevis | non |  |

**LigneDevis**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `produitId` | string (uuid) | oui |  |
| `designation` | string | oui |  |
| `prixUnitaire` | Montant | oui |  |
| `tauxTVA` | number | oui | Pourcentage, copié du catalogue |
| `quantite` | integer | oui |  |
| `remise` | number | non |  |
| `totalLigneHT` | Montant | oui |  |

**DevisModification** : Devis BROUILLON seulement (409 sinon).

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `dateValidite` | string (date) | oui |  |
| `campagneId` | string (uuid) | non | peut être null |
| `remiseGlobale` | number | non |  |
| `clientEmail` | string (email) | non | Corrige l'email copié dans le devis |

**LigneSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `produitId` | string (uuid) | oui |  |
| `quantite` | integer | oui |  |
| `remise` | number | non |  |

**LigneModification**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `quantite` | integer | oui |  |
| `remise` | number | non |  |

## 4. Règles métier à respecter

- **Création** : le service appelle `GET /clients/{id}` de Customer **une seule fois**. Client inconnu (404 de Customer) → 404 ; client `INACTIF` → 422 ; Customer injoignable ou en erreur → 503 « Service Customer indisponible » **sans rien créer**.
- À la création, `clientNom` et `clientEmail` sont copiés ; le devis reste utilisable si Customer tombe ensuite.
- `dateValidite` ≥ `date` du devis, sinon 422.
- `campagneId` est facultatif et n'est **pas vérifié** (Quotation n'appelle pas Campaign) ; il peut désigner une campagne terminée.
- **Lignes** : à l'ajout, le service copie `designation`, `prixUnitaire` et `tauxTVA` du produit (catalogue) dans la ligne. Produit inconnu → 404 ; `quantite` ≤ 0 ou `remise` hors 0 à 100 → 422.
- **Calcul automatique** à chaque ajout, modification ou suppression de ligne, et à chaque changement de `remiseGlobale` : `totalLigneHT = prixUnitaire × quantite × (1 − remise/100)` ; `totalHT = somme(totalLigneHT) × (1 − remiseGlobale/100)` ; `totalTVA = somme(totalLigneHT × tauxTVA/100) × (1 − remiseGlobale/100)` ; `totalTTC = totalHT + totalTVA`. Arrondi à 3 décimales (`RoundingMode.HALF_UP`) aux totaux, avec `BigDecimal` partout.
- Les routes de lignes renvoient le **devis complet** recalculé.
- **Seul un devis BROUILLON** peut être modifié ou voir ses lignes changer → 409 sinon.
- **Annuler** : possible avant la réponse du client (BROUILLON ou ENVOYE) → ANNULE ; sinon 409. Aucun événement.
- Liste `GET /devis` paginée, filtres `clientId`, `statut`, `campagneId` (c'est aussi l'historique des devis d'un client).

## 5. Tâches, dans l'ordre

- [ ] Créer le paquet `devis` : entités `Devis` et `LigneDevis`, enum `StatutDevis`, repositories (filtres de liste en `Specification`).
- [ ] Créer `CustomerClient` (dossier `client`) : un `RestClient` configuré par `app.customer-url` (par défaut `http://localhost:8081`) qui appelle `GET /clients/{id}` et convertit les erreurs : 404 → introuvable, panne ou délai dépassé → `ApiException` 503. Délais de connexion et de lecture courts (2 s).
- [ ] Créer `CalculateurDevis` : une classe sans état qui recalcule les totaux (facile à tester seule).
- [ ] Créer `ReferenceDevis` : génération de `DEV-<année>-<numéro>` (numéro séquentiel par année, sans doublon).
- [ ] Écrire `DevisService` (création avec I1, modification, lignes, annulation, liste) et `DevisController`.
- [ ] Tests : le calcul seul (`CalculateurDevisTest`) puis l'API avec le client Customer **simulé** (`@MockBean` ou serveur de test), jamais le vrai service dans les tests.
- [ ] Essai réel avec Customer démarré, puis Customer arrêté (503).

## 6. Fichiers créés ou modifiés

- `entity/ : Devis, LigneDevis, StatutDevis ; repository/ : DevisRepository, LigneDevisRepository ; service/ : DevisService, CalculateurDevis, ReferenceDevis ; controller/ : DevisController ; dto/ : objets d'échange (sous `com/example/quotation/`)`
- `client/ : CustomerClient, ClientCustomer (réponse reçue de Customer)`
- `src/main/resources/application.yml (clé `app.customer-url`)`
- `src/test/java/com/example/quotation/CalculateurDevisTest.java, DevisApiTests.java`

## 7. Tests à écrire

- Tests d'API avec MockMvc (`@SpringBootTest` + `@AutoConfigureMockMvc` + profil `test`, base H2 en mémoire).
- **Calcul** : une ligne 2 × 1250,500 avec remise 10 % et TVA 19 % ; deux lignes de TVA différentes ; remise globale 5 % ; arrondi à 3 décimales ; devis sans ligne → totaux à 0.
- Création : client simulé existant → 201, `clientNom` et `clientEmail` copiés, statut `BROUILLON`.
- Client simulé introuvable → 404 ; client INACTIF → 422 ; Customer indisponible → 503 et **aucun devis créé** (vérifier en base).
- `dateValidite` antérieure à `date` → 422.
- Ajout d'une ligne : copies du catalogue ; si le prix du produit change ensuite, la ligne **ne change pas**.
- Modification d'une ligne : totaux recalculés ; suppression de la dernière ligne → totaux à 0.
- Devis non BROUILLON : ajout de ligne ou `PUT` → 409.
- Annulation : BROUILLON → ANNULE ; ACCEPTE ou ANNULE → 409.
- Liste filtrée par `clientId`, `statut`, `campagneId` ; pagination.

## 8. Points d'attention

- Les tests ne doivent **jamais** dépendre de Customer réel. Ils simulent la réponse de `GET /clients/{id}` (cas : existe, INACTIF, introuvable, indisponible).
- Panne de Customer : si l'appel échoue, **ne rien enregistrer** ; la création du devis et l'appel se font dans cet ordre (appel d'abord, enregistrement ensuite).
- Le jeton `tokenReponse` ne doit jamais apparaître dans les réponses de `/devis` : il n'est pas dans le contrat. Il sera créé à l'étape S3.
- Tous les montants sont des `BigDecimal` créés à partir de chaînes (`new BigDecimal("1250.500")`), jamais de `double`.
- Le numéro de référence doit rester unique même si deux devis sont créés en même temps : contrainte unique en base et nouvel essai en cas de conflit.

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` dans `quotation-service` : tout vert (dont `CalculateurDevisTest`).
2. Démarrer Customer, Quotation et les bases. Créer un client dans Customer, et un produit dans Quotation.
3. Dans Swagger de Quotation :
   - `POST /devis` avec `{"clientId": "<id du client>", "dateValidite": "2026-12-31"}` → 201, `clientNom` et `clientEmail` copiés.
   - `POST /devis/{id}/lignes` avec `{"produitId": "<id>", "quantite": 2, "remise": 10}` → le devis renvoyé contient `totalHT`, `totalTVA`, `totalTTC` justes (vérifier à la calculatrice).
4. **Panne** : arrêter Customer puis refaire `POST /devis` → **503**, et `GET /devis` ne contient aucun nouveau devis.
5. Passer le client en INACTIF (Customer) puis `POST /devis` → **422**.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-S2`, push (voir `README.md`).

Message de commit proposé :

```
feat(quotation): devis, lignes, calcul automatique et lecture de Customer (I1)
```
