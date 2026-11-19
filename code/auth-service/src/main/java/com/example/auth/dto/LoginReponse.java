package com.example.auth.dto;

import java.util.UUID;

/** Le jeton est à envoyer ensuite dans l'en-tête « Authorization: Bearer <jeton> » aux trois autres services. */
public record LoginReponse(String jeton, String type, long expireDans, String role, UUID utilisateurId) {
}
