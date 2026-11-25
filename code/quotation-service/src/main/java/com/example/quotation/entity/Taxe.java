package com.example.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
public class Taxe {

    @Id
    private UUID id = UUID.randomUUID();

    // Pourcentage de 0 à 100 (19 pour 19 %)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTVA;

    private String description;

    public UUID getId() { return id; }

    public BigDecimal getTauxTVA() { return tauxTVA; }
    public void setTauxTVA(BigDecimal tauxTVA) { this.tauxTVA = tauxTVA; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
