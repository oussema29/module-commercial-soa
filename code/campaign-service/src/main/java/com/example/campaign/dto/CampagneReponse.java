package com.example.campaign.dto;

import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.ObjectifCampagne;
import com.example.campaign.entity.StatutCampagne;
import java.time.LocalDate;
import java.util.UUID;

public record CampagneReponse(UUID id, String nom, ObjectifCampagne objectif, LocalDate dateDebut, LocalDate dateFin,
                              StatutCampagne statut, UUID creePar) {

    public static CampagneReponse de(Campagne c) {
        return new CampagneReponse(c.getId(), c.getNom(), c.getObjectif(), c.getDateDebut(), c.getDateFin(),
                c.getStatut(), c.getCreePar());
    }
}
