package com.example.quotation.service;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.LigneDevis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Calcule les montants d'un devis. Aucun accès à la base : on lui donne un devis, il remplit les totaux.
 *
 * totalLigneHT = prixUnitaire x quantite x (1 - remise/100)
 * totalHT      = somme(totalLigneHT) x (1 - remiseGlobale/100)
 * totalTVA     = somme(totalLigneHT x tauxTVA/100) x (1 - remiseGlobale/100)
 * totalTTC     = totalHT + totalTVA
 * Tout est arrondi à 3 décimales (HALF_UP).
 */
@Component
public class CalculateurDevis {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    public void recalculer(Devis devis) {
        BigDecimal sommeHT = BigDecimal.ZERO;
        BigDecimal sommeTVA = BigDecimal.ZERO;

        for (LigneDevis ligne : devis.getLignes()) {
            BigDecimal facteurRemise = BigDecimal.ONE.subtract(ligne.getRemise().divide(CENT, 10, RoundingMode.HALF_UP));
            BigDecimal ht = ligne.getPrixUnitaire()
                    .multiply(BigDecimal.valueOf(ligne.getQuantite()))
                    .multiply(facteurRemise)
                    .setScale(3, RoundingMode.HALF_UP);
            ligne.setTotalLigneHT(ht);
            sommeHT = sommeHT.add(ht);
            sommeTVA = sommeTVA.add(ht.multiply(ligne.getTauxTVA()).divide(CENT, 10, RoundingMode.HALF_UP));
        }

        BigDecimal facteurGlobal = BigDecimal.ONE.subtract(devis.getRemiseGlobale().divide(CENT, 10, RoundingMode.HALF_UP));
        BigDecimal totalHT = sommeHT.multiply(facteurGlobal).setScale(3, RoundingMode.HALF_UP);
        BigDecimal totalTVA = sommeTVA.multiply(facteurGlobal).setScale(3, RoundingMode.HALF_UP);
        devis.setTotalHT(totalHT);
        devis.setTotalTVA(totalTVA);
        devis.setTotalTTC(totalHT.add(totalTVA));
    }
}
