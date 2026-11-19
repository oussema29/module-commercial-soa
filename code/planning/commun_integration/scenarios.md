# Scénarios de bout en bout (étape E2)

Données fictives. Les scénarios ont été **joués en réel** : bases MySQL, RabbitMQ et les trois services.

## Préparer

```
cd code
docker compose --profile services up -d --build        # infrastructure + les trois services (6 minutes la première fois)
# ou, pour lancer les services sur le poste : docker compose up -d, puis mvn spring-boot:run dans chaque service
```

Pour essayer avec l'authentification : `SECURITY_MODE=jwt JWT_SECRET=<32 caractères au moins> docker compose --profile services up -d --build`.

## 1. Scénario nominal : campagne → prospect → client → devis accepté → ROI

```
python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service
python scenarios/scenario_complet.py ... --jwt-secret <le même secret>        # en mode jwt
```

Le script joue, dans l'ordre :

| Étape | Flux | Ce qui est vérifié | Résultat |
|---|---|---|---|
| 1 | Customer | Une catégorie VIP et deux clients existants (statut CLIENT) sont créés | OK |
| 2 | Quotation | Une taxe et un produit du catalogue sont créés | OK |
| 3 | F1 | Une campagne est configurée (canal EMAIL, public « clients existants », contenu avec un produit, budget 500) puis **lancée** : Campaign lit la liste des destinataires chez Customer (lien I2), crée **2 invitations** et envoie 2 emails simulés ; la campagne est ACTIVE ; la liste des invitations n'expose pas le jeton | OK |
| 4 | F2b | Un client clique « Je suis intéressé » (jeton lu dans l'email simulé) : compté ; **un second clic répond 409 « Vous avez déjà répondu »** ; `GET /reponses` compte 1 réponse | OK |
| 5 | F2a | Un inconnu (une entreprise) remplit le formulaire public : Campaign publie `ProspectGenere`, **Customer crée la fiche PROSPECT** (via RabbitMQ) avec la campagne d'origine et le contact de l'entreprise ; un deuxième envoi du même formulaire ne crée **pas de doublon** | OK |
| 6 | F3 | Le commercial passe le prospect à CLIENT (décision manuelle) | OK |
| 7 | F4, F5 | Un devis est créé pour ce client avec `campagneId` (Quotation lit Customer, lien I1) ; une ligne de 2 × 1 000 donne 2 000 HT et 2 380 TTC ; le devis est envoyé ; le client l'accepte par son lien (jeton lu dans l'email simulé) ; **une seconde acceptation répond 409** | OK |
| 8 | I4 | Quotation publie `DevisAccepte`, Campaign l'ajoute : `devisAcceptes = 1`, chiffre d'affaires 2 000 ; après saisie des mesures (500 dépensés, 250 clics) : **ROI = (2 000 − 500) / 500 = 3**, CPC = 2, prospects générés = 2, clients intéressés = 1 ; le rapport PDF se télécharge | OK |

Résultat joué : **28 vérifications correctes sur 28**, avec les services sur le poste, dans Docker, et dans Docker en mode jwt (un jeton par rôle, JWT retransmis de Quotation et de Campaign vers Customer).

**Avec un vrai login** (service `auth-service`, services en mode jwt) : `--auth-url http://localhost:8084`. Le script se connecte une fois par rôle (ADMIN, COMMERCIAL, MARKETING) avec les utilisateurs fictifs et appelle chaque route avec le jeton du bon rôle. Résultat joué dans Docker : **28 vérifications sur 28**.

Contrôles complémentaires joués sur le login :

| Contrôle | Résultat |
|---|---|
| `POST /login` du marketing | 200, jeton, rôle MARKETING |
| Mauvais mot de passe ; email inconnu | 401 « Identifiants invalides » dans les deux cas |
| Jeton du marketing sur Campaign `GET /campagnes` | 200 |
| Même jeton sur Customer `GET /clients` et Quotation `GET /devis` | 403 (rôle non autorisé) |
| Sans jeton ; jeton falsifié | 401 |
| Scénario lancé sans option alors que les services exigent un jeton | message d'explication (code de sortie 2) |

**À travers la passerelle** (`gateway-service`, un seul point d'entrée) : `--gateway http://localhost:8080 --auth-url http://localhost:8080`. Les services sont en mode jwt, le login et tous les appels passent par le port 8080. Résultat joué : **28 vérifications sur 28**.

Contrôles complémentaires joués sur la passerelle (Docker, mode jwt) :

| Contrôle | Résultat |
|---|---|
| `POST /login` par la passerelle, sans jeton | 200, jeton |
| `GET /campagnes` avec le jeton du MARKETING | 200 |
| Sans jeton ; jeton falsifié | 401 renvoyé par la passerelle, avant de transmettre |
| `GET /clients` avec le jeton du MARKETING | 403 renvoyé par le **service** Customer (la passerelle ne contrôle pas les rôles) |
| Chemin inconnu | 404 « Route inconnue » |
| Route publique sans jeton | Transmise au service |
| Pré-vérification CORS (`OPTIONS`) | 200 avec les en-têtes `Access-Control-Allow-*` |
| `customer-service` arrêté : `GET /clients` | 503 « Service indisponible » ; `GET /devis` reste à 200 ; retour à 200 au redémarrage |
| Accès direct à un service (8081, 8083) | Toujours protégé : 401 sans jeton, 403 avec un mauvais rôle |

## 2. Cas dégradés (F7), joués en réel

| Cas | Manipulation | Résultat attendu | Résultat constaté |
|---|---|---|---|
| Customer en panne au lancement | Arrêter Customer, `POST /campagnes/{id}/lancer` | 503 « Service Customer indisponible », la campagne reste BROUILLON, aucune invitation | 503 ; statut BROUILLON |
| Customer en panne à la création d'un devis | Arrêter Customer, `POST /devis` | 503, aucun devis créé | 503 ; aucun devis créé |
| Campaign en panne pendant des acceptations | Arrêter Campaign, faire accepter 2 devis, relancer Campaign | Les 2 messages attendent dans la file `campaign.devis-accepte`, puis sont traités au retour | 2 messages en attente pendant la panne ; au retour `devisAcceptes = 2`, chiffre d'affaires 2 000 |
| Événement reçu deux fois | Publier deux fois le même `DevisAccepte` (même `eventId`) depuis la console de RabbitMQ | Compté une seule fois | `devisAcceptes` passe de 2 à 3 (un seul ajout) |
| RabbitMQ en panne à l'acceptation | Arrêter RabbitMQ, accepter un devis, relancer RabbitMQ | L'acceptation du client reste enregistrée ; l'événement est écrit en JSON dans les logs ; les services se reconnectent | 200 « ACCEPTE » ; ligne « ÉVÉNEMENT NON PUBLIÉ » avec le JSON ; au retour, 1 consommateur sur chaque file |
| Campagne terminée | Après `arreter` ou après la date de fin | Les routes publiques répondent 409 « La campagne n'est plus active » ; un `DevisAccepte` reçu plus tard est **compté quand même** | Couvert par les tests automatiques (`ReponsePubliqueApiTests`, `DevisAccepteServiceTests`) |

## 3. Les tests automatiques

Chaque service a ses tests (`mvn clean test`, sans Docker, base H2 en mémoire) : règles métier, appels aux autres services **simulés**, consommateurs d'événements, et en mode jwt la **matrice complète des rôles** (chaque route avec chacun des quatre rôles, générée à partir du contrat).

| Service | Tests au vert |
|---|---|
| Customer | 131 |
| Quotation | 145 |
| Campaign | 227 |
