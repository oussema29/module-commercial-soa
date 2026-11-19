package com.example.campaign.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Totaux CUMULÉS (simulés, faute d'accès aux plateformes publicitaires) : ils remplacent les valeurs précédentes. */
public record MesuresSaisie(
        @NotNull @Min(0) Long impressions,
        @NotNull @Min(0) Long clics,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 3) BigDecimal montantDepense) {
}
