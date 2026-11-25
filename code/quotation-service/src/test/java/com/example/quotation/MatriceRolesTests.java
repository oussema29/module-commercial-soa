package com.example.quotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Matrice complète des droits, générée à partir des x-roles du contrat (quotation.yaml) : pour CHAQUE route et CHAQUE rôle,
 * un rôle autorisé n'est jamais refusé (ni 401 ni 403) et un rôle non autorisé reçoit 403. Les routes publiques
 * ne répondent jamais 401 ni 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.security.mode=jwt", "app.security.jwt-secret=secret-de-test-secret-de-test-0123456789"})
class MatriceRolesTests {

    static final List<String> ROLES = List.of("ADMIN", "COMMERCIAL", "MARKETING", "RESPONSABLE");

    // {méthode, chemin, rôles autorisés}
    static final String[][] ROUTES = {
        {"POST", "/devis", "COMMERCIAL"},
        {"GET", "/devis", "COMMERCIAL,RESPONSABLE"},
        {"GET", "/devis/00000000-0000-0000-0000-000000000042", "COMMERCIAL,RESPONSABLE"},
        {"PUT", "/devis/00000000-0000-0000-0000-000000000042", "COMMERCIAL"},
        {"POST", "/devis/00000000-0000-0000-0000-000000000042/lignes", "COMMERCIAL"},
        {"PUT", "/devis/00000000-0000-0000-0000-000000000042/lignes/00000000-0000-0000-0000-000000000042", "COMMERCIAL"},
        {"DELETE", "/devis/00000000-0000-0000-0000-000000000042/lignes/00000000-0000-0000-0000-000000000042", "COMMERCIAL"},
        {"POST", "/devis/00000000-0000-0000-0000-000000000042/envoyer", "COMMERCIAL"},
        {"POST", "/devis/00000000-0000-0000-0000-000000000042/annuler", "COMMERCIAL"},
        {"GET", "/devis/00000000-0000-0000-0000-000000000042/pdf", "COMMERCIAL,RESPONSABLE"},
        {"GET", "/produits", "ADMIN,COMMERCIAL,MARKETING,RESPONSABLE"},
        {"POST", "/produits", "ADMIN"},
        {"GET", "/produits/00000000-0000-0000-0000-000000000042", "ADMIN,COMMERCIAL,MARKETING,RESPONSABLE"},
        {"PUT", "/produits/00000000-0000-0000-0000-000000000042", "ADMIN"},
        {"DELETE", "/produits/00000000-0000-0000-0000-000000000042", "ADMIN"},
        {"GET", "/taxes", "ADMIN,COMMERCIAL,RESPONSABLE"},
        {"POST", "/taxes", "ADMIN"},
        {"PUT", "/taxes/00000000-0000-0000-0000-000000000042", "ADMIN"},
        {"GET", "/public/reponse/00000000-0000-0000-0000-000000000042", "PUBLIC"},
        {"POST", "/public/reponse/00000000-0000-0000-0000-000000000042/accepter", "PUBLIC"},
        {"POST", "/public/reponse/00000000-0000-0000-0000-000000000042/refuser", "PUBLIC"},
    };

    @Autowired
    MockMvc mvc;

    static Stream<Arguments> cas() {
        return Stream.of(ROUTES).flatMap(r -> ROLES.stream().map(role -> Arguments.of(r[0], r[1], r[2], role)));
    }

    @ParameterizedTest(name = "{0} {1} en tant que {3}")
    @MethodSource("cas")
    void chaqueRoleRecoitLaBonneReponse(String methode, String chemin, String autorises, String role) throws Exception {
        boolean publique = autorises.equals("PUBLIC");
        boolean autorise = !publique && List.of(autorises.split(",")).contains(role);
        var requete = request(HttpMethod.valueOf(methode), chemin).contentType(MediaType.APPLICATION_JSON).content("{}");
        if (!publique) {
            requete = requete.header("Authorization", SecuriteJwtTests.jeton(role));
        }
        int statut = mvc.perform(requete).andReturn().getResponse().getStatus();
        if (publique || autorise) {
            assertThat(statut).as("%s %s en tant que %s : un accès autorisé ne doit pas être refusé", methode, chemin, role)
                    .isNotIn(401, 403);
        } else {
            assertThat(statut).as("%s %s en tant que %s : un rôle non autorisé doit recevoir 403", methode, chemin, role)
                    .isEqualTo(403);
        }
    }

    @ParameterizedTest(name = "{0} {1} sans jeton")
    @MethodSource("sansJeton")
    void sansJetonUneRouteProtegeeEst401(String methode, String chemin) throws Exception {
        int statut = mvc.perform(request(HttpMethod.valueOf(methode), chemin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        assertThat(statut).isEqualTo(401);
    }

    static Stream<Arguments> sansJeton() {
        return Stream.of(ROUTES).filter(r -> !r[2].equals("PUBLIC")).map(r -> Arguments.of(r[0], r[1]));
    }
}
