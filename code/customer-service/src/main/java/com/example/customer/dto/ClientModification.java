package com.example.customer.dto;

import com.example.customer.entity.TypeClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Corps de PUT /clients/{id}. Ne contient pas le statut (route dédiée). */
public record ClientModification(
        @NotNull TypeClient type,
        @NotBlank String nom,
        String prenom,
        @NotBlank @Email String email,
        String telephone,
        String adresse,
        UUID categorieId) {

    // Les espaces autour de l'email sont enlevés avant la validation
    public ClientModification {
        if (email != null) {
            email = email.trim();
        }
    }
}
