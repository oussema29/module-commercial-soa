package com.example.customer.controller;

import com.example.customer.dto.InteractionReponse;
import com.example.customer.dto.InteractionSaisie;
import com.example.customer.service.InteractionService;
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
public class InteractionController {

    private final InteractionService service;

    public InteractionController(InteractionService service) {
        this.service = service;
    }

    @GetMapping("/clients/{clientId}/interactions")
    public List<InteractionReponse> lister(@PathVariable UUID clientId) {
        return service.lister(clientId);
    }

    @PostMapping("/clients/{clientId}/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public InteractionReponse ajouter(@PathVariable UUID clientId, @Valid @RequestBody InteractionSaisie saisie) {
        return service.ajouter(clientId, saisie);
    }

    @PutMapping("/interactions/{id}")
    public InteractionReponse modifier(@PathVariable UUID id, @Valid @RequestBody InteractionSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @DeleteMapping("/interactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
