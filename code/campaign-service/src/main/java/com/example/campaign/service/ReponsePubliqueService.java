package com.example.campaign.service;

import com.example.campaign.dto.CampagnePublique;
import com.example.campaign.dto.FormulaireProspect;
import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.InvitationClient;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.exception.ApiException;
import com.example.campaign.messaging.EditeurEvenements;
import com.example.campaign.messaging.ProspectGenereEvent;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.InvitationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Réponses du public : aucune identité, aucun appel à un autre service (on publie seulement un événement). */
@Service
@Transactional
public class ReponsePubliqueService {

    private final CampagneRepository campagnes;
    private final InvitationRepository invitations;
    private final EditeurEvenements evenements;
    private final Clock clock;

    public ReponsePubliqueService(CampagneRepository campagnes, InvitationRepository invitations,
                                  EditeurEvenements evenements, Clock clock) {
        this.campagnes = campagnes;
        this.invitations = invitations;
        this.evenements = evenements;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CampagnePublique consulter(UUID campagneId) {
        Campagne c = trouver(campagneId);
        verifierActive(c);
        return CampagnePublique.de(c);
    }

    /** Un nouveau contact remplit le formulaire : on compte le prospect et on publie ProspectGenere (Customer crée la fiche). */
    public void repondreFormulaire(UUID campagneId, FormulaireProspect f) {
        Campagne c = trouver(campagneId);
        verifierActive(c);
        c.getPerformance().setProspectsGeneres(c.getPerformance().getProspectsGeneres() + 1);
        evenements.prospectGenere(new ProspectGenereEvent(UUID.randomUUID(), "ProspectGenere", Instant.now(clock),
                c.getId(), f.type().name(), f.nom(), f.nomContact(), f.email(), f.telephone()));
    }

    /** Un client existant clique « Je suis intéressé » : compté une seule fois, aucune fiche n'est créée. */
    public void repondreInvitation(String jeton) {
        InvitationClient inv = invitations.findByJeton(jeton)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lien invalide"));
        verifierActive(inv.getCampagne());
        if (inv.getRepondueLe() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Vous avez déjà répondu");
        }
        inv.setRepondueLe(Instant.now(clock));
    }

    private Campagne trouver(UUID id) {
        return campagnes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
    }

    /** Active, et date de fin non dépassée (garde-fou : la tâche de fin n'a peut-être pas encore tourné). */
    private void verifierActive(Campagne c) {
        boolean dateDepassee = LocalDate.now(clock).isAfter(c.getDateFin());
        if (c.getStatut() != StatutCampagne.ACTIVE || dateDepassee) {
            throw new ApiException(HttpStatus.CONFLICT, "La campagne n'est plus active");
        }
    }
}
