package com.example.quotation.service;

import com.example.quotation.client.ClientCustomer;
import com.example.quotation.client.CustomerClient;
import com.example.quotation.dto.DevisCreation;
import com.example.quotation.dto.DevisModification;
import com.example.quotation.dto.DevisReponse;
import com.example.quotation.dto.LigneModification;
import com.example.quotation.dto.LigneSaisie;
import com.example.quotation.dto.PageResponse;
import com.example.quotation.entity.Devis;
import com.example.quotation.entity.LigneDevis;
import com.example.quotation.entity.Produit;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.DevisRepository;
import com.example.quotation.repository.ProduitRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import com.example.quotation.security.UtilisateurCourant;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DevisService {

    private final DevisRepository devis;
    private final ProduitRepository produits;
    private final CustomerClient customer;
    private final CalculateurDevis calculateur;
    private final ReferenceDevis references;
    private final Clock clock;
    private final UtilisateurCourant utilisateur;

    public DevisService(DevisRepository devis, ProduitRepository produits, CustomerClient customer,
                        CalculateurDevis calculateur, ReferenceDevis references, Clock clock,
                        UtilisateurCourant utilisateur) {
        this.devis = devis;
        this.produits = produits;
        this.customer = customer;
        this.calculateur = calculateur;
        this.references = references;
        this.clock = clock;
        this.utilisateur = utilisateur;
    }

    public DevisReponse creer(DevisCreation saisie) {
        // 1. On lit le client chez Customer AVANT d'écrire quoi que ce soit : s'il est en panne, rien n'est créé
        ClientCustomer client = customer.trouver(saisie.clientId());
        if ("INACTIF".equals(client.statut())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Ce client est inactif : aucun devis possible");
        }
        LocalDate aujourdhui = LocalDate.now(clock);
        if (saisie.dateValidite().isBefore(aujourdhui)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La date de validité doit être postérieure ou égale à la date du devis");
        }
        // 2. On copie les coordonnées du client : le devis reste utilisable si Customer tombe plus tard
        Devis d = new Devis();
        d.setReference(references.suivante());
        d.setDate(aujourdhui);
        d.setDateValidite(saisie.dateValidite());
        d.setClientId(client.id() != null ? client.id() : saisie.clientId());
        d.setClientNom(client.prenom() == null || client.prenom().isBlank()
                ? client.nom() : client.prenom() + " " + client.nom());
        d.setClientEmail(client.email());
        d.setCampagneId(saisie.campagneId());
        d.setRemiseGlobale(saisie.remiseGlobale() == null ? BigDecimal.ZERO : saisie.remiseGlobale());
        d.setCreePar(utilisateur.id());
        calculateur.recalculer(d);
        return DevisReponse.de(devis.save(d));
    }

    @Transactional(readOnly = true)
    public DevisReponse consulter(UUID id) {
        return DevisReponse.de(trouver(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DevisReponse> lister(UUID clientId, StatutDevis statut, UUID campagneId, int page, int taille) {
        Specification<Devis> spec = (root, query, cb) -> cb.conjunction();
        if (clientId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clientId"), clientId));
        }
        if (statut != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statut"), statut));
        }
        if (campagneId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("campagneId"), campagneId));
        }
        return PageResponse.of(devis.findAll(spec, PageResponse.pageable(page, taille, Sort.by("reference").descending()))
                .map(DevisReponse::de));
    }

    public DevisReponse modifier(UUID id, DevisModification saisie) {
        Devis d = trouverBrouillon(id);
        if (saisie.dateValidite().isBefore(d.getDate())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La date de validité doit être postérieure ou égale à la date du devis");
        }
        d.setDateValidite(saisie.dateValidite());
        d.setCampagneId(saisie.campagneId());
        if (saisie.remiseGlobale() != null) {
            d.setRemiseGlobale(saisie.remiseGlobale());
        }
        if (saisie.clientEmail() != null) {
            d.setClientEmail(saisie.clientEmail());
        }
        calculateur.recalculer(d);
        return DevisReponse.de(d);
    }

    public DevisReponse ajouterLigne(UUID id, LigneSaisie saisie) {
        Devis d = trouverBrouillon(id);
        Produit produit = produits.findById(saisie.produitId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Produit introuvable"));
        LigneDevis ligne = new LigneDevis();
        ligne.setDevis(d);
        ligne.setPosition(d.getLignes().size());
        ligne.setProduitId(produit.getId());
        // COPIE du catalogue : si le produit change ensuite, cette ligne ne change pas
        ligne.setDesignation(produit.getNom());
        ligne.setPrixUnitaire(produit.getPrix());
        ligne.setTauxTVA(produit.getTaxe().getTauxTVA());
        ligne.setQuantite(saisie.quantite());
        ligne.setRemise(saisie.remise() == null ? BigDecimal.ZERO : saisie.remise());
        d.getLignes().add(ligne);
        calculateur.recalculer(d);
        return DevisReponse.de(d);
    }

    public DevisReponse modifierLigne(UUID id, UUID ligneId, LigneModification saisie) {
        Devis d = trouverBrouillon(id);
        LigneDevis ligne = trouverLigne(d, ligneId);
        ligne.setQuantite(saisie.quantite());
        ligne.setRemise(saisie.remise() == null ? BigDecimal.ZERO : saisie.remise());
        calculateur.recalculer(d);
        return DevisReponse.de(d);
    }

    public DevisReponse supprimerLigne(UUID id, UUID ligneId) {
        Devis d = trouverBrouillon(id);
        d.getLignes().remove(trouverLigne(d, ligneId));
        calculateur.recalculer(d);
        return DevisReponse.de(d);
    }

    /** Annulation possible avant la réponse du client : BROUILLON ou ENVOYE. */
    public DevisReponse annuler(UUID id) {
        Devis d = trouver(id);
        if (d.getStatut() != StatutDevis.BROUILLON && d.getStatut() != StatutDevis.ENVOYE) {
            throw new ApiException(HttpStatus.CONFLICT, "Ce devis ne peut plus être annulé");
        }
        d.setStatut(StatutDevis.ANNULE);
        return DevisReponse.de(d);
    }

    // ---- méthodes internes

    Devis trouver(UUID id) {
        return devis.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Devis introuvable"));
    }

    private Devis trouverBrouillon(UUID id) {
        Devis d = trouver(id);
        if (d.getStatut() != StatutDevis.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Seul un devis en brouillon peut être modifié");
        }
        return d;
    }

    private LigneDevis trouverLigne(Devis d, UUID ligneId) {
        return d.getLignes().stream().filter(l -> l.getId().equals(ligneId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ligne introuvable"));
    }
}
