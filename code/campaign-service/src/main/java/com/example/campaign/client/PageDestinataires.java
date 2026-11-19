package com.example.campaign.client;

import java.util.List;

/** Page renvoyée par GET /clients/destinataires de Customer. */
public record PageDestinataires(List<DestinataireCustomer> contenu, int page, int taille, long total) {
}
