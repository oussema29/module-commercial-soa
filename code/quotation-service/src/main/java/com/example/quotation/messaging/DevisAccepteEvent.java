package com.example.quotation.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contenu de l'événement DevisAccepte (voir contrat_api/evenements.md). */
public record DevisAccepteEvent(UUID eventId, String type, UUID devisId, String reference, UUID clientId,
                                UUID campagneId, BigDecimal montantHT, BigDecimal montantTTC, Instant date) {
}
