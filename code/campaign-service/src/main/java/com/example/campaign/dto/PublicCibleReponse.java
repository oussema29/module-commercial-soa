package com.example.campaign.dto;

import com.example.campaign.entity.PublicCible;
import com.example.campaign.entity.TypePublic;
import java.util.UUID;

public record PublicCibleReponse(UUID id, UUID campagneId, TypePublic type, UUID categorieClientId, Integer ageMin,
                                 Integer ageMax, String localisation, String centresInteret, String description) {

    public static PublicCibleReponse de(PublicCible p) {
        return new PublicCibleReponse(p.getId(), p.getCampagne().getId(), p.getType(), p.getCategorieClientId(),
                p.getAgeMin(), p.getAgeMax(), p.getLocalisation(), p.getCentresInteret(), p.getDescription());
    }
}
