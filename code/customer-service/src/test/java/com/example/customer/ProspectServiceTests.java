package com.example.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.customer.entity.Client;
import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import com.example.customer.messaging.ProspectGenereEvent;
import com.example.customer.repository.ClientRepository;
import com.example.customer.repository.ContactRepository;
import com.example.customer.repository.EvenementTraiteRepository;
import com.example.customer.service.ProspectService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProspectServiceTests {

    @Autowired
    ProspectService service;
    @Autowired
    ClientRepository clients;
    @Autowired
    ContactRepository contacts;
    @Autowired
    EvenementTraiteRepository traites;

    ProspectGenereEvent evenement(String typeClient, String email) {
        return new ProspectGenereEvent(UUID.randomUUID(), "ProspectGenere", Instant.now(), UUID.randomUUID(), typeClient,
                "Société Atlas", "Karim Ben Salah", email, "+216 20 123 456");
    }

    @Test
    void unParticulierDevientUnProspectSansContact() {
        ProspectGenereEvent e = evenement("PARTICULIER", "ali@x.tn");
        assertThat(service.traiter(e)).isTrue();
        Client c = clients.findAll().get(0);
        assertThat(c.getStatut()).isEqualTo(StatutClient.PROSPECT);
        assertThat(c.getType()).isEqualTo(TypeClient.PARTICULIER);
        assertThat(c.getCampagneSourceId()).isEqualTo(e.campagneId());
        assertThat(c.getCategorie()).isNull();
        assertThat(contacts.findByClientId(c.getId())).isEmpty();
        assertThat(traites.existsById(e.eventId())).isTrue();
    }

    @Test
    void uneEntrepriseCreeAussiSonContactAvecLeMemeEmail() {
        service.traiter(evenement("ENTREPRISE", "contact@atlas.tn"));
        Client c = clients.findAll().get(0);
        assertThat(c.getType()).isEqualTo(TypeClient.ENTREPRISE);
        assertThat(contacts.findByClientId(c.getId())).hasSize(1)
                .allSatisfy(ct -> {
                    assertThat(ct.getNom()).isEqualTo("Karim Ben Salah");
                    assertThat(ct.getEmail()).isEqualTo("contact@atlas.tn");
                });
    }

    @Test
    void lAdresseDUnClientDejaConnuNeCreeRienMaisLEvenementEstNote() {
        service.traiter(evenement("PARTICULIER", "ali@x.tn"));
        ProspectGenereEvent doublon = evenement("PARTICULIER", " ALI@x.tn");
        assertThat(service.traiter(doublon)).isFalse();
        assertThat(clients.count()).isEqualTo(1);
        assertThat(traites.existsById(doublon.eventId())).isTrue();
    }

    @Test
    void leMemeEvenementRecuDeuxFoisNeCreeQuUneFiche() {
        ProspectGenereEvent e = evenement("ENTREPRISE", "contact@atlas.tn");
        assertThat(service.traiter(e)).isTrue();
        assertThat(service.traiter(e)).isFalse();
        assertThat(clients.count()).isEqualTo(1);
        assertThat(contacts.count()).isEqualTo(1);
    }
}
