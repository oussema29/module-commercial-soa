package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests {

    static final String SECRET = "secret-de-developpement-secret-de-developpement-0123";

    @Autowired
    MockMvc mvc;
    @Autowired
    PasswordEncoder encodeur;

    String login(String email, String motDePasse) {
        return "{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}";
    }

    /** Se connecte et renvoie le corps de la réponse. */
    String connecter(String email, String motDePasse) throws Exception {
        return mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(login(email, motDePasse)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void lesQuatreUtilisateursObtiennentUnJetonAvecLeurRole() throws Exception {
        List<String[]> utilisateurs = List.of(
                new String[]{"admin@exemple.tn", "admin-demo-1", "ADMIN", "00000000-0000-0000-0000-0000000000a1"},
                new String[]{"commercial@exemple.tn", "commercial-demo-1", "COMMERCIAL", "00000000-0000-0000-0000-0000000000a2"},
                new String[]{"marketing@exemple.tn", "marketing-demo-1", "MARKETING", "00000000-0000-0000-0000-0000000000a3"},
                new String[]{"responsable@exemple.tn", "responsable-demo-1", "RESPONSABLE", "00000000-0000-0000-0000-0000000000a4"});
        for (String[] u : utilisateurs) {
            String corps = connecter(u[0], u[1]);
            assertThat((String) JsonPath.read(corps, "$.role")).isEqualTo(u[2]);
            assertThat((String) JsonPath.read(corps, "$.utilisateurId")).isEqualTo(u[3]);
            assertThat((String) JsonPath.read(corps, "$.type")).isEqualTo("Bearer");
            assertThat((Integer) JsonPath.read(corps, "$.expireDans")).isEqualTo(3600);

            // Le jeton est signé avec le secret partagé et contient le rôle (« role ») et l'identifiant (« sub »)
            SignedJWT jwt = SignedJWT.parse(JsonPath.read(corps, "$.jeton"));
            assertThat(jwt.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            assertThat(claims.getSubject()).isEqualTo(u[3]);
            assertThat(claims.getStringClaim("role")).isEqualTo(u[2]);
            assertThat(claims.getExpirationTime()).isAfter(Date.from(Instant.now().plusSeconds(3500)))
                    .isBefore(Date.from(Instant.now().plusSeconds(3700)));
        }
    }

    @Test
    void unJetonSigneAvecUnAutreSecretNEstPasValide() throws Exception {
        SignedJWT jwt = SignedJWT.parse(JsonPath.read(connecter("admin@exemple.tn", "admin-demo-1"), "$.jeton"));
        assertThat(jwt.verify(new MACVerifier("un-autre-secret-un-autre-secret-0123456789".getBytes(StandardCharsets.UTF_8)))).isFalse();
    }

    @Test
    void mauvaisMotDePasseOuEmailInconnuDonnentLeMemeRefus401() throws Exception {
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(login("admin@exemple.tn", "mauvais")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Identifiants invalides"));
        // même message : on ne révèle pas si l'email existe
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(login("inconnu@exemple.tn", "admin-demo-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiants invalides"));
    }

    @Test
    void lEmailSeComparePasSensibleALaCasseNiAuxEspaces() throws Exception {
        connecter("  ADMIN@Exemple.TN ", "admin-demo-1");
    }

    @Test
    void champAbsentOuVideEst422EtJsonIllisibleEst400() throws Exception {
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"admin@exemple.tn\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(login("", "")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laReponseNeContientNiMotDePasseNiEmpreinte() throws Exception {
        String corps = connecter("admin@exemple.tn", "admin-demo-1");
        assertThat(corps).doesNotContain("admin-demo-1").doesNotContain("$2a$").doesNotContain("motDePasse")
                .doesNotContain("empreinte");
    }

    @Test
    void lesMotsDePasseSontChiffresAvecBcrypt() {
        String empreinte = encodeur.encode("admin-demo-1");
        assertThat(empreinte).startsWith("$2");
        assertThat(empreinte).doesNotContain("admin-demo-1");
        assertThat(encodeur.matches("admin-demo-1", empreinte)).isTrue();
        // deux empreintes du même mot de passe sont différentes (sel aléatoire)
        assertThat(encodeur.encode("admin-demo-1")).isNotEqualTo(empreinte);
    }
}
