package com.example.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.campaign.entity.InvitationClient;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.messaging.EditeurEvenements;
import com.example.campaign.messaging.ProspectGenereEvent;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.repository.InvitationRepository;
import com.example.campaign.service.EnvoiEmail;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReponsePubliqueApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    CampagneRepository campagnes;
    @Autowired
    InvitationRepository invitations;

    @MockBean
    CustomerClient customer;
    @MockBean
    EnvoiEmail email;
    @MockBean
    EditeurEvenements evenements;

    final DestinataireCustomer alpha = new DestinataireCustomer(UUID.randomUUID(), "Alpha", "alpha@x.tn");

    @BeforeEach
    void clientsParDefaut() {
        when(customer.destinataires(any())).thenReturn(List.of(alpha));
    }

    String json(String chemin, String corps) throws Exception {
        return mvc.perform(post(chemin).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    /** Campagne ACTIVE avec une invitation envoyée à Alpha. */
    String campagneActive() throws Exception {
        String id = JsonPath.read(json("/campagnes", "{\"nom\":\"Offre\",\"objectif\":\"FIDELISATION\",\"dateDebut\":\""
                + LocalDate.now() + "\",\"dateFin\":\"" + LocalDate.now().plusDays(30) + "\"}"), "$.id");
        String canal = JsonPath.read(json("/canaux", "{\"nom\":\"Email\",\"type\":\"EMAIL\"}"), "$.id");
        json("/campagnes/" + id + "/canaux", "{\"canalId\":\"" + canal + "\"}");
        json("/campagnes/" + id + "/publics", "{\"type\":\"CLIENTS_EXISTANTS\",\"categorieClientId\":\"" + UUID.randomUUID() + "\"}");
        json("/campagnes/" + id + "/contenus", "{\"titre\":\"-20 %\",\"texte\":\"Offre\"}");
        mvc.perform(put("/campagnes/" + id + "/budget").contentType(MediaType.APPLICATION_JSON).content("{\"montantPrevu\":0}"));
        mvc.perform(post("/campagnes/" + id + "/lancer")).andExpect(status().isOk());
        return id;
    }

    String jeton(String campagneId) {
        return invitations.findByCampagneId(UUID.fromString(campagneId)).get(0).getJeton();
    }

    static final String FORMULAIRE = "{\"type\":\"ENTREPRISE\",\"nom\":\"Société Atlas\",\"nomContact\":\"Karim Ben Salah\","
            + "\"email\":\"karim@atlas.tn\",\"telephone\":\"+216 20 123 456\"}";

    @Test
    void laPagePubliqueMontreLeContenuSansIdentite() throws Exception {
        String id = campagneActive();
        mvc.perform(get("/public/campagnes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Offre"))
                .andExpect(jsonPath("$.contenus.length()").value(1))
                .andExpect(jsonPath("$.contenus[0].titre").value("-20 %"));
        mvc.perform(get("/public/campagnes/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void laPagePubliqueEstFermeeSiLaCampagneNEstPlusActive() throws Exception {
        String id = campagneActive();
        campagnes.findById(UUID.fromString(id)).orElseThrow().setStatut(StatutCampagne.TERMINEE);
        mvc.perform(get("/public/campagnes/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La campagne n'est plus active"));
    }

    @Test
    void leFormulairePubliePropspectGenereEtCompteLeProspect() throws Exception {
        String id = campagneActive();
        mvc.perform(post("/public/campagnes/" + id + "/reponse").contentType(MediaType.APPLICATION_JSON).content(FORMULAIRE))
                .andExpect(status().isCreated());
        ArgumentCaptor<ProspectGenereEvent> capture = ArgumentCaptor.forClass(ProspectGenereEvent.class);
        verify(evenements, times(1)).prospectGenere(capture.capture());
        ProspectGenereEvent e = capture.getValue();
        assertThat(e.type()).isEqualTo("ProspectGenere");
        assertThat(e.campagneId()).isEqualTo(UUID.fromString(id));
        assertThat(e.typeClient()).isEqualTo("ENTREPRISE");
        assertThat(e.email()).isEqualTo("karim@atlas.tn");
        assertThat(e.nomContact()).isEqualTo("Karim Ben Salah");
        assertThat(e.eventId()).isNotNull();
        assertThat(campagnes.findById(UUID.fromString(id)).orElseThrow().getPerformance().getProspectsGeneres()).isEqualTo(1);
    }

    @Test
    void formulaireInvalideOuCampagneNonActive() throws Exception {
        String id = campagneActive();
        mvc.perform(post("/public/campagnes/" + id + "/reponse").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PARTICULIER\",\"nom\":\"Ali\",\"email\":\"pas-un-email\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/public/campagnes/" + id + "/reponse").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Ali\",\"email\":\"ali@x.tn\"}"))
                .andExpect(status().isUnprocessableEntity());
        verify(evenements, never()).prospectGenere(any());

        campagnes.findById(UUID.fromString(id)).orElseThrow().setStatut(StatutCampagne.SUSPENDUE);
        mvc.perform(post("/public/campagnes/" + id + "/reponse").contentType(MediaType.APPLICATION_JSON).content(FORMULAIRE))
                .andExpect(status().isConflict());
        verify(evenements, never()).prospectGenere(any());
    }

    @Test
    void jeSuisInteresseEstCompteUneSeuleFois() throws Exception {
        String id = campagneActive();
        String jeton = jeton(id);
        mvc.perform(post("/public/invitations/" + jeton + "/reponse")).andExpect(status().isOk());
        mvc.perform(post("/public/invitations/" + jeton + "/reponse"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vous avez déjà répondu"));

        mvc.perform(get("/campagnes/" + id + "/reponses"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].clientId").value(alpha.clientId().toString()))
                .andExpect(jsonPath("$.contenu[0].repondueLe").exists())
                .andExpect(jsonPath("$.contenu[0].jeton").doesNotExist());
    }

    @Test
    void jetonInconnuOuCampagneTerminee() throws Exception {
        mvc.perform(post("/public/invitations/jeton-inconnu/reponse")).andExpect(status().isNotFound());
        String id = campagneActive();
        String jeton = jeton(id);
        campagnes.findById(UUID.fromString(id)).orElseThrow().setStatut(StatutCampagne.TERMINEE);
        mvc.perform(post("/public/invitations/" + jeton + "/reponse"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La campagne n'est plus active"));
    }

    @Test
    void uneCampagneDontLaDateDeFinEstPasseeRefuseMemeSiElleEstEncoreActive() throws Exception {
        String id = campagneActive();
        campagnes.findById(UUID.fromString(id)).orElseThrow().setDateFin(LocalDate.now().minusDays(1));
        mvc.perform(post("/public/campagnes/" + id + "/reponse").contentType(MediaType.APPLICATION_JSON).content(FORMULAIRE))
                .andExpect(status().isConflict());
    }
}
