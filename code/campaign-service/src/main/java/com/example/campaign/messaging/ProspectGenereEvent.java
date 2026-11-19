package com.example.campaign.messaging;

import java.time.Instant;
import java.util.UUID;

/** Contenu de l'événement ProspectGenere (voir contrat_api/evenements.md). */
public record ProspectGenereEvent(UUID eventId, String type, Instant date, UUID campagneId, String typeClient,
                                  String nom, String nomContact, String email, String telephone) {
}
