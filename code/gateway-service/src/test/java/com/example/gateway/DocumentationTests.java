package com.example.gateway;

import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** La page Swagger de la passerelle lit la description d'API de chaque service à travers la passerelle. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentationTests {

    static Fournisseurs customer, quotation, campaign;

    @AfterAll
    static void arreter() {
        customer.arreter();
        quotation.arreter();
        campaign.arreter();
    }

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) throws IOException {
        customer = new Fournisseurs("customer");
        quotation = new Fournisseurs("quotation");
        campaign = new Fournisseurs("campaign");
        registre.add("CUSTOMER_URL", customer::url);
        registre.add("QUOTATION_URL", quotation::url);
        registre.add("CAMPAIGN_URL", campaign::url);
    }

    @Autowired
    WebTestClient web;

    @Test
    void ladescriptionDeChaqueServiceEstLueAvecLeBonChemin() {
        web.get().uri("/docs/customer/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("customer").jsonPath("$.chemin").isEqualTo("/v3/api-docs");
        web.get().uri("/docs/quotation/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("quotation").jsonPath("$.chemin").isEqualTo("/v3/api-docs");
        web.get().uri("/docs/campaign/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("campaign").jsonPath("$.chemin").isEqualTo("/v3/api-docs");
    }

    @Test
    void laPageSwaggerDeLaPasserelleEstServie() {
        web.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
        web.get().uri("/webjars/swagger-ui/index.html").exchange().expectStatus().isOk();
    }

    @Test
    void leMenuListeLesTroisServices() {
        web.get().uri("/v3/api-docs/swagger-config").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.urls.length()").isEqualTo(3);
    }
}
