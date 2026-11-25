package com.example.customer.dto;

import com.example.customer.entity.Client;
import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import java.time.Instant;
import java.util.UUID;

public record ClientReponse(
        UUID id,
        TypeClient type,
        StatutClient statut,
        String nom,
        String prenom,
        String email,
        String telephone,
        String adresse,
        UUID categorieId,
        UUID campagneSourceId,
        Instant createdAt,
        Instant updatedAt) {

    public static ClientReponse de(Client c) {
        return new ClientReponse(c.getId(), c.getType(), c.getStatut(), c.getNom(), c.getPrenom(), c.getEmail(),
                c.getTelephone(), c.getAdresse(),
                c.getCategorie() == null ? null : c.getCategorie().getId(),
                c.getCampagneSourceId(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
