package com.example.customer.service;

import com.example.customer.entity.Client;
import com.example.customer.entity.Contact;
import com.example.customer.entity.EvenementTraite;
import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import com.example.customer.messaging.ProspectGenereEvent;
import com.example.customer.repository.ClientRepository;
import com.example.customer.repository.ContactRepository;
import com.example.customer.repository.EvenementTraiteRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crée la fiche d'un nouveau prospect à partir de l'événement ProspectGenere (consommateur IDEMPOTENT). */
@Service
@Transactional
public class ProspectService {

    private static final Logger LOG = LoggerFactory.getLogger(ProspectService.class);

    private final ClientRepository clients;
    private final ContactRepository contacts;
    private final EvenementTraiteRepository traites;

    public ProspectService(ClientRepository clients, ContactRepository contacts, EvenementTraiteRepository traites) {
        this.clients = clients;
        this.contacts = contacts;
        this.traites = traites;
    }

    /** Renvoie true si une fiche a été créée. */
    public boolean traiter(ProspectGenereEvent e) {
        // 1. Déjà reçu : on ignore (le message est rejoué par RabbitMQ)
        if (traites.existsById(e.eventId())) {
            return false;
        }
        boolean cree = false;
        String emailNormalise = ClientService.normaliser(e.email());
        // 2. Un client a déjà cet email (casse et espaces ignorés) : on ne crée rien
        if (!clients.existsByEmailNormalise(emailNormalise)) {
            TypeClient type = TypeClient.valueOf(e.typeClient());
            Client c = new Client();
            c.setType(type);
            c.setStatut(StatutClient.PROSPECT);
            c.setNom(e.nom());
            c.setEmail(e.email().trim());
            c.setEmailNormalise(emailNormalise);
            c.setTelephone(e.telephone());
            c.setCampagneSourceId(e.campagneId());   // pas de catégorie : le commercial qualifiera le prospect
            clients.save(c);
            if (type == TypeClient.ENTREPRISE) {
                Contact contact = new Contact();
                contact.setClient(c);
                contact.setNom(e.nomContact() == null || e.nomContact().isBlank() ? e.nom() : e.nomContact());
                contact.setEmail(c.getEmail());
                contact.setTelephone(e.telephone());
                contacts.save(contact);
            }
            cree = true;
        } else {
            LOG.info("ProspectGenere {} ignoré : un client a déjà cet email", e.eventId());
        }
        // 3. On note l'événement DANS LA MÊME TRANSACTION : la fiche et la mémoire du traitement vont ensemble
        traites.save(new EvenementTraite(e.eventId(), Instant.now()));
        return cree;
    }
}
