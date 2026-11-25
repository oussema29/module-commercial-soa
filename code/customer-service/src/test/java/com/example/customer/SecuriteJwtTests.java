package com.example.customer;

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
        mvc.perform(get("/clients")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/clients").header("Authorization", jeton("COMMERCIAL", UUID.randomUUID().toString(),
                Instant.now().plusSeconds(600), "un-autre-secret-un-autre-secret-0123456789")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/clients").header("Authorization", jeton("COMMERCIAL", UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60), SECRET)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/clients").header("Authorization", "Bearer pas-un-jeton")).andExpect(status().isUnauthorized());
    }

    @Test
    void lAdministrateurOuLeBonRoleEstAccepte() throws Exception {
        mvc.perform(get("/clients").header("Authorization", jeton("COMMERCIAL")))
                .andExpect(status().isOk());
    }

    @Test
    void unRoleNonAutoriseEst403AvecLeFormatDErreurDuContrat() throws Exception {
        mvc.perform(get("/clients").header("Authorization", jeton("MARKETING")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void lesRoutesPubliquesNExigentAucunJeton() throws Exception {
        mvc.perform(get("/public/inconnu")).andExpect(status().isNotFound());
    }

    @Test
    void lesAutresRolesSelonLeContrat() throws Exception {
        // la liste réduite des destinataires est ouverte aux quatre rôles : MARKETING passe (404 = catégorie inconnue, pas 403)
        mvc.perform(get("/clients/destinataires?categorieId=" + UUID.randomUUID()).header("Authorization", jeton("MARKETING")))
                .andExpect(status().isNotFound());
        // la suppression d'un client est réservée à l'administrateur
        mvc.perform(delete("/clients/" + UUID.randomUUID()).header("Authorization", jeton("COMMERCIAL"))).andExpect(status().isForbidden());
        mvc.perform(delete("/clients/" + UUID.randomUUID()).header("Authorization", jeton("ADMIN"))).andExpect(status().isNotFound());
        // le responsable consulte tout mais ne modifie rien
        mvc.perform(post("/clients").header("Authorization", jeton("RESPONSABLE")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"X\",\"email\":\"x@x.tn\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lAuteurDUneInteractionEstLIdentifiantDuJeton() throws Exception {
        String sub = UUID.randomUUID().toString();
        String auth = jeton("COMMERCIAL", sub, Instant.now().plusSeconds(600), SECRET);
        String json = mvc.perform(post("/clients").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"" + UUID.randomUUID() + "@atlas.tn\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String clientId = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        mvc.perform(post("/clients/" + clientId + "/interactions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPEL\",\"date\":\"2026-11-18T10:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.auteurId").value(sub));
    }
}
