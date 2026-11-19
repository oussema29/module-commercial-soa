package com.example.campaign.config;

import com.example.campaign.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité du service. Deux modes (propriété app.security.mode) :
 *  - none (défaut en développement) : aucune authentification, toutes les routes sont ouvertes ;
 *  - jwt : chaque route d'employé exige un JWT valide (en-tête Authorization: Bearer ...) ; les rôles
 *    autorisés de chaque route sont écrits plus bas (droitsDesRoutes), repris des x-roles du contrat.
 * Les routes /public/** et la santé du service n'exigent jamais d'identité.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "jwt")
    public SecurityFilterChain chaineJwt(HttpSecurity http, ObjectMapper json,
                                         @Value("${app.security.jwt-secret}") String secret,
                                         @Value("${app.security.role-claim:role}") String claimRole) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers("/public/**", "/actuator/health", "/actuator/health/**",
                            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    droitsDesRoutes(a);
                    a.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(decodeur(secret)).jwtAuthenticationConverter(convertisseur(claimRole)))
                        .authenticationEntryPoint((req, res, e) -> erreur(json, res, HttpStatus.UNAUTHORIZED, "Authentification requise"))
                        .accessDeniedHandler((req, res, e) -> erreur(json, res, HttpStatus.FORBIDDEN, "Rôle non autorisé pour cette route")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> erreur(json, res, HttpStatus.UNAUTHORIZED, "Authentification requise"))
                        .accessDeniedHandler((req, res, ex) -> erreur(json, res, HttpStatus.FORBIDDEN, "Rôle non autorisé pour cette route")));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "none", matchIfMissing = true)
    public SecurityFilterChain chaineOuverte(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Rôles autorisés de chaque route d'employé, repris des x-roles du contrat (contrat_api/campaign.yaml).
     * Ils sont vérifiés AVANT la validation du corps de la requête : un rôle non autorisé reçoit toujours 403, jamais 422.
     * Pour changer le droit d'une route : modifier le contrat, puis la ligne correspondante ci-dessous.
     * Les routes /public/** n'y figurent pas : elles n'exigent aucune identité.
     */
    private static void droitsDesRoutes(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry a) {
        a.requestMatchers(HttpMethod.POST, "/campagnes").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.GET, "/campagnes").hasAnyRole("COMMERCIAL", "MARKETING", "RESPONSABLE");
        a.requestMatchers(HttpMethod.GET, "/campagnes/{id}").hasAnyRole("COMMERCIAL", "MARKETING", "RESPONSABLE");
        a.requestMatchers(HttpMethod.PUT, "/campagnes/{id}").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.DELETE, "/campagnes/{id}").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.POST, "/campagnes/{id}/lancer").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.POST, "/campagnes/{id}/suspendre").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.POST, "/campagnes/{id}/reprendre").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.POST, "/campagnes/{id}/arreter").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.GET, "/campagnes/{id}/invitations").hasAnyRole("MARKETING", "RESPONSABLE");
        a.requestMatchers(HttpMethod.POST, "/campagnes/{id}/invitations/renvoyer").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.GET, "/campagnes/{id}/reponses").hasAnyRole("COMMERCIAL", "MARKETING", "RESPONSABLE");
        a.requestMatchers(HttpMethod.GET, "/campagnes/{id}/publics").hasAnyRole("MARKETING", "RESPONSABLE");
        a.requestMatchers(HttpMethod.POST, "/campagnes/{id}/publics").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.DELETE, "/publics/{id}").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.GET, "/canaux").hasAnyRole("ADMIN", "MARKETING", "RESPONSABLE");
        a.requestMatchers(HttpMethod.POST, "/canaux").hasAnyRole("ADMIN");
        a.requestMatchers(HttpMethod.POST, "/campagnes/{id}/canaux").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.DELETE, "/campagnes/{id}/canaux/{canalId}").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.GET, "/campagnes/{id}/contenus").hasAnyRole("MARKETING", "RESPONSABLE");
        a.requestMatchers(HttpMethod.POST, "/campagnes/{id}/contenus").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.PUT, "/contenus/{id}").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.DELETE, "/contenus/{id}").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.POST, "/contenus/{id}/produits").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.DELETE, "/contenus/{id}/produits/{produitId}").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.GET, "/campagnes/{id}/budget").hasAnyRole("MARKETING", "RESPONSABLE");
        a.requestMatchers(HttpMethod.PUT, "/campagnes/{id}/budget").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.PUT, "/campagnes/{id}/mesures").hasAnyRole("MARKETING");
        a.requestMatchers(HttpMethod.GET, "/campagnes/{id}/performance").hasAnyRole("MARKETING", "RESPONSABLE");
        a.requestMatchers(HttpMethod.GET, "/campagnes/{id}/rapport").hasAnyRole("MARKETING", "RESPONSABLE");
    }

    /** Vérifie la signature (HS256, secret partagé avec le service Authentication) et la date d'expiration. */
    private static JwtDecoder decodeur(String secret) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decodeur.setJwtValidator(JwtValidators.createDefault());
        return decodeur;
    }

    /**
     * Le rôle est lu dans le claim indiqué par app.security.role-claim (« role » par défaut), sous forme de texte ou de liste.
     * Si l'autre trinôme utilise d'autres noms de rôles, c'est ICI (et seulement ici) qu'on les convertit.
     */
    private static JwtAuthenticationConverter convertisseur(String claimRole) {
        JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
        Converter<Jwt, Collection<GrantedAuthority>> roles = jwt -> {
            Object valeur = jwt.getClaim(claimRole);
            List<?> liste = valeur instanceof List<?> l ? l : valeur == null ? List.of() : List.of(valeur);
            return liste.stream()
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + String.valueOf(r).toUpperCase()))
                    .toList();
        };
        convertisseur.setJwtGrantedAuthoritiesConverter(roles);
        return convertisseur;
    }

    private static void erreur(ObjectMapper json, HttpServletResponse res, HttpStatus statut, String message)
            throws java.io.IOException {
        res.setStatus(statut.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), new ErrorResponse(statut.value(), message, Instant.now()));
    }
}
