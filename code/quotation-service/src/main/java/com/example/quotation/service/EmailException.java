package com.example.quotation.service;

/** L'envoi d'un email a échoué. */
public class EmailException extends RuntimeException {

    public EmailException(String message) {
        super(message);
    }
}
