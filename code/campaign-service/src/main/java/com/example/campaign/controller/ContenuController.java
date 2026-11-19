package com.example.campaign.controller;

import com.example.campaign.dto.ContenuReponse;
import com.example.campaign.dto.ContenuSaisie;
import com.example.campaign.dto.ProduitMisEnAvant;
import com.example.campaign.service.ContenuService;
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
public class ContenuController {

    private final ContenuService service;

    public ContenuController(ContenuService service) {
        this.service = service;
    }

    @GetMapping("/campagnes/{campagneId}/contenus")
    public List<ContenuReponse> lister(@PathVariable UUID campagneId) {
        return service.lister(campagneId);
    }

    @PostMapping("/campagnes/{campagneId}/contenus")
    @ResponseStatus(HttpStatus.CREATED)
    public ContenuReponse ajouter(@PathVariable UUID campagneId, @Valid @RequestBody ContenuSaisie saisie) {
        return service.ajouter(campagneId, saisie);
    }

    @PutMapping("/contenus/{id}")
    public ContenuReponse modifier(@PathVariable UUID id, @Valid @RequestBody ContenuSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @DeleteMapping("/contenus/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }

    @PostMapping("/contenus/{id}/produits")
    @ResponseStatus(HttpStatus.CREATED)
    public ContenuReponse ajouterProduit(@PathVariable UUID id, @Valid @RequestBody ProduitMisEnAvant saisie) {
        return service.ajouterProduit(id, saisie);
    }

    @DeleteMapping("/contenus/{id}/produits/{produitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirerProduit(@PathVariable UUID id, @PathVariable UUID produitId) {
        service.retirerProduit(id, produitId);
    }
}
