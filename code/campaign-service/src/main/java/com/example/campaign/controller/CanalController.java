package com.example.campaign.controller;

import com.example.campaign.dto.CanalAssociation;
import com.example.campaign.dto.CanalReponse;
import com.example.campaign.dto.CanalSaisie;
import com.example.campaign.service.CanalService;
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
public class CanalController {

    private final CanalService service;

    public CanalController(CanalService service) {
        this.service = service;
    }

    @GetMapping("/canaux")
    public List<CanalReponse> lister() {
        return service.lister();
    }

    @PostMapping("/canaux")
    @ResponseStatus(HttpStatus.CREATED)
    public CanalReponse creer(@Valid @RequestBody CanalSaisie saisie) {
        return service.creer(saisie);
    }

    @PostMapping("/campagnes/{campagneId}/canaux")
    @ResponseStatus(HttpStatus.CREATED)
    public CanalReponse associer(@PathVariable UUID campagneId, @Valid @RequestBody CanalAssociation saisie) {
        return service.associer(campagneId, saisie);
    }

    @DeleteMapping("/campagnes/{campagneId}/canaux/{canalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirer(@PathVariable UUID campagneId, @PathVariable UUID canalId) {
        service.retirer(campagneId, canalId);
    }
}
