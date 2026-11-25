package com.example.quotation.messaging;

import com.example.quotation.config.RabbitConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Envoie DevisAccepte à RabbitMQ (échange module-commercial, clé devis.accepte). */
@Component
@ConditionalOnProperty(name = "app.events.rabbit", havingValue = "true")
public class EditeurEvenementsRabbit implements EditeurEvenements {

    private static final Logger LOG = LoggerFactory.getLogger(EditeurEvenementsRabbit.class);

    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public EditeurEvenementsRabbit(RabbitTemplate rabbit, ObjectMapper json) {
        this.rabbit = rabbit;
        this.json = json;
    }

    @Override
    public void devisAccepte(DevisAccepteEvent evenement) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.CLE_DEVIS, evenement);
        } catch (AmqpException e) {
            // L'acceptation du client est déjà enregistrée : on ne la refuse pas. L'événement est journalisé en entier pour être rejoué.
            LOG.error("ÉVÉNEMENT NON PUBLIÉ (RabbitMQ indisponible), à rejouer avec ce JSON : {}", enJson(evenement), e);
        }
    }

    /** Le message exact à republier (par exemple depuis la console web de RabbitMQ). */
    private String enJson(Object evenement) {
        try {
            return json.writeValueAsString(evenement);
        } catch (JsonProcessingException e) {
            return String.valueOf(evenement);
        }
    }
}
