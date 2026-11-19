# Contrat des événements (RabbitMQ)

Deux événements, un par flux asynchrone du module. Un événement **contient toutes les données utiles** : celui qui le reçoit n'a rien à rappeler.

## Topologie RabbitMQ

| Élément | Valeur |
|---|---|
| Échange | `module-commercial`, type `topic`, durable |
| Clé de routage `ProspectGenere` | `prospect.genere` |
| Clé de routage `DevisAccepte` | `devis.accepte` |
| File de Customer | `customer.prospect-genere`, liée à `prospect.genere`, durable |
| File de Campaign | `campaign.devis-accepte`, liée à `devis.accepte`, durable |
| Messages | JSON UTF-8, persistants (`delivery_mode = 2`), accusé de réception **manuel** après le traitement |
| Autre module | Une autre équipe peut consommer `DevisAccepte` en créant **sa propre file** liée à `devis.accepte`, sans rien changer chez nous |

## Enveloppe commune

Tout événement a ces champs en plus de ses données :

| Champ | Type | Sens |
|---|---|---|
| `eventId` | UUID | Identifiant unique de l'événement, créé par l'émetteur. Sert à ignorer un doublon |
| `type` | chaîne | Nom de l'événement (`ProspectGenere`, `DevisAccepte`) |
| `date` | date-heure ISO 8601 UTC | Moment de l'émission |

## ProspectGenere : Campaign → Customer

Un inconnu a rempli le formulaire public d'une campagne ACTIVE.

```json
{
  "eventId": "6f1c2a3e-0b8d-4c52-9a41-7d2e5b1f8c90",
  "type": "ProspectGenere",
  "date": "2026-11-18T14:30:00Z",
  "campagneId": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f",
  "typeClient": "ENTREPRISE",
  "nom": "Société Atlas",
  "nomContact": "Karim Ben Salah",
  "email": "karim@atlas.tn",
  "telephone": "+216 20 123 456"
}
```

| Champ | Obligatoire | Note |
|---|---|---|
| `campagneId` | oui | Devient `campagneSourceId` de la fiche |
| `typeClient` | oui | `PARTICULIER` ou `ENTREPRISE` (nommé ainsi pour ne pas heurter le champ `type` de l'enveloppe) |
| `nom` | oui | Nom du particulier, ou nom de la société |
| `nomContact` | entreprise seulement | Crée un `Contact` rattaché à la fiche |
| `email` | oui | Déjà validé par Campaign. Sert à chercher un doublon |
| `telephone` | non | |

**Traitement par Customer** (consommateur idempotent) :
1. Si `eventId` est déjà dans `EvenementTraite` : ignorer, accuser réception.
2. Si un client a déjà cet email (sans tenir compte des majuscules ni des espaces) : ne rien créer.
3. Sinon créer un `Client` de statut `PROSPECT`, sans catégorie, avec `campagneSourceId = campagneId` ; pour une entreprise, créer aussi un `Contact` (même email).
4. Enregistrer `eventId` dans `EvenementTraite` **dans la même transaction**, puis accuser réception.

## DevisAccepte : Quotation → Campaign

Un client a accepté un devis par son lien. **Publié seulement si le devis a un `campagneId`.**

```json
{
  "eventId": "0d7e4c19-3a62-4b5f-b8d1-92c6e0a47f33",
  "type": "DevisAccepte",
  "date": "2026-11-18T15:02:00Z",
  "devisId": "e41a8b07-6d93-4f2c-a5e8-3b9c0d1f2a47",
  "reference": "DEV-2026-0042",
  "clientId": "9a2f7c31-84b6-4d10-b3e5-6c8a1d0e9f52",
  "campagneId": "b3d9a2f1-5c47-4e88-8f0a-1a2b3c4d5e6f",
  "montantHT": 4200.000,
  "montantTTC": 4998.000
}
```

`date` est la date de l'acceptation (`repondueLe` du devis). Les montants suivent la règle du contrat commun : nombres décimaux à trois décimales au plus.

**Traitement par Campaign** (consommateur idempotent) :
1. Si `eventId` est déjà dans `EvenementTraite`, ou si `campagneId` est inconnu : ignorer sans erreur, accuser réception.
2. Sinon : `devisAcceptes` + 1 et `chiffreAffairesAttribue` + `montantHT`, **même si la campagne est TERMINEE**.
3. Enregistrer `eventId` dans la même transaction, puis accuser réception.

Le ROI n'est pas stocké : il se calcule à la lecture, `(chiffreAffairesAttribue − montantDepense) / montantDepense`, seulement si `montantDepense > 0`.

## Table EvenementTraite (Customer et Campaign)

| Champ | Type | Note |
|---|---|---|
| `eventId` | UUID, clé primaire | |
| `traiteLe` | date-heure | |

Aucun lien avec les autres tables : elle sert seulement à reconnaître un événement déjà reçu.
