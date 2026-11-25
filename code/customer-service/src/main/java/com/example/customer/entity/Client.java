package com.example.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Client {

    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeClient type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutClient statut = StatutClient.PROSPECT;

    @Column(nullable = false)
    private String nom;

    private String prenom;

    // Email tel que saisi
    @Column(nullable = false)
    private String email;

    // Email sans espaces et en minuscules : sert à garantir l'unicité (jamais exposé dans l'API)
    @Column(nullable = false, unique = true)
    private String emailNormalise;

    private String telephone;
    private String adresse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private CategorieClient categorie;

    // Simple référence à une campagne (autre service) : pas de clé étrangère
    private UUID campagneSourceId;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void avantCreation() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void avantModification() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public TypeClient getType() { return type; }
    public void setType(TypeClient type) { this.type = type; }

    public StatutClient getStatut() { return statut; }
    public void setStatut(StatutClient statut) { this.statut = statut; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getEmailNormalise() { return emailNormalise; }
    public void setEmailNormalise(String emailNormalise) { this.emailNormalise = emailNormalise; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public CategorieClient getCategorie() { return categorie; }
    public void setCategorie(CategorieClient categorie) { this.categorie = categorie; }

    public UUID getCampagneSourceId() { return campagneSourceId; }
    public void setCampagneSourceId(UUID campagneSourceId) { this.campagneSourceId = campagneSourceId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
