package com.example.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.campaign.client.CustomerClient;
import com.example.campaign.client.DestinataireCustomer;
import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.exception.ApiException;
import com.example.campaign.messaging.EditeurEvenements;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.InvitationRepository;
import com.example.campaign.service.EmailException;
import com.example.campaign.service.EnvoiEmail;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LancementApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    CampagneRepository campagnes;
    @Autowired
    InvitationRepository invitations;

    // Customer et l'envoi d'email sont SIMULÉS : les tests ne dépendent d'aucun autre service
    @MockBean
    CustomerClient customer;
    @MockBean
    EnvoiEmail email;
    @MockBean
    EditeurEvenements evenements;

    final UUID categorie = UUID.randomUUID();
    final DestinataireCustomer alpha = new DestinataireCustomer(UUID.randomUUID(), "Alpha", "alpha@x.tn");
    final DestinataireCustomer beta = new DestinataireCustomer(UUID.randomUUID(), "Beta", "beta@x.tn");
    final DestinataireCustomer gamma = new DestinataireCustomer(UUID.randomUUID(), "Gamma", "gamma@x.tn");

    @BeforeEach
    void clientsParDefaut() {
        when(customer.destinataires(any())).thenReturn(List.of(alpha, beta, gamma));
    }

    String json(String chemin, String corps) throws Exception {
        return mvc.perform(post(chemin).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    /** Campagne prête à être lancée : canal EMAIL, public clients existants, un contenu, budget défini. */
    String campagnePrete() throws Exception {
        String id = JsonPath.read(json("/campagnes", "{\"nom\":\"Offre\",\"objectif\":\"FIDELISATION\",\"dateDebut\":\""
                + LocalDate.now() + "\",\"dateFin\":\"" + LocalDate.now().plusDays(30) + "\"}"), "$.id");
        String canal = JsonPath.read(json("/canaux", "{\"nom\":\"Email\",\"type\":\"EMAIL\"}"), "$.id");
        json("/campagnes/" + id + "/canaux", "{\"canalId\":\"" + canal + "\"}");
        json("/campagnes/" + id + "/publics", "{\"type\":\"CLIENTS_EXISTANTS\",\"categorieClientId\":\"" + categorie + "\"}");
        json("/campagnes/" + id + "/contenus", "{\"titre\":\"-20 % ce mois-ci\",\"texte\":\"Offre fidélité\"}");
        mvc.perform(put("/campagnes/" + id + "/budget").contentType(MediaType.APPLICATION_JSON).content("{\"montantPrevu\":500}"))
                .andExpect(status().isOk());
        return id;
    }

    void statutEst(String id, String attendu) throws Exception {
        mvc.perform(get("/campagnes/" + id)).andExpect(jsonPath("$.statut").value(attendu));
    }

    @Test
    void lancementNominalCreeUneInvitationParClient() throws Exception {
        String id = campagnePrete();
        mvc.perform(post("/campagnes/" + id + "/lancer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envoisReussis").value(3))
                .andExpect(jsonPath("$.echecs.length()").value(0));
        statutEst(id, "ACTIVE");
        verify(email, times(3)).envoyer(any(), any(), any(), any());
        assertThat(invitations.findByCampagneId(UUID.fromString(id))).hasSize(3)
                .allSatisfy(i -> {
                    assertThat(i.getJeton()).isNotBlank();
                    assertThat(i.getEnvoyeLe()).isNotNull();
                });
    }

    @Test
    void lancerDeuxFoisEst409() throws Exception {
        String id = campagnePrete();
        mvc.perform(post("/campagnes/" + id + "/lancer")).andExpect(status().isOk());
        mvc.perform(post("/campagnes/" + id + "/lancer")).andExpect(status().isConflict());
    }

    @Test
    void sansCanalSansContenuOuSansBudgetEst422EtLaCampagneResteBrouillon() throws Exception {
        // sans canal
        String sansCanal = JsonPath.read(json("/campagnes", "{\"nom\":\"A\",\"objectif\":\"VENTES\",\"dateDebut\":\""
                + LocalDate.now() + "\",\"dateFin\":\"" + LocalDate.now().plusDays(5) + "\"}"), "$.id");
        json("/campagnes/" + sansCanal + "/contenus", "{\"titre\":\"T\"}");
        mvc.perform(put("/campagnes/" + sansCanal + "/budget").contentType(MediaType.APPLICATION_JSON).content("{\"montantPrevu\":0}"));
        mvc.perform(post("/campagnes/" + sansCanal + "/lancer")).andExpect(status().isUnprocessableEntity());
        statutEst(sansCanal, "BROUILLON");

        // sans contenu
        String id = campagnePrete();
        Campagne c = campagnes.findById(UUID.fromString(id)).orElseThrow();
        c.getContenus().clear();
        mvc.perform(post("/campagnes/" + id + "/lancer")).andExpect(status().isUnprocessableEntity());

        // sans budget défini
        String sansBudget = campagnePrete();
        campagnes.findById(UUID.fromString(sansBudget)).orElseThrow().getPerformance().setMontantPrevu(null);
        mvc.perform(post("/campagnes/" + sansBudget + "/lancer")).andExpect(status().isUnprocessableEntity());
        statutEst(sansBudget, "BROUILLON");
        assertThat(invitations.count()).isZero();
        verify(customer, never()).destinataires(any());
    }

    @Test
    void budgetADefiniZeroEstAccepte() throws Exception {
        String id = campagnePrete();
        mvc.perform(put("/campagnes/" + id + "/budget").contentType(MediaType.APPLICATION_JSON).content("{\"montantPrevu\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post("/campagnes/" + id + "/lancer")).andExpect(status().isOk());
    }

    @Test
    void canalIncoherentAvecLePublicEst422() throws Exception {
        // public externe avec seulement un canal EMAIL
        String id = campagnePrete();
        json("/campagnes/" + id + "/publics", "{\"type\":\"PUBLIC_EXTERNE\",\"localisation\":\"Tunis\"}");
        mvc.perform(post("/campagnes/" + id + "/lancer"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("FACEBOOK")));
        statutEst(id, "BROUILLON");
    }

    @Test
    void dateDeDebutFutureOuDateDeFinPasseeEst409() throws Exception {
        String futur = campagnePrete();
        campagnes.findById(UUID.fromString(futur)).orElseThrow().setDateDebut(LocalDate.now().plusDays(2));
        mvc.perform(post("/campagnes/" + futur + "/lancer")).andExpect(status().isConflict());
        statutEst(futur, "BROUILLON");

        String passe = campagnePrete();
        Campagne c = campagnes.findById(UUID.fromString(passe)).orElseThrow();
        c.setDateDebut(LocalDate.now().minusDays(10));
        c.setDateFin(LocalDate.now().minusDays(1));
        mvc.perform(post("/campagnes/" + passe + "/lancer")).andExpect(status().isConflict());
    }

    @Test
    void customerIndisponibleDonne503EtLaCampagneResteBrouillon() throws Exception {
        String id = campagnePrete();
        when(customer.destinataires(any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service Customer indisponible"));
        mvc.perform(post("/campagnes/" + id + "/lancer"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
        statutEst(id, "BROUILLON");
        assertThat(invitations.count()).isZero();
        verify(email, never()).envoyer(any(), any(), any(), any());
    }

    @Test
    void categorieInconnueChezCustomerDonne422() throws Exception {
        String id = campagnePrete();
        when(customer.destinataires(any())).thenThrow(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Catégorie de clients introuvable chez Customer"));
        mvc.perform(post("/campagnes/" + id + "/lancer")).andExpect(status().isUnprocessableEntity());
        statutEst(id, "BROUILLON");
    }

    @Test
    void publicExterneSeulNAppellePasCustomerEtLaCampagneDevientActive() throws Exception {
        String id = JsonPath.read(json("/campagnes", "{\"nom\":\"Ext\",\"objectif\":\"ACQUISITION\",\"dateDebut\":\""
                + LocalDate.now() + "\",\"dateFin\":\"" + LocalDate.now().plusDays(30) + "\"}"), "$.id");
        String canal = JsonPath.read(json("/canaux", "{\"nom\":\"FB\",\"type\":\"FACEBOOK\"}"), "$.id");
        json("/campagnes/" + id + "/canaux", "{\"canalId\":\"" + canal + "\"}");
        json("/campagnes/" + id + "/publics", "{\"type\":\"PUBLIC_EXTERNE\",\"ageMin\":25,\"ageMax\":40}");
        json("/campagnes/" + id + "/contenus", "{\"titre\":\"T\"}");
        mvc.perform(put("/campagnes/" + id + "/budget").contentType(MediaType.APPLICATION_JSON).content("{\"montantPrevu\":100}"));
        mvc.perform(post("/campagnes/" + id + "/lancer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envoisReussis").value(0));
        statutEst(id, "ACTIVE");
        verify(customer, never()).destinataires(any());
    }

    @Test
    void listeVideAccepteeZeroInvitation() throws Exception {
        String id = campagnePrete();
        when(customer.destinataires(any())).thenReturn(List.of());
        mvc.perform(post("/campagnes/" + id + "/lancer")).andExpect(status().isOk()).andExpect(jsonPath("$.envoisReussis").value(0));
        statutEst(id, "ACTIVE");
    }

    @Test
    void unEnvoiEnEchecNeBloquePasLeLancement() throws Exception {
        String id = campagnePrete();
        doThrow(new EmailException("Adresse email invalide")).when(email).envoyer(org.mockito.ArgumentMatchers.eq("beta@x.tn"), any(), any(), any());
        mvc.perform(post("/campagnes/" + id + "/lancer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envoisReussis").value(2))
                .andExpect(jsonPath("$.echecs.length()").value(1))
                .andExpect(jsonPath("$.echecs[0].clientId").value(beta.clientId().toString()))
                .andExpect(jsonPath("$.echecs[0].cause").value("Adresse email invalide"));
        statutEst(id, "ACTIVE");

        mvc.perform(get("/campagnes/" + id + "/invitations?envoi=ECHEC"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].erreurEnvoi").value("Adresse email invalide"))
                .andExpect(jsonPath("$.contenu[0].envoyeLe").doesNotExist())
                .andExpect(jsonPath("$.contenu[0].jeton").doesNotExist());
        mvc.perform(get("/campagnes/" + id + "/invitations?envoi=REUSSI")).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/campagnes/" + id + "/invitations")).andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void renvoyerNeTraiteQueLesEchecsEtPasLesClientsInactifs() throws Exception {
        String id = campagnePrete();
        doThrow(new EmailException("Boîte pleine")).when(email).envoyer(org.mockito.ArgumentMatchers.eq("beta@x.tn"), any(), any(), any());
        doThrow(new EmailException("Boîte pleine")).when(email).envoyer(org.mockito.ArgumentMatchers.eq("gamma@x.tn"), any(), any(), any());
        mvc.perform(post("/campagnes/" + id + "/lancer")).andExpect(jsonPath("$.envoisReussis").value(1));

        // Gamma devient inactif (Customer ne le renvoie plus) ; Beta a corrigé son email
        when(customer.destinataires(any())).thenReturn(List.of(alpha, new DestinataireCustomer(beta.clientId(), "Beta", "beta-corrige@x.tn")));
        org.mockito.Mockito.reset(email);
        mvc.perform(post("/campagnes/" + id + "/invitations/renvoyer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envoisReussis").value(1))
                .andExpect(jsonPath("$.echecs.length()").value(0));
        // une seule invitation relancée, à l'email corrigé ; Alpha (déjà envoyé) et Gamma (inactif) ne sont pas touchés
        verify(email, times(1)).envoyer(any(), any(), any(), any());
        verify(email).envoyer(org.mockito.ArgumentMatchers.eq("beta-corrige@x.tn"), any(), any(), any());
        mvc.perform(get("/campagnes/" + id + "/invitations?envoi=ECHEC")).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void renvoyerExigeUneCampagneActiveEtCustomerDisponible() throws Exception {
        String id = campagnePrete();
        mvc.perform(post("/campagnes/" + id + "/invitations/renvoyer")).andExpect(status().isConflict());
        mvc.perform(post("/campagnes/" + id + "/lancer")).andExpect(status().isOk());
        when(customer.destinataires(any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service Customer indisponible"));
        mvc.perform(post("/campagnes/" + id + "/invitations/renvoyer")).andExpect(status().isServiceUnavailable());
        campagnes.findById(UUID.fromString(id)).orElseThrow().setStatut(StatutCampagne.TERMINEE);
        mvc.perform(post("/campagnes/" + id + "/invitations/renvoyer")).andExpect(status().isConflict());
    }

    @Test
    void campagneInconnue() throws Exception {
        mvc.perform(post("/campagnes/" + UUID.randomUUID() + "/lancer")).andExpect(status().isNotFound());
        mvc.perform(get("/campagnes/" + UUID.randomUUID() + "/invitations")).andExpect(status().isNotFound());
        mvc.perform(get("/campagnes/" + UUID.randomUUID() + "/reponses")).andExpect(status().isNotFound());
    }
}
