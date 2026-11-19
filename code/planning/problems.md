# Journal des problèmes rencontrés et corrigés

**But.** Noter chaque erreur trouvée en réalisant les étapes (B0 à E5), la corriger dans les fiches et le contrat, et la faire valider par le trinôme.

Tout a été essayé avec **les vraies bases MySQL, le vrai RabbitMQ et les services dans Docker**.

**États possibles :** *Corrigé, à valider* (la correction est faite, le trinôme doit la confirmer) · *Résolu* (vérifié) · *Ouvert* (reste à faire) · *Limite assumée* (connue, non corrigée volontairement).

## Résultats

| Contrôle | Résultat |
|---|---|
| Tests automatiques Customer | 131 au vert (dont la matrice des rôles) |
| Tests automatiques Quotation | 145 au vert (dont la matrice des rôles) |
| Tests automatiques Campaign | 227 au vert (dont la matrice des rôles) |
| Tests automatiques Auth (login provisoire) | 7 au vert |
| Tests automatiques Gateway (passerelle) | 35 au vert |
| Scénario de bout en bout, services sur le poste, MySQL et RabbitMQ réels | 28 vérifications sur 28 |
| Même scénario, services dans Docker | 28 vérifications sur 28 |
| Même scénario, services dans Docker **en mode jwt** (jetons fabriqués par le script) | 28 vérifications sur 28 |
| Même scénario, services dans Docker en mode jwt, jetons obtenus par un **vrai login** (`auth-service`) | 28 vérifications sur 28 |
| Même scénario **entièrement à travers la passerelle** (8080), en mode jwt, avec un vrai login | 28 vérifications sur 28 |
| Passerelle en réel (Docker) | Sans jeton → 401 ; jeton falsifié → 401 ; rôle interdit → 403 renvoyé par le service ; chemin inconnu → 404 « Route inconnue » ; route publique sans jeton → transmise ; pré-vérification CORS acceptée ; Customer arrêté → 503 pour `/clients` et 200 pour `/devis` ; retour à 200 au redémarrage ; accès direct aux services toujours protégé (401, 403) |
| Contrôles du login en réel | Bon mot de passe → jeton ; mauvais mot de passe et email inconnu → même 401 ; jeton du MARKETING : 200 sur Campaign, 403 sur Customer et Quotation ; sans jeton ou jeton falsifié → 401 |
| Pannes jouées en réel | Customer arrêté pendant un lancement ou un devis → 503, rien de créé ; Campaign arrêté pendant des acceptations → 2 messages attendent dans la file puis sont traités au retour (devis comptés : 2) ; même événement publié deux fois → compté une seule fois ; RabbitMQ arrêté pendant une acceptation → acceptation enregistrée, événement écrit en JSON dans les logs ; les consommateurs se reconnectent au retour de RabbitMQ |

---

## A. Erreurs de guide ou de code (corrigées)

