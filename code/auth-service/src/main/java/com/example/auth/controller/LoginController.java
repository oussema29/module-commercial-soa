package com.example.auth.controller;

import com.example.auth.dto.LoginReponse;
import com.example.auth.dto.LoginRequest;
import com.example.auth.service.JetonService;
import com.example.auth.service.Utilisateur;
import com.example.auth.service.UtilisateurService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    private final UtilisateurService utilisateurs;
    private final JetonService jetons;

    public LoginController(UtilisateurService utilisateurs, JetonService jetons) {
        this.utilisateurs = utilisateurs;
        this.jetons = jetons;
    }

    /** Vérifie l'email et le mot de passe, puis renvoie un JWT. Mauvais identifiants : 401 « Identifiants invalides ». */
    @PostMapping("/login")
    public LoginReponse login(@Valid @RequestBody LoginRequest requete) {
        Utilisateur u = utilisateurs.authentifier(requete.email(), requete.motDePasse());
        return new LoginReponse(jetons.creer(u), "Bearer", jetons.dureeSecondes(), u.role(), u.id());
    }
}
