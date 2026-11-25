package com.example.customer.dto;

import com.example.customer.entity.TypeInteraction;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Pas de auteurId ici : il vient de l'utilisateur courant, jamais du corps de la requête. */
public record InteractionSaisie(@NotNull TypeInteraction type, @NotNull Instant date, String description) {
}
