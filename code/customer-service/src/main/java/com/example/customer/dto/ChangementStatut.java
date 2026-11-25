package com.example.customer.dto;

import com.example.customer.entity.StatutClient;
import jakarta.validation.constraints.NotNull;

public record ChangementStatut(@NotNull StatutClient statut) {
}
