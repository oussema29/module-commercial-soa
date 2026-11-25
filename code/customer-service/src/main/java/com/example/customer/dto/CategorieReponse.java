package com.example.customer.dto;

import com.example.customer.entity.CategorieClient;
import java.util.UUID;

public record CategorieReponse(UUID id, String nom, String description) {

    public static CategorieReponse de(CategorieClient c) {
        return new CategorieReponse(c.getId(), c.getNom(), c.getDescription());
    }
}
