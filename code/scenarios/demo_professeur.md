# Démonstration pour le professeur (sécurité JWT + Customer)

Tout passe par la **passerelle (8080)**, le seul point d'entrée. Les adresses `localhost:8081` à `8084` sont les services directs.

## 0. Démarrage (sécurité activée)

```
cd code
# Linux / macOS / Git Bash
SECURITY_MODE=jwt docker compose --profile services up -d --build
# PowerShell
$env:SECURITY_MODE="jwt"; docker compose --profile services up -d --build
```

Attendre que les services soient prêts (environ une minute après le build) : `curl localhost:8080/actuator/health`.

## 1. Sans jeton : refusé (401)

```
curl -i localhost:8080/clients
```

Réponse attendue : `401`, `{"code":401,"message":"Authentification requise",...}` (refusé par la passerelle).

## 2. Login : obtenir un jeton

```
curl -X POST localhost:8080/login -H "Content-Type: application/json" \
  -d '{"email":"admin@exemple.tn","motDePasse":"admin-demo-1"}'
```

Réponse attendue : `{"jeton":"eyJ...","type":"Bearer","expireDans":3600,"role":"ADMIN","utilisateurId":"00000000-...-a1"}`.
Copier la valeur de `jeton` (le tester sur https://jwt.io pour montrer `sub`, `role`, `exp`).

Mauvais mot de passe : `401 {"message":"Identifiants invalides"}`.

## 3. Avec le jeton : créer puis lire (rôle ADMIN)

Remplacer `<JETON>` par le jeton reçu.

```
# créer une catégorie (ADMIN seulement) ; noter l'id de la réponse
curl -X POST localhost:8080/categories -H "Authorization: Bearer <JETON>" -H "Content-Type: application/json" \
  -d '{"nom":"Grands comptes","description":"Clients stratégiques"}'

# créer un client (ADMIN ou COMMERCIAL) ; categorieId est facultatif
curl -X POST localhost:8080/clients -H "Authorization: Bearer <JETON>" -H "Content-Type: application/json" \
  -d '{"type":"ENTREPRISE","nom":"Société Démo","email":"contact@demo.tn","telephone":"20123456","adresse":"Tunis","categorieId":"<ID_CATEGORIE>"}'

# lister les clients
curl localhost:8080/clients -H "Authorization: Bearer <JETON>"

# ajouter une interaction à un client (ADMIN ou COMMERCIAL) ; l'auteur vient du jeton, pas du corps
curl -X POST localhost:8080/clients/<ID_CLIENT>/interactions -H "Authorization: Bearer <JETON>" -H "Content-Type: application/json" \
  -d '{"type":"APPEL","date":"2026-11-24T10:00:00Z","description":"Premier contact"}'
```

## 4. Mauvais rôle : refusé (403) par le service

Se connecter avec `marketing@exemple.tn` / `marketing-demo-1`, puis :

```
curl -i localhost:8080/clients -H "Authorization: Bearer <JETON_MARKETING>"
```

Réponse attendue : `403 {"message":"Rôle non autorisé pour cette route"}`. Le jeton est valide (la passerelle laisse passer), mais le rôle MARKETING n'a pas le droit de lister les clients (décision du service Customer).

## 5. Appel direct au service, sans passer par la passerelle

```
curl -i localhost:8081/clients
```

Réponse attendue : `401` (le service se protège lui-même).

## Utilisateurs fictifs

| Rôle | Email | Mot de passe |
|---|---|---|
| ADMIN | admin@exemple.tn | admin-demo-1 |
| COMMERCIAL | commercial@exemple.tn | commercial-demo-1 |
| MARKETING | marketing@exemple.tn | marketing-demo-1 |
| RESPONSABLE | responsable@exemple.tn | responsable-demo-1 |

## Démonstration complète automatique

```
python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service --gateway http://localhost:8080 --auth-url http://localhost:8080
```

Joue le parcours campagne -> prospect -> client -> devis accepté -> ROI.

## Interface Swagger

http://localhost:8080/swagger-ui.html : un menu déroulant avec un service par entrée (Customer, Quotation, Campaign).
