# Étape S1 : Quotation : squelette, catalogue et taxes

| | |
|---|---|
| **Service** | Quotation (squelette, sous-services 2.2 Catalogue et 2.3 Calcul : taxes) |
| **Responsable** | Oussema Souidi |
| **Ce que couvre l'étape** | Catalogue de produits et taux de TVA |
| **Dossier de code** | `code/quotation-service` |
| **Prérequis** | Aucun autre service : Quotation démarre seul (la structure de dossiers est décrite en B0) |
| **État** | Réalisé ; à valider par le trinôme |

## 1. Objectif

Créer le service Quotation avec la même structure que Customer (dossiers `controller`, `service`, `repository`, `entity`, `dto`, `exception`), puis réaliser le catalogue (produits) et les taux de TVA, dont dépendent les devis de l'étape S2.

**Pas dans cette étape :**

- Devis et lignes (étape S2).
- Les services Customer et Campaign : un autre membre les réalise.

## 2. Modèle de données

**Taxe** : `id` (UUID), `tauxTVA` (pourcentage 0 à 100, `BigDecimal`, obligatoire), `description`.

**Produit** : `id` (UUID), `nom` (obligatoire), `description`, `prix` (`BigDecimal` ≥ 0, trois décimales au plus, en dinars), `taxe` (lien vers `Taxe`, obligatoire, clé étrangère interne à `quotation_db`).

## 3. API à réaliser

Source de vérité : le fichier OpenAPI du service dans `contrat_api/`. Les champs ci-dessous en sont extraits.

La colonne « Rôles » est le contrat : elle est **appliquée à l'étape E3** (JWT). D'ici là, aucune route ne vérifie le rôle.

**quotation.yaml**

| Méthode | Chemin | Rôles | Succès | Erreurs | Rôle de la route |
|---|---|---|---|---|---|
| GET | `/produits` | ADMIN, COMMERCIAL, MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les produits |
| POST | `/produits` | ADMIN | 201 | 400, 401, 403, 404, 422 | Créer un produit |
| GET | `/produits/{id}` | ADMIN, COMMERCIAL, MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Consulter un produit |
| PUT | `/produits/{id}` | ADMIN | 200 | 400, 401, 403, 404, 422 | Modifier un produit |
| DELETE | `/produits/{id}` | ADMIN | 204 | 400, 401, 403, 404 | Supprimer un produit |
| GET | `/taxes` | ADMIN, COMMERCIAL, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les taxes |
| POST | `/taxes` | ADMIN | 201 | 400, 401, 403, 404, 422 | Créer une taxe |
| PUT | `/taxes/{id}` | ADMIN | 200 | 400, 401, 403, 404, 422 | Modifier une taxe |

### Objets échangés

**Produit**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `nom` | string | oui |  |
| `description` | string | non |  |
| `prix` | Montant | oui |  |
| `taxeId` | string (uuid) | oui |  |

**ProduitSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `nom` | string | oui |  |
| `description` | string | non |  |
| `prix` | Montant | oui |  |
| `taxeId` | string (uuid) | oui |  |

**Taxe**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `tauxTVA` | number | oui |  |
| `description` | string | non |  |

**TaxeSaisie**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `tauxTVA` | number | oui |  |
| `description` | string | non |  |

## 4. Règles métier à respecter

- **Squelette** : reprendre la structure de Customer (`controller`, `service`, `repository`, `entity`, `dto`, `exception`, dont `ApiException`, `ErrorResponse`, `GlobalExceptionHandler` et `PageResponse`) avec le paquet `com.example.quotation` : **aucun code partagé entre services**. Port 8082, base `quotation_db` (MySQL 3308).
- Produit : `prix` ≥ 0 et trois décimales au plus ; `taxeId` obligatoire et existant (sinon 404 ou 422 : le contrat prévoit les deux codes, en choisir un et le fixer dans le test) ; `GET /produits` paginée ; `DELETE` → 204.
- **Suppression d'un produit** : sans effet sur les devis existants, car ils gardent leur copie (désignation, prix, TVA).
- Taxe : `tauxTVA` entre 0 et 100 ; pas de suppression (le contrat n'en prévoit pas) car des produits la référencent.
- Les montants se lisent et s'écrivent comme **nombres décimaux** JSON ; en Java, `BigDecimal` partout.

## 5. Tâches, dans l'ordre

- [ ] Créer `quotation-service/` : `pom.xml` (mêmes dépendances que Customer, plus la génération de PDF à l'étape S3), `QuotationServiceApplication` (port 8082), `application.yml` (base 3308), `application-test.yml`, dossiers de base et classes d'erreur.
- [ ] Quotation : `Taxe` et `Produit` (entity), `TaxeRepository`, `ProduitRepository`, `TaxeService`, `ProduitService`, `TaxeController`, `ProduitController`, objets d'échange (dto).
- [ ] Pagination de `GET /produits` avec la classe `PageResponse`.
- [ ] Tests de la section 7 (« Tests à écrire ») ; vérifier que Quotation démarre seul.

## 6. Fichiers créés ou modifiés

- `code/quotation-service/ (pom.xml, application.yml, application-test.yml, QuotationServiceApplication, exception/…)`
- `code/quotation-service/src/main/java/com/example/quotation/ : entity/Taxe, Produit ; repository/ ; service/ ; controller/ ; dto/`
- `code/README.md (mise à jour)`

## 7. Tests à écrire

- Tests d'API avec MockMvc (`@SpringBootTest` + `@AutoConfigureMockMvc` + profil `test`, base H2 en mémoire).
- Démarrage de Quotation (profil `test`).
- Créer une taxe 19 % puis un produit avec cette taxe → 201 ; `prix` négatif → 422 ; quatre décimales → 422.
- Produit avec `taxeId` inconnu → erreur du contrat (404 ou 422, à fixer dans le test d'après `quotation.yaml`).
- Modifier un produit → 200 ; supprimer → 204 ; `GET` ensuite → 404.

## 8. Points d'attention

- Chaque service a ses propres classes d'erreur : la duplication est volontaire (services indépendants, déployables seuls). Ne pas créer de bibliothèque partagée.
- Les montants de l'API sont des **nombres JSON** ; ne pas les déclarer `double` dans les objets Java, sinon l'erreur d'arrondi revient.
- Le catalogue appartient à Quotation. Campaign ne le lit jamais : l'interface le lit et envoie `produitId` et `produitNom` à Campaign (étape A2).

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` dans `quotation-service` : tout vert.
2. `docker compose up -d`, puis `mvn spring-boot:run` dans `quotation-service` : `http://localhost:8082/actuator/health` répond `UP`.
3. Dans Swagger de Quotation (`http://localhost:8082/swagger-ui.html`) :
   - `POST /taxes` avec `{"tauxTVA": 19, "description": "TVA standard"}` → 201.
   - `POST /produits` avec `{"nom": "Étude de faisabilité", "description": "Forfait 5 jours", "prix": 1250.500, "taxeId": "<id>"}` → 201.
   - `GET /produits` → liste paginée avec ce produit.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-S1`, push (voir `README.md`).

Message de commit proposé :

```
feat(quotation): squelette du service, catalogue et taxes
```
