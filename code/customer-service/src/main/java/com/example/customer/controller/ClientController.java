package com.example.customer.controller;

import com.example.customer.dto.ChangementStatut;
import com.example.customer.dto.ClientCreation;
import com.example.customer.dto.ClientModification;
import com.example.customer.dto.ClientReponse;
import com.example.customer.dto.Destinataire;
import com.example.customer.dto.PageResponse;
import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import com.example.customer.service.ClientService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientReponse creer(@Valid @RequestBody ClientCreation saisie) {
        return service.creer(saisie);
    }

    @GetMapping
    public PageResponse<ClientReponse> rechercher(
            @RequestParam(required = false) TypeClient type,
            @RequestParam(required = false) StatutClient statut,
            @RequestParam(required = false) UUID categorieId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int taille) {
        return service.rechercher(type, statut, categorieId, q, page, taille);
    }

    @GetMapping("/destinataires")
    public PageResponse<Destinataire> destinataires(
            @RequestParam UUID categorieId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int taille) {
        return service.destinataires(categorieId, page, taille);
    }

    @GetMapping("/{id}")
    public ClientReponse consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    @PutMapping("/{id}")
    public ClientReponse modifier(@PathVariable UUID id, @Valid @RequestBody ClientModification saisie) {
        return service.modifier(id, saisie);
    }

    @PatchMapping("/{id}/statut")
    public ClientReponse changerStatut(@PathVariable UUID id, @Valid @RequestBody ChangementStatut changement) {
        return service.changerStatut(id, changement);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
