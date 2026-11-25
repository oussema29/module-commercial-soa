package com.example.quotation.dto;

import com.example.quotation.entity.Taxe;
import java.math.BigDecimal;
import java.util.UUID;

public record TaxeReponse(UUID id, BigDecimal tauxTVA, String description) {

    public static TaxeReponse de(Taxe t) {
        return new TaxeReponse(t.getId(), t.getTauxTVA(), t.getDescription());
    }
}
