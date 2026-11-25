# Étape A3 : Campaign : lancement, invitations et réponses publiques

| | |
|---|---|
| **Service** | Campaign (sous-service 3.1 Campagnes) |
| **Responsable** | Oussema Arfaoui |
| **Ce que couvre l'étape** | Lancement, invitations par email, renvoi des échecs, réponses publiques ; lecture de Customer (lien I2) |
| **Dossier de code** | `code/campaign-service` |
| **Prérequis** | A2 validée. Customer est **simulé** pendant le développement ; l'essai réel se fait quand B1 est validée |
| **État** | Réalisé lors de l'essai complet (branche `essai-complet`) ; à valider par le trinôme |

## 1. Objectif

Lancer une campagne : vérifier les conditions, lire Customer (seulement si un public « clients existants » existe), créer une invitation à jeton par client et envoyer l'email (simulé), puis permettre le renvoi des échecs et les réponses publiques (formulaire et « Je suis intéressé »).

**Pas dans cette étape :**

- Publication réelle de `ProspectGenere` sur RabbitMQ (étape E1) : le formulaire public appelle une interface `EditeurEvenements` dont l'implémentation écrit seulement dans les logs.

## 2. Modèle de données

**InvitationClient** : `id` (UUID), `campagne` (clé étrangère interne), `clientId` (UUID externe), `jeton` (valeur aléatoire non devinable, unique, **jamais exposée par l'API**), `envoyeLe` (vide si l'envoi a échoué), `erreurEnvoi` (cause de l'échec), `repondueLe` (vide tant que le client n'a pas répondu).

`PerformanceCampagne.prospectsGeneres` est augmenté à chaque formulaire public.

## 3. API à réaliser

Source de vérité : le fichier OpenAPI du service dans `contrat_api/`. Les champs ci-dessous en sont extraits.

La colonne « Rôles » est le contrat : elle est **appliquée à l'étape E3** (JWT). D'ici là, aucune route ne vérifie le rôle.

**campaign.yaml**

| Méthode | Chemin | Rôles | Succès | Erreurs | Rôle de la route |
|---|---|---|---|---|---|
| POST | `/campagnes/{id}/lancer` | MARKETING | 200 | 400, 401, 403, 404, 409, 422, 503 | Lancer la campagne |
| GET | `/campagnes/{id}/invitations` | MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Lister les invitations |
| POST | `/campagnes/{id}/invitations/renvoyer` | MARKETING | 200 | 400, 401, 403, 404, 409, 503 | Renvoyer les invitations en échec |
| GET | `/campagnes/{id}/reponses` | COMMERCIAL, MARKETING, RESPONSABLE | 200 | 400, 401, 403, 404 | Clients existants ayant répondu |
| GET | `/public/campagnes/{id}` | PUBLIC | 200 | 404, 409 | Page publique de la campagne |
| POST | `/public/campagnes/{id}/reponse` | PUBLIC | 201 | 404, 409, 422 | Formulaire d'un nouveau contact |
| POST | `/public/invitations/{jeton}/reponse` | PUBLIC | 200 | 404, 409 | Un client existant répond « Je suis intéressé » |

### Objets échangés

**ResultatEnvoi**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `envoisReussis` | integer | oui |  |
| `echecs` | liste de object | oui |  |

**Invitation** : Le jeton n'est jamais exposé (il n'est que dans l'email du client).

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `clientId` | string (uuid) | oui |  |
| `envoyeLe` | string (date-time) | non | peut être null |
| `erreurEnvoi` | string | non | peut être null |
| `repondueLe` | string (date-time) | non | peut être null |

**ReponseClient** : Les noms viennent de Customer via l'interface.

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `clientId` | string (uuid) | oui |  |
| `repondueLe` | string (date-time) | oui |  |

**CampagnePublique** : Page publique : contenu et formulaire.

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `id` | string (uuid) | oui |  |
| `nom` | string | oui |  |
| `contenus` | liste de Contenu | non |  |

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

**FormulaireProspect**

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `type` | string | oui | PARTICULIER / ENTREPRISE |
| `nom` | string | oui |  |
| `nomContact` | string | non | Entreprise seulement |
| `email` | string (email) | oui |  |
| `telephone` | string | non |  |

## 4. Règles métier à respecter

