# Étape S3 : Quotation : PDF, envoi, réponse publique et expiration

| | |
|---|---|
| **Service** | Quotation (sous-service 2.4 Documents et envoi) |
| **Responsable** | Oussema Souidi |
| **Ce que couvre l'étape** | PDF et envoi du devis par email, réponse du client par lien (accepter, refuser), expiration automatique ; l'événement `DevisAccepte` est publié en E1 |
| **Dossier de code** | `code/quotation-service` |
| **Prérequis** | S2 validée |
| **État** | Réalisé lors de l'essai complet (branche `essai-complet`) ; à valider par le trinôme |

## 1. Objectif

Générer le devis en PDF, l'envoyer au client avec ses liens Accepter / Refuser (envoi simulé), laisser le client répondre sans compte par un jeton à usage unique, et faire expirer les devis chaque jour.

**Pas dans cette étape :**

- Publication réelle de `DevisAccepte` sur RabbitMQ (étape E1). Ici, l'acceptation appelle une interface `EditeurEvenements` dont l'implémentation écrit seulement dans les logs.

## 2. Modèle de données

**Devis** : le champ `tokenReponse` est maintenant rempli à l'envoi (valeur aléatoire non devinable : 32 octets aléatoires encodés en base64 URL, ou un UUID aléatoire de version 4 au minimum) et `repondueLe` à la réponse.

**Document** : `id` (UUID), `devis` (clé étrangère interne), `format` (`PDF`), `contenu` (octets du PDF) ou `chemin`, `genereLe`, `envoyeLe`, `destinataire` (copie de `clientEmail`).

## 3. API à réaliser

Source de vérité : le fichier OpenAPI du service dans `contrat_api/`. Les champs ci-dessous en sont extraits.

La colonne « Rôles » est le contrat : elle est **appliquée à l'étape E3** (JWT). D'ici là, aucune route ne vérifie le rôle.

**quotation.yaml**

| Méthode | Chemin | Rôles | Succès | Erreurs | Rôle de la route |
|---|---|---|---|---|---|
| POST | `/devis/{id}/envoyer` | COMMERCIAL | 200 | 400, 401, 403, 404, 409, 422, 503 | Générer le PDF et envoyer l'email au client |
| GET | `/devis/{id}/pdf` | COMMERCIAL, RESPONSABLE | 200 | 400, 401, 403, 404 | Télécharger le PDF |
| GET | `/public/reponse/{token}` | PUBLIC | 200 | 404 | Le client consulte son devis |
| POST | `/public/reponse/{token}/accepter` | PUBLIC | 200 | 404, 409 | Le client accepte le devis |
| POST | `/public/reponse/{token}/refuser` | PUBLIC | 200 | 404, 409 | Le client refuse le devis |

### Objets échangés

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

**DevisPublic** : Vue publique : aucune donnée personnelle autre que le nom. L'interface n'affiche les boutons que si le statut est ENVOYE.

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `reference` | string | oui |  |
| `date` | string (date) | non |  |
| `dateValidite` | string (date) | non |  |
| `statut` | string | oui | BROUILLON / ENVOYE / ACCEPTE / REFUSE / EXPIRE / ANNULE |
| `clientNom` | string | non |  |
| `lignes` | liste de LigneDevis | non |  |
| `totalHT` | Montant | non |  |
| `totalTVA` | Montant | non |  |
| `totalTTC` | Montant | oui |  |

## 4. Règles métier à respecter

- **Envoyer** : exige un devis `BROUILLON` (409 sinon), un `clientEmail` valide et une `dateValidite` non dépassée (422 sinon). Étapes : créer le jeton, générer le PDF, appeler `envoyerEmail(destinataire, sujet, texte, pieceJointe)` avec les liens Accepter et Refuser, puis passer à `ENVOYE`.
- Si l'envoi échoue : **503 avec la cause**, le devis **reste BROUILLON** (le jeton n'est conservé que si l'envoi réussit, ou régénéré à l'essai suivant) ; le commercial corrige l'email et recommence.
- `envoyerEmail` est une **méthode unique** (une classe `EnvoiEmail`) : aujourd'hui elle écrit le message dans les logs ; plus tard son corps appellera le service Notification, sans toucher au reste.
- **PDF** : référence, date, client, lignes, totaux, date de validité. `GET /devis/{id}/pdf` renvoie `application/pdf`.
- **Réponse publique** : `GET /public/reponse/{token}` renvoie **toujours** le devis avec son statut (404 si le jeton est inconnu) et ne modifie rien ; `accepter` et `refuser` répondent 200 puis le devis passe à `ACCEPTE` ou `REFUSE` et `repondueLe` est remplie.
- Si le devis est déjà traité, annulé, ou si le jour de `dateValidite` est passé : **409 « Ce devis ne peut plus être accepté »** (mêmes cas pour `refuser`). Le jeton est valable **jusqu'à la fin du jour** de `dateValidite` (le jour même, le lien marche encore).
- Les routes `/public/**` n'exigent **aucune identité** et ne font **aucun appel** à un autre service.
- **À l'acceptation** : appeler `EditeurEvenements.devisAccepte(...)` seulement si le devis a un `campagneId`.
- **Expiration** : une tâche `@Scheduled` quotidienne passe à `EXPIRE` les devis `ENVOYE` dont `dateValidite` est strictement passée (l'étape est doublée par la vérification de la date à chaque ouverture du lien).

## 5. Tâches, dans l'ordre

- [ ] Ajouter `Document` et `DocumentRepository`, `GenerateurPdf` (bibliothèque PDF, par exemple OpenPDF ou Apache PDFBox : ajouter la dépendance au `pom.xml`).
- [ ] Créer `EnvoiEmail` (dans `service`) avec `envoyerEmail(destinataire, sujet, texte, pieceJointe)` (écrit dans les logs ; échec simulable par configuration pour les tests).
- [ ] Créer `JetonReponse` (génération aléatoire) et ajouter `tokenReponse` à `Devis`.
- [ ] Écrire `EnvoiDevisService` : vérifications, jeton, PDF, email, changement de statut, `Document` enregistré.
- [ ] Écrire `ReponsePubliqueService` : lecture, acceptation, refus, règle de la fin de journée.
- [ ] Créer l'interface `EditeurEvenements` (dossier `messaging`) et son implémentation « logs » (remplacée à l'étape E1).
- [ ] Écrire `ExpirationDevisTache` (`@Scheduled(cron = "0 0 1 * * *")`) et activer `@EnableScheduling`.
- [ ] Écrire `PublicController` (préfixe `/public/reponse`), ajouter `envoyer` et `pdf` au contrôleur des devis.
- [ ] Tests de la section 7 (« Tests à écrire ») ; essai de bout en bout dans Swagger.

