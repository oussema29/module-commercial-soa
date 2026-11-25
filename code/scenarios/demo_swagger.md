# Démonstration avec Swagger (guide pas à pas)

Tout se fait dans le navigateur, sur **une seule page** : **http://localhost:8080/swagger-ui.html**
(la passerelle, point d'entrée unique ; les adresses `localhost:8081` à `8084` ne servent pas dans cette démo).

## A. Avant la démo : lancer avec la sécurité JWT et une base vide

Dans un terminal, dossier `code` :

```
docker compose --profile services down -v          # supprime toutes les données : bases vides
```

Puis, **selon le terminal** :

```
# PowerShell
$env:SECURITY_MODE="jwt"; docker compose --profile services up -d --build

# Git Bash / Linux / macOS
SECURITY_MODE=jwt docker compose --profile services up -d --build
```

Attendre environ une minute, puis ouvrir http://localhost:8080/actuator/health : `{"status":"UP"}`.
Sans `SECURITY_MODE=jwt`, la sécurité est désactivée (mode `none`) : tout passe sans jeton, ce qui est faux pour la démo.

Test de contrôle (facultatif) : ouvrir http://localhost:8080/clients dans le navigateur : la réponse doit être **401 « Authentification requise »**.

## B. Comment utiliser Swagger (à connaître avant de commencer)

1. En haut à droite, le **menu déroulant « Select a definition »** change de service : **Connexion (Auth)**, **Clients (Customer)**, **Devis (Quotation)**, **Campagnes (Campaign)**. La page s'ouvre sur **Connexion (Auth)**. Il faut changer le menu à chaque fois qu'on passe à un autre service.
2. Pour appeler une route : cliquer sur la route, puis **Try it out**, remplir le corps JSON, puis **Execute**. La réponse (code et corps) s'affiche en dessous.
3. **Se connecter avec un rôle (à répéter à chaque changement de rôle) :**
   1. Menu déroulant -> **Connexion (Auth)** -> `POST /login` -> Try it out -> coller l'email et le mot de passe du rôle -> Execute.
   2. Copier la valeur de **`jeton`** dans la réponse (sans les guillemets).
   3. Changer le menu vers le service à utiliser (Customer, Quotation ou Campaign), cliquer sur le bouton vert **Authorize** (en haut à droite, avec un cadenas), coller le jeton dans le champ **Value** (**seulement le jeton : ne pas écrire « Bearer »**, Swagger l'ajoute), cliquer **Authorize** dans la fenêtre, puis **Close**. Le cadenas se ferme.
   4. Les essais de **ce service** partent alors avec ce jeton, donc avec ce rôle. **Swagger n'enregistre pas le jeton quand on change de service dans le menu** (vérifié) : à chaque passage à un autre service, refaire le clic sur **Authorize** et recoller le **même** jeton. Garder le jeton dans un bloc-notes pour le recoller.
   5. La page de **Connexion (Auth)** n'a pas de bouton Authorize : le login est public.
4. **Changer de rôle :** refaire l'étape 3 avec un autre utilisateur. Dans la fenêtre Authorize, cliquer d'abord sur **Logout**, puis recoller le nouveau jeton (dans chaque service utilisé).
5. Le jeton dure **1 heure**. Au-delà, les routes répondent 401 : refaire l'étape 3.
6. Les `id` à réutiliser (`<CAT>`, `<CAMP>`, ...) sont dans les réponses : les copier au fur et à mesure dans un bloc-notes.

### Utilisateurs

| Rôle | Email | Mot de passe |
|---|---|---|
| ADMIN | admin@exemple.tn | admin-demo-1 |
| COMMERCIAL | commercial@exemple.tn | commercial-demo-1 |
| MARKETING | marketing@exemple.tn | marketing-demo-1 |
| RESPONSABLE | responsable@exemple.tn | responsable-demo-1 |

Corps de login : `{"email":"admin@exemple.tn","motDePasse":"admin-demo-1"}`

### Dates

Les dates de début et de fin de campagne se donnent en **UTC** : `<AUJOURDHUI>` = la date du jour en UTC (la date locale peut avoir un jour d'avance, et le lancement répondrait « La date de début n'est pas encore atteinte »), `<DANS_30_JOURS>` = 30 jours plus tard.
Les obtenir : `python -c "from datetime import *; d=datetime.now(timezone.utc).date(); print(d, d+timedelta(days=30))"`

## C. Le parcours (le fil rouge)

**Une campagne marketing amène un prospect, qui devient client, reçoit un devis, l'accepte, et la campagne mesure son ROI.**

Pour chaque étape : le **rôle** à avoir (voir B.3), le **service** à choisir dans le menu, la **route** et le **corps**. **À chaque changement de service dans le menu : cliquer sur Authorize et recoller le jeton du rôle de l'étape** (B.3, point 4). Si une route répond 401 alors que vous avez fait Authorize, c'est presque toujours que vous avez changé de service sans recoller le jeton.

### Étape 0 - Sans jeton : refusé

Ne pas cliquer sur Authorize. Menu -> **Clients (Customer)** -> `GET /clients` -> Execute.
Attendu : **401** `"Authentification requise"` (refusé par la passerelle).

### Étape 1 - Rôle **ADMIN** : catalogue et canal

Se connecter en **ADMIN** (B.3).

| Service (menu) | Route | Corps | Garder |
|---|---|---|---|
| Clients (Customer) | `POST /categories` | `{"nom":"VIP","description":"Clients importants"}` | `id` -> `<CAT>` |
| Devis (Quotation) | `POST /taxes` | `{"tauxTVA":19,"description":"TVA standard"}` | `id` -> `<TAXE>` |
| Devis (Quotation) | `POST /produits` | `{"nom":"Étude de faisabilité","description":"Forfait 5 jours","prix":1000.000,"taxeId":"<TAXE>"}` | `id` -> `<PRODUIT>` |
| Campagnes (Campaign) | `POST /canaux` | `{"nom":"Email","type":"EMAIL"}` | `id` -> `<CANAL>` |

### Étape 2 - Rôle **COMMERCIAL** : deux clients existants

**Changer de rôle : se connecter en COMMERCIAL** (B.4). Service **Clients (Customer)**.

| Route | Corps |
|---|---|
| `POST /clients` | `{"type":"ENTREPRISE","nom":"Alpha","statut":"CLIENT","email":"alpha@exemple.tn","categorieId":"<CAT>"}` |
| `POST /clients` | `{"type":"ENTREPRISE","nom":"Beta","statut":"CLIENT","email":"beta@exemple.tn","categorieId":"<CAT>"}` |

### Étape 3 - Rôle **MARKETING** : créer et lancer la campagne

**Changer de rôle : se connecter en MARKETING.** Service **Campagnes (Campaign)**.

| Route | Corps / paramètre | Garder |
|---|---|---|
| `POST /campagnes` | `{"nom":"Offre fidélité","objectif":"FIDELISATION","dateDebut":"<AUJOURDHUI>","dateFin":"<DANS_30_JOURS>"}` | `id` -> `<CAMP>` |
| `POST /campagnes/{id}/canaux` | id = `<CAMP>` ; `{"canalId":"<CANAL>"}` | |
| `POST /campagnes/{id}/publics` | id = `<CAMP>` ; `{"type":"CLIENTS_EXISTANTS","categorieClientId":"<CAT>"}` | |
| `POST /campagnes/{id}/contenus` | id = `<CAMP>` ; `{"titre":"-20 % ce mois-ci","texte":"Offre fidélité"}` | `id` -> `<CONTENU>` |
| `POST /contenus/{id}/produits` | id = `<CONTENU>` ; `{"produitId":"<PRODUIT>","produitNom":"Étude de faisabilité"}` | |
| `PUT /campagnes/{id}/budget` | id = `<CAMP>` ; `{"montantPrevu":500}` | |
| `POST /campagnes/{id}/lancer` | id = `<CAMP>` | |

Attendu au lancement : `envoisReussis = 2`. **À expliquer :** Campaign a appelé Customer (HTTP) pour lire les clients de la catégorie VIP, en transmettant le jeton de l'utilisateur, puis a « envoyé » 2 invitations.

### Étape 4 - Public (sans jeton) : un client répond à l'invitation

Les emails sont simulés : le lien est dans les logs de Campaign. Dans un terminal :

```
# PowerShell
docker logs campaign-service 2>&1 | Select-String "campagnes/invitation"
# Git Bash
docker logs campaign-service 2>&1 | grep "campagnes/invitation"
```

Le lien se termine par `.../campagnes/invitation/<TOKEN_INVITATION>` : copier le long texte final (deux liens apparaissent : en prendre un).

**Se déconnecter** (Authorize -> Logout) : ces routes sont publiques. Service **Campagnes (Campaign)** :

| Route | Paramètre | Attendu |
|---|---|---|
| `POST /public/invitations/{jeton}/reponse` | `<TOKEN_INVITATION>` | 200 |
| (la même, une 2e fois) | `<TOKEN_INVITATION>` | **409** `"Vous avez déjà répondu"` |

### Étape 5 - Public : un inconnu remplit le formulaire (événement RabbitMQ)

Sans jeton, service **Campagnes (Campaign)** : `POST /public/campagnes/{id}/reponse`, id = `<CAMP>` :

```
{"type":"ENTREPRISE","nom":"Société Atlas","nomContact":"Karim Ben Salah","email":"karim@atlas-exemple.tn","telephone":"+216 20 123 456"}
```

Attendu : **201**. Attendre 2 à 3 secondes (c'est asynchrone).

**Changer de rôle : se connecter en COMMERCIAL.** Service **Clients (Customer)** :

| Route | Paramètre | Attendu |
|---|---|---|
| `GET /clients` | `q` = `karim@atlas-exemple.tn` | une fiche, statut **PROSPECT**, `campagneSourceId` = `<CAMP>` ; noter son `id` -> `<PROSPECT>` |
| `GET /clients/{id}/contacts` | id = `<PROSPECT>` | le contact « Karim Ben Salah » |
| `PATCH /clients/{id}/statut` | id = `<PROSPECT>` ; `{"statut":"CLIENT"}` | 200 |

**À expliquer :** Campaign n'a pas appelé Customer. Il a publié l'événement `ProspectGenere` sur RabbitMQ, et Customer l'a reçu et a créé la fiche. Pour le montrer : console RabbitMQ http://localhost:15672 (guest / guest), onglet Queues, file `customer.prospect-genere`.

### Étape 6 - Rôle **COMMERCIAL** : le devis

Toujours COMMERCIAL. Service **Devis (Quotation)** :

| Route | Corps / paramètre | Garder |
|---|---|---|
| `POST /devis` | `{"clientId":"<PROSPECT>","dateValidite":"<DANS_30_JOURS>","campagneId":"<CAMP>"}` | `id` -> `<DEVIS>` |
| `POST /devis/{id}/lignes` | id = `<DEVIS>` ; `{"produitId":"<PRODUIT>","quantite":2}` | |
| `POST /devis/{id}/envoyer` | id = `<DEVIS>` | |

Attendu : le devis contient `clientEmail = karim@atlas-exemple.tn` (**Quotation a appelé Customer par HTTP**) ; totaux **2000 HT, 2380 TTC**.

### Étape 7 - Public : le client accepte le devis

Le lien est dans les logs de Quotation :

```
# PowerShell
docker logs quotation-service 2>&1 | Select-String "devis/reponse"
# Git Bash
docker logs quotation-service 2>&1 | grep "devis/reponse"
```

Copier le texte entre `/devis/reponse/` et `?choix=accepter` : `<TOKEN_DEVIS>`.

**Se déconnecter** (Logout). Service **Devis (Quotation)** :

| Route | Paramètre | Attendu |
|---|---|---|
| `POST /public/reponse/{jeton}/accepter` | `<TOKEN_DEVIS>` | 200, statut `ACCEPTE` |
| (la même, une 2e fois) | `<TOKEN_DEVIS>` | **409** |

### Étape 8 - Rôle **MARKETING** : le ROI (la fin de l'histoire)

**Changer de rôle : se connecter en MARKETING.** Service **Campagnes (Campaign)** :

| Route | Corps / paramètre | Attendu |
|---|---|---|
| `PUT /campagnes/{id}/mesures` | id = `<CAMP>` ; `{"impressions":10000,"clics":250,"montantDepense":500}` | 200 |
| `GET /campagnes/{id}/performance` | id = `<CAMP>` (attendre 2-3 s après l'étape 7) | `devisAcceptes` 1, `chiffreAffairesAttribue` 2000, **`roi` 3**, `cpc` 2, `clientsInteresses` 1, `prospectsGeneres` 1 |
| `GET /campagnes/{id}/rapport` | id = `<CAMP>` | un PDF |

**À expliquer :** Quotation a publié `DevisAccepte` sur RabbitMQ ; Campaign l'a reçu et a attribué les 2000 à la campagne. ROI = (2000 - 500) / 500 = 3.

## D. La sécurité, à montrer (3 minutes)

| Rôle à avoir | Service | Route | Attendu |
|---|---|---|---|
| aucun (Logout) | Customer | `GET /clients` | **401** (la passerelle refuse) |
| aucun | Auth | `POST /login` avec `{"email":"admin@exemple.tn","motDePasse":"faux"}` | **401** « Identifiants invalides » |
| **MARKETING** | Customer | `GET /clients` | **403** « Rôle non autorisé pour cette route » (jeton valide, mais ce rôle n'a pas ce droit : le service décide) |
| **COMMERCIAL** | Customer | `GET /clients` | **200** (le même appel, autorisé pour ce rôle) |
| **COMMERCIAL** | Customer | `POST /categories` | **403** (réservé à ADMIN) |
| **ADMIN** | Customer | `POST /categories` | **201** |

**À expliquer :** la passerelle vérifie seulement que le jeton est valide (401 sinon). Chaque service revérifie le jeton et contrôle le **rôle** de la route (403). Un service appelé directement reste protégé : `http://localhost:8081/clients` dans le navigateur répond aussi 401.

## E. Si quelque chose ne marche pas

| Symptôme | Cause probable | Solution |
|---|---|---|
| 401 partout, même après Authorize | jeton non collé **dans ce service** (le menu a changé), expiré (1 h), ou Logout fait | refaire B.3 : Authorize et recoller le jeton dans le service courant |
| 403 sur une route | mauvais rôle | voir le rôle indiqué à l'étape, refaire B.3 avec lui |
| « La date de début n'est pas encore atteinte » | date locale au lieu d'UTC | utiliser la date UTC (section Dates) |
| 409 à la création d'un client | l'email existe déjà (démo déjà jouée) | refaire A (`down -v`) |
| `GET /clients?q=...` ne trouve rien | l'événement n'est pas encore traité | attendre 3 s et réessayer |
| un service ne répond pas (503, erreur réseau) | il démarre encore, ou s'est arrêté | `docker compose --profile services ps` ; attendre ; au besoin `docker compose --profile services up -d` |

Alternative sans clics : `python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service --gateway http://localhost:8080 --auth-url http://localhost:8080` joue tout le parcours automatiquement (28 vérifications).
