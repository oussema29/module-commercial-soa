package com.example.auth.service;

import java.util.UUID;

/** Un utilisateur connu du service : seule l'EMPREINTE du mot de passe est conservée. */
public record Utilisateur(UUID id, String email, String empreinteMotDePasse, String role) {
}
