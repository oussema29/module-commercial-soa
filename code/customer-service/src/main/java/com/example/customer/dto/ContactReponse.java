package com.example.customer.dto;

import com.example.customer.entity.Contact;
import java.util.UUID;

public record ContactReponse(UUID id, UUID clientId, String nom, String prenom, String fonction, String email,
                             String telephone) {

    public static ContactReponse de(Contact c) {
        return new ContactReponse(c.getId(), c.getClient().getId(), c.getNom(), c.getPrenom(), c.getFonction(),
                c.getEmail(), c.getTelephone());
    }
}
