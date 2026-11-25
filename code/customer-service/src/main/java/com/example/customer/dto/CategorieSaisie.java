package com.example.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record CategorieSaisie(@NotBlank String nom, String description) {
}
