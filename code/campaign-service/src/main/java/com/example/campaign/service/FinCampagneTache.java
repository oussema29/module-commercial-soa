package com.example.campaign.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tâche quotidienne (2 h du matin) : termine les campagnes dont la date de fin est passée. */
@Component
public class FinCampagneTache {

    private static final Logger LOG = LoggerFactory.getLogger(FinCampagneTache.class);

    private final FinCampagneService service;

    public FinCampagneTache(FinCampagneService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void executer() {
        int nombre = service.terminerLesCampagnesEchues();
        LOG.info("Fin des campagnes : {} campagne(s) terminée(s)", nombre);
    }
}
