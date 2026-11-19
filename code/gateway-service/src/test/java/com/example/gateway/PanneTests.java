package com.example.gateway;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Un service arrêté donne 503 au format du module, sans gêner les autres. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PanneTests {

    static Fournisseurs vivant;

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) throws IOException {
        vivant = new Fournisseurs("vivant");
        // Customer pointe vers un port où personne n'écoute : le service est « arrêté »
        registre.add("CUSTOMER_URL", () -> "http://127.0.0.1:1");
        registre.add("QUOTATION_URL", vivant::url);
        registre.add("CAMPAIGN_URL", vivant::url);
        registre.add("AUTH_URL", vivant::url);
    }

    @Autowired
    WebTestClient web;

    @Test
    void unServiceArreteDonne503AuFormatDuModule() {
        web.get().uri("/clients").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo(503)
                .jsonPath("$.message").isEqualTo("Service indisponible");
    }

    @Test
    void lesAutresServicesRestentJoignables() {
        web.get().uri("/devis").exchange().expectStatus().isOk();
        web.get().uri("/campagnes").exchange().expectStatus().isOk();
    }
}
