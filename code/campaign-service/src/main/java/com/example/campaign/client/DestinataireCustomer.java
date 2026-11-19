package com.example.campaign.client;

import java.util.UUID;

/** Un client à qui écrire : ce que Customer renvoie, rien de plus. */
public record DestinataireCustomer(UUID clientId, String nom, String email) {
}
