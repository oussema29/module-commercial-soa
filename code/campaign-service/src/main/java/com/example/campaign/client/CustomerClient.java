package com.example.campaign.client;

import com.example.campaign.exception.ApiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

/** Lecture des destinataires chez Customer (lien I2). Lecture seule : on n'écrit jamais chez Customer. */
@Component
public class CustomerClient {

    private final RestClient rest;

    public CustomerClient(@Value("${app.customer-url}") String url) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(2));
        fabrique.setReadTimeout(Duration.ofSeconds(5));
        this.rest = RestClient.builder().baseUrl(url).requestFactory(fabrique).build();
    }

    /** Tous les clients de statut CLIENT de la catégorie (toutes les pages). */
    public List<DestinataireCustomer> destinataires(UUID categorieId) {
        List<DestinataireCustomer> tous = new ArrayList<>();
        int page = 0;
        while (true) {
            PageDestinataires reponse;
            try {
                reponse = rest.get()
                        .uri("/clients/destinataires?categorieId={c}&page={p}&taille=100", categorieId, page)
                        .headers(this::transmettreIdentite).retrieve().body(PageDestinataires.class);
            } catch (HttpClientErrorException.NotFound e) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Catégorie de clients introuvable chez Customer");
            } catch (RestClientException e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service Customer indisponible");
            }
            if (reponse == null || reponse.contenu() == null || reponse.contenu().isEmpty()) {
                break;
            }
            tous.addAll(reponse.contenu());
            if (tous.size() >= reponse.total()) {
                break;
            }
            page++;
        }
        return tous;
    }

    /** Un service qui en appelle un autre retransmet l'identité reçue (le JWT). Sans JWT (mode none) : rien à retransmettre. */
    private void transmettreIdentite(HttpHeaders entetes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            entetes.setBearerAuth(jwt.getToken().getTokenValue());
        }
    }
}
