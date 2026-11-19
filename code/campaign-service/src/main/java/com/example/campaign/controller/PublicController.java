package com.example.campaign.controller;

import com.example.campaign.dto.CampagnePublique;
import com.example.campaign.dto.FormulaireProspect;
import com.example.campaign.service.ReponsePubliqueService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Routes sans identité (préfixe /public). */
@RestController
@RequestMapping("/public")
public class PublicController {

    private final ReponsePubliqueService service;

    public PublicController(ReponsePubliqueService service) {
        this.service = service;
    }

    @GetMapping("/campagnes/{id}")
    public CampagnePublique consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    @PostMapping("/campagnes/{id}/reponse")
    @ResponseStatus(HttpStatus.CREATED)
    public void formulaire(@PathVariable UUID id, @Valid @RequestBody FormulaireProspect formulaire) {
        service.repondreFormulaire(id, formulaire);
    }

    @PostMapping("/invitations/{jeton}/reponse")
    public void interesse(@PathVariable String jeton) {
        service.repondreInvitation(jeton);
    }
}
