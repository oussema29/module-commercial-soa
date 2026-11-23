package com.example.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Mode jwt : la passerelle refuse (401) sans jeton valide, mais ne contrôle pas les rôles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"app.security.mode=jwt", "app.security.jwt-secret=secret-de-test-secret-de-test-0123456789"})
class SecuriteJwtTests {

    static final String SECRET = "secret-de-test-secret-de-test-0123456789";
    static Fournisseurs service;

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) throws IOException {
        service = new Fournisseurs("service");
        registre.add("CUSTOMER_URL", service::url);
        registre.add("QUOTATION_URL", service::url);
        registre.add("CAMPAIGN_URL", service::url);
        registre.add("AUTH_URL", service::url);
    }

    @Autowired
    WebTestClient web;

    static String jeton(String role, Instant expiration, String secret) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("00000000-0000-0000-0000-000000000001")
                .claim("role", role).expirationTime(Date.from(expiration)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + jwt.serialize();
    }

    @Test
    void sansJetonUneRouteProtegeeEst401AuFormatDuModule() {
        web.get().uri("/clients").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(401).jsonPath("$.message").exists();
    }

    @Test
    void laPageSwaggerEtLesDescriptionsDApiSontOuvertesSansJeton() {
        web.get().uri("/webjars/swagger-ui/index.html").exchange().expectStatus().isOk();
        web.get().uri("/docs/customer/v3/api-docs").exchange().expectStatus().isOk();
    }

    @Test
    void unJetonValideEstTransmisAuService() throws Exception {
        String jeton = jeton("MARKETING", Instant.now().plusSeconds(600), SECRET);
        web.get().uri("/campagnes").header("Authorization", jeton).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.autorisation").isEqualTo(jeton);
    }

    @Test
    void unJetonMalSigneExpireOuMalFormeEst401() throws Exception {
        web.get().uri("/clients").header("Authorization", jeton("ADMIN", Instant.now().plusSeconds(600), "un-autre-secret-un-autre-secret-0123456789"))
                .exchange().expectStatus().isUnauthorized();
        web.get().uri("/clients").header("Authorization", jeton("ADMIN", Instant.now().minusSeconds(60), SECRET))
                .exchange().expectStatus().isUnauthorized();
        web.get().uri("/clients").header("Authorization", "Bearer pas-un-jeton").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void laPasserelleNeControlePasLesRoles() throws Exception {
        // le rôle n'est pas vérifié ici : c'est le service qui répondra 403 si besoin
        web.get().uri("/clients").header("Authorization", jeton("MARKETING", Instant.now().plusSeconds(600), SECRET))
                .exchange().expectStatus().isOk();
    }

    @Test
    void lesRoutesPubliquesLoginEtSanteNExigentAucunJeton() {
        web.get().uri("/public/reponse/jeton123").exchange().expectStatus().isOk();
        web.get().uri("/public/campagnes/12").exchange().expectStatus().isOk();
        web.method(HttpMethod.POST).uri("/public/invitations/jeton123/reponse").exchange().expectStatus().isOk();
        web.get().uri("/login").exchange().expectStatus().isOk();
        // la santé n'est pas routée : la passerelle répond elle-même, sans jeton
        web.get().uri("/actuator/health").exchange().expectStatus().value(code -> {
            if (code == 401 || code == 403) {
                throw new AssertionError("la santé ne doit pas exiger de jeton");
            }
        });
    }
}
