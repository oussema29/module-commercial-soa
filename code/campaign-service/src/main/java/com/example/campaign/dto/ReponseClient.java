package com.example.campaign.dto;

import com.example.campaign.entity.InvitationClient;
import java.time.Instant;
import java.util.UUID;

/** Un client existant qui a répondu : identifiant et date seulement (l'interface lit les noms chez Customer). */
public record ReponseClient(UUID clientId, Instant repondueLe) {

    public static ReponseClient de(InvitationClient i) {
        return new ReponseClient(i.getClientId(), i.getRepondueLe());
    }
}
