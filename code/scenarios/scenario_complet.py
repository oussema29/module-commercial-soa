#!/usr/bin/env python3
"""
Scénario de bout en bout du module commercial (données fictives) :
campagne -> prospect -> client -> devis accepté -> ROI de la campagne.

Prérequis : RabbitMQ, les trois bases et les trois services démarrés
(Customer 8081, Quotation 8082, Campaign 8083).

Les emails étant simulés, les liens à jeton sont écrits dans la console des services : le script les lit dans
les fichiers de logs passés en argument (ou « docker:<conteneur> » si les services tournent dans Docker).

    python scenarios/scenario_complet.py --log-campaign campaign.log --log-quotation quotation.log
    python scenarios/scenario_complet.py --log-campaign docker:campaign-service --log-quotation docker:quotation-service
    python scenarios/scenario_complet.py ... --gateway http://localhost:8080 --auth-url http://localhost:8080   (tout à travers la passerelle)
    (services en mode jwt : ajouter --auth-url http://localhost:8084 pour obtenir les jetons par un vrai login,
     ou --jwt-secret <le secret JWT_SECRET> pour les fabriquer soi-même)

Seulement la bibliothèque standard de Python.
"""
import argparse
import base64
import hashlib
import hmac
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

CUSTOMER = "http://localhost:8081"
QUOTATION = "http://localhost:8082"
CAMPAIGN = "http://localhost:8083"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

resultats = []
SECRET_JWT = None   # renseigné par --jwt-secret : jetons fabriqués par le script (services en mode jwt)
AUTH_URL = None     # renseigné par --auth-url : jetons obtenus par un VRAI login auprès du service d'authentification
JETONS = {}
UTILISATEURS = {    # utilisateurs fictifs de démonstration du service auth-service
    "ADMIN": ("admin@exemple.tn", "admin-demo-1"),
    "COMMERCIAL": ("commercial@exemple.tn", "commercial-demo-1"),
    "MARKETING": ("marketing@exemple.tn", "marketing-demo-1"),
    "RESPONSABLE": ("responsable@exemple.tn", "responsable-demo-1"),
}


def b64(octets):
    return base64.urlsafe_b64encode(octets).rstrip(b"=").decode()


def jeton_par_login(role):
    """Se connecte (POST /login) avec l'utilisateur fictif du rôle et garde le jeton reçu."""
    if role not in JETONS:
        email, mot_de_passe = UTILISATEURS[role]
        requete = urllib.request.Request(AUTH_URL + "/login", method="POST", headers={"Content-Type": "application/json"},
                                         data=json.dumps({"email": email, "motDePasse": mot_de_passe}).encode())
        JETONS[role] = json.loads(urllib.request.urlopen(requete, timeout=20).read())["jeton"]
    return JETONS[role]


def jeton(role):
    """JWT d'un rôle : obtenu par login si --auth-url est donné, sinon fabriqué avec le secret partagé."""
    if AUTH_URL:
        return jeton_par_login(role)
    entete = b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    charge = b64(json.dumps({"sub": "00000000-0000-0000-0000-0000000000a1", "role": role, "exp": int(time.time()) + 3600}).encode())
    signature = b64(hmac.new(SECRET_JWT.encode(), f"{entete}.{charge}".encode(), hashlib.sha256).digest())
    return f"{entete}.{charge}.{signature}"


def role_pour(methode, url):
    """Rôle utilisé pour appeler une route d'employé (droits du contrat) ; aucun pour les routes publiques."""
    chemin = urllib.parse.urlparse(url).path   # on décide d'après le chemin : marche aussi à travers la passerelle
    if chemin.startswith("/public/"):
        return None
    if chemin.startswith(("/clients", "/categories", "/contacts", "/interactions")):
        return "ADMIN" if chemin.startswith("/categories") and methode != "GET" else "COMMERCIAL"
    if chemin.startswith(("/devis", "/produits", "/taxes")):
        return "ADMIN" if methode == "POST" and chemin.startswith(("/taxes", "/produits")) else "COMMERCIAL"
    return "ADMIN" if methode == "POST" and chemin == "/canaux" else "MARKETING"


