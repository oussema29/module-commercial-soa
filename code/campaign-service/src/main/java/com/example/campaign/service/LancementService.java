package com.example.campaign.service;

import com.example.campaign.client.CustomerClient;
import com.example.campaign.client.DestinataireCustomer;
import com.example.campaign.dto.EchecEnvoi;
import com.example.campaign.dto.ResultatEnvoi;
import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.ContenuPublicitaire;
import com.example.campaign.entity.InvitationClient;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.entity.TypePublic;
import com.example.campaign.exception.ApiException;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.InvitationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lancement d'une campagne (lien I2 : lecture de Customer) et renvoi des invitations en échec. */
@Service
@Transactional
public class LancementService {

    private final CampagneRepository campagnes;
    private final InvitationRepository invitations;
    private final CustomerClient customer;
    private final EnvoiEmail envoiEmail;
    private final CoherenceCanalPublic coherence;
    private final Clock clock;
    private final String interfaceUrl;

    public LancementService(CampagneRepository campagnes, InvitationRepository invitations, CustomerClient customer,
                            EnvoiEmail envoiEmail, CoherenceCanalPublic coherence, Clock clock,
                            @Value("${app.interface-url}") String interfaceUrl) {
        this.campagnes = campagnes;
        this.invitations = invitations;
        this.customer = customer;
        this.envoiEmail = envoiEmail;
        this.coherence = coherence;
        this.clock = clock;
        this.interfaceUrl = interfaceUrl;
    }

    public ResultatEnvoi lancer(UUID id) {
        Campagne c = trouver(id);
        if (c.getStatut() != StatutCampagne.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Seule une campagne en brouillon peut être lancée");
        }
        // 1. Toutes les vérifications, AVANT tout appel : en cas d'échec la campagne reste BROUILLON et rien n'est créé
        if (c.getCanaux().isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Il faut au moins un canal pour lancer la campagne");
        }
        if (c.getContenus().isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Il faut au moins un contenu pour lancer la campagne");
        }
        if (c.getPerformance().getMontantPrevu() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Le budget prévu doit être défini (0 est accepté)");
        }
        coherence.verifier(c);
        LocalDate aujourdhui = LocalDate.now(clock);
        if (aujourdhui.isBefore(c.getDateDebut())) {
            throw new ApiException(HttpStatus.CONFLICT, "La date de début n'est pas encore atteinte");
        }
        if (aujourdhui.isAfter(c.getDateFin())) {
            throw new ApiException(HttpStatus.CONFLICT, "La date de fin est dépassée");
        }

        // 2. Lecture de Customer, seulement s'il existe un public de clients existants (503 ou 422 sinon : rien n'est écrit)
        List<DestinataireCustomer> destinataires = lireDestinataires(c);

        // 3. Une invitation à jeton par client, et l'envoi de l'email (un envoi en échec ne bloque pas le lancement)
        List<EchecEnvoi> echecs = new ArrayList<>();
        int reussis = 0;
        for (DestinataireCustomer d : destinataires) {
            InvitationClient inv = new InvitationClient();
            inv.setCampagne(c);
            inv.setClientId(d.clientId());
            inv.setJeton(JetonInvitation.generer());
            c.getInvitations().add(inv);
            if (envoyer(c, inv, d)) {
                reussis++;
            } else {
                echecs.add(new EchecEnvoi(d.clientId(), inv.getErreurEnvoi()));
            }
        }

        // 4. La liste peut être vide (public externe seul, ou catégorie sans client) : la campagne devient ACTIVE quand même
        c.setStatut(StatutCampagne.ACTIVE);
        return new ResultatEnvoi(reussis, echecs);
    }

    /** Renvoie seulement les invitations en échec, sur une campagne ACTIVE. Ne renvoie jamais une invitation déjà envoyée. */
    public ResultatEnvoi renvoyer(UUID id) {
        Campagne c = trouver(id);
        if (c.getStatut() != StatutCampagne.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "Les invitations ne se renvoient que sur une campagne active");
        }
        // On relit Customer : un client devenu INACTIF n'est plus renvoyé, et un email corrigé est repris
        Map<UUID, DestinataireCustomer> actuels = new LinkedHashMap<>();
        lireDestinataires(c).forEach(d -> actuels.put(d.clientId(), d));

        List<EchecEnvoi> echecs = new ArrayList<>();
        int reussis = 0;
        for (InvitationClient inv : invitations.findByCampagneIdAndEnvoyeLeIsNullAndErreurEnvoiIsNotNull(id)) {
            DestinataireCustomer d = actuels.get(inv.getClientId());
            if (d == null) {
                continue;
            }
            if (envoyer(c, inv, d)) {
                reussis++;
            } else {
                echecs.add(new EchecEnvoi(d.clientId(), inv.getErreurEnvoi()));
            }
        }
        return new ResultatEnvoi(reussis, echecs);
    }

    // ---- méthodes internes

    private Campagne trouver(UUID id) {
        return campagnes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
    }

    /** Customer n'est appelé que s'il existe un public CLIENTS_EXISTANTS ; un même client n'apparaît qu'une fois. */
    private List<DestinataireCustomer> lireDestinataires(Campagne c) {
        Map<UUID, DestinataireCustomer> parClient = new LinkedHashMap<>();
        c.getPublics().stream()
                .filter(p -> p.getType() == TypePublic.CLIENTS_EXISTANTS)
                .forEach(p -> customer.destinataires(p.getCategorieClientId()).forEach(d -> parClient.putIfAbsent(d.clientId(), d)));
        return new ArrayList<>(parClient.values());
    }

    /** Envoie l'email d'invitation ; renvoie true si l'envoi a réussi. Un échec est noté dans l'invitation. */
    private boolean envoyer(Campagne c, InvitationClient inv, DestinataireCustomer d) {
        ContenuPublicitaire contenu = c.getContenus().get(0);
        String lien = interfaceUrl + "/campagnes/invitation/" + inv.getJeton();
        String texte = "Bonjour " + d.nom() + ",\n\n" + contenu.getTitre() + "\n"
                + (contenu.getTexte() == null ? "" : contenu.getTexte() + "\n")
                + "\nJe suis intéressé : " + lien + "\n";
        try {
            envoiEmail.envoyer(d.email(), contenu.getTitre(), texte, null);
            inv.setEnvoyeLe(Instant.now(clock));
            inv.setErreurEnvoi(null);
            return true;
        } catch (EmailException e) {
            inv.setEnvoyeLe(null);
            inv.setErreurEnvoi(e.getMessage());
            return false;
        }
    }
}
