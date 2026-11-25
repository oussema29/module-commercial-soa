package com.example.quotation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;

/** Trace du PDF généré et envoyé pour un devis. */
@Entity
public class Document {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devis_id")
    private Devis devis;

    private String format = "PDF";

    @Lob
    private byte[] contenu;

    private Instant genereLe;
    private Instant envoyeLe;
    private String destinataire;

    public UUID getId() { return id; }

    public Devis getDevis() { return devis; }
    public void setDevis(Devis devis) { this.devis = devis; }

    public String getFormat() { return format; }

    public byte[] getContenu() { return contenu; }
    public void setContenu(byte[] contenu) { this.contenu = contenu; }

    public Instant getGenereLe() { return genereLe; }
    public void setGenereLe(Instant genereLe) { this.genereLe = genereLe; }

    public Instant getEnvoyeLe() { return envoyeLe; }
    public void setEnvoyeLe(Instant envoyeLe) { this.envoyeLe = envoyeLe; }

    public String getDestinataire() { return destinataire; }
    public void setDestinataire(String destinataire) { this.destinataire = destinataire; }
}
