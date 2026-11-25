package com.example.quotation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Corps de PUT /devis/{id}. clientEmail sert à corriger l'adresse d'envoi (la fiche Customer n'est pas modifiée). */
public record DevisModification(
        @NotNull LocalDate dateValidite,
        UUID campagneId,
        @DecimalMin("0") @DecimalMax("100") BigDecimal remiseGlobale,
        @Email String clientEmail) {
}
