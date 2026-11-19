package com.example.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campaign.service.CalculPerformance;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Test unitaire pur : pas de Spring, pas de base. */
class CalculPerformanceTest {

    @Test
    void roiEstLeGainRapporteAuDepense() {
        // dépensé 100, chiffre d'affaires 350 : (350 - 100) / 100 = 2,5
        assertThat(CalculPerformance.roi(new BigDecimal("350"), new BigDecimal("100"))).isEqualByComparingTo("2.5");
        // chiffre d'affaires inférieur au dépensé : ROI négatif
        assertThat(CalculPerformance.roi(new BigDecimal("50"), new BigDecimal("100"))).isEqualByComparingTo("-0.5");
    }

    @Test
    void roiNonCalculableSiRienNEstDepense() {
        assertThat(CalculPerformance.roi(new BigDecimal("350"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void cpcEstLeDepenseDiviseParLesClics() {
        assertThat(CalculPerformance.cpc(new BigDecimal("100"), 250)).isEqualByComparingTo("0.4");
        assertThat(CalculPerformance.cpc(new BigDecimal("10"), 3)).isEqualByComparingTo("3.333");
    }

    @Test
    void cpcNonCalculableSansClic() {
        assertThat(CalculPerformance.cpc(new BigDecimal("100"), 0)).isNull();
    }
}
