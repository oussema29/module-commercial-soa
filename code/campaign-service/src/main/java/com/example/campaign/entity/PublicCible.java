package com.example.campaign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.UUID;

@Entity
public class PublicCible {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campagne_id")
    private Campagne campagne;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypePublic type;

    // CLIENTS_EXISTANTS : la catégorie de clients visée (id chez Customer, sans clé étrangère)
    private UUID categorieClientId;

    // PUBLIC_EXTERNE : critères de profil transmis à la plateforme publicitaire
    private Integer ageMin;
    private Integer ageMax;
    private String localisation;
    private String centresInteret;
    private String description;

    public UUID getId() { return id; }

    public Campagne getCampagne() { return campagne; }
    public void setCampagne(Campagne campagne) { this.campagne = campagne; }

    public TypePublic getType() { return type; }
    public void setType(TypePublic type) { this.type = type; }

    public UUID getCategorieClientId() { return categorieClientId; }
    public void setCategorieClientId(UUID categorieClientId) { this.categorieClientId = categorieClientId; }

    public Integer getAgeMin() { return ageMin; }
    public void setAgeMin(Integer ageMin) { this.ageMin = ageMin; }

    public Integer getAgeMax() { return ageMax; }
    public void setAgeMax(Integer ageMax) { this.ageMax = ageMax; }

    public String getLocalisation() { return localisation; }
    public void setLocalisation(String localisation) { this.localisation = localisation; }

    public String getCentresInteret() { return centresInteret; }
    public void setCentresInteret(String centresInteret) { this.centresInteret = centresInteret; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
