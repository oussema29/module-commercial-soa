package com.example.customer.dto;

import com.example.customer.entity.Interaction;
import com.example.customer.entity.TypeInteraction;
import java.time.Instant;
import java.util.UUID;

public record InteractionReponse(UUID id, UUID clientId, TypeInteraction type, Instant date, String description,
                                 UUID auteurId) {

    public static InteractionReponse de(Interaction i) {
        return new InteractionReponse(i.getId(), i.getClient().getId(), i.getType(), i.getDate(), i.getDescription(),
                i.getAuteurId());
    }
}
