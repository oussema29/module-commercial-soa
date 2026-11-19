package com.example.campaign.service;

import com.example.campaign.entity.EvenementTraite;
import com.example.campaign.entity.PerformanceCampagne;
import com.example.campaign.messaging.DevisAccepteEvent;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.EvenementTraiteRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Attribue le chiffre d'affaires d'un devis accepté à sa campagne (consommateur IDEMPOTENT). */
@Service
@Transactional
public class DevisAccepteService {

    private static final Logger LOG = LoggerFactory.getLogger(DevisAccepteService.class);

    private final CampagneRepository campagnes;
    private final EvenementTraiteRepository traites;

    public DevisAccepteService(CampagneRepository campagnes, EvenementTraiteRepository traites) {
        this.campagnes = campagnes;
        this.traites = traites;
    }

    /** Renvoie true si le chiffre d'affaires a été attribué. */
    public boolean traiter(DevisAccepteEvent e) {
        // 1. Événement déjà reçu : on ignore
        if (traites.existsById(e.eventId())) {
            return false;
        }
        // 2. Campagne inconnue : on ignore sans erreur. Campagne TERMINEE : on compte quand même
        //    (le devis a été envoyé pendant la campagne, le client peut l'accepter plus tard)
        boolean attribue = campagnes.findById(e.campagneId()).map(c -> {
            PerformanceCampagne p = c.getPerformance();
            p.setDevisAcceptes(p.getDevisAcceptes() + 1);
            p.setChiffreAffairesAttribue(p.getChiffreAffairesAttribue().add(e.montantHT()));
            return true;
        }).orElse(false);
        if (!attribue) {
            LOG.info("DevisAccepte {} ignoré : campagne {} inconnue", e.eventId(), e.campagneId());
        }
        // 3. On note l'événement DANS LA MÊME TRANSACTION
        traites.save(new EvenementTraite(e.eventId(), Instant.now()));
        return attribue;
    }
}
