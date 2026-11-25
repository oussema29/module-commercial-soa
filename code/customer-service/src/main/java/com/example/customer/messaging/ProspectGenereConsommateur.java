package com.example.customer.messaging;

import com.example.customer.config.RabbitConfig;
import com.example.customer.service.ProspectService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Écoute la file customer.prospect-genere. Accusé de réception MANUEL, envoyé seulement après le traitement. */
@Component
public class ProspectGenereConsommateur {

    private static final Logger LOG = LoggerFactory.getLogger(ProspectGenereConsommateur.class);

    private final ProspectService service;

    public ProspectGenereConsommateur(ProspectService service) {
        this.service = service;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_PROSPECT)
    public void recevoir(ProspectGenereEvent evenement, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            service.traiter(evenement);
            channel.basicAck(tag, false);
        } catch (RuntimeException e) {
            LOG.error("ProspectGenere {} impossible à traiter : le message part en file des échecs", evenement.eventId(), e);
            channel.basicNack(tag, false, false);
        }
    }
}
