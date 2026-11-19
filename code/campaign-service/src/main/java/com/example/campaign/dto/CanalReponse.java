package com.example.campaign.dto;

import com.example.campaign.entity.CanalPublicitaire;
import com.example.campaign.entity.TypeCanal;
import java.util.UUID;

public record CanalReponse(UUID id, String nom, TypeCanal type) {

    public static CanalReponse de(CanalPublicitaire c) {
        return new CanalReponse(c.getId(), c.getNom(), c.getType());
    }
}
