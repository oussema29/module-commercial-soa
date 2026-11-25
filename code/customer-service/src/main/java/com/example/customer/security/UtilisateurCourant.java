package com.example.customer.security;

import com.example.customer.exception.ApiException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Seul endroit qui connaît l'utilisateur courant. Avec un JWT : l'identifiant est le champ « sub » du jeton.
 * Sans JWT (mode none) : la valeur fixe app.default-user-id.
 */
@Component
public class UtilisateurCourant {

    private final UUID parDefaut;

    public UtilisateurCourant(@Value("${app.default-user-id}") UUID parDefaut) {
        this.parDefaut = parDefaut;
    }

    public UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Le jeton ne contient pas d'identifiant d'utilisateur valide");
            }
        }
        return parDefaut;
    }
}
