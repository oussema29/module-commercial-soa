package com.example.campaign.service;

import com.example.campaign.dto.CanalAssociation;
import com.example.campaign.dto.CanalReponse;
import com.example.campaign.dto.CanalSaisie;
import com.example.campaign.entity.CampagneCanal;
import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.CanalPublicitaire;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.exception.ApiException;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.CanalRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CanalService {

    private final CanalRepository canaux;
    private final CampagneRepository campagnes;

    public CanalService(CanalRepository canaux, CampagneRepository campagnes) {
        this.canaux = canaux;
        this.campagnes = campagnes;
    }

    @Transactional(readOnly = true)
    public List<CanalReponse> lister() {
        return canaux.findAll().stream().map(CanalReponse::de).toList();
    }

    /** Un canal n'est ni modifié ni supprimé : des campagnes peuvent le référencer. */
    public CanalReponse creer(CanalSaisie saisie) {
        CanalPublicitaire c = new CanalPublicitaire();
        c.setNom(saisie.nom());
        c.setType(saisie.type());
        return CanalReponse.de(canaux.save(c));
    }

    public CanalReponse associer(UUID campagneId, CanalAssociation saisie) {
        Campagne campagne = brouillon(campagneId);
        CanalPublicitaire canal = canaux.findById(saisie.canalId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Canal introuvable"));
        if (campagne.getCanaux().stream().anyMatch(cc -> cc.getCanal().getId().equals(canal.getId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Ce canal est déjà associé à la campagne");
        }
        CampagneCanal lien = new CampagneCanal();
        lien.setCampagne(campagne);
        lien.setCanal(canal);
        campagne.getCanaux().add(lien);
        return CanalReponse.de(canal);
    }

    public void retirer(UUID campagneId, UUID canalId) {
        Campagne campagne = brouillon(campagneId);
        CampagneCanal lien = campagne.getCanaux().stream().filter(cc -> cc.getCanal().getId().equals(canalId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ce canal n'est pas associé à la campagne"));
        campagne.getCanaux().remove(lien);
    }

    private Campagne brouillon(UUID campagneId) {
        Campagne c = campagnes.findById(campagneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
        if (c.getStatut() != StatutCampagne.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Les canaux ne se modifient qu'en brouillon");
        }
        return c;
    }
}
