package com.example.campaign.dto;

import com.example.campaign.entity.ObjectifCampagne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CampagneSaisie(
        @NotBlank String nom,
        @NotNull ObjectifCampagne objectif,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin) {
}
