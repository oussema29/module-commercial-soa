package com.example.customer.controller;

import com.example.customer.dto.ContactReponse;
import com.example.customer.dto.ContactSaisie;
import com.example.customer.service.ContactService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContactController {

    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    @GetMapping("/clients/{clientId}/contacts")
    public List<ContactReponse> lister(@PathVariable UUID clientId) {
        return service.lister(clientId);
    }

    @PostMapping("/clients/{clientId}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactReponse ajouter(@PathVariable UUID clientId, @Valid @RequestBody ContactSaisie saisie) {
        return service.ajouter(clientId, saisie);
    }

    @PutMapping("/contacts/{id}")
    public ContactReponse modifier(@PathVariable UUID id, @Valid @RequestBody ContactSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @DeleteMapping("/contacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
