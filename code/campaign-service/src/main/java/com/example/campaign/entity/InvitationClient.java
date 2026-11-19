package com.example.campaign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Invitation envoyée à un client existant. Le jeton n'existe que dans l'email : jamais exposé aux employés. */
@Entity
public class InvitationClient {

    @Id
    private UUID id = UUID.randomUUID();

    // Deux clics simultanés sur « Je suis intéressé » ne comptent qu'une réponse
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campagne_id")
    private Campagne campagne;

    // Référence au client chez Customer (pas de clé étrangère)
    private UUID clientId;

    @Column(nullable = false, unique = true)
    private String jeton;

    // Vide tant que l'envoi n'a pas réussi
    private Instant envoyeLe;

    @Column(length = 500)
    private String erreurEnvoi;

    private Instant repondueLe;

    public UUID getId() { return id; }

    public Campagne getCampagne() { return campagne; }
    public void setCampagne(Campagne campagne) { this.campagne = campagne; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getJeton() { return jeton; }
    public void setJeton(String jeton) { this.jeton = jeton; }

    public Instant getEnvoyeLe() { return envoyeLe; }
    public void setEnvoyeLe(Instant envoyeLe) { this.envoyeLe = envoyeLe; }

    public String getErreurEnvoi() { return erreurEnvoi; }
    public void setErreurEnvoi(String erreurEnvoi) { this.erreurEnvoi = erreurEnvoi; }

    public Instant getRepondueLe() { return repondueLe; }
    public void setRepondueLe(Instant repondueLe) { this.repondueLe = repondueLe; }
}
