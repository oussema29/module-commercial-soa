package com.example.campaign.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calculs de rentabilité, sans accès à la base : facile à tester seul. Un calcul impossible donne null, jamais une erreur. */
public final class CalculPerformance {

    private CalculPerformance() {
    }

    /** Coût par clic = dépensé / clics (null s'il n'y a aucun clic). */
    public static BigDecimal cpc(BigDecimal depense, long clics) {
        if (clics <= 0) {
            return null;
        }
        return depense.divide(BigDecimal.valueOf(clics), 3, RoundingMode.HALF_UP);
    }

    /** ROI = (chiffre d'affaires - dépensé) / dépensé (null si rien n'a été dépensé). */
    public static BigDecimal roi(BigDecimal chiffreAffaires, BigDecimal depense) {
        if (depense.signum() <= 0) {
            return null;
        }
        return chiffreAffaires.subtract(depense).divide(depense, 3, RoundingMode.HALF_UP);
    }
}
