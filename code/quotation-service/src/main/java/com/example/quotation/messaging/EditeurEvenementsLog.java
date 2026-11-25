package com.example.quotation.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.events.rabbit", havingValue = "false", matchIfMissing = true)
public class EditeurEvenementsLog implements EditeurEvenements {

    private static final Logger LOG = LoggerFactory.getLogger(EditeurEvenementsLog.class);

    @Override
    public void devisAccepte(DevisAccepteEvent evenement) {
        LOG.info("[ÉVÉNEMENT SIMULÉ] DevisAccepte {}", evenement);
    }
}
