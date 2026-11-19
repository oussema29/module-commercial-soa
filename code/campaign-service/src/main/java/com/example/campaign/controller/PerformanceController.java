package com.example.campaign.controller;

import com.example.campaign.dto.MesuresSaisie;
import com.example.campaign.dto.PerformanceReponse;
import com.example.campaign.service.PerformanceService;
import com.example.campaign.service.RapportPdfService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campagnes/{id}")
public class PerformanceController {

    private final PerformanceService performances;
    private final RapportPdfService rapports;

    public PerformanceController(PerformanceService performances, RapportPdfService rapports) {
        this.performances = performances;
        this.rapports = rapports;
    }

    @PutMapping("/mesures")
    public PerformanceReponse mesures(@PathVariable UUID id, @Valid @RequestBody MesuresSaisie saisie) {
        return performances.remplacerMesures(id, saisie);
    }

    @GetMapping("/performance")
    public PerformanceReponse performance(@PathVariable UUID id) {
        return performances.consulter(id);
    }

    @GetMapping("/rapport")
    public ResponseEntity<byte[]> rapport(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"rapport-campagne.pdf\"")
                .body(rapports.generer(id));
    }
}
