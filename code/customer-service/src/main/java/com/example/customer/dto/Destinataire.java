package com.example.customer.dto;

import com.example.customer.entity.Client;
import java.util.UUID;

/** Liste réduite lue par Campaign : rien d'autre que l'identifiant, le nom et l'email. */
public record Destinataire(UUID clientId, String nom, String email) {

    public static Destinataire de(Client c) {
        return new Destinataire(c.getId(), c.getNom(), c.getEmail());
    }
}
