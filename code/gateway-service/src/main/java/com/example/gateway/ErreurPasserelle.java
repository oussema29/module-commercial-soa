package com.example.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Toute erreur de la passerelle a le corps commun du module : { code, message, timestamp }. */
@Component
@Order(-2)
public class ErreurPasserelle implements ErrorWebExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ErreurPasserelle.class);

    private final ObjectMapper json;

    /** La suite des causes de l'erreur, pour les logs : « A <- B <- C ». */
    private static String chaine(Throwable erreur) {
        StringBuilder s = new StringBuilder();
        for (Throwable e = erreur; e != null; e = e.getCause()) {
            s.append(s.length() == 0 ? "" : " <- ").append(e.getClass().getSimpleName());
        }
        return s.toString();
    }

    public ErreurPasserelle(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable erreur) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(erreur);
        }
        HttpStatus statut = statut(erreur);
        LOG.warn("{} {} -> {} ({})", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), statut.value(), chaine(erreur));
        String message = switch (statut) {
            case NOT_FOUND -> "Route inconnue";
            case SERVICE_UNAVAILABLE -> "Service indisponible";
            case GATEWAY_TIMEOUT -> "Le service n'a pas répondu à temps";
            default -> "Erreur de la passerelle";
        };
        return ecrire(exchange, statut, message);
    }

    static HttpStatus statut(Throwable erreur) {
        for (Throwable e = erreur; e != null; e = e.getCause()) {
            if (e instanceof ResponseStatusException r && r.getStatusCode().value() < 500) {
                return HttpStatus.valueOf(r.getStatusCode().value());
            }
            // Service arrêté : connexion refusée, nom introuvable ou adresse injoignable (dans Docker, un conteneur arrêté
            // n'a plus d'adresse : « no route to host » constaté en réel)
            if (e instanceof ConnectException || e instanceof UnknownHostException || e instanceof NoRouteToHostException
                    || e.getClass().getSimpleName().equals("PrematureCloseException")) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (e instanceof TimeoutException || e instanceof SocketTimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (e instanceof ResponseStatusException r) {
                return HttpStatus.valueOf(r.getStatusCode().value());
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private Mono<Void> ecrire(ServerWebExchange exchange, HttpStatus statut, String message) {
        exchange.getResponse().setStatusCode(statut);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] corps = json.writeValueAsBytes(Map.of("code", statut.value(), "message", message, "timestamp", Instant.now().toString()));
            DataBuffer tampon = exchange.getResponse().bufferFactory().wrap(corps);
            return exchange.getResponse().writeWith(Mono.just(tampon));
        } catch (Exception e) {
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory()
                    .wrap(("{\"code\":" + statut.value() + "}").getBytes(StandardCharsets.UTF_8))));
        }
    }
}
