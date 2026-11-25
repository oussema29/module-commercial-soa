package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TaxeSaisie(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal tauxTVA,
        String description) {
}
