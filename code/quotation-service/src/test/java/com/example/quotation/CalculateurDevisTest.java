package com.example.quotation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.LigneDevis;
import com.example.quotation.service.CalculateurDevis;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Test unitaire pur : pas de Spring, pas de base. */
class CalculateurDevisTest {

    private final CalculateurDevis calculateur = new CalculateurDevis();

    private static LigneDevis ligne(String prix, int quantite, String remise, String tva) {
        LigneDevis l = new LigneDevis();
        l.setPrixUnitaire(new BigDecimal(prix));
        l.setQuantite(quantite);
        l.setRemise(new BigDecimal(remise));
        l.setTauxTVA(new BigDecimal(tva));
        return l;
    }

    @Test
    void uneLigneAvecRemiseEtTva() {
        Devis d = new Devis();
        d.getLignes().add(ligne("100.000", 2, "10", "19"));
        calculateur.recalculer(d);
        assertThat(d.getLignes().get(0).getTotalLigneHT()).isEqualByComparingTo("180.000");
        assertThat(d.getTotalHT()).isEqualByComparingTo("180.000");
        assertThat(d.getTotalTVA()).isEqualByComparingTo("34.200");
        assertThat(d.getTotalTTC()).isEqualByComparingTo("214.200");
    }

    @Test
    void deuxLignesDeTvaDifferenteEtRemiseGlobale() {
        Devis d = new Devis();
        d.getLignes().add(ligne("100.000", 1, "0", "19"));
        d.getLignes().add(ligne("50.000", 2, "0", "7"));
        d.setRemiseGlobale(new BigDecimal("10"));
        calculateur.recalculer(d);
        // HT : 200 x 0,9 = 180 ; TVA : (19 + 7) x 0,9 = 23,4
        assertThat(d.getTotalHT()).isEqualByComparingTo("180.000");
        assertThat(d.getTotalTVA()).isEqualByComparingTo("23.400");
        assertThat(d.getTotalTTC()).isEqualByComparingTo("203.400");
    }

    @Test
    void arrondiATroisDecimales() {
        Devis d = new Devis();
        d.getLignes().add(ligne("100.000", 1, "33.333", "0"));
        calculateur.recalculer(d);
        assertThat(d.getTotalHT()).isEqualByComparingTo("66.667");
    }

    @Test
    void devisSansLigneTotauxANul() {
        Devis d = new Devis();
        calculateur.recalculer(d);
        assertThat(d.getTotalHT()).isEqualByComparingTo("0");
        assertThat(d.getTotalTVA()).isEqualByComparingTo("0");
        assertThat(d.getTotalTTC()).isEqualByComparingTo("0");
    }
}
