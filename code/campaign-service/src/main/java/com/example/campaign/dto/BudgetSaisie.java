package com.example.campaign.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BudgetSaisie(@NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 3) BigDecimal montantPrevu) {
}
