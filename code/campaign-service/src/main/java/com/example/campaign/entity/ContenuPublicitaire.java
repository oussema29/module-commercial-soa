package com.example.campaign.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class ContenuPublicitaire {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campagne_id")
    private Campagne campagne;

    @Column(nullable = false)
    private String titre;

    @Column(length = 4000)
    private String texte;

    private String imageUrl;

    // La vidéo est un lien, jamais une pièce jointe
    private String videoUrl;

    private String lien;

    @OneToMany(mappedBy = "contenu", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContenuProduit> produits = new ArrayList<>();

    public UUID getId() { return id; }

    public Campagne getCampagne() { return campagne; }
    public void setCampagne(Campagne campagne) { this.campagne = campagne; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getLien() { return lien; }
    public void setLien(String lien) { this.lien = lien; }

    public List<ContenuProduit> getProduits() { return produits; }
}