| N° | Où | Problème | Conséquence si non corrigé | Correction | État |
|---|---|---|---|---|---|
| P1 | Guide Customer, B1 | Le guide ne listait pas les 6 fichiers `entity/` et `repository/` de B1 (`TypeClient`, `StatutClient`, `CategorieClient`, `Client`, `CategorieRepository`, `ClientRepository`) | Le code de B1 ne compile pas. Découvert en reconstruisant le service à partir du guide | Les 6 fichiers sont ajoutés (le guide B1 compte maintenant 20 fichiers) | Corrigé, à valider |
| P2 | Code B1, `ClientCreation` et `ClientModification` | Un email avec des espaces autour (`" ali@mail.tn "`) était refusé (422) par la validation au lieu d'être reconnu comme doublon (409) | La règle « email unique sans tenir compte des espaces » n'était pas respectée | Les espaces sont enlevés dans le constructeur des deux objets, avant la validation | Corrigé, à valider |
| P3 | Squelette B0, `GlobalExceptionHandler` de Customer | Un paramètre obligatoire manquant (`GET /clients/destinataires` sans `categorieId`) renvoyait l'erreur par défaut de Spring, pas le format `{code, message, timestamp}` ; une violation d'unicité en base (deux requêtes simultanées avec le même email) donnait une erreur 500 | Corps d'erreur incohérent avec le contrat ; 500 au lieu de 409 | Le gestionnaire traite `MissingServletRequestParameterException` (400) et `DataIntegrityViolationException` (409). Quotation et Campaign ont le même, plus le verrou optimiste | Corrigé, à valider |
| P4 | Fiche B1, modèle de données | Les champs de date s'appelaient `creeLe` et `modifieLe`, alors que le contrat et le code utilisent `createdAt` et `updatedAt` | Un lecteur de la fiche écrirait des noms qui ne correspondent pas au contrat | Fiche corrigée | Corrigé, à valider |
| P5 | Fiches B1 et B2 | Elles annonçaient « valeur d'énumération inconnue → 422 » (statut, type d'interaction) | Le test attendu aurait échoué : Spring renvoie 400 pour une valeur d'énumération inconnue | Fiches corrigées : 400 pour une valeur inconnue, 422 pour un champ absent | Corrigé, à valider |
| P6 | Fiche S3, « Points d'attention » | « Le jeton ne doit jamais être dans les logs » contredisait le test à la main, qui demande de le copier depuis la console | Instruction contradictoire pour Souidi | Précisé : jamais dans les réponses aux employés ; tant que l'email est simulé, il n'apparaît que dans la console | Corrigé, à valider |
| P17 | Guide Quotation, S3, `DevisAccepteEvent` | L'événement n'avait pas le champ `type` de l'enveloppe commune (`eventId`, `type`, `date`) exigée par `contrat_api/evenements.md` | Campaign ne reconnaîtrait pas l'événement conformément au contrat ; le contrat et le code divergeaient | Champ `type` ajouté (valeur `DevisAccepte`) dans le guide, le code et le test | Corrigé, à valider |
| P18 | Campaign A2, services (constaté en testant) | Quand un public, un contenu ou un produit est ajouté ou retiré dans la collection d'une campagne, il n'est retrouvable par son identifiant qu'après la fin de la transaction. 5 tests ont échoué | En production chaque requête a sa transaction : invisible ; mais impossible à tester | `flush()` appelé après chaque ajout et chaque retrait ; fiche A2 complétée | Corrigé, à valider |

## B. Erreurs de documentation (corrigées)

