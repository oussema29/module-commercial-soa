package com.example.quotation.controller;

import com.example.quotation.dto.PageResponse;
import com.example.quotation.dto.ProduitReponse;
import com.example.quotation.dto.ProduitSaisie;
import com.example.quotation.service.ProduitService;
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
@RequestMapping("/produits")
public class ProduitController {

    private final ProduitService service;

    public ProduitController(ProduitService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProduitReponse> lister(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int taille) {
        return service.lister(page, taille);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProduitReponse creer(@Valid @RequestBody ProduitSaisie saisie) {
        return service.creer(saisie);
    }

    @GetMapping("/{id}")
    public ProduitReponse consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    @PutMapping("/{id}")
    public ProduitReponse modifier(@PathVariable UUID id, @Valid @RequestBody ProduitSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
