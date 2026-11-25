package com.example.quotation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tâche quotidienne (1 h du matin) : expire les devis dont la date de validité est passée. */
@Component
public class ExpirationDevisTache {

    private static final Logger LOG = LoggerFactory.getLogger(ExpirationDevisTache.class);

    private final ExpirationService service;

    public ExpirationDevisTache(ExpirationService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void executer() {
        int nombre = service.expirerLesDevisEchus();
        LOG.info("Expiration des devis : {} devis expiré(s)", nombre);
    }
}
