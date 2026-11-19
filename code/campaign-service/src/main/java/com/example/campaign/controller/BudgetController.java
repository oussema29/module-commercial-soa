package com.example.campaign.controller;

import com.example.campaign.dto.BudgetReponse;
import com.example.campaign.dto.BudgetSaisie;
import com.example.campaign.service.BudgetService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campagnes/{campagneId}/budget")
public class BudgetController {

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping
    public BudgetReponse consulter(@PathVariable UUID campagneId) {
        return service.consulter(campagneId);
    }

    @PutMapping
    public BudgetReponse definirPrevu(@PathVariable UUID campagneId, @Valid @RequestBody BudgetSaisie saisie) {
        return service.definirPrevu(campagneId, saisie);
    }
}
