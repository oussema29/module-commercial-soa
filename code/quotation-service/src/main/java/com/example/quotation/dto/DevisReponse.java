package com.example.quotation.dto;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Vue d'un devis pour les employés. Le jeton du lien client n'y figure jamais. */
public record DevisReponse(
        UUID id,
        String reference,
        LocalDate date,
        LocalDate dateValidite,
        StatutDevis statut,
        BigDecimal remiseGlobale,
        BigDecimal totalHT,
        BigDecimal totalTVA,
        BigDecimal totalTTC,
        UUID clientId,
        String clientNom,
        String clientEmail,
        UUID campagneId,
        Instant repondueLe,
        UUID creePar,
        List<LigneReponse> lignes) {

    public static DevisReponse de(Devis d) {
        return new DevisReponse(d.getId(), d.getReference(), d.getDate(), d.getDateValidite(), d.getStatut(),
                d.getRemiseGlobale(), d.getTotalHT(), d.getTotalTVA(), d.getTotalTTC(), d.getClientId(),
                d.getClientNom(), d.getClientEmail(), d.getCampagneId(), d.getRepondueLe(), d.getCreePar(),
                d.getLignes().stream().map(LigneReponse::de).toList());
    }
}
