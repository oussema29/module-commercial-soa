package com.example.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Deux modes (app.security.mode) :
 *  - none (défaut) : la passerelle ne vérifie rien, elle route seulement ;
 *  - jwt : elle vérifie la SIGNATURE et l'expiration du jeton et répond 401 sans le transmettre s'il est absent ou invalide.
 * Elle ne contrôle PAS les rôles : ce sont les services qui refusent (403) un rôle non autorisé, car un service appelé
 * directement doit rester protégé. Les routes /public/** et /login n'exigent jamais de jeton.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "jwt")
    public SecurityWebFilterChain chaineJwt(ServerHttpSecurity http, ObjectMapper json,
                                            @Value("${app.security.jwt-secret}") String secret) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(e -> e
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/public/**", "/login", "/actuator/health", "/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtDecoder(decodeur(secret)))
                        .authenticationEntryPoint((exchange, e) -> erreur(json, exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Authentification requise")));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "none", matchIfMissing = true)
    public SecurityWebFilterChain chaineOuverte(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable).authorizeExchange(e -> e.anyExchange().permitAll());
        return http.build();
    }

    private static ReactiveJwtDecoder decodeur(String secret) {
        return NimbusReactiveJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static Mono<Void> erreur(ObjectMapper json, org.springframework.http.server.reactive.ServerHttpResponse reponse,
                                     HttpStatus statut, String message) {
        reponse.setStatusCode(statut);
        reponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] corps = json.writeValueAsBytes(Map.of("code", statut.value(), "message", message, "timestamp", Instant.now().toString()));
            DataBuffer tampon = reponse.bufferFactory().wrap(corps);
            return reponse.writeWith(Mono.just(tampon));
        } catch (Exception e) {
            return reponse.setComplete();
        }
    }
}
