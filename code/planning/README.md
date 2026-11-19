# Planning : répartition du travail

Ce dossier est la **répartition principale** du travail entre les trois membres du trinôme : qui fait quoi, dans quel dossier, dans quel ordre, et comment les trois parties s'assemblent. Chaque étape a une fiche qui contient tout ce qu'il faut pour la réaliser (objectif, modèle de données, routes, règles, tâches, fichiers, tests, validation).

## Le projet en bref

Un module commercial pour une société d'ingénierie, fait de **trois services Java (Spring Boot)** indépendants : **Customer** (clients, prospects, contacts), **Quotation** (devis) et **Campaign** (campagnes publicitaires et leur rentabilité). Chaîne couverte : une campagne attire un prospect → il devient client → on lui envoie un devis → son acceptation est attribuée à la campagne, ce qui donne le ROI. Les services communiquent par des appels REST (lectures) et par deux événements RabbitMQ (écritures). Les contrats exacts sont dans `../contrat_api/`.

## 1. Principe

**Un membre = un service = un dossier de code = une base de données.** Chacun travaille seul dans son dossier : les fichiers que deux personnes modifient en même temps sont presque inexistants, donc très peu de conflits. Les services ne se parlent qu'à travers le **contrat d'API** (`../contrat_api/`) : chacun peut donc développer et tester son service **sans attendre les autres**, en simulant ce qu'il appelle. Quand les trois parties sont prêtes, on les **intègre** (phase 2).

## 2. Qui fait quoi

| Membre | Service | Dossier de code | Port | Base (MySQL) | Sous-services | Étapes |
|---|---|---|---|---|---|---|
| **Baha Farhati** | Customer | `customer-service/` | 8081 | `customer_db` (3307) | 1.1 Fiches clients · 1.2 Contacts · 1.3 Interactions | B0, B1, B2 |
| **Oussema Souidi** | Quotation | `quotation-service/` | 8082 | `quotation_db` (3308) | 2.1 Devis · 2.2 Catalogue · 2.3 Calcul · 2.4 Documents et envoi | S1, S2, S3 |
| **Oussema Arfaoui** | Campaign | `campaign-service/` | 8083 | `campaign_db` (3309) | 3.1 Campagnes · 3.2 Ciblage et canaux · 3.3 Contenus · 3.4 Performance et budget | A1, A2, A3, A4 |
| **Tout le trinôme** | Intégration | les trois services | – | – | Événements, bout en bout, Docker, sécurité | E1, E2, E3 |

Les fiches de chaque membre sont dans son sous-dossier de `planning/` : `baha_customer/`, `souidi_quotation/`, `arfaoui_campaign/`, `commun_integration/`.

## 3. Règles pour éviter les conflits

- **On ne modifie que son propre dossier de service** (`code/<son-service>/`) et son dossier de planning.
- **Pas de code partagé** entre services : chaque service a ses propres classes d'erreur et ses objets d'échange. Un service ne lit jamais la base d'un autre.
- **Fichiers communs** (`docker-compose.yml`, `code/README.md`, `contrat_api/`) : modifiés seulement après accord du trinôme, dans un commit séparé, par une seule personne à la fois.
- **Le contrat d'API est la frontière.** Si une route doit changer, on en discute ensemble, on modifie le contrat, puis chacun adapte son code.
- **Une branche par membre** (`customer`, `quotation`, `campaign`), fusionnée dans `main` quand l'étape est validée.

## 4. Ordre de travail

```
Phase 1 : chacun dans son service, en parallèle
   Baha     :  B0 → B1 → B2
   Souidi   :  S1 → S2 → S3            (Customer simulé)
   Arfaoui  :  A1 → A2 → A3 → A4       (Customer simulé)

Phase 2 : intégration, ensemble
   E1  événements RabbitMQ  →  E2  scénario de bout en bout et Docker  →  E3  JWT et rôles
```

## 5. Les points d'intégration

C'est là que les trois parties se rejoignent. Chaque lien (I1 à I4) est décrit dans le contrat d'API ; chacun le développe d'abord contre une **simulation**, puis on le teste en réel.

