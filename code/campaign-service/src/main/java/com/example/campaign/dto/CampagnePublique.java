package com.example.campaign.dto;

import com.example.campaign.entity.Campagne;
import java.util.List;
import java.util.UUID;

/** Ce que voit une personne qui ouvre la page publique d'une campagne. */
public record CampagnePublique(UUID id, String nom, List<ContenuReponse> contenus) {

    public static CampagnePublique de(Campagne c) {
        return new CampagnePublique(c.getId(), c.getNom(), c.getContenus().stream().map(ContenuReponse::de).toList());
    }
}
