package com.example.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.quotation.client.ClientCustomer;
import com.example.quotation.client.CustomerClient;
import com.example.quotation.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Le client REST de Customer est testé contre un mini serveur HTTP local : aucun service Clients n'est lancé. */
class CustomerClientTest {

    static final UUID CONNU = UUID.fromString("9a2f7c31-0000-4000-8000-000000000001");

    HttpServer serveur;
    CustomerClient client;

    @BeforeEach
    void demarrer() throws Exception {
        serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        serveur.createContext("/clients/", ex -> {
            boolean connu = ex.getRequestURI().getPath().endsWith(CONNU.toString());
            byte[] corps = (connu
                    ? "{\"id\":\"" + CONNU + "\",\"nom\":\"Karim\",\"prenom\":\"Ben Ali\",\"email\":\"karim@mail.com\",\"statut\":\"CLIENT\",\"champInconnu\":1}"
                    : "{\"code\":404,\"message\":\"Client introuvable\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(connu ? 200 : 404, corps.length);
            ex.getResponseBody().write(corps);
            ex.close();
        });
        serveur.start();
        client = new CustomerClient("http://localhost:" + serveur.getAddress().getPort());
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    @Test
    void clientConnuEstLuEtLesChampsInconnusIgnores() {
        ClientCustomer c = client.trouver(CONNU);
        assertEquals("Karim", c.nom());
        assertEquals("karim@mail.com", c.email());
        assertEquals("CLIENT", c.statut());
    }

    @Test
    void clientInconnuDonne404() {
        ApiException e = assertThrows(ApiException.class, () -> client.trouver(UUID.randomUUID()));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void serviceArreteDonne503() {
        serveur.stop(0);
        ApiException e = assertThrows(ApiException.class, () -> client.trouver(CONNU));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }
}
