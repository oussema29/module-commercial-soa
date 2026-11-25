package com.example.quotation.exception;

import org.springframework.http.HttpStatus;

/**
 * Erreur que le service lance quand une règle n'est pas respectée.
 * Exemple : throw new ApiException(HttpStatus.NOT_FOUND, "Devis introuvable");
 * Codes utilisés : 404 introuvable, 409 état incompatible, 422 donnée invalide, 503 service indisponible.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
