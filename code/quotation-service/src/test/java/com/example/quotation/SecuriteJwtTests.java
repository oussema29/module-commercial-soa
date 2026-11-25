package com.example.quotation;

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
import com.example.quotation.client.ClientCustomer;
import com.example.quotation.client.CustomerClient;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Mode jwt : sans jeton 401, mauvais rôle 403, bon rôle accepté ; routes publiques sans jeton. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.security.mode=jwt", "app.security.jwt-secret=secret-de-test-secret-de-test-0123456789"})
class SecuriteJwtTests {

    static final String SECRET = "secret-de-test-secret-de-test-0123456789";

    @Autowired
    MockMvc mvc;

    @MockBean
    CustomerClient customer;

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
        mvc.perform(get("/devis")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/devis").header("Authorization", jeton("COMMERCIAL", UUID.randomUUID().toString(),
                Instant.now().plusSeconds(600), "un-autre-secret-un-autre-secret-0123456789")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/devis").header("Authorization", jeton("COMMERCIAL", UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60), SECRET)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/devis").header("Authorization", "Bearer pas-un-jeton")).andExpect(status().isUnauthorized());
    }

    @Test
    void lAdministrateurOuLeBonRoleEstAccepte() throws Exception {
        mvc.perform(get("/devis").header("Authorization", jeton("COMMERCIAL")))
                .andExpect(status().isOk());
    }

    @Test
    void unRoleNonAutoriseEst403AvecLeFormatDErreurDuContrat() throws Exception {
        mvc.perform(get("/devis").header("Authorization", jeton("MARKETING")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void lesRoutesPubliquesNExigentAucunJeton() throws Exception {
        mvc.perform(get("/public/reponse/jeton-inconnu")).andExpect(status().isNotFound());
    }

    @Test
    void lesAutresRolesSelonLeContrat() throws Exception {
        // le catalogue : l'administrateur écrit, le commercial lit seulement
        mvc.perform(post("/taxes").header("Authorization", jeton("COMMERCIAL")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tauxTVA\":19}")).andExpect(status().isForbidden());
        mvc.perform(post("/taxes").header("Authorization", jeton("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tauxTVA\":19}")).andExpect(status().isCreated());
        // le marketing lit les produits pour les citer dans un contenu, mais ne lit pas les taxes
        mvc.perform(get("/produits").header("Authorization", jeton("MARKETING"))).andExpect(status().isOk());
        mvc.perform(get("/taxes").header("Authorization", jeton("MARKETING"))).andExpect(status().isForbidden());
    }

    @Test
    void lAuteurDUnDevisEstLIdentifiantDuJeton() throws Exception {
        UUID clientId = UUID.randomUUID();
        org.mockito.Mockito.when(customer.trouver(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ClientCustomer(clientId, "Atlas", null, "a@atlas.tn", "CLIENT"));
        String sub = UUID.randomUUID().toString();
        mvc.perform(post("/devis").header("Authorization", jeton("COMMERCIAL", sub, Instant.now().plusSeconds(600), SECRET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + java.time.LocalDate.now().plusDays(5) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creePar").value(sub));
    }
}
