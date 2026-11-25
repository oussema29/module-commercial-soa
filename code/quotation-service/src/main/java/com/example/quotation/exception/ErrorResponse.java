package com.example.quotation.exception;

import java.time.Instant;

/** Le corps JSON renvoyé en cas d'erreur (le même dans les trois services) : { code, message, timestamp }. */
public record ErrorResponse(int code, String message, Instant timestamp) {
}
