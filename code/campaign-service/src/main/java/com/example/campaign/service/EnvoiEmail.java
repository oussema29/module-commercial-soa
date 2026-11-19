package com.example.campaign.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * UNIQUE point d'envoi d'email du service. Aujourd'hui l'envoi est SIMULÉ (écrit dans les logs).
 * Quand le service Notification existera, il suffira de remplacer le corps de envoyer().
 */
@Component
public class EnvoiEmail {

    private static final Logger LOG = LoggerFactory.getLogger(EnvoiEmail.class);

    private final boolean echecSimule;

    public EnvoiEmail(@Value("${app.email.echec-simule:false}") boolean echecSimule) {
        this.echecSimule = echecSimule;
    }

    public void envoyer(String destinataire, String sujet, String texte, byte[] pieceJointe) {
        if (echecSimule) {
            throw new EmailException("Boîte du destinataire indisponible (échec simulé)");
        }
        LOG.info("[EMAIL SIMULÉ] à={} sujet={} pièceJointe={} octets\n{}",
                destinataire, sujet, pieceJointe == null ? 0 : pieceJointe.length, texte);
    }
}
