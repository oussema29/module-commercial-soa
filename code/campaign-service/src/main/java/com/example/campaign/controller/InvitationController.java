package com.example.campaign.controller;

import com.example.campaign.dto.InvitationReponse;
import com.example.campaign.dto.PageResponse;
import com.example.campaign.dto.ReponseClient;
import com.example.campaign.entity.FiltreEnvoi;
import com.example.campaign.service.InvitationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campagnes/{id}")
public class InvitationController {

    private final InvitationService service;

    public InvitationController(InvitationService service) {
        this.service = service;
    }

    @GetMapping("/invitations")
    public PageResponse<InvitationReponse> invitations(@PathVariable UUID id,
                                                       @RequestParam(required = false) FiltreEnvoi envoi,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int taille) {
        return service.lister(id, envoi, page, taille);
    }

    @GetMapping("/reponses")
    public PageResponse<ReponseClient> reponses(@PathVariable UUID id,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int taille) {
        return service.reponses(id, page, taille);
    }
}
