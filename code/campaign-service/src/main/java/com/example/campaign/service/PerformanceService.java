package com.example.campaign.service;

import com.example.campaign.dto.MesuresSaisie;
import com.example.campaign.dto.PerformanceReponse;
import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.PerformanceCampagne;
import com.example.campaign.exception.ApiException;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.InvitationRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PerformanceService {

    private final CampagneRepository campagnes;
    private final InvitationRepository invitations;

    public PerformanceService(CampagneRepository campagnes, InvitationRepository invitations) {
        this.campagnes = campagnes;
        this.invitations = invitations;
    }

    /** Remplace les totaux cumulés : envoyée deux fois, la même requête donne le même résultat. Seule route qui modifie le dépensé. */
    public PerformanceReponse remplacerMesures(UUID campagneId, MesuresSaisie m) {
        Campagne c = trouver(campagneId);
        PerformanceCampagne p = c.getPerformance();
        p.setImpressions(m.impressions());
        p.setClics(m.clics());
        p.setMontantDepense(m.montantDepense());
        return vers(c);
    }

    @Transactional(readOnly = true)
    public PerformanceReponse consulter(UUID campagneId) {
        return vers(trouver(campagneId));
    }

    public Campagne trouver(UUID id) {
        return campagnes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
    }

    private PerformanceReponse vers(Campagne c) {
        PerformanceCampagne p = c.getPerformance();
        long interesses = invitations.countByCampagneIdAndRepondueLeIsNotNull(c.getId());
        return new PerformanceReponse(c.getId(), p.getMontantPrevu(), p.getMontantDepense(), p.getImpressions(),
                p.getClics(), p.getProspectsGeneres(), interesses, p.getDevisAcceptes(), p.getChiffreAffairesAttribue(),
                CalculPerformance.cpc(p.getMontantDepense(), p.getClics()),
                CalculPerformance.roi(p.getChiffreAffairesAttribue(), p.getMontantDepense()));
    }
}
