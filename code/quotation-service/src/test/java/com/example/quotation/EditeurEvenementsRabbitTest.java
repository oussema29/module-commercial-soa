package com.example.quotation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.quotation.messaging.DevisAccepteEvent;
import com.example.quotation.messaging.EditeurEvenementsRabbit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class EditeurEvenementsRabbitTest {

    static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    DevisAccepteEvent evenement = new DevisAccepteEvent(UUID.randomUUID(), "DevisAccepte", UUID.randomUUID(), "DEV-2026-0042",
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("4200.000"), new BigDecimal("4998.000"), Instant.now());

    @Test
    void devisAccepteEstEnvoyeSurLEchangeAvecLaBonneCle() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        new EditeurEvenementsRabbit(rabbit, MAPPER).devisAccepte(evenement);
        verify(rabbit).convertAndSend("module-commercial", "devis.accepte", evenement);
    }

    @Test
    void rabbitIndisponibleNeFaitPasEchouerLAcceptation() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new RuntimeException("connexion refusée"))).when(rabbit)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));
        // aucune exception : l'acceptation du client reste enregistrée
        new EditeurEvenementsRabbit(rabbit, MAPPER).devisAccepte(evenement);
    }
}
