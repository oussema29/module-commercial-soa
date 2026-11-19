package com.example.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.ObjectifCampagne;
import com.example.campaign.entity.PerformanceCampagne;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.messaging.DevisAccepteEvent;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.EvenementTraiteRepository;
import com.example.campaign.service.CalculPerformance;
import com.example.campaign.service.DevisAccepteService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DevisAccepteServiceTests {

    @Autowired
    DevisAccepteService service;
    @Autowired
    CampagneRepository campagnes;
    @Autowired
    EvenementTraiteRepository traites;

    Campagne campagne(StatutCampagne statut) {
        Campagne c = new Campagne();
        c.setNom("Offre");
        c.setObjectif(ObjectifCampagne.VENTES);
        c.setDateDebut(LocalDate.now().minusDays(10));
        c.setDateFin(LocalDate.now().plusDays(10));
        c.setStatut(statut);
        PerformanceCampagne p = new PerformanceCampagne();
        p.setCampagne(c);
        c.setPerformance(p);
        return campagnes.saveAndFlush(c);
    }

    DevisAccepteEvent evenement(UUID campagneId, String montantHT) {
        return new DevisAccepteEvent(UUID.randomUUID(), "DevisAccepte", UUID.randomUUID(), "DEV-2026-0042", UUID.randomUUID(),
                campagneId, new BigDecimal(montantHT), new BigDecimal("4998.000"), Instant.now());
    }

    @Test
    void leDevisAccepteAugmenteLeChiffreDAffairesEtLeNombreDeDevis() {
        Campagne c = campagne(StatutCampagne.ACTIVE);
        assertThat(service.traiter(evenement(c.getId(), "4200.000"))).isTrue();
        assertThat(service.traiter(evenement(c.getId(), "800.500"))).isTrue();
        PerformanceCampagne p = c.getPerformance();
        assertThat(p.getDevisAcceptes()).isEqualTo(2);
        assertThat(p.getChiffreAffairesAttribue()).isEqualByComparingTo("5000.500");
    }

    @Test
    void leMemeEvenementRecuDeuxFoisNEstCompteQuUneFois() {
        Campagne c = campagne(StatutCampagne.ACTIVE);
        DevisAccepteEvent e = evenement(c.getId(), "4200.000");
        assertThat(service.traiter(e)).isTrue();
        assertThat(service.traiter(e)).isFalse();
        assertThat(c.getPerformance().getDevisAcceptes()).isEqualTo(1);
        assertThat(c.getPerformance().getChiffreAffairesAttribue()).isEqualByComparingTo("4200.000");
    }

    @Test
    void uneCampagneTermineeCompteQuandMeme() {
        Campagne c = campagne(StatutCampagne.TERMINEE);
        assertThat(service.traiter(evenement(c.getId(), "1000"))).isTrue();
        assertThat(c.getPerformance().getDevisAcceptes()).isEqualTo(1);
    }

    @Test
    void uneCampagneInconnueEstIgnoreeSansErreur() {
        DevisAccepteEvent e = evenement(UUID.randomUUID(), "1000");
        assertThat(service.traiter(e)).isFalse();
        assertThat(traites.existsById(e.eventId())).isTrue();
    }

    @Test
    void leRoiSeCalculeAPartirDuChiffreDAffairesAttribue() {
        Campagne c = campagne(StatutCampagne.ACTIVE);
        c.getPerformance().setMontantDepense(new BigDecimal("1000"));
        service.traiter(evenement(c.getId(), "3500"));
        assertThat(CalculPerformance.roi(c.getPerformance().getChiffreAffairesAttribue(), c.getPerformance().getMontantDepense()))
                .isEqualByComparingTo("2.5");
    }
}
