package com.example.campaign.service;

import com.example.campaign.dto.ContenuReponse;
import com.example.campaign.dto.ContenuSaisie;
import com.example.campaign.dto.ProduitMisEnAvant;
import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.ContenuProduit;
import com.example.campaign.entity.ContenuPublicitaire;
import com.example.campaign.exception.ApiException;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.ContenuRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Les contenus restent modifiables à tout moment : corriger un texte n'affecte pas les invitations déjà envoyées. */
@Service
@Transactional
public class ContenuService {

    private final CampagneRepository campagnes;
    private final ContenuRepository contenus;

    public ContenuService(CampagneRepository campagnes, ContenuRepository contenus) {
        this.campagnes = campagnes;
        this.contenus = contenus;
    }

    @Transactional(readOnly = true)
    public List<ContenuReponse> lister(UUID campagneId) {
        return campagne(campagneId).getContenus().stream().map(ContenuReponse::de).toList();
    }

    public ContenuReponse ajouter(UUID campagneId, ContenuSaisie saisie) {
        Campagne c = campagne(campagneId);
        ContenuPublicitaire contenu = new ContenuPublicitaire();
        contenu.setCampagne(c);
        remplir(contenu, saisie);
        c.getContenus().add(contenu);
        campagnes.flush();
        return ContenuReponse.de(contenu);
    }

    public ContenuReponse modifier(UUID id, ContenuSaisie saisie) {
        ContenuPublicitaire contenu = trouver(id);
        remplir(contenu, saisie);
        return ContenuReponse.de(contenu);
    }

    public void supprimer(UUID id) {
        ContenuPublicitaire contenu = trouver(id);
        contenu.getCampagne().getContenus().remove(contenu);
        contenus.flush();
    }

    /** Identifiant et nom du produit sont COPIÉS : Campaign n'appelle pas Quotation. */
    public ContenuReponse ajouterProduit(UUID contenuId, ProduitMisEnAvant saisie) {
        ContenuPublicitaire contenu = trouver(contenuId);
        ContenuProduit p = new ContenuProduit();
        p.setContenu(contenu);
        p.setProduitId(saisie.produitId());
        p.setProduitNom(saisie.produitNom());
        contenu.getProduits().add(p);
        contenus.flush();
        return ContenuReponse.de(contenu);
    }

    public void retirerProduit(UUID contenuId, UUID produitId) {
        ContenuPublicitaire contenu = trouver(contenuId);
        ContenuProduit p = contenu.getProduits().stream().filter(x -> x.getProduitId().equals(produitId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Produit absent de ce contenu"));
        contenu.getProduits().remove(p);
        contenus.flush();
    }

    private ContenuPublicitaire trouver(UUID id) {
        return contenus.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Contenu introuvable"));
    }

    private Campagne campagne(UUID id) {
        return campagnes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
    }

    private void remplir(ContenuPublicitaire c, ContenuSaisie s) {
        c.setTitre(s.titre());
        c.setTexte(s.texte());
        c.setImageUrl(s.imageUrl());
        c.setVideoUrl(s.videoUrl());
        c.setLien(s.lien());
    }
}
