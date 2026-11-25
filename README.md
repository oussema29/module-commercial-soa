# Module commercial : clients, devis, campagnes

Projet DOS-IDL-Engineer (gestion des employés ingénieurs), module Développement Orienté Service, 3ème IDL, ISI, année universitaire 2026/27.

Trinôme : Oussema Arfaoui, Oussema Souidi, Baha Farhati. Toutes les données sont fictives.

Ce dépôt contient le volet commercial du système d'information d'une société d'ingénierie, découpé en trois services Spring Boot indépendants, chacun avec sa base MySQL, plus RabbitMQ. Ils communiquent par API REST et par événements, pour s'intégrer aux services des autres trinômes. Le parcours suit une vente : une campagne attire des contacts, ils deviennent prospects puis clients, et reçoivent des devis dont l'acceptation permet de calculer le ROI de la campagne.

Toutes les informations (conception, décisions, modèles de données, API, flux) se trouvent dans `rapport_detaille.pdf`.

## Contenu du dépôt

| Dossier ou fichier | Contenu |
|---|---|
| `code/` | Les services, le `docker-compose.yml`, le scénario de bout en bout, les contrats d'API (`code/contrat_api/`) et le planning des étapes (`code/planning/`) |
| `rapport_prof.pdf` | Rapport de conception, version de synthèse |
| `rapport_detaille.pdf` | Rapport de conception détaillé : décisions, modèles de données, API, flux |
| `tps de formation/` | Les quatre TP conçus par le trinôme (énoncés et corrigés, PDF) |
| `les tp de prof/` | Les TP du cours réalisés : TP1 service SOAP, TP2 service REST, TP3 microservices, avec leurs rapports |

## Où est quoi

```
code/
├── docker-compose.yml      3 bases MySQL + RabbitMQ, et les services (profil « services »)
├── customer-service/       Baha Farhati        port 8081  base customer_db  (MySQL 3307)
├── quotation-service/      Oussema Souidi      port 8082  base quotation_db (MySQL 3308)
├── campaign-service/       Oussema Arfaoui     port 8083  base campaign_db  (MySQL 3309)
├── auth-service/           (provisoire)        port 8084  pas de base : utilisateurs fictifs, émet des JWT
├── gateway-service/        (tout le trinôme)   port 8080  passerelle : UN SEUL point d'entrée, route vers les quatre services
├── contrat_api/            contrats OpenAPI, règles communes, événements
└── planning/               une fiche par étape
```

Chaque service est un projet Maven autonome : aucun code partagé, un service ne dépend jamais du code d'un autre.

- **Customer** : fiches clients et prospects, contacts, interactions.
- **Quotation** : devis, catalogue, calcul HT/TVA/TTC, PDF, envoi par email, réponse du client par lien.
- **Campaign** : campagnes, publics, canaux, contenus, budget, performance et ROI.
- **Auth** : login provisoire, à remplacer par le service d'authentification d'un autre trinôme.
- **Gateway** : point d'entrée unique, vérification du jeton, page Swagger avec un menu par service.

## Principes

- Une base par service, sans clé étrangère entre bases.
- Les lectures entre services sont synchrones (REST) ; les écritures passent par des événements RabbitMQ (échange `module-commercial`) : `ProspectGenere` (Campaign vers Customer) et `DevisAccepte` (Quotation vers Campaign). Les consommateurs sont idempotents.
- Un devis copie les données du client et des prix, pour rester lisible si Customer est indisponible.
- Sécurité : JWT et quatre rôles (`ADMIN`, `COMMERCIAL`, `MARKETING`, `RESPONSABLE`) ; les routes `/public/**` restent ouvertes.
- Erreurs au format `{ code, message, timestamp }`, JSON en `camelCase`, dates ISO 8601, montants décimaux.

## Lancer

Prérequis : Java 21 ou plus, Maven 3, Docker, Python 3.

Tout dans Docker (bases, RabbitMQ et les cinq services ; 6 minutes la première fois) :

```
cd code
docker compose --profile services up -d --build
python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service
```

Ports : **passerelle 8080 (point d'entrée)**, Customer 8081, Quotation 8082, Campaign 8083, Auth 8084. La page Swagger commune est sur `http://localhost:8080/swagger-ui.html`. Console RabbitMQ : `http://localhost:15672` (`guest` / `guest`).

Par défaut, aucune authentification (`SECURITY_MODE=none`). Pour exiger un JWT, lancer avec `SECURITY_MODE=jwt`. Les identifiants MySQL et le secret JWT par défaut sont de développement.

## Répartition

Un membre = un service = un dossier = une base.

| Membre | Service | Étapes |
|---|---|---|
| Baha Farhati | Customer | B0 structure et squelette · B1 catégories et clients · B2 contacts et interactions |
| Oussema Souidi | Quotation | S1 squelette, catalogue, taxes · S2 devis et calcul · S3 envoi, PDF, réponse publique |
| Oussema Arfaoui | Campaign | A1 squelette · A2 campagnes, publics, canaux, contenus, budget · A3 lancement, invitations, réponses · A4 performance, ROI |
| Tous | Intégration | E1 événements RabbitMQ · E2 bout en bout et Docker · E3 JWT et rôles · E4 service de login provisoire · E5 passerelle |

## Périmètre

- Hors périmètre : module Ventes (commande après un devis accepté), Finance, produits et stocks.
- Les impressions, clics et dépenses publicitaires sont saisis ou simulés.
- L'envoi d'email est simulé (écrit dans les logs). Le service Notification, appelé en SOAP, est décrit dans les rapports.
