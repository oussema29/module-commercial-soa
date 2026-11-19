package com.example.campaign.dto;

import com.example.campaign.entity.ContenuProduit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Corps de POST /contenus/{id}/produits ; sert aussi à afficher les produits d'un contenu. */
public record ProduitMisEnAvant(@NotNull UUID produitId, @NotBlank String produitNom) {

    public static ProduitMisEnAvant de(ContenuProduit p) {
        return new ProduitMisEnAvant(p.getProduitId(), p.getProduitNom());
    }
}
