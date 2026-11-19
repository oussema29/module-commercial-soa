package com.example.campaign.dto;

import com.example.campaign.entity.TypeCanal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CanalSaisie(@NotBlank String nom, @NotNull TypeCanal type) {
}
