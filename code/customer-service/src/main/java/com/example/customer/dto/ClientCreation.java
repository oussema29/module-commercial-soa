package com.example.customer.dto;

import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Corps de POST /clients. Le statut est facultatif (PROSPECT par défaut ; CLIENT accepté). */
public record ClientCreation(
        @NotNull TypeClient type,
        StatutClient statut,
        @NotBlank String nom,
        String prenom,
        @NotBlank @Email String email,
        String telephone,
        String adresse,
        UUID categorieId) {

    // Les espaces autour de l'email sont enlevés avant la validation
    public ClientCreation {
        if (email != null) {
            email = email.trim();
        }
    }
}
