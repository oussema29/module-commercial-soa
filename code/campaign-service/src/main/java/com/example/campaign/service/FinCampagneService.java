package com.example.campaign.service;

import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.repository.CampagneRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FinCampagneService {

    private final CampagneRepository campagnes;
    private final Clock clock;

    public FinCampagneService(CampagneRepository campagnes, Clock clock) {
        this.campagnes = campagnes;
        this.clock = clock;
    }

    /**
     * Passe à TERMINEE les campagnes ACTIVE ou SUSPENDUE dont la date de fin est strictement passée.
     * Sans effet sur BROUILLON et TERMINEE. Relancée plusieurs fois, elle ne change rien de plus.
     */
    public int terminerLesCampagnesEchues() {
        List<Campagne> echues = campagnes.findByStatutInAndDateFinBefore(
                List.of(StatutCampagne.ACTIVE, StatutCampagne.SUSPENDUE), LocalDate.now(clock));
        echues.forEach(c -> c.setStatut(StatutCampagne.TERMINEE));
        return echues.size();
    }
}
