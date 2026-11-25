package com.example.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
public class LigneDevis {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devis_id")
    private Devis devis;

    // Rang de la ligne dans le devis (pour garder l'ordre d'ajout)
    private int position;

    // Référence au produit du catalogue ; les champs suivants en sont des COPIES
    private UUID produitId;

    @Column(nullable = false)
    private String designation;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal prixUnitaire;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTVA;

    private int quantite;

    // Remise de la ligne en pourcentage (0 à 100)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal remise = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal totalLigneHT = BigDecimal.ZERO;

    public UUID getId() { return id; }

    public Devis getDevis() { return devis; }
    public void setDevis(Devis devis) { this.devis = devis; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public UUID getProduitId() { return produitId; }
    public void setProduitId(UUID produitId) { this.produitId = produitId; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public BigDecimal getPrixUnitaire() { return prixUnitaire; }
    public void setPrixUnitaire(BigDecimal prixUnitaire) { this.prixUnitaire = prixUnitaire; }

    public BigDecimal getTauxTVA() { return tauxTVA; }
    public void setTauxTVA(BigDecimal tauxTVA) { this.tauxTVA = tauxTVA; }

    public int getQuantite() { return quantite; }
    public void setQuantite(int quantite) { this.quantite = quantite; }

    public BigDecimal getRemise() { return remise; }
    public void setRemise(BigDecimal remise) { this.remise = remise; }

    public BigDecimal getTotalLigneHT() { return totalLigneHT; }
    public void setTotalLigneHT(BigDecimal totalLigneHT) { this.totalLigneHT = totalLigneHT; }
}
