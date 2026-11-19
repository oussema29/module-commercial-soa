package com.example.campaign.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CanalAssociation(@NotNull UUID canalId) {
}
