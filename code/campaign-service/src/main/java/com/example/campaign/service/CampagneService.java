package com.example.campaign.service;

import com.example.campaign.dto.CampagneReponse;
import com.example.campaign.dto.CampagneSaisie;
import com.example.campaign.dto.PageResponse;
import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.PerformanceCampagne;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.exception.ApiException;
import com.example.campaign.repository.CampagneRepository;
import java.time.Clock;
import java.time.LocalDate;
import com.example.campaign.security.UtilisateurCourant;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CampagneService {

    private final CampagneRepository campagnes;
    private final Clock clock;
    private final UtilisateurCourant utilisateur;

    public CampagneService(CampagneRepository campagnes, Clock clock,
                           UtilisateurCourant utilisateur) {
        this.campagnes = campagnes;
        this.clock = clock;
        this.utilisateur = utilisateur;
    }

    public CampagneReponse creer(CampagneSaisie saisie) {
        verifierDates(saisie);
        Campagne c = new Campagne();
        remplir(c, saisie);
        c.setCreePar(utilisateur.id());
        // La ligne de performance est créée avec la campagne (budget prévu vide) : le budget peut être saisi en brouillon
        PerformanceCampagne perf = new PerformanceCampagne();
        perf.setCampagne(c);
        c.setPerformance(perf);
        return CampagneReponse.de(campagnes.save(c));
    }

    @Transactional(readOnly = true)
    public PageResponse<CampagneReponse> lister(int page, int taille) {
        return PageResponse.of(campagnes.findAll(PageResponse.pageable(page, taille, Sort.by("dateDebut").descending()))
                .map(CampagneReponse::de));
    }

    @Transactional(readOnly = true)
    public CampagneReponse consulter(UUID id) {
        return CampagneReponse.de(trouver(id));
    }

    /** Les champs d'une campagne ne se modifient qu'en BROUILLON. */
    public CampagneReponse modifier(UUID id, CampagneSaisie saisie) {
        Campagne c = trouver(id);
        if (c.getStatut() != StatutCampagne.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Seule une campagne en brouillon peut être modifiée");
        }
        verifierDates(saisie);
        remplir(c, saisie);
        return CampagneReponse.de(c);
    }

    /** Supprime un BROUILLON avec sa performance, ses publics, ses canaux associés et ses contenus. */
    public void supprimer(UUID id) {
        Campagne c = trouver(id);
        if (c.getStatut() != StatutCampagne.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Seule une campagne en brouillon peut être supprimée");
        }
        campagnes.delete(c);
    }

    public CampagneReponse suspendre(UUID id) {
        Campagne c = trouver(id);
        if (c.getStatut() != StatutCampagne.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "Seule une campagne active peut être suspendue");
        }
        c.setStatut(StatutCampagne.SUSPENDUE);
        return CampagneReponse.de(c);
    }

    public CampagneReponse reprendre(UUID id) {
        Campagne c = trouver(id);
        if (c.getStatut() != StatutCampagne.SUSPENDUE) {
            throw new ApiException(HttpStatus.CONFLICT, "Seule une campagne suspendue peut être reprise");
        }
        if (LocalDate.now(clock).isAfter(c.getDateFin())) {
            throw new ApiException(HttpStatus.CONFLICT, "La date de fin est dépassée : la campagne ne peut plus reprendre");
        }
        c.setStatut(StatutCampagne.ACTIVE);
        return CampagneReponse.de(c);
    }

    /** Fin définitive depuis ACTIVE ou SUSPENDUE. Pour une simple pause, utiliser suspendre. */
    public CampagneReponse arreter(UUID id) {
        Campagne c = trouver(id);
        if (c.getStatut() != StatutCampagne.ACTIVE && c.getStatut() != StatutCampagne.SUSPENDUE) {
            throw new ApiException(HttpStatus.CONFLICT, "Seule une campagne active ou suspendue peut être arrêtée");
        }
        c.setStatut(StatutCampagne.TERMINEE);
        return CampagneReponse.de(c);
    }

    // ---- méthodes internes

    Campagne trouver(UUID id) {
        return campagnes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
    }

    Campagne trouverBrouillon(UUID id, String action) {
        Campagne c = trouver(id);
        if (c.getStatut() != StatutCampagne.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, action + " n'est possible que sur une campagne en brouillon");
        }
        return c;
    }

    private void verifierDates(CampagneSaisie s) {
        if (s.dateFin().isBefore(s.dateDebut())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "La date de fin est avant la date de début");
        }
    }

    private void remplir(Campagne c, CampagneSaisie s) {
        c.setNom(s.nom());
        c.setObjectif(s.objectif());
        c.setDateDebut(s.dateDebut());
        c.setDateFin(s.dateFin());
    }
}
