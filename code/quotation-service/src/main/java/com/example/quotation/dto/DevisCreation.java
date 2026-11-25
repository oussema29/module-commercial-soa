package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DevisCreation(
        @NotNull UUID clientId,
        @NotNull LocalDate dateValidite,
        UUID campagneId,
        @DecimalMin("0") @DecimalMax("100") BigDecimal remiseGlobale) {
}
