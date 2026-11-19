package com.example.campaign.dto;

import com.example.campaign.entity.InvitationClient;
import java.time.Instant;
import java.util.UUID;

/** Le jeton n'y figure jamais : il n'existe que dans l'email du client. */
public record InvitationReponse(UUID id, UUID clientId, Instant envoyeLe, String erreurEnvoi, Instant repondueLe) {

    public static InvitationReponse de(InvitationClient i) {
        return new InvitationReponse(i.getId(), i.getClientId(), i.getEnvoyeLe(), i.getErreurEnvoi(), i.getRepondueLe());
    }
}
