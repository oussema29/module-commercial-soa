package com.example.campaign.controller;

import com.example.campaign.dto.ResultatEnvoi;
import com.example.campaign.service.LancementService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campagnes/{id}")
public class LancementController {

    private final LancementService service;

    public LancementController(LancementService service) {
        this.service = service;
    }

    @PostMapping("/lancer")
    public ResultatEnvoi lancer(@PathVariable UUID id) {
        return service.lancer(id);
    }

    @PostMapping("/invitations/renvoyer")
    public ResultatEnvoi renvoyer(@PathVariable UUID id) {
        return service.renvoyer(id);
    }
}
