package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record LigneSaisie(
        @NotNull UUID produitId,
        @NotNull @Min(1) Integer quantite,
        @DecimalMin("0") @DecimalMax("100") BigDecimal remise) {
}
