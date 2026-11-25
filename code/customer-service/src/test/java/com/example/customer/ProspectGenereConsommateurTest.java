package com.example.customer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.customer.messaging.ProspectGenereConsommateur;
import com.example.customer.messaging.ProspectGenereEvent;
import com.example.customer.service.ProspectService;
import com.rabbitmq.client.Channel;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Test unitaire : l'accusé de réception n'est envoyé qu'après un traitement réussi. */
class ProspectGenereConsommateurTest {

    ProspectService service = mock(ProspectService.class);
    Channel channel = mock(Channel.class);
    ProspectGenereConsommateur consommateur = new ProspectGenereConsommateur(service);
    ProspectGenereEvent evenement = new ProspectGenereEvent(UUID.randomUUID(), "ProspectGenere", Instant.now(),
            UUID.randomUUID(), "PARTICULIER", "Ali", null, "ali@x.tn", null);

    @Test
    void traitementReussiDonneUnAccuseDeReception() throws Exception {
        consommateur.recevoir(evenement, channel, 7L);
        verify(service).traiter(evenement);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void traitementEnEchecRefuseLeMessageSansLeRemettreEnFile() throws Exception {
        doThrow(new IllegalStateException("base indisponible")).when(service).traiter(any());
        consommateur.recevoir(evenement, channel, 8L);
        verify(channel).basicNack(8L, false, false);
    }
}
