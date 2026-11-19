package com.example.campaign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Mode jwt : sans jeton 401, mauvais rôle 403, bon rôle accepté ; routes publiques sans jeton. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.security.mode=jwt", "app.security.jwt-secret=secret-de-test-secret-de-test-0123456789"})
class SecuriteJwtTests {

    static final String SECRET = "secret-de-test-secret-de-test-0123456789";

    @Autowired
    MockMvc mvc;

    static String jeton(String role, String sub, Instant expiration, String secret) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(sub).claim("role", role).expirationTime(Date.from(expiration)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + jwt.serialize();
    }

    static String jeton(String role) throws Exception {
        return jeton(role, UUID.randomUUID().toString(), Instant.now().plusSeconds(600), SECRET);
    }

    @Test
    void sansJetonOuAvecUnJetonInvalideOuExpireEst401() throws Exception {
        mvc.perform(get("/campagnes")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/campagnes").header("Authorization", jeton("RESPONSABLE", UUID.randomUUID().toString(),
                Instant.now().plusSeconds(600), "un-autre-secret-un-autre-secret-0123456789")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/campagnes").header("Authorization", jeton("RESPONSABLE", UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60), SECRET)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/campagnes").header("Authorization", "Bearer pas-un-jeton")).andExpect(status().isUnauthorized());
    }

    @Test
    void lAdministrateurOuLeBonRoleEstAccepte() throws Exception {
        mvc.perform(get("/campagnes").header("Authorization", jeton("RESPONSABLE")))
                .andExpect(status().isOk());
    }

    @Test
    void unRoleNonAutoriseEst403AvecLeFormatDErreurDuContrat() throws Exception {
        mvc.perform(get("/campagnes/00000000-0000-0000-0000-000000000099/budget").header("Authorization", jeton("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void lesRoutesPubliquesNExigentAucunJeton() throws Exception {
        mvc.perform(get("/public/campagnes/00000000-0000-0000-0000-000000000099")).andExpect(status().isNotFound());
    }

    static final String CORPS = "{\"nom\":\"Offre\",\"objectif\":\"VENTES\",\"dateDebut\":\"2026-11-18\",\"dateFin\":\"2026-12-18\"}";

    @Test
    void lesAutresRolesSelonLeContrat() throws Exception {
        // le commercial consulte les campagnes (pour choisir le campagneId d'un devis) mais ne les crée pas
        mvc.perform(get("/campagnes").header("Authorization", jeton("COMMERCIAL"))).andExpect(status().isOk());
        mvc.perform(post("/campagnes").header("Authorization", jeton("COMMERCIAL")).contentType(MediaType.APPLICATION_JSON)
                .content(CORPS)).andExpect(status().isForbidden());
        // seul le marketing crée une campagne
        mvc.perform(post("/campagnes").header("Authorization", jeton("RESPONSABLE")).contentType(MediaType.APPLICATION_JSON)
                .content(CORPS)).andExpect(status().isForbidden());
        // l'administrateur configure les canaux
        mvc.perform(post("/canaux").header("Authorization", jeton("MARKETING")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Email\",\"type\":\"EMAIL\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/canaux").header("Authorization", jeton("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Email\",\"type\":\"EMAIL\"}")).andExpect(status().isCreated());
    }

    @Test
    void lAuteurDUneCampagneEstLIdentifiantDuJeton() throws Exception {
        String sub = UUID.randomUUID().toString();
        mvc.perform(post("/campagnes").header("Authorization", jeton("MARKETING", sub, Instant.now().plusSeconds(600), SECRET))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creePar").value(sub));
    }
}
