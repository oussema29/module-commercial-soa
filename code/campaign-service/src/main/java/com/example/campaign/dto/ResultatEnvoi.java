package com.example.campaign.dto;

import java.util.List;

/** Compte rendu d'un lancement ou d'un renvoi : combien d'envois réussis, et la liste des échecs avec leur cause. */
public record ResultatEnvoi(int envoisReussis, List<EchecEnvoi> echecs) {
}
