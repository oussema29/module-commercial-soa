package com.example.campaign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class CanalPublicitaire {

    @Id
    private UUID id = UUID.randomUUID();

    // Libellé libre, par exemple « Facebook - page principale »
    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeCanal type;

    public UUID getId() { return id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public TypeCanal getType() { return type; }
    public void setType(TypeCanal type) { this.type = type; }
}
