package com.example.auth.service;

import com.example.auth.config.AuthProperties;
import com.example.auth.exception.ApiException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UtilisateurService {

    static final Set<String> ROLES = Set.of("ADMIN", "COMMERCIAL", "MARKETING", "RESPONSABLE");

    private final Map<String, Utilisateur> parEmail = new HashMap<>();
    private final PasswordEncoder encodeur;
    // Empreinte d'un mot de passe qui n'existe pas : pour qu'un email inconnu prenne le même temps qu'un mauvais mot de passe
    private final String empreinteFactice;

    public UtilisateurService(AuthProperties proprietes, PasswordEncoder encodeur) {
        this.encodeur = encodeur;
        this.empreinteFactice = encodeur.encode("mot-de-passe-factice");
        for (AuthProperties.UtilisateurConfig u : proprietes.getUtilisateurs()) {
            String role = u.getRole() == null ? "" : u.getRole().toUpperCase();
            if (!ROLES.contains(role)) {
                throw new IllegalStateException("Rôle inconnu pour " + u.getEmail() + " : " + u.getRole());
            }
            // Le mot de passe de démonstration est chiffré tout de suite : seul le résultat est gardé
            parEmail.put(normaliser(u.getEmail()),
                    new Utilisateur(u.getId(), u.getEmail(), encodeur.encode(u.getMotDePasse()), role));
        }
    }

    /** Renvoie l'utilisateur si l'email et le mot de passe sont bons ; sinon 401, avec le même message dans tous les cas. */
    public Utilisateur authentifier(String email, String motDePasse) {
        Utilisateur utilisateur = parEmail.get(normaliser(email));
        String empreinte = utilisateur == null ? empreinteFactice : utilisateur.empreinteMotDePasse();
        boolean bonMotDePasse = encodeur.matches(motDePasse, empreinte);
        if (utilisateur == null || !bonMotDePasse) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }
        return utilisateur;
    }

    private static String normaliser(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