| N° | Où | Problème | Correction | État |
|---|---|---|---|---|
| P7 | Toutes les fiches | La ligne « Exigences couvertes » citait des codes (C1, Q4, P7…) qui renvoient au cahier des charges, absent du dépôt | Remplacée par « Ce que couvre l'étape », en une phrase | Corrigé, à valider |
| P8 | Fiches sans route (B0, A1, E1, E2, E3) | La numérotation sautait de la section 2 à la section 4, alors que le texte renvoie à « section 4 » et « section 7 » | La section 3 existe partout (« Aucune route dans cette étape ») ; les renvois précisent le titre | Corrigé, à valider |
| P9 | Fiches E1 et E3, `contrat_commun.md` | Renvois vers `amelioration.md`, `rapport_prof.md` ou « les rapports », absents du dépôt | Reformulés pour être compréhensibles sans ces documents | Corrigé, à valider |
| P10 | `planning/README.md` | Pas de contexte : il commençait par « qui fait quoi » | Ajout du paragraphe « Le projet en bref » | Corrigé, à valider |
| P11 | Guides Customer et Quotation | Beaucoup de code et pas de résumé de l'objectif : difficile à lire | Ajout de « Objectif, en bref » et « Comment lire ce guide sans se perdre » | Corrigé, à valider |
| P19 | Fiche E3 | Elle demandait de protéger les routes par `@PreAuthorize` sur les méthodes des contrôleurs. **Faux** : Spring valide le corps de la requête avant d'appeler la méthode, donc un rôle non autorisé qui envoie un corps invalide reçoit **422 au lieu de 403** (constaté par la matrice de rôles : 20, 24 et 30 cas en échec) | Les droits sont posés **sur les URL**, une ligne `requestMatchers(...).hasAnyRole(...)` par route dans `SecurityConfig` (reprises des `x-roles`) ; la fiche E3 est réécrite et la matrice complète est un test | Corrigé, à valider |
| P20 | Fiche E1 | Trois décisions manquaient : topologie des files, sort d'un message impossible à traiter, comportement si RabbitMQ est arrêté au moment de publier | Décisions prises, testées en réel et écrites dans la fiche (voir P23, P24) | Corrigé, à valider |
| P21 | `contrat_api/evenements.md` | Ne disait pas que **l'émetteur** déclare aussi la file du destinataire. Sans cela, un événement publié avant le premier démarrage du destinataire est perdu (l'échange n'a aucune file liée) | Précision ajoutée dans le contrat | Corrigé, à valider |
| P22 | `contrat_api/contrat_commun.md` | Ne disait pas où se trouvent le rôle et l'identifiant dans le JWT | Hypothèse écrite : rôle dans le champ `role`, identifiant dans `sub`, signature HS256 ; **à confirmer avec le trinôme Authentication** | Corrigé, à confirmer |
| P26 | Générateur des droits par route (outil interne) | Il traitait `/publics/{id}` (route d'employé, suppression d'un public) comme une route publique parce que son chemin commence par `/public` : cette route est restée sans protection | Découvert en comparant le nombre de routes protégées au contrat (66 au lieu de 67) ; corrigé, puis rendu impossible par les lignes explicites de `SecurityConfig` et par la matrice de rôles | Corrigé, à valider |

## C. Points ouverts dans le premier passage, maintenant résolus

