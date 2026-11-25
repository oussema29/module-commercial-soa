# Démonstration complète avec Swagger (tout ce qu'il faut est dans ce document)

**Fil rouge :** une campagne marketing amène un prospect, qui devient client, reçoit un devis, l'accepte, et la campagne mesure son ROI. Début du flux : le **login**. Fin du flux : le **ROI de la campagne**.

Tout se fait dans le navigateur, sur **une seule page : http://localhost:8080/swagger-ui.html** (la passerelle, point d'entrée unique). Seules deux commandes de terminal sont nécessaires : le lancement, et la lecture de deux liens dans les logs (étapes 6 et 10).

---

## 1. Avant la démo (10 minutes, à faire avant l'arrivée du professeur)

### 1.1 Lancer avec la sécurité JWT, sur une base vide

Dans un terminal, dossier `code` :

```
docker compose --profile services down -v          # supprime toutes les données : bases vides
```

Puis, **selon votre terminal** :

```
# PowerShell
$env:SECURITY_MODE="jwt"; docker compose --profile services up -d --build

# Git Bash / Linux / macOS
SECURITY_MODE=jwt docker compose --profile services up -d --build
```

- **`SECURITY_MODE=jwt` est indispensable** : sans lui, la sécurité est désactivée (mode `none`) et les étapes 401 et 403 de la démonstration ne marchent pas.
- Attendre environ une minute. Les trois services de données se relancent seuls si leur base n'est pas encore prête (`restart: on-failure`).
- **Contrôle :** ouvrir http://localhost:8080/actuator/health dans le navigateur : `{"status":"UP"}`. Puis http://localhost:8080/clients : doit afficher une erreur **401** « Authentification requise ».
- Les valeurs de ce document (`alpha@exemple.tn`...) ne se créent qu'**une fois** : pour rejouer, refaire `down -v` puis le lancement.

### 1.2 Préparer trois jetons propres dans le Bloc-notes

Un jeton est un long texte qui prouve qui vous êtes. Il **dure 1 heure** : préparez-le dans l'heure qui précède la démo (sinon, refaire cette étape).

1. Ouvrir la page Swagger : elle s'ouvre sur **Connexion (Auth)**. Cliquer sur `POST /login`, puis **Try it out**.
2. Cliquer dans la zone **Request body**, `Ctrl+A`, et coller le corps de l'utilisateur voulu (tableau ci-dessous), puis **Execute**. Le code **200** s'affiche avec une réponse qui contient une ligne `"jeton": "eyJ...",`.
3. **Triple-clic** sur cette ligne (elle se sélectionne en entier), puis `Ctrl+C`.
4. Dans le Bloc-notes, taper un titre (par exemple `ADMIN`), puis une nouvelle ligne, et coller (`Ctrl+V`). La ligne collée ressemble à `  "jeton": "eyJ...",`.
5. **Nettoyer cette ligne** : appuyer sur **Début**, puis **Suppr 12 fois**, puis **Fin**, puis **Retour arrière 2 fois**. Il ne reste que le jeton, qui commence par `eyJ`, sans guillemet, sans virgule, sans espace.
6. Recommencer pour les deux autres utilisateurs.

Résultat dans le Bloc-notes :

```
ADMIN
eyJ...                (le jeton ADMIN, propre)
COMMERCIAL
eyJ...
MARKETING
eyJ...
```

| Rôle | Corps à coller dans `POST /login` |
|---|---|
| ADMIN | `{"email":"admin@exemple.tn","motDePasse":"admin-demo-1"}` |
| COMMERCIAL | `{"email":"commercial@exemple.tn","motDePasse":"commercial-demo-1"}` |
| MARKETING | `{"email":"marketing@exemple.tn","motDePasse":"marketing-demo-1"}` |

