package com.example.campaign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.UUID;

/** Produit mis en avant dans un contenu : identifiant et nom COPIÉS depuis le catalogue de Quotation. */
@Entity
public class ContenuProduit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contenu_id")
    private ContenuPublicitaire contenu;

    private UUID produitId;

    @Column(nullable = false)
    private String produitNom;

    public Long getId() { return id; }

    public ContenuPublicitaire getContenu() { return contenu; }
    public void setContenu(ContenuPublicitaire contenu) { this.contenu = contenu; }

    public UUID getProduitId() { return produitId; }
    public void setProduitId(UUID produitId) { this.produitId = produitId; }

    public String getProduitNom() { return produitNom; }
    public void setProduitNom(String produitNom) { this.produitNom = produitNom; }
}
