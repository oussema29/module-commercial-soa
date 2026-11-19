package com.example.campaign.service;

import com.example.campaign.dto.BudgetReponse;
import com.example.campaign.dto.BudgetSaisie;
import com.example.campaign.entity.Campagne;
import com.example.campaign.exception.ApiException;
import com.example.campaign.repository.CampagneRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BudgetService {

    private final CampagneRepository campagnes;

    public BudgetService(CampagneRepository campagnes) {
        this.campagnes = campagnes;
    }

    @Transactional(readOnly = true)
    public BudgetReponse consulter(UUID campagneId) {
        return BudgetReponse.de(campagne(campagneId).getPerformance());
    }

    /** Définit le budget PRÉVU seulement : le dépensé ne change que par les mesures. */
    public BudgetReponse definirPrevu(UUID campagneId, BudgetSaisie saisie) {
        Campagne c = campagne(campagneId);
        c.getPerformance().setMontantPrevu(saisie.montantPrevu());
        return BudgetReponse.de(c.getPerformance());
    }

    private Campagne campagne(UUID id) {
        return campagnes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
    }
}
