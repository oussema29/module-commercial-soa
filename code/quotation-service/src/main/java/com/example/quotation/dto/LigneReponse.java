package com.example.quotation.dto;

import com.example.quotation.entity.LigneDevis;
import java.math.BigDecimal;
import java.util.UUID;

public record LigneReponse(UUID id, UUID produitId, String designation, BigDecimal prixUnitaire, BigDecimal tauxTVA,
                           int quantite, BigDecimal remise, BigDecimal totalLigneHT) {

    public static LigneReponse de(LigneDevis l) {
        return new LigneReponse(l.getId(), l.getProduitId(), l.getDesignation(), l.getPrixUnitaire(), l.getTauxTVA(),
                l.getQuantite(), l.getRemise(), l.getTotalLigneHT());
    }
}
