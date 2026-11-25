package com.example.customer.service;

import com.example.customer.dto.ContactReponse;
import com.example.customer.dto.ContactSaisie;
import com.example.customer.entity.Client;
import com.example.customer.entity.Contact;
import com.example.customer.exception.ApiException;
import com.example.customer.repository.ClientRepository;
import com.example.customer.repository.ContactRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContactService {

    private final ContactRepository contacts;
    private final ClientRepository clients;

    public ContactService(ContactRepository contacts, ClientRepository clients) {
        this.contacts = contacts;
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public List<ContactReponse> lister(UUID clientId) {
        verifierClient(clientId);
        return contacts.findByClientId(clientId).stream().map(ContactReponse::de).toList();
    }

    public ContactReponse ajouter(UUID clientId, ContactSaisie saisie) {
        Client client = verifierClient(clientId);
        Contact c = new Contact();
        c.setClient(client);
        remplir(c, saisie);
        return ContactReponse.de(contacts.save(c));
    }

    public ContactReponse modifier(UUID id, ContactSaisie saisie) {
        Contact c = contacts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Contact introuvable"));
        remplir(c, saisie);
        return ContactReponse.de(c);
    }

    public void supprimer(UUID id) {
        Contact c = contacts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Contact introuvable"));
        contacts.delete(c);
    }

    private Client verifierClient(UUID clientId) {
        return clients.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Client introuvable"));
    }

    private void remplir(Contact c, ContactSaisie s) {
        c.setNom(s.nom());
        c.setPrenom(s.prenom());
        c.setFonction(s.fonction());
        c.setEmail(s.email());
        c.setTelephone(s.telephone());
    }
}
