package com.example.campaign.messaging;

import com.example.campaign.config.RabbitConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Envoie ProspectGenere à RabbitMQ (échange module-commercial, clé prospect.genere). */
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
    public void prospectGenere(ProspectGenereEvent evenement) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.CLE_PROSPECT, evenement);
        } catch (AmqpException e) {
            // La réponse du visiteur est déjà comptée : on ne la refuse pas. L'événement est journalisé en entier pour être rejoué.
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
