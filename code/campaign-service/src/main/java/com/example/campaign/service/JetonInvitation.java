package com.example.campaign.service;

import java.security.SecureRandom;
import java.util.Base64;

/** Jeton du lien personnel envoyé à un client : 32 octets aléatoires, impossibles à deviner. */
public final class JetonInvitation {

    private static final SecureRandom ALEA = new SecureRandom();

    private JetonInvitation() {
    }

    public static String generer() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }
}
