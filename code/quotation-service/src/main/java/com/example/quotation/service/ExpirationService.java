package com.example.quotation.service;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.repository.DevisRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExpirationService {

    private final DevisRepository devis;
    private final Clock clock;

    public ExpirationService(DevisRepository devis, Clock clock) {
        this.devis = devis;
        this.clock = clock;
    }

    /** Passe à EXPIRE les devis ENVOYE dont la date de validité est strictement passée. Renvoie leur nombre. */
    public int expirerLesDevisEchus() {
        List<Devis> echus = devis.findByStatutAndDateValiditeBefore(StatutDevis.ENVOYE, LocalDate.now(clock));
        echus.forEach(d -> d.setStatut(StatutDevis.EXPIRE));
        return echus.size();
    }
}
