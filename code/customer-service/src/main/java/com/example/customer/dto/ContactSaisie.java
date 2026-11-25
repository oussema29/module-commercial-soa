package com.example.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactSaisie(@NotBlank String nom, String prenom, String fonction, @Email String email, String telephone) {
}