| Lien | Qui fournit | Qui consomme | Contrat | Simulation avant l'intégration |
|---|---|---|---|---|
| I1 : Quotation lit un client (`GET /clients/{id}`) | Baha (B1) | Souidi (S2) | `customer.yaml` | Faux Customer qui répond comme le contrat |
| I2 : Campaign lit les destinataires (`GET /clients/destinataires`) | Baha (B1) | Arfaoui (A3) | `customer.yaml` | Faux Customer |
| I3 : événement `ProspectGenere` | Arfaoui publie (A3) | Baha reçoit (E1) | `evenements.md` | L'envoi écrit seulement dans les logs |
| I4 : événement `DevisAccepte` | Souidi publie (S3) | Arfaoui reçoit (E1) | `evenements.md` | L'envoi écrit seulement dans les logs |
| Authentification (JWT) | Autre trinôme | Les trois services (E3) | `contrat_commun.md` | Aucune, en attendant |

## 6. Contenu d'une fiche

Une fiche contient : l'objectif et ce qui n'en fait pas partie ; le modèle de données ; les routes, rôles et objets échangés (extraits des fichiers OpenAPI) ; les règles métier ; les tâches à cocher, dans l'ordre ; les fichiers créés ; les tests à écrire ; les points d'attention ; comment valider ; le message de commit.

## 6 bis. Guides de code (Customer et Quotation)

Pour les étapes B1, B2, S1, S2 et S3, un **guide de code** donne tout ce qu'il faut pour réaliser l'étape sans avoir à réfléchir : l'arborescence, le code complet de chaque fichier (à créer au chemin indiqué), les routes avec leurs corps de requête et de réponse, le flux et les commandes de vérification. Ce code a été compilé et testé.

| Guide | Pour | Étapes |
|---|---|---|
| [`baha_customer/GUIDE_CODE_CUSTOMER.md`](baha_customer/GUIDE_CODE_CUSTOMER.md) | Baha Farhati (Customer) | B1, B2 |
| [`souidi_quotation/GUIDE_CODE_QUOTATION.md`](souidi_quotation/GUIDE_CODE_QUOTATION.md) | Oussema Souidi (Quotation) | S1, S2, S3 |

## 7. Validation et livraison

1. Lire la fiche, écrire le code, cocher les tâches.
2. `mvn clean test` : tout vert.
3. Essai à la main (section « Comment valider » de la fiche).
4. Le trinôme valide.
5. Commit, tag `etape-<id>` (par exemple `etape-B1`) et push.

Jamais de mot de passe réel, de fichier `.env` ni de `target/` dans git. Les identifiants du `docker-compose.yml` sont de développement.

## 8. Liste des étapes

| Étape | Fiche | Responsable | État |
|---|---|---|---|
| B0 | [Structure du code et squelette Customer](baha_customer/B0_structure_et_squelette_customer.md) | Baha Farhati | Fait, en attente de validation |
| B1 | [Customer : catégories et clients](baha_customer/B1_customer_categories_et_clients.md) | Baha Farhati | À faire |
| B2 | [Customer : contacts et interactions](baha_customer/B2_customer_contacts_et_interactions.md) | Baha Farhati | À faire |
| S1 | [Quotation : squelette, catalogue et taxes](souidi_quotation/S1_quotation_squelette_catalogue_taxes.md) | Oussema Souidi | À faire |
| S2 | [Quotation : devis, lignes et calcul](souidi_quotation/S2_quotation_devis_et_calcul.md) | Oussema Souidi | À faire |
| S3 | [Quotation : PDF, envoi, réponse publique et expiration](souidi_quotation/S3_quotation_envoi_pdf_reponse_publique.md) | Oussema Souidi | À faire |
| A1 | [Campaign : squelette du service](arfaoui_campaign/A1_squelette_campaign.md) | Oussema Arfaoui | À faire |
| A2 | [Campaign : campagnes, publics, canaux, contenus et budget](arfaoui_campaign/A2_campaign_campagnes_publics_canaux_contenus_budget.md) | Oussema Arfaoui | À faire |
| A3 | [Campaign : lancement, invitations et réponses publiques](arfaoui_campaign/A3_campaign_lancement_invitations_reponses.md) | Oussema Arfaoui | À faire |
| A4 | [Campaign : mesures, performance, ROI, rapport et fin planifiée](arfaoui_campaign/A4_campaign_performance_roi_taches.md) | Oussema Arfaoui | À faire |
| E1 | [Événements RabbitMQ : ProspectGenere et DevisAccepte](commun_integration/E1_evenements_rabbitmq.md) | Tout le trinôme | À faire |
| E2 | [Scénario de bout en bout et Docker des services](commun_integration/E2_bout_en_bout_et_docker.md) | Tout le trinôme | À faire |
| E3 | [Authentification JWT et contrôle des rôles](commun_integration/E3_jwt_et_roles.md) | Tout le trinôme | À faire |
