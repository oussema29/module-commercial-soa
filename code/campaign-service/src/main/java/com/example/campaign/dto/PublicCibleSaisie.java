package com.example.campaign.dto;

import com.example.campaign.entity.TypePublic;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PublicCibleSaisie(
        @NotNull TypePublic type,
        UUID categorieClientId,
        Integer ageMin,
        Integer ageMax,
        String localisation,
        String centresInteret,
        String description) {
}