## 6. Fichiers créés ou modifiés

- `entity/Document ; repository/DocumentRepository ; service/GenerateurPdf (sous `com/example/quotation/`)`
- `service/ : EnvoiEmail, EnvoiDevisService, JetonReponse, ReponsePubliqueService, ExpirationDevisTache`
- `controller/PublicController (préfixe `/public/reponse`)`
- `messaging/ : EditeurEvenements, EditeurEvenementsLog`
- `pom.xml (dépendance PDF)`

## 7. Tests à écrire

- Tests d'API avec MockMvc (`@SpringBootTest` + `@AutoConfigureMockMvc` + profil `test`, base H2 en mémoire).
- Envoi réussi : devis ENVOYE, jeton créé, `Document` enregistré, email « envoyé » (vérifié dans le journal simulé), le PDF a un contenu non vide.
- Envoi d'un devis non BROUILLON → 409 ; `dateValidite` passée → 422 ; email invalide → 422.
- Échec d'envoi simulé → 503 avec la cause et le devis reste BROUILLON.
- `GET /public/reponse/{token}` sans identité → 200 avec statut ; jeton inconnu → 404 ; ne modifie rien (deux appels identiques).
- Acceptation : statut ACCEPTE, `repondueLe` remplie ; deuxième acceptation → 409 « Ce devis ne peut plus être accepté » ; `refuser` après acceptation → 409.
- Le jour même de `dateValidite` l'acceptation est possible ; le lendemain → 409 (horloge fixée dans le test).
- `DevisAccepte` demandé à l'éditeur **seulement** si `campagneId` renseigné, avec les bons montants et la référence.
- Tâche d'expiration : un devis ENVOYE dont la date est passée devient EXPIRE ; un BROUILLON ne change pas ; un ACCEPTE ne change pas.
- Les réponses de `/devis` ne contiennent jamais le jeton.

## 8. Points d'attention

- Le jeton est une donnée sensible : jamais dans les réponses aux employés ; il n'existe que dans l'email du client. Tant que l'email est simulé, le seul endroit où il apparaît est la console (l'email simulé).
- Utiliser une horloge injectable (`java.time.Clock`) pour tester « fin du jour » et l'expiration sans attendre.
- Les liens de l'email visent l'**interface** : `<url-interface>/devis/reponse/<token>` ouvre une page dont les boutons appellent les routes publiques. Le contrat décrit les routes, pas la page.
- Concurrence : deux clics simultanés sur Accepter ne doivent produire qu'**une seule** acceptation (verrou ou version optimiste sur le devis).
- Le PDF peut rester simple : le but est de démontrer la génération, pas la mise en page.

## 9. Comment valider (à faire par le trinôme)

1. `mvn clean test` : tout vert.
2. Dans Swagger de Quotation, avec un devis BROUILLON de l'étape S2 :
   - `POST /devis/{id}/envoyer` → 200 ; dans les logs du service, l'email simulé contient les liens avec le jeton.
   - Copier le jeton depuis les logs. `GET /public/reponse/<jeton>` → 200 avec le statut `ENVOYE`.
   - `POST /public/reponse/<jeton>/accepter` → 200 ; refaire → **409 « Ce devis ne peut plus être accepté »**.
   - `GET /devis/{id}/pdf` → un PDF s'ouvre.
3. Dans les logs : si le devis a un `campagneId`, une ligne « DevisAccepte » apparaît (l'événement réel viendra à l'étape E1).

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-S3`, push (voir `README.md`).

Message de commit proposé :

```
feat(quotation): PDF, envoi simulé, réponse publique par jeton et expiration planifiée
```
