package com.example.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.customer.repository.ContactRepository;
import com.example.customer.repository.InteractionRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InteractionApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    ContactRepository contacts;
    @Autowired
    InteractionRepository interactions;
    @Autowired
    EntityManager em;

    private String creerClient() throws Exception {
        String json = mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"" + UUID.randomUUID() + "@atlas.tn\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void auteurVientDeLaConfigurationMemeSiLeCorpsEnContientUn() throws Exception {
        String clientId = creerClient();
        mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPEL\",\"date\":\"2026-11-18T10:00:00Z\","
                                + "\"description\":\"Premier appel\",\"auteurId\":\"11111111-1111-1111-1111-111111111111\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.auteurId").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.type").value("APPEL"));
    }

    @Test
    void listeDeLaPlusRecenteALaPlusAncienne() throws Exception {
        String clientId = creerClient();
        for (String date : new String[]{"2026-11-01T10:00:00Z", "2026-11-20T10:00:00Z", "2026-11-10T10:00:00Z"}) {
            mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"EMAIL\",\"date\":\"" + date + "\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/clients/" + clientId + "/interactions"))
                .andExpect(jsonPath("$[0].date").value("2026-11-20T10:00:00Z"))
                .andExpect(jsonPath("$[2].date").value("2026-11-01T10:00:00Z"));
    }

    @Test
    void modifierSupprimerEtErreurs() throws Exception {
        String clientId = creerClient();
        String json = mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"REUNION\",\"date\":\"2026-11-18T10:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");

        mvc.perform(put("/interactions/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPEL\",\"date\":\"2026-11-19T10:00:00Z\",\"description\":\"Corrigé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("APPEL"))
                .andExpect(jsonPath("$.description").value("Corrigé"));

        mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPEL\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INCONNU\",\"date\":\"2026-11-18T10:00:00Z\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/interactions/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/interactions/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/clients/" + UUID.randomUUID() + "/interactions")).andExpect(status().isNotFound());
    }

    @Test
    void supprimerUnClientSupprimeSesContactsEtInteractions() throws Exception {
        String clientId = creerClient();
        mvc.perform(post("/clients/" + clientId + "/contacts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Ben Salah\"}")).andExpect(status().isCreated());
        mvc.perform(post("/clients/" + clientId + "/interactions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"APPEL\",\"date\":\"2026-11-18T10:00:00Z\"}")).andExpect(status().isCreated());
        em.flush();
        assertThat(contacts.count()).isEqualTo(1);
        assertThat(interactions.count()).isEqualTo(1);

        mvc.perform(delete("/clients/" + clientId)).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(contacts.count()).isZero();
        assertThat(interactions.count()).isZero();
    }
}