| N° | Sujet | Résultat |
|---|---|---|
| P12 | Base MySQL réelle | **Résolu.** Tout fonctionne sur MySQL 8 réel (créations, doublons, suppression en cascade de `Contact` et `Interaction`, contraintes d'unicité, colonnes UUID). |
| P13 | `/actuator/health` | **Résolu.** Il répond `UP` quand RabbitMQ est démarré ; il répond `DOWN` sans RabbitMQ (l'indicateur de santé teste aussi RabbitMQ) alors que le service fonctionne. Les fiches demandent déjà de lancer `docker compose up -d` avant. |

## D. Points ouverts et limites assumées

| N° | Sujet | Détail | État |
|---|---|---|---|
| P14 | Qui a suivi les guides | Seul Claude a fait le passage, avec une connaissance du contexte que les collègues n'ont pas | **Ouvert** : un collègue suit une étape (par exemple S1) sans aide et note ce qui l'arrête |
| P15 | Guides de code manquants | Il n'y a pas de guide de code pour Campaign (A1 à A4), les événements (E1), Docker (E2) et JWT (E3). Ces parties ont été faites à partir des fiches : les fiches sont donc **testées**, mais pas encore un guide copiable | **Ouvert** |
| P16 | Documents remis au prof | Aucune des erreurs ci-dessus ne contredit `rapport_detaille` ni le cahier des charges, sauf à relire P22 (JWT) | À confirmer par le trinôme |
| P23 | Pas d'« outbox » | L'événement est publié dans la transaction de l'acceptation (ou de la réponse du visiteur). Si RabbitMQ est arrêté, la donnée métier est conservée et l'événement est écrit **en JSON** dans les logs (niveau ERROR) pour être republié à la main ; si la transaction échoue après l'envoi, le message est déjà parti | **Limite assumée** |
| P24 | Message en échec | Un message que le consommateur ne peut pas traiter est refusé sans remise en file et part directement dans la file `.dlq`, même si la cause est une panne passagère de la base (pas de nouvel essai automatique) | **Limite assumée** |
| P25 | Service Authentication de l'autre trinôme | Il n'existe pas. Un **service de login provisoire** (`auth-service`, port 8084) a été ajouté (étape E4) : utilisateurs fictifs, mots de passe BCrypt, `POST /login`, JWT HS256. Les noms des champs du JWT (`role`, `sub`) et le secret restent des hypothèses à confirmer avec l'autre trinôme ; le nom du champ du rôle se règle par `app.security.role-claim` | **Ouvert** : confirmer avec l'autre trinôme, puis remplacer le service provisoire |
| P27 | Premier démarrage Docker | `docker compose --profile services up -d --build` dure environ 6 minutes la première fois (images et dépendances Maven) | À savoir |
| P29 | Constat du trinôme : « sécurisé sans utilisateur ni login » | Avec la seule étape E3, les services vérifiaient des jetons mais **personne ne pouvait en obtenir** : la sécurité ne pouvait pas être démontrée de bout en bout, et le mode par défaut (`none`) laisse tout ouvert | Étape E4 ajoutée : service de login provisoire testé seul (7 tests), puis de bout en bout dans Docker | Corrigé, à valider |
| P30 | `auth-service/pom.xml` | La version de `nimbus-jose-jwt` n'est pas gérée par Spring Boot : la construction échouait (« version is missing ») | Version `9.37.3` fixée explicitement | Corrigé |
| P31 | Scénario de bout en bout | Lancé sans jeton contre des services en mode jwt, il s'arrêtait sur une erreur Python incompréhensible (`KeyError: 'id'`) | Message d'explication et code de sortie 2 ; options `--auth-url` et `--jwt-secret` documentées | Corrigé |
| P32 | Choix d'un serveur d'identité | Un serveur comme **Keycloak** apporterait une vraie base d'utilisateurs, la double authentification, le renouvellement et la révocation des jetons, et une signature à clé publique (plus de secret partagé) ; il alourdit l'installation. Nos services y sont déjà compatibles : il suffirait de changer la configuration de `SecurityConfig` (adresse de l'émetteur au lieu du secret, champ des rôles) | **Ouvert** : décision du groupe |
| P33 | Passerelle, service arrêté (constaté en réel dans Docker) | Un service arrêté donnait **500** au lieu de 503 : un conteneur arrêté n'a plus d'adresse, l'erreur réseau est « no route to host », ni « connexion refusée » ni « nom introuvable » ; le test avec un port fermé passait pourtant | Cette erreur est maintenant reconnue (503) ; test unitaire des correspondances ; arrêt d'un vrai conteneur joué | Corrigé, à valider |
| P34 | Passerelle absente | Elle était dans l'architecture annoncée mais n'était pas réalisée : le client devait connaître quatre adresses | Étape E5 ajoutée (`gateway-service`, port 8080) | Corrigé, à valider |
| P35 | Droits des routes | Ils étaient dans une table de données (`DroitsParRoute`), moins lisible | Remplacée par des lignes explicites dans `SecurityConfig`, sans changer le comportement (matrice de rôles toujours au vert) | Corrigé, à valider |
| P36 | Où vérifier le jeton | Aujourd'hui chaque service le vérifie, et la passerelle peut le vérifier aussi (mode jwt) ; les rôles ne sont contrôlés que dans les services | **Choix assumé** : un service appelé directement reste protégé ; les droits ne sont écrits qu'à un seul endroit |
| P28 | Aucune interface web | Les pages publiques (lien du devis, formulaire de campagne, bouton « Je suis intéressé ») n'existent que sous forme de routes. Les liens des emails (`/devis/reponse/<jeton>`, `/campagnes/invitation/<jeton>`) pointent vers une interface qui n'est pas réalisée | **Limite assumée** |

---

## Marche à suivre

1. Le trinôme relit ce journal et confirme ou conteste chaque ligne « Corrigé, à valider ».
2. Les lignes « Ouvert » sont traitées (en priorité P14 avec un collègue, puis P15 pour Campaign).
3. Chacun avance sur sa part à partir des fiches corrigées ; l'assemblage vient en dernier.
