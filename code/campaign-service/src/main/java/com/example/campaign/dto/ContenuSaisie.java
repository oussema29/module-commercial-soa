package com.example.campaign.dto;

import jakarta.validation.constraints.NotBlank;

public record ContenuSaisie(@NotBlank String titre, String texte, String imageUrl, String videoUrl, String lien) {
}
