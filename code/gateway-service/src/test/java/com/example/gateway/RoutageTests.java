package com.example.gateway;

import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Mode none : la passerelle route seulement. Chaque chemin doit arriver au bon service. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutageTests {

    static Fournisseurs customer, quotation, campaign, auth;

    @AfterAll
    static void arreter() {
        customer.arreter();
        quotation.arreter();
        campaign.arreter();
        auth.arreter();
    }

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) throws IOException {
        customer = new Fournisseurs("customer");
        quotation = new Fournisseurs("quotation");
        campaign = new Fournisseurs("campaign");
        auth = new Fournisseurs("auth");
        registre.add("CUSTOMER_URL", customer::url);
        registre.add("QUOTATION_URL", quotation::url);
        registre.add("CAMPAIGN_URL", campaign::url);
        registre.add("AUTH_URL", auth::url);
    }

    @Autowired
    WebTestClient web;

    @ParameterizedTest(name = "{0} arrive chez {1}")
    @CsvSource({
            "/clients,customer", "/clients/9a2f7c31/contacts,customer", "/clients/destinataires,customer",
            "/categories,customer", "/contacts/12,customer", "/interactions/12,customer",
            "/devis,quotation", "/devis/12/lignes,quotation", "/produits,quotation", "/taxes,quotation",
            "/public/reponse/jeton123,quotation", "/public/reponse/jeton123/accepter,quotation",
            "/campagnes,campaign", "/campagnes/12/lancer,campaign", "/canaux,campaign", "/contenus/12/produits,campaign",
            "/publics/12,campaign", "/public/campagnes/12,campaign", "/public/invitations/jeton123/reponse,campaign",
            "/login,auth"})
    void chaqueCheminVaVersLeBonService(String chemin, String service) {
        web.get().uri(chemin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo(service)
                .jsonPath("$.chemin").isEqualTo(chemin);
    }

    @Test
    void lEnteteAuthorizationEstRetransmisTelQuel() {
        web.get().uri("/campagnes").header("Authorization", "Bearer abc.def.ghi").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.autorisation").isEqualTo("Bearer abc.def.ghi");
    }

    @Test
    void lesMethodesSontRetransmises() {
        web.post().uri("/devis").exchange().expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("quotation");
        web.put().uri("/campagnes/12").exchange().expectStatus().isOk();
        web.delete().uri("/clients/12").exchange().expectStatus().isOk();
    }

    @Test
    void unCheminInconnuDonneUn404AuFormatDuModule() {
        web.get().uri("/n-importe-quoi").exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(404)
                .jsonPath("$.message").isEqualTo("Route inconnue")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void sansJetonLaPasserelleNeRefuseRienEnModeNone() {
        web.get().uri("/clients").exchange().expectStatus().isOk();
    }
}
