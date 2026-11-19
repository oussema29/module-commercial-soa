package com.example.campaign.messaging;

import com.example.campaign.config.RabbitConfig;
import com.example.campaign.service.DevisAccepteService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Écoute la file campaign.devis-accepte. Accusé de réception MANUEL, envoyé seulement après le traitement. */
@Component
public class DevisAccepteConsommateur {

    private static final Logger LOG = LoggerFactory.getLogger(DevisAccepteConsommateur.class);

    private final DevisAccepteService service;

    public DevisAccepteConsommateur(DevisAccepteService service) {
        this.service = service;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_DEVIS)
    public void recevoir(DevisAccepteEvent evenement, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            service.traiter(evenement);
            channel.basicAck(tag, false);
        } catch (RuntimeException e) {
            LOG.error("DevisAccepte {} impossible à traiter : le message part en file des échecs", evenement.eventId(), e);
            channel.basicNack(tag, false, false);
        }
    }
}