(Le quatrième utilisateur fictif, `responsable@exemple.tn` / `responsable-demo-1`, de rôle RESPONSABLE, n'est pas utilisé dans le parcours.)

### 1.3 Les deux dates (UTC)

Les services Docker sont en **UTC** : la date de début d'une campagne doit être la date du jour **en UTC** (la date locale peut avoir un jour d'avance, et le lancement répondrait « La date de début n'est pas encore atteinte »). Dans un terminal :

```
python -c "from datetime import *; d=datetime.now(timezone.utc).date(); print(d, d+timedelta(days=30))"
```

Noter les deux dates : `<AUJOURDHUI>` et `<DANS_30_JOURS>` (format `2026-11-24`).

---

## 2. Mode d'emploi de Swagger (à lire une fois)

1. **Le menu déroulant « Select a definition »** (en haut à droite) change de service : **Connexion (Auth)**, **Clients (Customer)**, **Devis (Quotation)**, **Campagnes (Campaign)**. La page s'ouvre sur Connexion.
2. **Appeler une route :** cliquer sur la route pour la déplier, puis **Try it out**. Dans la zone **Request body**, `Ctrl+A` puis coller le JSON de ce document (Swagger y met un exemple à remplacer). Remplir les champs du chemin (par exemple `id`), puis **Execute**. Le **code** et la réponse s'affichent en dessous.
3. **Donner son identité à un service (le bouton Authorize) :**
   1. Choisir le service dans le menu.
   2. Cliquer sur le bouton vert **Authorize** (en haut à droite, avec un cadenas).
   3. Dans le champ **Value**, coller le jeton du rôle voulu **(seulement le jeton : ne pas écrire « Bearer », Swagger l'ajoute)**.
   4. Cliquer **Authorize** dans la fenêtre, puis **Close**. Le cadenas du bouton se ferme.
4. **Swagger oublie le jeton quand on change de service dans le menu** (vérifié) : à chaque changement de service, refaire le point 3 avec le jeton du rôle de l'étape. C'est l'erreur la plus fréquente : un **401** après un changement de service veut dire « jeton non collé dans ce service ».
5. **Changer de rôle dans un même service :** Authorize, **Logout**, coller l'autre jeton, Authorize, Close.
6. **Appeler sans jeton :** Authorize, **Logout**, Close.
7. La page **Connexion (Auth)** n'a pas de bouton Authorize : le login est public.
8. Les `id` à réutiliser (`<CAT>`, `<CAMP>`...) sont dans les réponses : les copier au fur et à mesure dans le Bloc-notes.

---

## 3. La carte du flux

```
Auth ──jeton──► (tous les appels)

ADMIN        Customer: catégorie VIP          COMMERCIAL   Customer: deux clients existants
ADMIN        Quotation: taxe + produit        ADMIN        Campaign: canal Email
MARKETING    Campaign: crée la campagne, le public (clients VIP), le contenu (avec le produit), puis la LANCE
                  └─ Campaign ──HTTP──► Customer (lit les clients VIP) ──► envoie les invitations (emails simulés)
Visiteur     (public) clique l'invitation, ou remplit le formulaire de la campagne
                  └─ Campaign ──RabbitMQ: ProspectGenere──► Customer crée une fiche PROSPECT
COMMERCIAL   Customer: passe le prospect à CLIENT
COMMERCIAL   Quotation: crée un devis pour ce client (Quotation ──HTTP──► Customer lit le client), ajoute une ligne, l'envoie
Client       (public) accepte le devis par son lien
                  └─ Quotation ──RabbitMQ: DevisAccepte──► Campaign attribue le chiffre d'affaires à la campagne
MARKETING    Campaign: consulte la performance (ROI, CPC...) et le rapport PDF
```

---

## 4. Le parcours, étape par étape

Chaque étape indique : le **service** à choisir dans le menu, le **jeton** à mettre avec Authorize (rôle), puis les routes et les corps. Rappel : **à chaque changement de service, Authorize + coller le jeton** (point 2.4).

### Étape 0. Sans jeton : refusé (401)

Menu **Clients (Customer)**, ne pas faire Authorize. `GET /clients` : Try it out, Execute.
**Attendu : 401** `"Authentification requise"` (refusé par la passerelle).

### Étape 1. Customer, jeton ADMIN : la catégorie

Menu **Clients (Customer)**, Authorize avec **ADMIN**.

| Route | Corps | Garder |
|---|---|---|
| `POST /categories` | `{"nom":"VIP","description":"Clients importants"}` | `id` -> `<CAT>` |

### Étape 2. Customer, jeton COMMERCIAL : deux clients existants

Même service, **changer de rôle** : Authorize, Logout, coller **COMMERCIAL**.

| Route | Corps |
|---|---|
| `POST /clients` | `{"type":"ENTREPRISE","nom":"Alpha","statut":"CLIENT","email":"alpha@exemple.tn","categorieId":"<CAT>"}` |
| `POST /clients` | `{"type":"ENTREPRISE","nom":"Beta","statut":"CLIENT","email":"beta@exemple.tn","categorieId":"<CAT>"}` |

**Attendu : 201** chacun. (Vérification du rôle : essayez `POST /categories` avec ce jeton, **403** car réservé à ADMIN.)

### Étape 3. Quotation, jeton ADMIN : la taxe et le produit

Menu **Devis (Quotation)**, Authorize avec **ADMIN**.

| Route | Corps | Garder |
|---|---|---|
| `POST /taxes` | `{"tauxTVA":19,"description":"TVA standard"}` | `id` -> `<TAXE>` |
| `POST /produits` | `{"nom":"Étude de faisabilité","description":"Forfait 5 jours","prix":1000.000,"taxeId":"<TAXE>"}` | `id` -> `<PRODUIT>` |

### Étape 4. Campaign, jeton ADMIN : le canal

Menu **Campagnes (Campaign)**, Authorize avec **ADMIN**.

| Route | Corps | Garder |
|---|---|---|
| `POST /canaux` | `{"nom":"Email","type":"EMAIL"}` | `id` -> `<CANAL>` |

### Étape 5. Campaign, jeton MARKETING : créer et lancer la campagne

Même service, **changer de rôle** : Authorize, Logout, coller **MARKETING**.

| Route | Corps / paramètre | Garder |
|---|---|---|
| `POST /campagnes` | `{"nom":"Offre fidélité","objectif":"FIDELISATION","dateDebut":"<AUJOURDHUI>","dateFin":"<DANS_30_JOURS>"}` | `id` -> `<CAMP>` |
| `POST /campagnes/{id}/canaux` | chemin = `<CAMP>` ; `{"canalId":"<CANAL>"}` | |
| `POST /campagnes/{id}/publics` | chemin = `<CAMP>` ; `{"type":"CLIENTS_EXISTANTS","categorieClientId":"<CAT>"}` | |
| `POST /campagnes/{id}/contenus` | chemin = `<CAMP>` ; `{"titre":"-20 % ce mois-ci","texte":"Offre fidélité"}` | `id` -> `<CONTENU>` |
| `POST /contenus/{id}/produits` | chemin = `<CONTENU>` ; `{"produitId":"<PRODUIT>","produitNom":"Étude de faisabilité"}` | |
| `PUT /campagnes/{id}/budget` | chemin = `<CAMP>` ; `{"montantPrevu":500}` | |
| `POST /campagnes/{id}/lancer` | chemin = `<CAMP>` (pas de corps) | |

**Attendu au lancement :** `envoisReussis` = **2**. **À expliquer :** Campaign a appelé Customer par HTTP, en transmettant le jeton de l'utilisateur, pour lire les clients de la catégorie VIP, puis a « envoyé » 2 invitations (emails simulés).

### Étape 6. Un client existant répond à l'invitation (route publique)

Les emails sont simulés : le lien est écrit dans les logs de Campaign. Dans un terminal :

```
# PowerShell
docker logs campaign-service 2>&1 | Select-String "campagnes/invitation"
# Git Bash
docker logs campaign-service 2>&1 | grep "campagnes/invitation"
```

Le lien se termine par `.../campagnes/invitation/<TOKEN_INVITATION>` : copier le long texte final (deux liens apparaissent, un par client : en prendre un).

Menu **Campagnes (Campaign)** (pas besoin de jeton : routes publiques ; si une réponse 401 apparaît, faire Logout).

| Route | Paramètre | Attendu |
|---|---|---|
| `POST /public/invitations/{jeton}/reponse` | `<TOKEN_INVITATION>` | **200** |
| la même, une deuxième fois | `<TOKEN_INVITATION>` | **409** « Vous avez déjà répondu » |

### Étape 7. Un inconnu remplit le formulaire : l'événement RabbitMQ (route publique)

Menu **Campagnes (Campaign)** : `POST /public/campagnes/{id}/reponse`, chemin = `<CAMP>` :

```
{"type":"ENTREPRISE","nom":"Société Atlas","nomContact":"Karim Ben Salah","email":"karim@atlas-exemple.tn","telephone":"+216 20 123 456"}
```

**Attendu : 201.** Attendre 3 secondes (le traitement est asynchrone).

Menu **Clients (Customer)**, Authorize avec **COMMERCIAL** :

| Route | Paramètre | Attendu |
|---|---|---|
| `GET /clients` | `q` = `karim@atlas-exemple.tn` (laisser les autres vides) | une fiche, statut **PROSPECT**, `campagneSourceId` = `<CAMP>` ; garder son `id` -> `<PROSPECT>` |
| `GET /clients/{clientId}/contacts` | `<PROSPECT>` | le contact « Karim Ben Salah » |

**À expliquer :** Campaign n'a pas appelé Customer. Il a publié l'événement `ProspectGenere` sur RabbitMQ (échange `module-commercial`, clé `prospect.genere`), et Customer l'a reçu et a créé la fiche. Renvoyer le même formulaire ne crée pas de doublon (idempotence). *Facultatif :* console RabbitMQ http://localhost:15672 (guest / guest), onglet Queues, file `customer.prospect-genere`.

### Étape 8. Customer, jeton COMMERCIAL : le prospect devient client

Même service, même jeton (déjà fait à l'étape 7).

| Route | Paramètre / corps | Attendu |
|---|---|---|
| `PATCH /clients/{id}/statut` | chemin = `<PROSPECT>` ; `{"statut":"CLIENT"}` | **200**, statut `CLIENT` |

### Étape 9. Quotation, jeton COMMERCIAL : le devis

Menu **Devis (Quotation)**, Authorize avec **COMMERCIAL**.

| Route | Corps / paramètre | Garder |
|---|---|---|
| `POST /devis` | `{"clientId":"<PROSPECT>","dateValidite":"<DANS_30_JOURS>","campagneId":"<CAMP>"}` | `id` -> `<DEVIS>` |
| `POST /devis/{id}/lignes` | chemin = `<DEVIS>` ; `{"produitId":"<PRODUIT>","quantite":2}` | |
| `POST /devis/{id}/envoyer` | chemin = `<DEVIS>` (pas de corps) | |

**Attendu :** la réponse du devis contient `clientEmail` = `karim@atlas-exemple.tn` (**Quotation a appelé Customer par HTTP** pour lire le client) ; totaux **2000 HT, 2380 TTC** (2 x 1000, TVA 19 %).

### Étape 10. Le client accepte le devis (route publique)

Le lien du devis est dans les logs de Quotation :

```
# PowerShell
docker logs quotation-service 2>&1 | Select-String "devis/reponse"
# Git Bash
docker logs quotation-service 2>&1 | grep "devis/reponse"
```

Le lien contient `.../devis/reponse/<TOKEN_DEVIS>?choix=accepter` : copier le texte entre `/devis/reponse/` et `?choix=accepter`.

Menu **Devis (Quotation)** :

| Route | Paramètre | Attendu |
|---|---|---|
| `POST /public/reponse/{jeton}/accepter` | `<TOKEN_DEVIS>` | **200**, statut `ACCEPTE` |
| la même, une deuxième fois | `<TOKEN_DEVIS>` | **409** |

Quotation publie l'événement `DevisAccepte` sur RabbitMQ (clé `devis.accepte`) ; Campaign le reçoit et attribue le devis à sa campagne. Attendre 3 secondes.

### Étape 11. Campaign, jeton MARKETING : le ROI (la fin de l'histoire)

Menu **Campagnes (Campaign)**, Authorize avec **MARKETING**.

| Route | Corps / paramètre | Attendu |
|---|---|---|
| `PUT /campagnes/{id}/mesures` | chemin = `<CAMP>` ; `{"impressions":10000,"clics":250,"montantDepense":500}` | **200** |
| `GET /campagnes/{id}/performance` | chemin = `<CAMP>` | voir ci-dessous |
| `GET /campagnes/{id}/rapport` | chemin = `<CAMP>` | un fichier PDF (lien « Download file ») |

**Résultats attendus dans `performance` :** `devisAcceptes` = 1, `chiffreAffairesAttribue` = 2000, **`roi` = 3** ((2000 - 500) / 500), `cpc` = 2 (500 / 250), `clientsInteresses` = 1 (l'invitation de l'étape 6), `prospectsGeneres` = 1 (le formulaire de l'étape 7).

---

## 5. La sécurité, à montrer (3 minutes)

| Service, jeton | Route | Attendu | À expliquer |
|---|---|---|---|
| Customer, Logout (aucun) | `GET /clients` | **401** « Authentification requise » | la **passerelle** refuse : pas de jeton |
| Auth | `POST /login` avec `{"email":"admin@exemple.tn","motDePasse":"faux"}` | **401** « Identifiants invalides » | on ne dit pas si c'est l'email ou le mot de passe |
| Customer, **MARKETING** | `GET /clients` | **403** « Rôle non autorisé pour cette route » | jeton **valide**, mais ce rôle n'a pas ce droit : le **service** décide |
| Customer, **COMMERCIAL** | `GET /clients` | **200** | le même appel, autorisé pour ce rôle |
| Customer, **COMMERCIAL** | `POST /categories` | **403** | réservé à ADMIN |
| Customer, **ADMIN** | `POST /categories` | **201** | |
| navigateur, aucun | ouvrir http://localhost:8081/clients | **401** (page JSON) | un service appelé **directement** se protège aussi |

**À dire :** la passerelle vérifie seulement que le jeton est valide (401 sinon). Chaque service revérifie le jeton et contrôle le **rôle** de la route (403). Le jeton est signé avec un secret partagé (HS256) : il contient l'identité (`sub`) et le rôle (`role`), il ne peut pas être modifié sans le secret. Pour montrer son contenu : coller un jeton sur https://jwt.io.

## 6. Les interactions entre services, à présenter

| Étape | De | Vers | Mode | Pourquoi |
|---|---|---|---|---|
| 5 (lancer) | Campaign | Customer | HTTP | lire les clients du public ciblé |
| 7 (formulaire) | Campaign | Customer | RabbitMQ `ProspectGenere` | créer la fiche prospect, sans couplage |
| 9 (devis) | Quotation | Customer | HTTP | lire le client (nom, e-mail) |
| 10 (acceptation) | Quotation | Campaign | RabbitMQ `DevisAccepte` | attribuer le chiffre d'affaires à la campagne |

Le HTTP sert quand on a besoin de la réponse tout de suite. RabbitMQ sert à prévenir un autre service sans l'attendre ni dépendre de sa disponibilité.

---

## 7. Si quelque chose ne marche pas

| Symptôme | Cause probable | Solution |
|---|---|---|
| **401** après un changement de service | le jeton n'est pas collé dans ce service (Swagger l'oublie) | Authorize, coller le jeton du rôle de l'étape |
| **401** partout | jeton expiré (1 h), ou Logout fait, ou mauvais collage | refaire 1.2 ; vérifier que le champ ne contient que le jeton (commence par `eyJ`, sans guillemet ni virgule) |
| **403** | mauvais rôle pour cette route | prendre le rôle indiqué à l'étape |
| « La date de début n'est pas encore atteinte » | date locale au lieu de la date UTC | utiliser `<AUJOURDHUI>` du point 1.3 |
| **409** à la création d'un client | l'e-mail existe déjà (démo déjà jouée) | refaire `down -v` puis le lancement (1.1) |
| `GET /clients?q=...` ne trouve rien | l'événement n'est pas encore traité | attendre 3 secondes et réessayer |
| **404** sur une route dépliée | l'`id` du chemin est faux ou vide | recopier l'`id` depuis la réponse de création |
| erreur réseau, 503, page qui ne répond pas | un service démarre encore ou s'est arrêté | `docker compose --profile services ps` ; attendre ; au besoin `docker compose --profile services up -d` |
| aucune sécurité (tout passe sans jeton) | lancé sans `SECURITY_MODE=jwt` | refaire 1.1 |

**Plan B, sans aucun clic :** ce script joue tout le parcours automatiquement et affiche OK ou KO pour 28 vérifications (à lancer sur une base vide, depuis le dossier `code`) :

```
python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service --gateway http://localhost:8080 --auth-url http://localhost:8080
```

---

## 8. Liste de contrôle, 5 minutes avant

- [ ] `down -v` puis lancement avec `SECURITY_MODE=jwt` (1.1), santé UP, `/clients` répond 401.
- [ ] Trois jetons propres dans le Bloc-notes, de moins d'une heure (1.2).
- [ ] Les deux dates UTC notées (1.3).
- [ ] Un terminal ouvert dans `code` pour les deux commandes de logs (étapes 6 et 10).
- [ ] Swagger ouvert sur http://localhost:8080/swagger-ui.html.

*Ce qui a été vérifié :* le parcours complet (28 vérifications du script, puis ce document rejoué appel par appel sur une base vide, avec les codes et valeurs ci-dessus) ; dans le navigateur : le login, la copie et le nettoyage du jeton, le bouton Authorize, un appel `GET /clients` en 200 avec le jeton, et la perte du jeton au changement de service. Les autres routes n'ont pas été cliquées une à une dans Swagger.
