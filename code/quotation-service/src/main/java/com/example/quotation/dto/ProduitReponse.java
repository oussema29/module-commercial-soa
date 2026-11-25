package com.example.quotation.dto;

import com.example.quotation.entity.Produit;
import java.math.BigDecimal;
import java.util.UUID;

public record ProduitReponse(UUID id, String nom, String description, BigDecimal prix, UUID taxeId) {

    public static ProduitReponse de(Produit p) {
        return new ProduitReponse(p.getId(), p.getNom(), p.getDescription(), p.getPrix(), p.getTaxe().getId());
    }
}
