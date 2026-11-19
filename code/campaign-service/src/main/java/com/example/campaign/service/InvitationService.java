package com.example.campaign.service;

import com.example.campaign.dto.InvitationReponse;
import com.example.campaign.dto.PageResponse;
import com.example.campaign.dto.ReponseClient;
import com.example.campaign.entity.FiltreEnvoi;
import com.example.campaign.exception.ApiException;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.InvitationRepository;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InvitationService {

    private final CampagneRepository campagnes;
    private final InvitationRepository invitations;

    public InvitationService(CampagneRepository campagnes, InvitationRepository invitations) {
        this.campagnes = campagnes;
        this.invitations = invitations;
    }

    /** ECHEC : celles qui n'ont pas pu partir, avec la cause. REUSSI : celles qui sont parties. Sans filtre : toutes. */
    public PageResponse<InvitationReponse> lister(UUID campagneId, FiltreEnvoi filtre, int page, int taille) {
        verifier(campagneId);
        Pageable pageable = PageResponse.pageable(page, taille, Sort.by("clientId"));
        var resultat = filtre == null ? invitations.findByCampagneId(campagneId, pageable)
                : filtre == FiltreEnvoi.ECHEC ? invitations.findByCampagneIdAndEnvoyeLeIsNullAndErreurEnvoiIsNotNull(campagneId, pageable)
                : invitations.findByCampagneIdAndEnvoyeLeIsNotNull(campagneId, pageable);
        return PageResponse.of(resultat.map(InvitationReponse::de));
    }

    /** Clients existants ayant répondu « Je suis intéressé ». */
    public PageResponse<ReponseClient> reponses(UUID campagneId, int page, int taille) {
        verifier(campagneId);
        return PageResponse.of(invitations
                .findByCampagneIdAndRepondueLeIsNotNull(campagneId, PageResponse.pageable(page, taille, Sort.by("repondueLe")))
                .map(ReponseClient::de));
    }

    private void verifier(UUID campagneId) {
        if (!campagnes.existsById(campagneId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Campagne introuvable");
        }
    }
}
