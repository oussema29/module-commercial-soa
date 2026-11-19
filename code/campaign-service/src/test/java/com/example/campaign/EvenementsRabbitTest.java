package com.example.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.campaign.messaging.DevisAccepteConsommateur;
import com.example.campaign.messaging.DevisAccepteEvent;
import com.example.campaign.messaging.EditeurEvenementsRabbit;
import com.example.campaign.messaging.ProspectGenereEvent;
import com.example.campaign.service.DevisAccepteService;
import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Tests unitaires de l'envoi et de la réception des événements (sans RabbitMQ). */
class EvenementsRabbitTest {

    static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    ProspectGenereEvent prospect = new ProspectGenereEvent(UUID.randomUUID(), "ProspectGenere", Instant.now(),
            UUID.randomUUID(), "ENTREPRISE", "Atlas", "Karim", "karim@atlas.tn", null);
    DevisAccepteEvent devis = new DevisAccepteEvent(UUID.randomUUID(), "DevisAccepte", UUID.randomUUID(), "DEV-1",
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("119"), Instant.now());

    @Test
    void prospectGenereEstEnvoyeSurLEchangeAvecLaBonneCle() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        new EditeurEvenementsRabbit(rabbit, MAPPER).prospectGenere(prospect);
        verify(rabbit).convertAndSend("module-commercial", "prospect.genere", prospect);
    }

    @Test
    void rabbitIndisponibleNeFaitPasEchouerLaReponseDuVisiteur() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new RuntimeException("connexion refusée"))).when(rabbit)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));
        // aucune exception : l'événement est journalisé en entier pour être rejoué
        new EditeurEvenementsRabbit(rabbit, MAPPER).prospectGenere(prospect);
    }

    @Test
    void lAccuseDeReceptionVientApresLeTraitement() throws Exception {
        DevisAccepteService service = mock(DevisAccepteService.class);
        Channel channel = mock(Channel.class);
        new DevisAccepteConsommateur(service).recevoir(devis, channel, 3L);
        verify(service).traiter(devis);
        verify(channel).basicAck(3L, false);
    }

    @Test
    void unTraitementEnEchecEstRefuseSansRemiseEnFile() throws Exception {
        DevisAccepteService service = mock(DevisAccepteService.class);
        Channel channel = mock(Channel.class);
        doThrow(new IllegalStateException("base indisponible")).when(service).traiter(eq(devis));
        new DevisAccepteConsommateur(service).recevoir(devis, channel, 4L);
        verify(channel).basicNack(4L, false, false);
        assertThat(devis.type()).isEqualTo("DevisAccepte");
    }
}
