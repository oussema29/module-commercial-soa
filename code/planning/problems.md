# Journal des problèmes trouvés en suivant les guides

**But.** Réaliser les étapes en suivant **seulement** les guides (`GUIDE_CODE_CUSTOMER.md`, `GUIDE_CODE_QUOTATION.md`), noter chaque erreur trouvée, corriger les guides, puis faire valider ce journal par le trinôme avant de laisser les collègues travailler dessus. Aucune erreur ne doit rester dans un guide au moment où un collègue le suit.

**Comment le passage a été fait** (premier passage, par Claude) :
1. Reconstruction de chaque service **à partir des seuls blocs de code des guides**, dans un dossier vierge, puis `mvn clean test`.
2. Démarrage des deux services (base H2 en mémoire) et rejeu des « essais à la main » avec de vraies requêtes HTTP, Quotation appelant le **vrai** Customer.
3. Résultat après corrections : Customer 24 tests au vert, Quotation 35 tests au vert, 27 vérifications du scénario réel correctes, panne de Customer simulée (503, aucun devis créé).

**États possibles :** *Corrigé, à valider* (la correction est faite, le trinôme doit la confirmer) · *Ouvert* (reste à faire).

---

## A. Erreurs des guides et du code (corrigées)

| N° | Où | Problème | Conséquence si non corrigé | Correction | État |
|---|---|---|---|---|---|
| P1 | Guide Customer, B1 | Le guide ne listait pas les 6 fichiers `entity/` et `repository/` de B1 (`TypeClient`, `StatutClient`, `CategorieClient`, `Client`, `CategorieRepository`, `ClientRepository`) | Le code de B1 ne compile pas : classes introuvables. Découvert en reconstruisant le service à partir du guide | Les 6 fichiers sont ajoutés (le guide B1 compte maintenant 20 fichiers) | Corrigé, à valider |
| P2 | Code B1, `ClientCreation` et `ClientModification` | Un email avec des espaces autour (`" ali@mail.tn "`) était refusé (422) par la validation au lieu d'être reconnu comme doublon (409) | La règle « email unique sans tenir compte des espaces » n'était pas respectée | Les espaces sont enlevés dans le constructeur des deux objets, avant la validation | Corrigé, à valider |
| P3 | Squelette B0, `GlobalExceptionHandler` de Customer | Un paramètre obligatoire manquant (`GET /clients/destinataires` sans `categorieId`) renvoyait l'erreur par défaut de Spring, pas le format `{code, message, timestamp}` ; une violation d'unicité en base (deux requêtes simultanées avec le même email) donnait une erreur 500 | Corps d'erreur incohérent avec le contrat ; 500 au lieu de 409 | Le gestionnaire traite maintenant `MissingServletRequestParameterException` (400) et `DataIntegrityViolationException` (409). Quotation a le même, plus le verrou optimiste | Corrigé, à valider |
| P4 | Fiche B1, modèle de données | Les champs de date s'appelaient `creeLe` et `modifieLe`, alors que le contrat et le code utilisent `createdAt` et `updatedAt` | Un lecteur de la fiche écrirait des noms qui ne correspondent pas au contrat | Fiche corrigée | Corrigé, à valider |
| P5 | Fiches B1 et B2 | Elles annonçaient « valeur d'énumération inconnue → 422 » (statut, type d'interaction) | Le test attendu aurait échoué : Spring renvoie 400 pour une valeur d'énumération inconnue | Fiches corrigées : 400 pour une valeur inconnue, 422 pour un champ absent | Corrigé, à valider |
| P6 | Fiche S3, « Points d'attention » | « Le jeton ne doit jamais être dans les logs » contredisait l'essai à la main, qui demande de le copier depuis la console | Instruction contradictoire pour Souidi | Précisé : jamais dans les réponses aux employés ; tant que l'email est simulé, il n'apparaît que dans la console | Corrigé, à valider |

## B. Erreurs de documentation (corrigées)

| N° | Où | Problème | Correction | État |
|---|---|---|---|---|
| P7 | Toutes les fiches | La ligne « Exigences couvertes » citait des codes (C1, Q4, P7…) qui renvoient au cahier des charges, absent du dépôt | Remplacée par « Ce que couvre l'étape », en une phrase | Corrigé, à valider |
| P8 | Fiches sans route (B0, A1, E1, E2, E3) | La numérotation sautait de la section 2 à la section 4, alors que le texte renvoie à « section 4 » et « section 7 » | La section 3 existe partout (« Aucune route dans cette étape ») ; les renvois précisent le titre | Corrigé, à valider |
| P9 | Fiches E1 et E3, `contrat_commun.md` | Renvois vers `amelioration.md`, `rapport_prof.md` ou « les rapports », absents du dépôt | Reformulés pour être compréhensibles sans ces documents | Corrigé, à valider |
| P10 | `planning/README.md` | Pas de contexte : il commençait par « qui fait quoi » | Ajout du paragraphe « Le projet en bref » | Corrigé, à valider |
| P11 | Guides Customer et Quotation | Beaucoup de code et pas de résumé de l'objectif : difficile à lire | Ajout de « Objectif, en bref » et « Comment lire ce guide sans se perdre » | Corrigé, à valider |

## C. Limites et points ouverts

| N° | Sujet | Détail | État |
|---|---|---|---|
| P12 | Base MySQL réelle | Docker Desktop n'était pas lancé : tout a été essayé sur **H2 en mode MySQL**. Un problème de schéma propre à MySQL reste possible | **Ouvert** : refaire le passage avec `docker compose up -d` |
| P13 | `/actuator/health` | Il répond `DOWN` tant que RabbitMQ n'est pas démarré (l'indicateur de santé teste aussi RabbitMQ), bien que le service fonctionne. Les fiches demandent de lancer `docker compose up -d` avant de l'ouvrir, mais le symptôme n'est pas expliqué | **Ouvert** : ajouter une phrase dans la fiche B0 |
| P14 | Qui a suivi les guides | Seul Claude a fait le premier passage, avec une connaissance du contexte que les collègues n'ont pas | **Ouvert** : un collègue suit une étape (par exemple S1) sans aide, et note ce qui l'arrête |
| P15 | Campaign, étapes E1 à E3 | Pas de guide de code pour A1 à A4 ni pour les événements, l'intégration et le JWT : le passage complet de bout en bout n'est donc pas possible sur ces parties | **Ouvert** |
| P16 | Documents remis au prof | Aucune des erreurs ci-dessus ne contredit `rapport_detaille` ni le cahier des charges. P2 applique la règle déjà écrite dans `contrat_api/contrat_commun.md` | À confirmer par le trinôme |

---

## Marche à suivre après validation

1. Le trinôme relit ce journal et confirme ou conteste chaque ligne « Corrigé, à valider ».
2. Les lignes « Ouvert » sont traitées (en particulier P12 avec Docker, et P14 avec un collègue).
3. On corrige les documents concernés s'il y en a, puis on marque l'état « Validé ».
4. Le travail en groupe commence à partir des guides corrigés.
