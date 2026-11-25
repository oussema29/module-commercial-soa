package com.example.quotation.controller;

import com.example.quotation.dto.DevisPublic;
import com.example.quotation.service.ReponsePubliqueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Routes sans identité : le client répond avec le jeton de son lien. */
@RestController
@RequestMapping("/public/reponse")
public class PublicController {

    private final ReponsePubliqueService service;

    public PublicController(ReponsePubliqueService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    public DevisPublic consulter(@PathVariable String token) {
        return service.consulter(token);
    }

    @PostMapping("/{token}/accepter")
    public DevisPublic accepter(@PathVariable String token) {
        return service.accepter(token);
    }

    @PostMapping("/{token}/refuser")
    public DevisPublic refuser(@PathVariable String token) {
        return service.refuser(token);
    }
}
