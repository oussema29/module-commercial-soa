package com.example.campaign.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologie RabbitMQ du module commercial (voir contrat_api/evenements.md). Les déclarations sont idempotentes :
 * chaque service déclare l'échange et les files qu'il utilise, y compris celle de son destinataire, pour qu'un
 * message publié ne soit jamais perdu si le destinataire n'est pas encore démarré.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "module-commercial";
    public static final String DLX = "module-commercial.dlx";
    public static final String QUEUE_DEVIS = "campaign.devis-accepte";
    public static final String CLE_DEVIS = "devis.accepte";
    // File de Customer : déclarée aussi ici (idempotent) pour ne perdre aucun ProspectGenere
    public static final String QUEUE_PROSPECT = "customer.prospect-genere";
    public static final String CLE_PROSPECT = "prospect.genere";

    @Bean
    public TopicExchange moduleCommercial() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // Les messages impossibles à traiter partent dans une file des échecs (.dlq) au lieu d'être rejoués sans fin
    @Bean
    public DirectExchange moduleCommercialDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue devisAccepteQueue() {
        return file(QUEUE_DEVIS);
    }

    @Bean
    public Queue devisAccepteDlq() {
        return QueueBuilder.durable(QUEUE_DEVIS + ".dlq").build();
    }

    @Bean
    public Binding devisAccepteBinding() {
        return BindingBuilder.bind(devisAccepteQueue()).to(moduleCommercial()).with(CLE_DEVIS);
    }

    @Bean
    public Binding devisAccepteDlqBinding() {
        return BindingBuilder.bind(devisAccepteDlq()).to(moduleCommercialDlx()).with(QUEUE_DEVIS + ".dlq");
    }

    @Bean
    public Queue prospectGenereQueue() {
        return file(QUEUE_PROSPECT);
    }

    @Bean
    public Queue prospectGenereDlq() {
        return QueueBuilder.durable(QUEUE_PROSPECT + ".dlq").build();
    }

    @Bean
    public Binding prospectGenereBinding() {
        return BindingBuilder.bind(prospectGenereQueue()).to(moduleCommercial()).with(CLE_PROSPECT);
    }

    @Bean
    public Binding prospectGenereDlqBinding() {
        return BindingBuilder.bind(prospectGenereDlq()).to(moduleCommercialDlx()).with(QUEUE_PROSPECT + ".dlq");
    }


    /** Messages JSON ; le type attendu vient du paramètre de la méthode qui écoute, pas d'un en-tête du message. */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter convertisseur = new Jackson2JsonMessageConverter(objectMapper);
        convertisseur.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return convertisseur;
    }

    private static Queue file(String nom) {
        return QueueBuilder.durable(nom).deadLetterExchange(DLX).deadLetterRoutingKey(nom + ".dlq").build();
    }
}