def appel(methode, url, corps=None, brut=False):
    donnees = json.dumps(corps).encode() if corps is not None else None
    entetes = {"Content-Type": "application/json"}
    if (SECRET_JWT or AUTH_URL) and role_pour(methode, url):
        entetes["Authorization"] = "Bearer " + jeton(role_pour(methode, url))
    requete = urllib.request.Request(url, data=donnees, method=methode, headers=entetes)
    try:
        reponse = urllib.request.urlopen(requete, timeout=20)
        code, texte = reponse.status, reponse.read()
    except urllib.error.HTTPError as e:
        code, texte = e.code, e.read()
    if brut:
        return code, texte
    try:
        return code, (json.loads(texte) if texte else None)
    except ValueError:
        return code, texte


def verifier(titre, ok, detail=""):
    resultats.append(ok)
    print(("OK  " if ok else "KO  ") + titre + (f"   [{detail}]" if detail and not ok else ""))
    return ok


def attendre(condition, delai=20):
    """Les événements RabbitMQ sont asynchrones : on attend un peu que l'effet soit visible."""
    fin = time.time() + delai
    while time.time() < fin:
        valeur = condition()
        if valeur:
            return valeur
        time.sleep(0.5)
    return None


def jetons(source, motif):
    """Lit les logs d'un service : un fichier, ou « docker:<conteneur> » pour un service lancé par docker compose."""
    if source.startswith("docker:"):
        sortie = subprocess.run(["docker", "logs", source[len("docker:"):]], capture_output=True, text=True,
                                encoding="utf-8", errors="replace")
        texte = sortie.stdout + sortie.stderr
    else:
        with open(source, encoding="utf-8", errors="replace") as f:
            texte = f.read()
    return re.findall(motif, texte)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--log-campaign", required=True, help="fichier de logs du service Campaign")
    p.add_argument("--log-quotation", required=True, help="fichier de logs du service Quotation")
    p.add_argument("--jwt-secret", help="secret JWT partagé : à donner si les services tournent en mode jwt (SECURITY_MODE=jwt)")
    p.add_argument("--auth-url", help="adresse du service de login (par exemple http://localhost:8084) : les jetons sont obtenus par un vrai login")
    p.add_argument("--gateway", help="adresse de la passerelle (par exemple http://localhost:8080) : toutes les requêtes passent par elle")
    a = p.parse_args()
    global SECRET_JWT, AUTH_URL, CUSTOMER, QUOTATION, CAMPAIGN
    if a.gateway:
        CUSTOMER = QUOTATION = CAMPAIGN = a.gateway.rstrip("/")
    SECRET_JWT = a.jwt_secret
    AUTH_URL = a.auth_url
    suffixe = uuid.uuid4().hex[:8]
    # Date UTC (celle des services dans Docker) : la date locale peut avoir un jour d'avance et la campagne serait « pas encore commencée »
    aujourdhui = datetime.now(timezone.utc).date()
    dans_30 = aujourdhui + timedelta(days=30)

    code, _ = appel("GET", CUSTOMER + "/categories")
    if code == 401:
        print("Les services sont en mode jwt (ils exigent un jeton) : relancez avec --auth-url http://localhost:8084 "
              "(vrai login) ou --jwt-secret <JWT_SECRET> (jetons fabriqués par le script).")
        sys.exit(2)

    print("=== 1. Customer : une catégorie VIP et deux clients existants")
    _, cat = appel("POST", CUSTOMER + "/categories", {"nom": f"VIP {suffixe}", "description": "Clients importants"})
    clients = []
    for nom in ("Alpha", "Beta"):
        c, cl = appel("POST", CUSTOMER + "/clients", {"type": "ENTREPRISE", "nom": f"{nom} {suffixe}", "statut": "CLIENT",
                                                      "email": f"{nom.lower()}-{suffixe}@exemple.tn", "categorieId": cat["id"]})
        verifier(f"client {nom} créé", c == 201)
        clients.append(cl)

    print("=== 2. Quotation : une taxe et un produit du catalogue")
    _, taxe = appel("POST", QUOTATION + "/taxes", {"tauxTVA": 19, "description": "TVA standard"})
    c, produit = appel("POST", QUOTATION + "/produits", {"nom": "Étude de faisabilité", "description": "Forfait 5 jours",
                                                        "prix": 1000.000, "taxeId": taxe["id"]})
    verifier("produit créé", c == 201)

    print("=== 3. Campaign (F1) : créer et lancer une campagne")
    _, canal = appel("POST", CAMPAIGN + "/canaux", {"nom": f"Email {suffixe}", "type": "EMAIL"})
    _, camp = appel("POST", CAMPAIGN + "/campagnes", {"nom": f"Offre {suffixe}", "objectif": "FIDELISATION",
                                                      "dateDebut": str(aujourdhui), "dateFin": str(dans_30)})
    cid = camp["id"]
    appel("POST", f"{CAMPAIGN}/campagnes/{cid}/canaux", {"canalId": canal["id"]})
    appel("POST", f"{CAMPAIGN}/campagnes/{cid}/publics", {"type": "CLIENTS_EXISTANTS", "categorieClientId": cat["id"]})
    c, contenu = appel("POST", f"{CAMPAIGN}/campagnes/{cid}/contenus", {"titre": "-20 % ce mois-ci", "texte": "Offre fidélité"})
    appel("POST", f"{CAMPAIGN}/contenus/{contenu['id']}/produits", {"produitId": produit["id"], "produitNom": produit["nom"]})
    appel("PUT", f"{CAMPAIGN}/campagnes/{cid}/budget", {"montantPrevu": 500})
    c, res = appel("POST", f"{CAMPAIGN}/campagnes/{cid}/lancer")
    verifier("lancement : 2 invitations envoyées (Customer lu en réel)", c == 200 and res["envoisReussis"] == 2, f"{c} {res}")
    _, camp = appel("GET", f"{CAMPAIGN}/campagnes/{cid}")
    verifier("la campagne est ACTIVE", camp["statut"] == "ACTIVE")
    c, inv = appel("GET", f"{CAMPAIGN}/campagnes/{cid}/invitations")
    verifier("les invitations n'exposent pas le jeton", inv["total"] == 2 and "jeton" not in inv["contenu"][0])

    print("=== 4. F2b : un client existant clique « Je suis intéressé »")
    liste = jetons(a.log_campaign, r"/campagnes/invitation/([A-Za-z0-9_-]{20,})")
    verifier("jetons lus dans l'email simulé", len(liste) >= 2)
    jeton = liste[-2]
    c, _ = appel("POST", f"{CAMPAIGN}/public/invitations/{jeton}/reponse")
    verifier("premier clic accepté", c == 200)
    c, e = appel("POST", f"{CAMPAIGN}/public/invitations/{jeton}/reponse")
    verifier("second clic : 409 « Vous avez déjà répondu »", c == 409 and e["message"] == "Vous avez déjà répondu")
    _, rep = appel("GET", f"{CAMPAIGN}/campagnes/{cid}/reponses")
    verifier("une réponse comptée", rep["total"] == 1)

    print("=== 5. F2a : un inconnu remplit le formulaire public -> événement ProspectGenere -> Customer")
    email_prospect = f"karim-{suffixe}@atlas-exemple.tn"
    formulaire = {"type": "ENTREPRISE", "nom": f"Société Atlas {suffixe}", "nomContact": "Karim Ben Salah",
                  "email": email_prospect, "telephone": "+216 20 123 456"}
    c, _ = appel("POST", f"{CAMPAIGN}/public/campagnes/{cid}/reponse", formulaire)
    verifier("formulaire accepté", c == 201)
    appel("POST", f"{CAMPAIGN}/public/campagnes/{cid}/reponse", formulaire)   # même personne : pas de doublon

    def prospect():
        _, page = appel("GET", f"{CUSTOMER}/clients?q={email_prospect}")
        return page["contenu"][0] if page and page["total"] >= 1 else None

    fiche = attendre(prospect)
    verifier("Customer a créé la fiche PROSPECT (via RabbitMQ)", fiche is not None and fiche["statut"] == "PROSPECT")
    if fiche:
        verifier("la fiche garde la campagne d'origine", fiche["campagneSourceId"] == cid)
        _, page = appel("GET", f"{CUSTOMER}/clients?q={email_prospect}")
        verifier("pas de doublon d'email", page["total"] == 1)
        _, contacts = appel("GET", f"{CUSTOMER}/clients/{fiche['id']}/contacts")
        verifier("le contact de l'entreprise est créé", len(contacts) == 1 and contacts[0]["nom"] == "Karim Ben Salah")

        print("=== 6. F3 : le commercial passe le prospect à CLIENT (décision manuelle)")
        c, _ = appel("PATCH", f"{CUSTOMER}/clients/{fiche['id']}/statut", {"statut": "CLIENT"})
        verifier("statut CLIENT", c == 200)

        print("=== 7. F4 et F5 : devis rattaché à la campagne, envoyé puis accepté par le client")
        c, devis = appel("POST", QUOTATION + "/devis", {"clientId": fiche["id"], "dateValidite": str(dans_30), "campagneId": cid})
        verifier("devis créé (Customer lu en réel)", c == 201 and devis["clientEmail"] == email_prospect, f"{c}")
        c, devis = appel("POST", f"{QUOTATION}/devis/{devis['id']}/lignes", {"produitId": produit["id"], "quantite": 2})
        verifier("totaux : 2000 HT, 2380 TTC", devis["totalHT"] == 2000 and devis["totalTTC"] == 2380, str(devis.get("totalTTC")))
        c, _ = appel("POST", f"{QUOTATION}/devis/{devis['id']}/envoyer")
        verifier("devis envoyé", c == 200)
        time.sleep(1)
        token = jetons(a.log_quotation, r"/devis/reponse/([A-Za-z0-9_-]{20,})\?choix=accepter")[-1]
        c, pub = appel("POST", f"{QUOTATION}/public/reponse/{token}/accepter")
        verifier("le client accepte par son lien", c == 200 and pub["statut"] == "ACCEPTE")
        c, e = appel("POST", f"{QUOTATION}/public/reponse/{token}/accepter")
        verifier("seconde acceptation : 409", c == 409)

        print("=== 8. Mesure : Campaign reçoit DevisAccepte et calcule le ROI")
        appel("PUT", f"{CAMPAIGN}/campagnes/{cid}/mesures", {"impressions": 10000, "clics": 250, "montantDepense": 500})

        def perf():
            _, p = appel("GET", f"{CAMPAIGN}/campagnes/{cid}/performance")
            return p if p["devisAcceptes"] >= 1 else None

        p = attendre(perf)
        verifier("devisAcceptes = 1 (via RabbitMQ)", p is not None and p["devisAcceptes"] == 1)
        if p:
            verifier("chiffre d'affaires attribué = 2000", p["chiffreAffairesAttribue"] == 2000, str(p["chiffreAffairesAttribue"]))
            verifier("ROI = (2000 - 500) / 500 = 3", p["roi"] == 3, str(p["roi"]))
            verifier("CPC = 500 / 250 = 2", p["cpc"] == 2, str(p["cpc"]))
            verifier("prospects générés = 2 (deux envois du formulaire)", p["prospectsGeneres"] == 2, str(p["prospectsGeneres"]))
            verifier("clients intéressés = 1", p["clientsInteresses"] == 1, str(p["clientsInteresses"]))
        c, pdf = appel("GET", f"{CAMPAIGN}/campagnes/{cid}/rapport", brut=True)
        verifier("rapport PDF", c == 200 and pdf[:4] == b"%PDF")

    print()
    ok = all(resultats)
    print(f"RÉSULTAT : {sum(resultats)} vérifications correctes sur {len(resultats)}" + ("  -> TOUT EST OK" if ok else "  -> IL Y A DES KO"))
    print(f"campagneId = {cid}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
