package com.example.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Test unitaire : quelle erreur réseau donne quel code. Les trois premières ont été constatées en réel. */
class ErreurPasserelleTest {

    @Test
    void unServiceInjoignableDonne503() {
        assertThat(ErreurPasserelle.statut(new ConnectException("Connection refused"))).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ErreurPasserelle.statut(new UnknownHostException("customer-service"))).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ErreurPasserelle.statut(new NoRouteToHostException("No route to host"))).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void lErreurReseauEstReconnueMemeEnveloppee() {
        Throwable enveloppee = new IllegalStateException("echec", new RuntimeException("ailleurs", new NoRouteToHostException("No route to host")));
        assertThat(ErreurPasserelle.statut(enveloppee)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void unDelaiDepasseDonne504() {
        assertThat(ErreurPasserelle.statut(new TimeoutException("trop long"))).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void uneRouteInconnueDonne404EtLeResteDonne500() {
        assertThat(ErreurPasserelle.statut(new ResponseStatusException(HttpStatus.NOT_FOUND))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErreurPasserelle.statut(new IllegalStateException("inattendu"))).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
