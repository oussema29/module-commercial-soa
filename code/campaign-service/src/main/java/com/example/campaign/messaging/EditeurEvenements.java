package com.example.campaign.messaging;

/**
 * Publication des événements de Campaign. Aujourd'hui : écriture dans les logs seulement.
 * À l'étape E1, une autre implémentation enverra le message à RabbitMQ, sans changer le reste du code.
 */
public interface EditeurEvenements {

    void prospectGenere(ProspectGenereEvent evenement);
}
