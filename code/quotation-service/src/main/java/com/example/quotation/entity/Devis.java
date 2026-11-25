package com.example.quotation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class Devis {

    @Id
    private UUID id = UUID.randomUUID();

    // Évite que deux personnes modifient le même devis en même temps
    @Version
    private Long version;

    @Column(nullable = false, unique = true)
    private String reference;

    private LocalDate date;
    private LocalDate dateValidite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDevis statut = StatutDevis.BROUILLON;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal remiseGlobale = BigDecimal.ZERO;

    // Montants CALCULÉS par CalculateurDevis : jamais saisis
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal totalHT = BigDecimal.ZERO;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal totalTVA = BigDecimal.ZERO;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal totalTTC = BigDecimal.ZERO;

    // Référence au client (autre service, pas de clé étrangère) et copie de ses coordonnées
    private UUID clientId;
    private String clientNom;
    private String clientEmail;

    // Facultatif ; non vérifié par ce service
    private UUID campagneId;

    // Jeton du lien envoyé au client (étape S3) : jamais exposé aux employés
    @Column(unique = true)
    private String tokenReponse;

    private Instant repondueLe;
    private UUID creePar;

    @OneToMany(mappedBy = "devis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<LigneDevis> lignes = new ArrayList<>();

    public UUID getId() { return id; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalDate getDateValidite() { return dateValidite; }
    public void setDateValidite(LocalDate dateValidite) { this.dateValidite = dateValidite; }

    public StatutDevis getStatut() { return statut; }
    public void setStatut(StatutDevis statut) { this.statut = statut; }

    public BigDecimal getRemiseGlobale() { return remiseGlobale; }
    public void setRemiseGlobale(BigDecimal remiseGlobale) { this.remiseGlobale = remiseGlobale; }

    public BigDecimal getTotalHT() { return totalHT; }
    public void setTotalHT(BigDecimal totalHT) { this.totalHT = totalHT; }

    public BigDecimal getTotalTVA() { return totalTVA; }
    public void setTotalTVA(BigDecimal totalTVA) { this.totalTVA = totalTVA; }

    public BigDecimal getTotalTTC() { return totalTTC; }
    public void setTotalTTC(BigDecimal totalTTC) { this.totalTTC = totalTTC; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getClientNom() { return clientNom; }
    public void setClientNom(String clientNom) { this.clientNom = clientNom; }

    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    public UUID getCampagneId() { return campagneId; }
    public void setCampagneId(UUID campagneId) { this.campagneId = campagneId; }

    public String getTokenReponse() { return tokenReponse; }
    public void setTokenReponse(String tokenReponse) { this.tokenReponse = tokenReponse; }

    public Instant getRepondueLe() { return repondueLe; }
    public void setRepondueLe(Instant repondueLe) { this.repondueLe = repondueLe; }

    public UUID getCreePar() { return creePar; }
    public void setCreePar(UUID creePar) { this.creePar = creePar; }

    public List<LigneDevis> getLignes() { return lignes; }
}
