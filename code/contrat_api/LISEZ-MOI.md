# Module Clients, Devis, Campagnes (Groupe B) : contrat à partager

Ce dossier décrit comment s'intégrer à notre module commercial. Trinôme : Oussema Arfaoui, Oussema Souidi, Baha Farhati (3 IDL, ISI). Les données sont fictives.

## Ce que fait le module

Trois services Java (Spring Boot), une base MySQL chacun :

| Service | Rôle | Port |
|---|---|---|
| **Customer** | Clients et prospects, contacts, interactions, catégories | 8081 |
| **Quotation** | Devis, catalogue, taxes, envoi par email, réponse du client par lien | 8082 |
| **Campaign** | Campagnes, publics, canaux, contenus, performance et ROI | 8083 |

## Fichiers

| Fichier | Contenu |
|---|---|
| `contrat_commun.md` | Règles communes : formats, erreurs, pagination, identité et rôles, règles d'intégration |
| `customer.yaml`, `quotation.yaml`, `campaign.yaml` | Contrats OpenAPI 3 de chaque service, avec le rôle autorisé de chaque route (`x-roles`) et des exemples |
| `auth.yaml` | Contrat du service de login **provisoire** (`POST /login`, renvoie un JWT) |
| `evenements.md` | Les deux événements RabbitMQ, `ProspectGenere` et `DevisAccepte` |

Pour lire un contrat : coller un fichier `.yaml` dans <https://editor.swagger.io>.

## Ce dont un autre trinôme a besoin

- **Authentication** : nous utilisons quatre rôles, `ADMIN`, `COMMERCIAL`, `MARKETING`, `RESPONSABLE`. Nous lirons l'identité dans un JWT `Authorization: Bearer ...` ; en attendant, un composant de contexte utilisateur fournit un utilisateur et un rôle par défaut.
- **Notification** : nous appelons une seule méthode, `envoyerEmail(destinataire, sujet, texte, pièce jointe facultative)`, aujourd'hui simulée. Quand votre service existera, nous remplacerons seulement le corps de cette méthode.
- **Ventes, Finance ou tout autre module** : vous pouvez consommer `DevisAccepte` en créant votre propre file RabbitMQ liée à la clé `devis.accepte` de l'échange `module-commercial`. Rien ne change chez nous (voir `evenements.md`).
- **API Gateway** : nous fournissons une passerelle (`gateway-service`, port 8080) qui route par préfixe de chemin vers Customer, Quotation, Campaign et le login provisoire. Si une passerelle commune existe, il suffit de reprendre les mêmes préfixes (voir `planning/commun_integration/E5_passerelle.md`). Les routes publiques (réponse d'un client à un devis ou à une campagne) commencent par `/public/` et n'exigent aucune identité.

## Ce que nous ne faisons pas

Commandes, facturation, paiement, stock, validation financière des devis, lien avec le service Employee.
