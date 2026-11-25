package com.example.quotation.service;

import com.example.quotation.repository.DevisRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Fabrique la référence lisible d'un devis : DEV-2026-0042 (numéro à 4 chiffres, recommencé chaque année). */
@Component
public class ReferenceDevis {

    private final DevisRepository devis;
    private final Clock clock;

    public ReferenceDevis(DevisRepository devis, Clock clock) {
        this.devis = devis;
        this.clock = clock;
    }

    public String suivante() {
        String prefixe = "DEV-" + LocalDate.now(clock).getYear() + "-";
        long numero = devis.countByReferenceStartingWith(prefixe) + 1;
        return prefixe + String.format("%04d", numero);
    }
}
