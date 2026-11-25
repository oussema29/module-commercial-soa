package com.example.quotation.dto;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ce que le client voit en ouvrant son lien : rien d'interne (ni identifiants, ni jeton). */
public record DevisPublic(String reference, LocalDate date, LocalDate dateValidite, StatutDevis statut,
                          String clientNom, List<LigneReponse> lignes, BigDecimal totalHT, BigDecimal totalTVA,
                          BigDecimal totalTTC) {

    public static DevisPublic de(Devis d) {
        return new DevisPublic(d.getReference(), d.getDate(), d.getDateValidite(), d.getStatut(), d.getClientNom(),
                d.getLignes().stream().map(LigneReponse::de).toList(), d.getTotalHT(), d.getTotalTVA(),
                d.getTotalTTC());
    }
}
