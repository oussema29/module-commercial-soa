package com.example.quotation.service;

import com.example.quotation.dto.DevisPublic;
import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.exception.ApiException;
import com.example.quotation.messaging.DevisAccepteEvent;
import com.example.quotation.messaging.EditeurEvenements;
import com.example.quotation.repository.DevisRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Réponse du client par son lien : aucune identité, le jeton fait foi. */
@Service
@Transactional
public class ReponsePubliqueService {

    private final DevisRepository devis;
    private final EditeurEvenements evenements;
    private final Clock clock;

    public ReponsePubliqueService(DevisRepository devis, EditeurEvenements evenements, Clock clock) {
        this.devis = devis;
        this.evenements = evenements;
        this.clock = clock;
    }

    /** Renvoie toujours le devis avec son statut ; ne modifie rien. */
    @Transactional(readOnly = true)
    public DevisPublic consulter(String token) {
        return DevisPublic.de(trouver(token));
    }

    public DevisPublic accepter(String token) {
        Devis d = trouver(token);
        verifierQuOnPeutRepondre(d);
        d.setStatut(StatutDevis.ACCEPTE);
        d.setRepondueLe(Instant.now(clock));
        // L'événement n'est publié que si le devis vient d'une campagne
        if (d.getCampagneId() != null) {
            evenements.devisAccepte(new DevisAccepteEvent(UUID.randomUUID(), "DevisAccepte", d.getId(), d.getReference(),
                    d.getClientId(), d.getCampagneId(), d.getTotalHT(), d.getTotalTTC(), d.getRepondueLe()));
        }
        return DevisPublic.de(d);
    }

    public DevisPublic refuser(String token) {
        Devis d = trouver(token);
        verifierQuOnPeutRepondre(d);
        d.setStatut(StatutDevis.REFUSE);
        d.setRepondueLe(Instant.now(clock));
        return DevisPublic.de(d);
    }

    private Devis trouver(String token) {
        return devis.findByTokenReponse(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lien invalide"));
    }

    /** Répondre n'est possible que sur un devis ENVOYE, jusqu'à la fin du jour de sa date de validité. */
    private void verifierQuOnPeutRepondre(Devis d) {
        boolean dateDepassee = LocalDate.now(clock).isAfter(d.getDateValidite());
        if (d.getStatut() != StatutDevis.ENVOYE || dateDepassee) {
            throw new ApiException(HttpStatus.CONFLICT, "Ce devis ne peut plus être accepté");
        }
    }
}
