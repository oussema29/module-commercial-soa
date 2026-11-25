package com.example.quotation.controller;

import com.example.quotation.dto.DevisCreation;
import com.example.quotation.dto.DevisModification;
import com.example.quotation.dto.DevisReponse;
import com.example.quotation.dto.LigneModification;
import com.example.quotation.dto.LigneSaisie;
import com.example.quotation.dto.PageResponse;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.service.DevisService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devis")
public class DevisController {

    private final DevisService service;

    public DevisController(DevisService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DevisReponse creer(@Valid @RequestBody DevisCreation saisie) {
        return service.creer(saisie);
    }

    @GetMapping
    public PageResponse<DevisReponse> lister(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) StatutDevis statut,
            @RequestParam(required = false) UUID campagneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int taille) {
        return service.lister(clientId, statut, campagneId, page, taille);
    }

    @GetMapping("/{id}")
    public DevisReponse consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    @PutMapping("/{id}")
    public DevisReponse modifier(@PathVariable UUID id, @Valid @RequestBody DevisModification saisie) {
        return service.modifier(id, saisie);
    }

    @PostMapping("/{id}/lignes")
    @ResponseStatus(HttpStatus.CREATED)
    public DevisReponse ajouterLigne(@PathVariable UUID id, @Valid @RequestBody LigneSaisie saisie) {
        return service.ajouterLigne(id, saisie);
    }

    @PutMapping("/{id}/lignes/{ligneId}")
    public DevisReponse modifierLigne(@PathVariable UUID id, @PathVariable UUID ligneId,
                                      @Valid @RequestBody LigneModification saisie) {
        return service.modifierLigne(id, ligneId, saisie);
    }

    @DeleteMapping("/{id}/lignes/{ligneId}")
    public DevisReponse supprimerLigne(@PathVariable UUID id, @PathVariable UUID ligneId) {
        return service.supprimerLigne(id, ligneId);
    }

    @PostMapping("/{id}/annuler")
    public DevisReponse annuler(@PathVariable UUID id) {
        return service.annuler(id);
    }
}
