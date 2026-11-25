package com.example.quotation.client;

import com.example.quotation.exception.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Lecture d'un client chez Customer (lien I1). Lecture seule : on n'écrit jamais chez Customer. */
@Component
public class CustomerClient {

    private final RestClient rest;

    public CustomerClient(@Value("${app.customer-url}") String url) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(2));
        fabrique.setReadTimeout(Duration.ofSeconds(2));
        this.rest = RestClient.builder().baseUrl(url).requestFactory(fabrique).build();
    }

    public ClientCustomer trouver(UUID clientId) {
        try {
            return rest.get().uri("/clients/{id}", clientId)
                    .headers(this::transmettreIdentite).retrieve().body(ClientCustomer.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Client introuvable");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service Customer indisponible");
        }
    }

    /** Un service qui en appelle un autre retransmet l'identité reçue (le JWT). Sans JWT (mode none) : rien à retransmettre. */
    private void transmettreIdentite(HttpHeaders entetes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            entetes.setBearerAuth(jwt.getToken().getTokenValue());
        }
    }
}
