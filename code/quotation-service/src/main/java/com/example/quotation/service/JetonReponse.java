package com.example.quotation.service;

import java.security.SecureRandom;
import java.util.Base64;

/** Jeton du lien envoyé au client : 32 octets aléatoires, impossibles à deviner. */
public final class JetonReponse {

    private static final SecureRandom ALEA = new SecureRandom();

    private JetonReponse() {
    }

    public static String generer() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }
}
