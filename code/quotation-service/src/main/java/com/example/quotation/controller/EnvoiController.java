package com.example.quotation.controller;

import com.example.quotation.dto.DevisReponse;
import com.example.quotation.service.EnvoiDevisService;
import com.example.quotation.service.FichierPdf;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devis")
public class EnvoiController {

    private final EnvoiDevisService service;

    public EnvoiController(EnvoiDevisService service) {
        this.service = service;
    }

    @PostMapping("/{id}/envoyer")
    public DevisReponse envoyer(@PathVariable UUID id) {
        return service.envoyer(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        FichierPdf fichier = service.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fichier.nom() + "\"")
                .body(fichier.contenu());
    }
}
