package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ProduitSaisie(
        @NotBlank String nom,
        String description,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 3) BigDecimal prix,
        @NotNull UUID taxeId) {
}
