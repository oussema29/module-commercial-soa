package com.example.auth.service;

import com.example.auth.config.AuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fabrique le JWT (signature HS256, secret partagé avec les trois services) :
 * « sub » = identifiant de l'utilisateur, « role » = son rôle, « exp » = fin de validité.
 */
@Service
public class JetonService {

    private final byte[] secret;
    private final long dureeSecondes;
    private final Clock clock;

    public JetonService(@Value("${app.security.jwt-secret}") String secret, AuthProperties proprietes, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalStateException("app.security.jwt-secret doit contenir 32 caractères au moins");
        }
        this.dureeSecondes = proprietes.getDureeJetonSecondes();
        this.clock = clock;
    }

    public long dureeSecondes() {
        return dureeSecondes;
    }

    public String creer(Utilisateur utilisateur) {
        Instant maintenant = Instant.now(clock);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(utilisateur.id().toString())
                .claim("role", utilisateur.role())
                .issueTime(Date.from(maintenant))
                .expirationTime(Date.from(maintenant.plusSeconds(dureeSecondes)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Signature du jeton impossible", e);
        }
    }
}
