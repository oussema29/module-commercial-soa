package com.example.campaign.controller;

import com.example.campaign.dto.PublicCibleReponse;
import com.example.campaign.dto.PublicCibleSaisie;
import com.example.campaign.service.PublicCibleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicCibleController {

    private final PublicCibleService service;

    public PublicCibleController(PublicCibleService service) {
        this.service = service;
    }

    @GetMapping("/campagnes/{campagneId}/publics")
    public List<PublicCibleReponse> lister(@PathVariable UUID campagneId) {
        return service.lister(campagneId);
    }

    @PostMapping("/campagnes/{campagneId}/publics")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicCibleReponse ajouter(@PathVariable UUID campagneId, @Valid @RequestBody PublicCibleSaisie saisie) {
        return service.ajouter(campagneId, saisie);
    }

    @DeleteMapping("/publics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
