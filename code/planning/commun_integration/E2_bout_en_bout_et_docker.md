# Étape E2 : Scénario de bout en bout et Docker des services

| | |
|---|---|
| **Service** | Les trois services |
| **Responsable** | Tout le trinôme |
| **Ce que couvre l'étape** | Scénario complet campagne → prospect → devis → ROI, cas de panne, Docker |
| **Dossier de code** | `code/ (les trois services)` |
| **Prérequis** | E1 et A4 validées |
| **État** | Image Docker et service de Campaign faits ; ceux de Customer et de Quotation à faire ; assemblage et scénario à la fin |

## 1. Objectif

Prouver que la chaîne complète fonctionne : campagne → prospect → client → devis accepté → ROI de la campagne ; mettre chaque service dans une image Docker et tout lancer avec une seule commande.

**Pas dans cette étape :**

- JWT (étape E3) et passerelle (étape E5).

## 2. Modèle de données

Aucune nouvelle table.

## 3. API à réaliser

Aucune route dans cette étape.

## 4. Règles métier à respecter

- **Scénario nominal** : F1 (campagne lancée) → F2a (un inconnu remplit le formulaire, un prospect apparaît dans Customer) → F3 (le commercial le passe CLIENT) → F4 (devis créé et envoyé avec `campagneId`) → F5 (le client accepte) → la performance de la campagne montre `devisAcceptes = 1` et le chiffre d'affaires.
- **Cas dégradés (F7)** : Customer arrêté pendant `POST /devis` (503) et pendant `lancer` (503, campagne BROUILLON) ; Campaign arrêté pendant qu'un devis est accepté (l'événement attend dans la file et est traité au retour) ; même événement reçu deux fois (aucun doublon) ; réponse sur une campagne TERMINEE (409).
- Chaque service a sa propre image ; les trois bases, RabbitMQ et les trois services démarrent avec `docker compose up`.
- Dans Docker, les services se joignent par nom (`customer-service`, `rabbitmq`, `customer-db`…) : toutes les adresses viennent des variables d'environnement déjà prévues.
- Les données de démonstration sont **fictives**.

## 5. Tâches, dans l'ordre

- [ ] Écrire un `Dockerfile` par service (image JRE 21, copie du JAR, `EXPOSE`).
- [ ] Compléter `docker-compose.yml` : trois services applicatifs avec `depends_on` (bases et RabbitMQ « healthy »), variables `DB_URL`, `DB_PASSWORD`, `RABBIT_HOST`, `module-commercial.customer.url`.
- [ ] Écrire un script (ou un fichier `.http` / une collection) qui rejoue le scénario nominal avec des données fictives.
- [ ] Écrire `planning/scenarios.md` : le scénario nominal et chaque cas dégradé, avec le résultat attendu.
- [ ] Exécuter tous les scénarios et noter les résultats.
- [ ] Mettre à jour `code/README.md` : lancement complet, ports, URL de Swagger de chacun, comptes de test.

## 6. Fichiers créés ou modifiés

- `code/customer-service/Dockerfile, code/quotation-service/Dockerfile, code/campaign-service/Dockerfile (+ .dockerignore)`
- `code/docker-compose.yml (profil `services` : les trois services, en plus de l'infrastructure)`
- `code/scenarios/scenario_complet.py (scénario nominal, bibliothèque standard de Python)`
- `planning/commun_integration/scenarios.md (scénarios joués et résultats)`
- `code/README.md`

## 7. Tests à écrire

- Test de bout en bout automatisé (au moins le scénario nominal) lancé contre les trois services démarrés.
- Chaque cas dégradé joué à la main et noté dans `planning/scenarios.md`.
- `mvn clean test` toujours vert dans les trois services.

## 8. Points d'attention

- Les services doivent attendre que MySQL et RabbitMQ soient prêts (`healthcheck` déjà défini pour les bases et RabbitMQ).
- Dans un conteneur, `localhost` n'est plus la machine : ne laisser aucune adresse codée en dur.
- Garder le port 3306 libre : MySQL de la machine peut l'occuper ; les bases du compose utilisent 3307 à 3309.
- Ne jamais mettre de vrais mots de passe dans le compose : identifiants de développement uniquement.
- Le premier `docker compose --profile services up -d --build` dure environ 6 minutes (images de base et dépendances Maven téléchargées) ; les suivants réutilisent le cache.
- `docker compose up -d` (sans profil) ne démarre que l'infrastructure : c'est ce qu'il faut pour lancer les services sur le poste avec `mvn spring-boot:run`.

## 9. Comment valider (à faire par le trinôme)

1. `docker compose up --build` à la racine de `code/` : tout démarre ; les trois `/actuator/health` répondent `UP`.
2. Rejouer le **scénario nominal** (script ou Swagger) et vérifier chaque résultat attendu de `planning/scenarios.md`.
3. Rejouer les **cas dégradés** : `docker compose stop customer-service`, tenter un devis (503), puis `docker compose start customer-service`, vérifier que tout repart sans intervention.

## 10. Livraison

Quand le trinôme valide cette étape : `git add`, commit, tag `etape-E2`, push (voir `README.md`).

Message de commit proposé :

```
feat: scénario de bout en bout, images Docker des services et docker-compose complet
```
