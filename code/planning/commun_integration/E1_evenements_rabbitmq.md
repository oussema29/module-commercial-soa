# Étape E1 : Événements RabbitMQ : ProspectGenere et DevisAccepte

| | |
|---|---|
| **Service** | Customer, Quotation et Campaign |
| **Responsable** | Tout le trinôme (Campaign : Oussema Arfaoui ; Quotation : Oussema Souidi ; Customer : Baha Farhati) |
| **Ce que couvre l'étape** | Les deux événements RabbitMQ (liens I3 et I4), traités sans doublon |
| **Dossier de code** | `code/ (les trois services)` |
| **Prérequis** | B2, S3 et A3 validées ; RabbitMQ démarré |
| **État** | Réalisé lors de l'essai complet (branche `essai-complet`) ; à valider par le trinôme |

## 1. Objectif

Remplacer les éditeurs « logs » par de vrais envois RabbitMQ, et réaliser les deux consommateurs : Customer crée un prospect à partir de `ProspectGenere`, Campaign met à jour le chiffre d'affaires à partir de `DevisAccepte`, chacun de façon idempotente.

**Pas dans cette étape :**

- Le service Notification et l'email réel.

## 2. Modèle de données

**EvenementTraite** (Customer et Campaign, chacun dans sa base) : `eventId` (UUID, **clé primaire**), `traiteLe` (date-heure). Aucun lien avec les autres tables.

**Topologie** (déclarée au démarrage dans le code, identique à `contrat_api/evenements.md`) :

| Élément | Valeur |
|---|---|
| Échange | `module-commercial`, type `topic`, durable |
| `ProspectGenere` | clé `prospect.genere` → file `customer.prospect-genere` (durable) |
| `DevisAccepte` | clé `devis.accepte` → file `campaign.devis-accepte` (durable) |
| Messages | JSON UTF-8, persistants, accusé de réception **manuel** après traitement |

**Enveloppe** : `eventId` (UUID), `type`, `date` (ISO 8601 UTC). Les messages complets (champs, exemples) sont dans `contrat_api/evenements.md`.

## 3. API à réaliser

Aucune route dans cette étape.

## 4. Règles métier à respecter

- **Campaign publie `ProspectGenere`** à chaque formulaire public (champs : `campagneId`, `typeClient`, `nom`, `nomContact` si entreprise, `email`, `telephone`).
- **Quotation publie `DevisAccepte`** à l'acceptation, **seulement si le devis a un `campagneId`** (champs : `devisId`, `reference`, `clientId`, `campagneId`, `montantHT`, `montantTTC`) ; `date` est `repondueLe`. Montants : nombres décimaux à 3 décimales au plus.
- **Customer consomme `ProspectGenere`** : (1) `eventId` déjà dans `EvenementTraite` → ignorer et accuser réception ; (2) un client a déjà cet email (casse et espaces ignorés) → ne rien créer ; (3) sinon créer un `Client` `PROSPECT` sans catégorie, avec `campagneSourceId = campagneId` ; pour une entreprise créer aussi un `Contact` avec le même email ; (4) enregistrer `eventId` dans **la même transaction**, puis accuser réception.
- **Campaign consomme `DevisAccepte`** : (1) `eventId` déjà traité **ou** `campagneId` inconnu → ignorer sans erreur ; (2) sinon `devisAcceptes + 1` et `chiffreAffairesAttribue + montantHT`, **même si la campagne est TERMINEE** ; (3) enregistrer `eventId` dans la même transaction, puis accuser réception.
- Le ROI n'est pas stocké : `(chiffreAffairesAttribue − montantDepense) / montantDepense`, seulement si `montantDepense > 0`.
- Une autre équipe peut créer **sa propre file** liée à `devis.accepte` sans rien changer chez nous.
- **Topologie** : chaque service déclare l'échange `module-commercial` **et la file de son destinataire** (déclarations identiques, donc idempotentes) : un message publié n'est jamais perdu si le destinataire n'est pas encore démarré, il attend dans la file.
- **Message impossible à traiter** : refusé sans remise en file (nack) ; il part dans la file des échecs `<file>.dlq`. Limite assumée : même une panne passagère de la base l'envoie en `.dlq` (pas de nouvel essai automatique).
- **RabbitMQ indisponible au moment de publier** : la réponse du visiteur (`ProspectGenere`) ou l'acceptation du client (`DevisAccepte`) reste **enregistrée** ; l'événement est écrit en entier, **en JSON**, dans les logs (niveau ERROR, « ÉVÉNEMENT NON PUBLIÉ ») pour être republié à la main depuis la console de RabbitMQ. Pas d'« outbox » : limite assumée.
- Les versions « logs » des éditeurs ne servent que si `app.events.rabbit=false` (tests) ; en fonctionnement normal `app.events.rabbit=true`.

## 5. Tâches, dans l'ordre

