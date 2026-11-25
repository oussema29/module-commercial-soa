package com.example.quotation.controller;

import com.example.quotation.dto.TaxeReponse;
import com.example.quotation.dto.TaxeSaisie;
import com.example.quotation.service.TaxeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/taxes")
public class TaxeController {

    private final TaxeService service;

    public TaxeController(TaxeService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaxeReponse> lister() {
        return service.lister();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaxeReponse creer(@Valid @RequestBody TaxeSaisie saisie) {
        return service.creer(saisie);
    }

    @PutMapping("/{id}")
    public TaxeReponse modifier(@PathVariable UUID id, @Valid @RequestBody TaxeSaisie saisie) {
        return service.modifier(id, saisie);
    }
}
