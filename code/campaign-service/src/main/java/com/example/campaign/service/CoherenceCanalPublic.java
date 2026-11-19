package com.example.campaign.service;

import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.PublicCible;
import com.example.campaign.entity.TypeCanal;
import com.example.campaign.entity.TypePublic;
import com.example.campaign.exception.ApiException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Règle canal / public, vérifiée au lancement :
 * clients existants -> canal EMAIL (le seul qui atteint un client précis, avec son lien personnel) ;
 * public externe -> au moins un canal de diffusion large (FACEBOOK, INSTAGRAM ou GOOGLE_ADS).
 */
@Component
public class CoherenceCanalPublic {

    public void verifier(Campagne c) {
        Set<TypeCanal> types = c.getCanaux().stream().map(cc -> cc.getCanal().getType()).collect(Collectors.toSet());
        for (PublicCible p : c.getPublics()) {
            if (p.getType() == TypePublic.CLIENTS_EXISTANTS && !types.contains(TypeCanal.EMAIL)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Un public de clients existants exige un canal EMAIL");
            }
            boolean diffusionLarge = types.contains(TypeCanal.FACEBOOK) || types.contains(TypeCanal.INSTAGRAM)
                    || types.contains(TypeCanal.GOOGLE_ADS);
            if (p.getType() == TypePublic.PUBLIC_EXTERNE && !diffusionLarge) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Un public externe exige au moins un canal FACEBOOK, INSTAGRAM ou GOOGLE_ADS");
            }
        }
    }
}