- **Lancer** : la campagne doit être `BROUILLON` (409 sinon). Conditions, vérifiées avant tout appel : au moins un canal, au moins un contenu, un budget prévu défini (0 accepté), canaux cohérents avec les publics → **422** ; `dateDebut` atteinte et `dateFin` non dépassée → **409** (la date relève de l'état dans le temps). Après tout échec de vérification, **la campagne reste BROUILLON** et rien n'est créé.
- **Appel à Customer (I2)** : seulement s'il existe un public `CLIENTS_EXISTANTS`. `GET /clients/destinataires?categorieId=…` (toutes les pages). 404 de Customer (catégorie inconnue) → **422**. Customer indisponible → **503** et la campagne reste BROUILLON.
- Une invitation est créée **pour chaque** destinataire, avec un jeton ; l'email (via `envoyerEmail`, simulé) contient le contenu et le lien personnel. Un envoi en échec **ne bloque pas** : l'invitation garde `erreurEnvoi`, `envoyeLe` reste vide, la campagne devient **ACTIVE quand même**.
- **Liste vide acceptée** : si Customer ne renvoie aucun client, ou s'il n'y a que des publics externes, la campagne devient ACTIVE avec 0 invitation.
- Réponse de `lancer` : nombre d'envois réussis et liste des échecs `{clientId, cause}`.
- **Renvoyer** (`POST …/invitations/renvoyer`) : campagne **ACTIVE** (409 sinon) ; relit Customer ; ne retient que les invitations en échec ; un client devenu `INACTIF` n'est plus renvoyé ; une invitation déjà envoyée n'est **jamais** renvoyée. Customer indisponible → 503.
- `GET …/invitations` : filtre `envoi=ECHEC` ou `REUSSI`, paginée ; **le jeton n'apparaît jamais**.
- `GET …/reponses` : clients existants ayant répondu, `{clientId, repondueLe}` ; accessible aussi au COMMERCIAL ; sans nom ni email (l'interface les lit dans Customer).
- **Page publique** `GET /public/campagnes/{id}` : contenu et formulaire ; **409 « La campagne n'est plus active »** si elle n'est pas ACTIVE.
- **Formulaire** `POST /public/campagnes/{id}/reponse` : particulier ou entreprise ; email valide sinon 422 ; campagne non ACTIVE → 409 ; incrémente `prospectsGeneres` ; appelle `EditeurEvenements.prospectGenere(...)` (aucune donnée personnelle conservée dans Campaign). Pas d'appel synchrone à un autre service.
- **« Je suis intéressé »** `POST /public/invitations/{jeton}/reponse` : renseigne `repondueLe` ; un **second clic → 409 « Vous avez déjà répondu »** ; campagne non ACTIVE → 409 « La campagne n'est plus active » ; jeton inconnu → 404. Aucune fiche n'est créée.

## 5. Tâches, dans l'ordre

- [ ] Créer `InvitationClient` (entity), `InvitationRepository` et `JetonInvitation` (génération aléatoire non devinable).
- [ ] Créer `client/CustomerClient` : `RestClient` (`app.customer-url`), lit toutes les pages de `GET /clients/destinataires`, traduit 404 en erreur « catégorie inconnue » (422) et panne ou délai dépassé en `ApiException` 503.
- [ ] Créer `EnvoiEmail` (dans `service`, même méthode qu'en Quotation, simulée dans les logs, échec configurable pour les tests).
- [ ] Écrire `LancementService` : les vérifications dans cet ordre, l'appel à Customer, la boucle d'invitations, le passage à ACTIVE, le compte rendu. L'ensemble des écritures en base est fait **après** l'appel à Customer et l'envoi.
- [ ] Écrire `RenvoiService` (réutilise la boucle d'envoi).
- [ ] Écrire `ReponsePubliqueService` (formulaire et clic « intéressé ») et `PublicController`.
- [ ] Ajouter les lectures `invitations` et `reponses`.
- [ ] Créer l'interface `EditeurEvenements` (méthode `prospectGenere`, dossier `messaging`) et son implémentation « logs ».
- [ ] Tests de la section 7 (« Tests à écrire ») avec Customer et `envoyerEmail` **simulés** ; essai réel avec Customer démarré.

## 6. Fichiers créés ou modifiés

- `entity/InvitationClient ; repository/InvitationRepository ; service/ : JetonInvitation, LancementService, RenvoiService, ReponsePubliqueService, EnvoiEmail ; controller/ : InvitationController, PublicController (sous `com/example/campaign/`)`
- `client/ : CustomerClient, Destinataire`
- `messaging/ : EditeurEvenements, EditeurEvenementsLog`
- `src/test/java/com/example/campaign/LancementApiTests.java, RenvoiApiTests.java, ReponsePubliqueApiTests.java`

## 7. Tests à écrire

- Tests d'API avec MockMvc (`@SpringBootTest` + `@AutoConfigureMockMvc` + profil `test`, base H2 en mémoire). Customer est simulé (liste de destinataires, 404, indisponible).
- Lancement nominal : 3 destinataires → 3 invitations, 3 emails, campagne ACTIVE, réponse `{envoisReussis: 3, echecs: []}`.
- Sans canal, sans contenu, sans budget défini, ou canal incohérent → 422 et la campagne reste BROUILLON, **aucune invitation créée**.
- `dateDebut` future ou `dateFin` passée → 409 et BROUILLON (horloge fixée).
- Customer indisponible → 503, BROUILLON, aucune invitation. Catégorie inconnue (404 simulé) → 422, BROUILLON.
- Public externe seul → **Customer n'est pas appelé** (vérifier qu'aucun appel n'est fait) ; ACTIVE, 0 invitation.
- Un envoi en échec parmi trois → 2 réussis, 1 échec avec cause ; campagne ACTIVE ; `GET …/invitations?envoi=ECHEC` → 1 élément.
- `renvoyer` : ne traite que l'invitation en échec ; une invitation déjà envoyée n'est pas renvoyée ; un client INACTIF n'est pas renvoyé ; campagne non ACTIVE → 409 ; Customer indisponible → 503.
- Page publique et formulaire : campagne ACTIVE → 200 / 201 ; non ACTIVE → 409 « La campagne n'est plus active » ; email invalide → 422 ; `prospectsGeneres` augmenté ; l'éditeur d'événements reçoit `prospectGenere` avec les bonnes données.
- « Je suis intéressé » : premier clic → 200, `repondueLe` remplie ; second clic → 409 « Vous avez déjà répondu » ; jeton inconnu → 404 ; campagne TERMINEE → 409.
- Le jeton n'apparaît dans aucune réponse des routes d'employés.
- `GET …/reponses` : `COMMERCIAL` autorisé ; la réponse ne contient que `clientId` et `repondueLe`.

## 8. Points d'attention

- **Ordre des opérations** : vérifier, lire Customer, envoyer les emails, **puis** enregistrer le tout et passer à ACTIVE. Ainsi une panne de Customer ne laisse aucune trace.
- Si l'application s'arrête en plein envoi, certaines invitations peuvent exister sans que la campagne soit ACTIVE : décider (et écrire dans le test) comment `lancer` reprend (réutiliser les invitations existantes plutôt que d'en créer en double).
- Un lancement ne doit pas pouvoir tourner **deux fois en même temps** sur la même campagne : verrou ou version optimiste sur `Campagne`.
- La lecture de Customer est paginée : parcourir **toutes** les pages.
- Les routes publiques ne font aucun appel synchrone ; elles ne publient qu'un événement (ici simulé).
- Une adresse email invalide d'un destinataire n'est pas une erreur de lancement : c'est un échec d'envoi, noté dans l'invitation.

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` : tout vert.
2. Démarrer Customer, Campaign et les bases. Dans Customer : une catégorie et deux clients de statut CLIENT dans cette catégorie.
3. Dans Campaign (Swagger) : créer une campagne complète (canal EMAIL, public `CLIENTS_EXISTANTS` avec cette catégorie, un contenu, budget 500, dates qui englobent aujourd'hui).
   - `POST /campagnes/{id}/lancer` → 200, `envoisReussis: 2`. Dans les logs, deux emails simulés avec un lien et un jeton.
   - `GET /campagnes/{id}/invitations` → 2 invitations **sans jeton**.
4. Prendre un jeton dans les logs : `POST /public/invitations/<jeton>/reponse` → 200 ; refaire → **409 « Vous avez déjà répondu »**. Puis `GET /campagnes/{id}/reponses` → 1 élément.
5. `POST /public/campagnes/{id}/reponse` avec un formulaire valide → 201, et une ligne « ProspectGenere » dans les logs.
6. **Panne** : arrêter Customer, créer une autre campagne et la lancer → **503** et elle reste BROUILLON.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-A3`, push (voir `README.md`).

Message de commit proposé :

```
feat(campaign): lancement, invitations, renvoi et réponses publiques (I2)
```
