package com.example.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Faux services pour les tests : chacun répond son nom, le chemin reçu et l'en-tête Authorization reçu. */
final class Fournisseurs {

    final HttpServer serveur;
    final String nom;

    Fournisseurs(String nom) throws IOException {
        this.nom = nom;
        this.serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/", echange -> {
            String autorisation = echange.getRequestHeaders().getFirst("Authorization");
            String corps = "{\"service\":\"" + nom + "\",\"chemin\":\"" + echange.getRequestURI().getPath() + "\",\"autorisation\":"
                    + (autorisation == null ? "null" : "\"" + autorisation + "\"") + "}";
            byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("Content-Type", "application/json");
            echange.sendResponseHeaders(200, octets.length);
            echange.getResponseBody().write(octets);
            echange.close();
        });
        serveur.start();
    }

    String url() {
        return "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    void arreter() {
        serveur.stop(0);
    }
}