- [ ] Ajouter à chaque service sa configuration RabbitMQ (`RabbitConfig`) : échange, files, liaisons, convertisseur JSON, accusé manuel, files des échecs.
- [ ] Campaign : écrire `EditeurEvenementsRabbit` (remplace l'implémentation « logs » de l'étape A3) ; Quotation : idem pour l'étape S3. Garder l'interface et ses tests.
- [ ] Customer : entité `EvenementTraite`, repository, `ProspectGenereConsommateur`, `ProspectService.creerDepuisEvenement` (réutilise la règle d'unicité de l'email de l'étape B1).
- [ ] Campaign : entité `EvenementTraite`, repository, `DevisAccepteConsommateur`, mise à jour de `PerformanceCampagne`.
- [ ] Tests unitaires des consommateurs (appel direct de la méthode, sans broker), avec le même événement envoyé **deux fois**.
- [ ] Test d'intégration avec un vrai RabbitMQ (Testcontainers ou le conteneur du compose) pour au moins un événement de bout en bout.
- [ ] Essai réel : les trois services démarrés, un formulaire public crée un prospect dans Customer ; un devis accepté augmente le chiffre d'affaires de la campagne.

## 6. Fichiers créés ou modifiés

- `customer-service : config/RabbitConfig ; entity/EvenementTraite ; repository/EvenementTraiteRepository ; messaging/ProspectGenereEvent, ProspectGenereConsommateur ; service/ProspectService`
- `campaign-service : config/RabbitConfig ; entity/EvenementTraite ; repository/EvenementTraiteRepository ; messaging/DevisAccepteEvent, DevisAccepteConsommateur, EditeurEvenementsRabbit ; service/DevisAccepteService`
- `quotation-service : config/RabbitConfig ; messaging/EditeurEvenementsRabbit`
- `les trois `application.yml` : accusé de réception manuel (`acknowledge-mode: manual`) ; Campaign et Quotation : `app.events.rabbit``
- `tests : ProspectServiceTests, ProspectGenereConsommateurTest (Customer) ; DevisAccepteServiceTests, EvenementsRabbitTest (Campaign) ; EditeurEvenementsRabbitTest (Quotation)`

## 7. Tests à écrire

- `ProspectGenere` particulier : un client PROSPECT est créé, `campagneSourceId` renseigné, pas de catégorie.
- `ProspectGenere` entreprise : le client et un contact (avec l'email) sont créés.
- Même `eventId` reçu deux fois : **un seul** client créé ; email déjà connu (autre casse) : aucun client créé.
- `DevisAccepte` : `devisAcceptes` et `chiffreAffairesAttribue` augmentent du `montantHT` ; deux fois le même `eventId` : une seule fois ; campagne TERMINEE : compté quand même ; `campagneId` inconnu : ignoré sans erreur.
- Panne simulée après le traitement mais avant l'accusé : au rejeu, aucun doublon.
- Quotation : un devis **sans** `campagneId` accepté ne publie rien ; avec `campagneId`, le message publié a les bons champs et des montants décimaux.
- Les noms (échange, clés, files) sont exactement ceux de `contrat_api/evenements.md`.

## 8. Points d'attention

- RabbitMQ garantit « au moins une fois », jamais « exactement une fois » : sans `EvenementTraite`, un `DevisAccepte` rejoué compterait deux fois le chiffre d'affaires et fausserait le ROI sans aucune erreur visible.
- L'enregistrement de `eventId` et l'effet métier doivent partager **la même transaction**, sinon on retrouve le doublon.
- Accusé de réception manuel : n'accuser réception qu'**après** le commit de la transaction.
- Aucun service ne lit la base d'un autre : l'événement porte toutes les données utiles.
- Ne pas mettre le jeton du devis ni de donnée inutile dans un événement.
- La publication a lieu dans la même transaction que l'enregistrement de l'acceptation : si la transaction échoue après l'envoi, le message est déjà parti (limite connue, que résoudrait une table « outbox »).

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` dans les trois services : tout vert.
2. `docker compose up -d`, démarrer les trois services. Dans la console RabbitMQ (`http://localhost:15672`, guest / guest) : l'échange `module-commercial` et les deux files existent.
3. **Prospect** : lancer une campagne (étape A3), puis `POST /public/campagnes/{id}/reponse` avec un formulaire entreprise → dans Customer, `GET /clients?statut=PROSPECT` montre la nouvelle fiche, et `GET /clients/{id}/contacts` son contact. Refaire le même formulaire → toujours une seule fiche.
4. **Devis accepté** : créer un devis avec `campagneId` (étape S2), l'envoyer, accepter par le lien (étape S3) → `GET /campagnes/{id}/performance` (étape A4) ou, en attendant, la base : `devisAcceptes` = 1 et le chiffre d'affaires augmente du `montantHT`.
5. **Idempotence** : republier à la main le même message depuis la console RabbitMQ → rien ne change.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-E1`, push (voir `README.md`).

Message de commit proposé :

```
feat: événements RabbitMQ ProspectGenere et DevisAccepte avec consommateurs idempotents (I3, I4)
```
