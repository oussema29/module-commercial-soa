package com.example.campaign.controller;

import com.example.campaign.dto.CampagneReponse;
import com.example.campaign.dto.CampagneSaisie;
import com.example.campaign.dto.PageResponse;
import com.example.campaign.service.CampagneService;
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
@RequestMapping("/campagnes")
public class CampagneController {

    private final CampagneService service;

    public CampagneController(CampagneService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampagneReponse creer(@Valid @RequestBody CampagneSaisie saisie) {
        return service.creer(saisie);
    }

    @GetMapping
    public PageResponse<CampagneReponse> lister(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int taille) {
        return service.lister(page, taille);
    }

    @GetMapping("/{id}")
    public CampagneReponse consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    @PutMapping("/{id}")
    public CampagneReponse modifier(@PathVariable UUID id, @Valid @RequestBody CampagneSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }

    @PostMapping("/{id}/suspendre")
    public CampagneReponse suspendre(@PathVariable UUID id) {
        return service.suspendre(id);
    }

    @PostMapping("/{id}/reprendre")
    public CampagneReponse reprendre(@PathVariable UUID id) {
        return service.reprendre(id);
    }

    @PostMapping("/{id}/arreter")
    public CampagneReponse arreter(@PathVariable UUID id) {
        return service.arreter(id);
    }
}
