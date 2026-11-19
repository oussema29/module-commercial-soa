package com.example.campaign.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class Campagne {

    @Id
    private UUID id = UUID.randomUUID();

    // Évite que deux personnes (ou deux lancements) modifient la même campagne en même temps
    @Version
    private Long version;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ObjectifCampagne objectif;

    private LocalDate dateDebut;
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCampagne statut = StatutCampagne.BROUILLON;

    private UUID creePar;

    // Tout ce qui suit disparaît avec la campagne (suppression d'un BROUILLON)
    @OneToOne(mappedBy = "campagne", cascade = CascadeType.ALL, orphanRemoval = true)
    private PerformanceCampagne performance;

    @OneToMany(mappedBy = "campagne", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PublicCible> publics = new ArrayList<>();

    @OneToMany(mappedBy = "campagne", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CampagneCanal> canaux = new ArrayList<>();

    @OneToMany(mappedBy = "campagne", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContenuPublicitaire> contenus = new ArrayList<>();

    @OneToMany(mappedBy = "campagne", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvitationClient> invitations = new ArrayList<>();

    public UUID getId() { return id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public ObjectifCampagne getObjectif() { return objectif; }
    public void setObjectif(ObjectifCampagne objectif) { this.objectif = objectif; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public StatutCampagne getStatut() { return statut; }
    public void setStatut(StatutCampagne statut) { this.statut = statut; }

    public UUID getCreePar() { return creePar; }
    public void setCreePar(UUID creePar) { this.creePar = creePar; }

    public PerformanceCampagne getPerformance() { return performance; }
    public void setPerformance(PerformanceCampagne performance) { this.performance = performance; }

    public List<PublicCible> getPublics() { return publics; }
    public List<CampagneCanal> getCanaux() { return canaux; }
    public List<ContenuPublicitaire> getContenus() { return contenus; }
    public List<InvitationClient> getInvitations() { return invitations; }
}
