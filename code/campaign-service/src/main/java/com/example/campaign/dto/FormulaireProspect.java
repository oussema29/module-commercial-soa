package com.example.campaign.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Formulaire rempli par un nouveau contact (particulier ou entreprise). */
public record FormulaireProspect(
        @NotNull TypeProspect type,
        @NotBlank String nom,
        String nomContact,
        @NotBlank @Email String email,
        String telephone) {

    // Les espaces autour de l'email sont enlevés avant la validation
    public FormulaireProspect {
        if (email != null) {
            email = email.trim();
        }
    }
}
