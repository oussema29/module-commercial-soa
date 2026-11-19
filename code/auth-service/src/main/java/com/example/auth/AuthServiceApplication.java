package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Service de login PROVISOIRE du module commercial : il vérifie un email et un mot de passe et renvoie un JWT.
 * Les trois autres services ne dépendent PAS de lui : ils vérifient seulement la signature du jeton, avec le même secret.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
