package com.example.campaign.dto;

import com.example.campaign.entity.ContenuPublicitaire;
import java.util.List;
import java.util.UUID;

public record ContenuReponse(UUID id, UUID campagneId, String titre, String texte, String imageUrl, String videoUrl,
                             String lien, List<ProduitMisEnAvant> produits) {

    public static ContenuReponse de(ContenuPublicitaire c) {
        return new ContenuReponse(c.getId(), c.getCampagne().getId(), c.getTitre(), c.getTexte(), c.getImageUrl(),
                c.getVideoUrl(), c.getLien(), c.getProduits().stream().map(ProduitMisEnAvant::de).toList());
    }
}
