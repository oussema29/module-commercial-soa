package com.example.campaign.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Événements déjà reçus. RabbitMQ garantit « au moins une fois », jamais « exactement une fois » : sans cette table,
 * un DevisAccepte rejoué compterait deux fois le chiffre d'affaires. Aucun lien avec les autres tables.
 */
@Entity
public class EvenementTraite {

    @Id
    private UUID eventId;

    private Instant traiteLe;

    protected EvenementTraite() {
    }

    public EvenementTraite(UUID eventId, Instant traiteLe) {
        this.eventId = eventId;
        this.traiteLe = traiteLe;
    }

    public UUID getEventId() { return eventId; }

    public Instant getTraiteLe() { return traiteLe; }
}
