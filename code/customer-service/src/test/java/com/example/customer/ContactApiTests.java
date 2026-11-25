package com.example.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContactApiTests {

    @Autowired
    MockMvc mvc;

    private String creerClient() throws Exception {
        String json = mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"" + UUID.randomUUID() + "@atlas.tn\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void ajouterListerModifierSupprimer() throws Exception {
        String clientId = creerClient();
        String json = mvc.perform(post("/clients/" + clientId + "/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Ben Salah\",\"prenom\":\"Karim\",\"fonction\":\"Directeur\",\"email\":\"karim@atlas.tn\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andReturn().getResponse().getContentAsString();
        String contactId = JsonPath.read(json, "$.id");

        mvc.perform(get("/clients/" + clientId + "/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fonction").value("Directeur"));

        mvc.perform(put("/contacts/" + contactId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Ben Salah\",\"fonction\":\"Acheteur\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonction").value("Acheteur"));

        mvc.perform(delete("/contacts/" + contactId)).andExpect(status().isNoContent());
        mvc.perform(get("/clients/" + clientId + "/contacts")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void clientOuContactInconnuEst404() throws Exception {
        mvc.perform(get("/clients/" + UUID.randomUUID() + "/contacts")).andExpect(status().isNotFound());
        mvc.perform(put("/contacts/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/contacts/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void sansNomOuEmailInvalideEst422() throws Exception {
        String clientId = creerClient();
        mvc.perform(post("/clients/" + clientId + "/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prenom\":\"Karim\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/clients/" + clientId + "/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"X\",\"email\":\"pas-un-email\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
