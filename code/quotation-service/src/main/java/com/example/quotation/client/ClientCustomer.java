package com.example.quotation.client;

import java.util.UUID;

/** Ce que Quotation lit d'un client chez Customer (les autres champs de la réponse sont ignorés). */
public record ClientCustomer(UUID id, String nom, String prenom, String email, String statut) {
}
