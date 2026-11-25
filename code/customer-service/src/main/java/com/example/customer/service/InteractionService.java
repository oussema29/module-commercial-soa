package com.example.customer.service;

import com.example.customer.dto.InteractionReponse;
import com.example.customer.dto.InteractionSaisie;
import com.example.customer.entity.Client;
import com.example.customer.entity.Interaction;
import com.example.customer.exception.ApiException;
import com.example.customer.repository.ClientRepository;
import com.example.customer.repository.InteractionRepository;
import java.util.List;
import com.example.customer.security.UtilisateurCourant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InteractionService {

    private final InteractionRepository interactions;
    private final ClientRepository clients;
    private final UtilisateurCourant utilisateur;

    public InteractionService(InteractionRepository interactions, ClientRepository clients,
                              UtilisateurCourant utilisateur) {
        this.interactions = interactions;
        this.clients = clients;
        this.utilisateur = utilisateur;
    }

    @Transactional(readOnly = true)
    public List<InteractionReponse> lister(UUID clientId) {
        verifierClient(clientId);
        return interactions.findByClientIdOrderByDateDesc(clientId).stream().map(InteractionReponse::de).toList();
    }

    public InteractionReponse ajouter(UUID clientId, InteractionSaisie saisie) {
        Client client = verifierClient(clientId);
        Interaction i = new Interaction();
        i.setClient(client);
        i.setAuteurId(utilisateur.id());
        remplir(i, saisie);
        return InteractionReponse.de(interactions.save(i));
    }

    public InteractionReponse modifier(UUID id, InteractionSaisie saisie) {
        Interaction i = trouver(id);
        remplir(i, saisie);   // l'auteur ne change pas
        return InteractionReponse.de(i);
    }

    public void supprimer(UUID id) {
        interactions.delete(trouver(id));
    }

    private Interaction trouver(UUID id) {
        return interactions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Interaction introuvable"));
    }

    private Client verifierClient(UUID clientId) {
        return clients.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Client introuvable"));
    }

    private void remplir(Interaction i, InteractionSaisie s) {
        i.setType(s.type());
        i.setDate(s.date());
        i.setDescription(s.description());
    }
}
