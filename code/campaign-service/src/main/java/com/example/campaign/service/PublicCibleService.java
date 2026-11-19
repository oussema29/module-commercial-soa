package com.example.campaign.service;

import com.example.campaign.dto.PublicCibleReponse;
import com.example.campaign.dto.PublicCibleSaisie;
import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.PublicCible;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.entity.TypePublic;
import com.example.campaign.exception.ApiException;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.PublicCibleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PublicCibleService {

    private final CampagneRepository campagnes;
    private final PublicCibleRepository publics;

    public PublicCibleService(CampagneRepository campagnes, PublicCibleRepository publics) {
        this.campagnes = campagnes;
        this.publics = publics;
    }

    @Transactional(readOnly = true)
    public List<PublicCibleReponse> lister(UUID campagneId) {
        return trouver(campagneId).getPublics().stream().map(PublicCibleReponse::de).toList();
    }

    /** Un public s'ajoute seulement en BROUILLON ; il ne se modifie pas (on le supprime et on le recrée). */
    public PublicCibleReponse ajouter(UUID campagneId, PublicCibleSaisie saisie) {
        Campagne c = trouver(campagneId);
        if (c.getStatut() != StatutCampagne.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Les publics ne se modifient qu'en brouillon");
        }
        verifierCoherence(saisie);
        PublicCible p = new PublicCible();
        p.setCampagne(c);
        p.setType(saisie.type());
        p.setCategorieClientId(saisie.categorieClientId());
        p.setAgeMin(saisie.ageMin());
        p.setAgeMax(saisie.ageMax());
        p.setLocalisation(saisie.localisation());
        p.setCentresInteret(saisie.centresInteret());
        p.setDescription(saisie.description());
        c.getPublics().add(p);
        campagnes.flush();   // l'identifiant du public est utilisable tout de suite
        return PublicCibleReponse.de(p);
    }

    public void supprimer(UUID id) {
        PublicCible p = publics.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Public introuvable"));
        Campagne c = p.getCampagne();
        if (c.getStatut() != StatutCampagne.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Les publics ne se modifient qu'en brouillon");
        }
        c.getPublics().remove(p);
        campagnes.flush();
    }

    /** CLIENTS_EXISTANTS : seulement la catégorie. PUBLIC_EXTERNE : seulement les critères de profil. */
    private void verifierCoherence(PublicCibleSaisie s) {
        boolean criteres = s.ageMin() != null || s.ageMax() != null || s.localisation() != null || s.centresInteret() != null;
        if (s.type() == TypePublic.CLIENTS_EXISTANTS) {
            if (s.categorieClientId() == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "categorieClientId est obligatoire pour des clients existants");
            }
            if (criteres) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Des clients existants se ciblent par catégorie, pas par critères de profil");
            }
        } else {
            if (s.categorieClientId() != null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Un public externe ne se cible pas par catégorie de clients");
            }
        }
        if (s.ageMin() != null && s.ageMax() != null && s.ageMin() > s.ageMax()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ageMin dépasse ageMax");
        }
    }

    private Campagne trouver(UUID id) {
        return campagnes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
    }
}
