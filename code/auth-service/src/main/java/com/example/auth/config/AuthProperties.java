package com.example.auth.config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Réglages lus dans application.yml (préfixe app.auth). */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private long dureeJetonSecondes = 3600;
    private List<UtilisateurConfig> utilisateurs = new ArrayList<>();

    public long getDureeJetonSecondes() { return dureeJetonSecondes; }
    public void setDureeJetonSecondes(long dureeJetonSecondes) { this.dureeJetonSecondes = dureeJetonSecondes; }

    public List<UtilisateurConfig> getUtilisateurs() { return utilisateurs; }
    public void setUtilisateurs(List<UtilisateurConfig> utilisateurs) { this.utilisateurs = utilisateurs; }

    /** Un utilisateur fictif déclaré dans application.yml. */
    public static class UtilisateurConfig {

        private UUID id;
        private String email;
        private String motDePasse;
        private String role;

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getMotDePasse() { return motDePasse; }
        public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
