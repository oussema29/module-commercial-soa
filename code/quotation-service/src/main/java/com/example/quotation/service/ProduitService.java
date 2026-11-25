package com.example.quotation.service;

import com.example.quotation.dto.PageResponse;
import com.example.quotation.dto.ProduitReponse;
import com.example.quotation.dto.ProduitSaisie;
import com.example.quotation.entity.Produit;
import com.example.quotation.entity.Taxe;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.ProduitRepository;
import com.example.quotation.repository.TaxeRepository;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProduitService {

    private final ProduitRepository produits;
    private final TaxeRepository taxes;

    public ProduitService(ProduitRepository produits, TaxeRepository taxes) {
        this.produits = produits;
        this.taxes = taxes;
    }

    public ProduitReponse creer(ProduitSaisie saisie) {
        Produit p = new Produit();
        remplir(p, saisie);
        return ProduitReponse.de(produits.save(p));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProduitReponse> lister(int page, int taille) {
        return PageResponse.of(produits.findAll(PageResponse.pageable(page, taille, Sort.by("nom")))
                .map(ProduitReponse::de));
    }

    @Transactional(readOnly = true)
    public ProduitReponse consulter(UUID id) {
        return ProduitReponse.de(trouver(id));
    }

    public ProduitReponse modifier(UUID id, ProduitSaisie saisie) {
        Produit p = trouver(id);
        remplir(p, saisie);
        return ProduitReponse.de(p);
    }

    /** Sans effet sur les devis existants : ils gardent leur copie du produit. */
    public void supprimer(UUID id) {
        produits.delete(trouver(id));
    }

    private Produit trouver(UUID id) {
        return produits.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Produit introuvable"));
    }

    private void remplir(Produit p, ProduitSaisie s) {
        Taxe taxe = taxes.findById(s.taxeId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Taxe introuvable"));
        p.setNom(s.nom());
        p.setDescription(s.description());
        p.setPrix(s.prix());
        p.setTaxe(taxe);
    }
}
