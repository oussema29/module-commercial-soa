# Étape A4 : Campaign : mesures, performance, ROI, rapport et fin planifiée

| | |
|---|---|
| **Service** | Campaign (sous-service 3.4 Performance et budget, et tâche planifiée) |
| **Responsable** | Oussema Arfaoui |
| **Ce que couvre l'étape** | Mesures, performance (CPC, ROI), rapport PDF, fin automatique des campagnes |
| **Dossier de code** | `code/campaign-service` |
| **Prérequis** | A3 validée (l'essai du chiffre d'affaires attribué demande E1) |
| **État** | Réalisé ; à valider par le trinôme |

## 1. Objectif

Saisir les mesures cumulées (simulées), consulter la performance avec CPC et ROI calculés à la lecture, produire le rapport PDF, et terminer automatiquement chaque jour les campagnes dont la date de fin est dépassée.

**Pas dans cette étape :**

- Accès réel aux plateformes publicitaires : impressions, clics et dépenses sont saisis ou simulés.

## 2. Modèle de données

Aucune nouvelle table. Les champs de `PerformanceCampagne` (créés à l'étape A2) sont utilisés : `montantPrevu`, `montantDepense`, `impressions`, `clics`, `prospectsGeneres`, `devisAcceptes`, `chiffreAffairesAttribue`.

**Valeurs calculées à la lecture** (jamais stockées) :
- `clientsInteresses` = nombre d'invitations de la campagne dont `repondueLe` est renseigné ;
- `cpc = montantDepense / clics` (null si `clics` = 0) ;
- `roi = (chiffreAffairesAttribue − montantDepense) / montantDepense` (null si `montantDepense` = 0).

## 3. API à réaliser

Source de vérité : le fichier OpenAPI du service dans `contrat_api/`. Les champs ci-dessous en sont extraits.

La colonne « Rôles » est le contrat : elle est **appliquée à l'étape E3** (JWT). D'ici là, aucune route ne vérifie le rôle.

**campaign.yaml**

| Méthode | Chemin | Rôles | Succès | Erreurs | Rôle de la route |
|---|---|---|---|---|---|
| PUT | `/campagnes/{id}/mesures` | MARKETING | 200 | 400, 401, 403, 404, 422 | Remplacer les mesures cumulées |
| GET | `/campagnes/{id}/performance` | MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Consulter la performance |
| GET | `/campagnes/{id}/rapport` | MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Rapport de performance (PDF) |

### Objets échangés

**MesuresSaisie** : Remplace les totaux cumulés (idempotent). Seule route qui modifie le dépensé.

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `impressions` | integer | oui |  |
| `clics` | integer | oui |  |
| `montantDepense` | Montant | oui |  |

**Performance**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `campagneId` | string (uuid) | oui |  |
| `montantPrevu` | Montant | non | peut être null |
| `montantDepense` | Montant | oui |  |
| `impressions` | integer | non |  |
| `clics` | integer | non |  |
| `prospectsGeneres` | integer | non | Formulaires publics reçus |
| `clientsInteresses` | integer | non | Calculé : invitations répondues |
| `devisAcceptes` | integer | oui |  |
| `chiffreAffairesAttribue` | Montant | oui |  |
| `cpc` | Montant | non | peut être null ; montantDepense / clics, calculé, vide si clics = 0 |
| `roi` | number | non | peut être null ; (CA − dépensé) / dépensé, calculé, vide si dépensé = 0 |

## 4. Règles métier à respecter

- `PUT /campagnes/{id}/mesures` **remplace** les totaux cumulés (`impressions`, `clics`, `montantDepense`) : envoyé deux fois, il donne le même résultat (idempotent). Valeurs négatives → 422. C'est la **seule** route qui modifie le dépensé.
- `GET /campagnes/{id}/performance` : renvoie les indicateurs stockés et les valeurs calculées (voir le modèle) ; arrondis à 3 décimales ; division par zéro → `null`, jamais une erreur.
- `GET /campagnes/{id}/rapport` : PDF avec les mêmes indicateurs que `/performance`.
- **Tâche quotidienne** : passe à TERMINEE les campagnes ACTIVE ou SUSPENDUE dont `dateFin` est strictement passée ; sans effet sur BROUILLON et TERMINEE. Après cela, `reprendre` est refusé (étape A2).
- Les routes publiques vérifient aussi la date à chaque réponse (garde-fou) : une campagne dont la `dateFin` est passée refuse la réponse même si la tâche n'a pas encore tourné.

## 5. Tâches, dans l'ordre

- [ ] Écrire `PerformanceService` : saisie des mesures et calcul à la lecture (`CalculPerformance` sans état, facile à tester).
- [ ] Ajouter `PerformanceController` (`mesures`, `performance`) et `RapportPdfService` (`rapport`).
- [ ] Écrire `FinCampagneTache` (`@Scheduled(cron = "0 0 2 * * *")`, horloge injectable) et l'activer.
- [ ] Ajouter le garde-fou de date aux routes publiques de l'étape A3.
- [ ] Tests de la section 7 (« Tests à écrire ») ; vérification dans Swagger.

## 6. Fichiers créés ou modifiés

- `service/ : PerformanceService, CalculPerformance, RapportPdfService, FinCampagneTache ; controller/PerformanceController (sous `com/example/campaign/`)`
- `src/test/java/com/example/campaign/CalculPerformanceTest.java, PerformanceApiTests.java, FinCampagneTacheTest.java`

## 7. Tests à écrire

- Tests d'API avec MockMvc (`@SpringBootTest` + `@AutoConfigureMockMvc` + profil `test`, base H2 en mémoire).
- Calcul : dépensé 100, chiffre d'affaires 350 → ROI 2,5 ; dépensé 0 → ROI `null` ; clics 0 → CPC `null` ; CPC = dépensé / clics.
- `mesures` envoyé deux fois → même résultat ; valeur négative → 422.
- `clientsInteresses` = nombre d'invitations répondues (créer 3 invitations dont 2 répondues → 2).
- Après un `DevisAccepte` traité (étape E1) : `devisAcceptes` et chiffre d'affaires visibles dans `performance`.
- Tâche de fin : ACTIVE et SUSPENDUE passées → TERMINEE ; BROUILLON passée → inchangée ; ACTIVE non passée → inchangée.
- Réponse publique sur une campagne ACTIVE mais dont la `dateFin` est passée → 409 « La campagne n'est plus active ».
- Le rapport renvoie `application/pdf` avec un contenu non vide.

## 8. Points d'attention

- Ne jamais écrire le ROI ou le CPC en base : ils se retrouvent par calcul.
- Un `DevisAccepte` reçu après la fin de la campagne est compté quand même (étape E1) : le chiffre d'affaires d'une campagne TERMINEE peut donc encore augmenter.
- Utiliser `BigDecimal` avec une échelle explicite et `RoundingMode.HALF_UP`.
- La tâche planifiée doit être sûre si le service est relancé : elle est idempotente (relancer ne change rien de plus).

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` : tout vert.
2. Avec une campagne ACTIVE ayant reçu un devis accepté (étape E1) :
   - `PUT /campagnes/{id}/mesures` `{"impressions": 10000, "clics": 250, "montantDepense": 100}` → 200.
   - `GET /campagnes/{id}/performance` → CPC = 0.4, ROI cohérent avec le chiffre d'affaires, `clientsInteresses` = nombre de clics « intéressé ».
   - `GET /campagnes/{id}/rapport` → un PDF s'ouvre.
3. Changer la `dateFin` d'une campagne de test à hier (directement en base ou par un test) et lancer la tâche (ou attendre) → statut TERMINEE.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-A4`, push (voir `README.md`).

Message de commit proposé :

```
feat(campaign): mesures, performance (CPC, ROI), rapport PDF et fin planifiée des campagnes
```
