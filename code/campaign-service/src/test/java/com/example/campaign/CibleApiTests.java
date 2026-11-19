package com.example.campaign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.repository.CampagneRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CibleApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    CampagneRepository repo;

    String creerCampagne() throws Exception {
        String json = mvc.perform(post("/campagnes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"C\",\"objectif\":\"VENTES\",\"dateDebut\":\"" + LocalDate.now() + "\",\"dateFin\":\""
                                + LocalDate.now().plusDays(30) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    String creerCanal(String type) throws Exception {
        String json = mvc.perform(post("/canaux").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Canal " + type + "\",\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    void activer(String id) {
        repo.findById(UUID.fromString(id)).orElseThrow().setStatut(StatutCampagne.ACTIVE);
    }

    @Test
    void publicClientsExistantsExigeUneCategorie() throws Exception {
        String id = creerCampagne();
        UUID categorie = UUID.randomUUID();
        mvc.perform(post("/campagnes/" + id + "/publics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CLIENTS_EXISTANTS\",\"categorieClientId\":\"" + categorie + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categorieClientId").value(categorie.toString()))
                .andExpect(jsonPath("$.campagneId").value(id));
        mvc.perform(post("/campagnes/" + id + "/publics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CLIENTS_EXISTANTS\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/campagnes/" + id + "/publics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CLIENTS_EXISTANTS\",\"categorieClientId\":\"" + categorie + "\",\"ageMin\":18}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void publicExterneSeCibleParCriteresPasParCategorie() throws Exception {
        String id = creerCampagne();
        mvc.perform(post("/campagnes/" + id + "/publics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PUBLIC_EXTERNE\",\"ageMin\":25,\"ageMax\":40,\"localisation\":\"Tunis\",\"centresInteret\":\"ingénierie\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ageMin").value(25));
        mvc.perform(post("/campagnes/" + id + "/publics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PUBLIC_EXTERNE\",\"categorieClientId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/campagnes/" + id + "/publics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PUBLIC_EXTERNE\",\"ageMin\":50,\"ageMax\":20}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/campagnes/" + id + "/publics")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void publicsSeModifientSeulementEnBrouillon() throws Exception {
        String id = creerCampagne();
        String json = mvc.perform(post("/campagnes/" + id + "/publics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PUBLIC_EXTERNE\",\"localisation\":\"Tunis\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String publicId = JsonPath.read(json, "$.id");
        activer(id);
        mvc.perform(post("/campagnes/" + id + "/publics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PUBLIC_EXTERNE\"}")).andExpect(status().isConflict());
        mvc.perform(delete("/publics/" + publicId)).andExpect(status().isConflict());
    }

    @Test
    void supprimerUnPublicEnBrouillon() throws Exception {
        String id = creerCampagne();
        String json = mvc.perform(post("/campagnes/" + id + "/publics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PUBLIC_EXTERNE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String publicId = JsonPath.read(json, "$.id");
        mvc.perform(delete("/publics/" + publicId)).andExpect(status().isNoContent());
        mvc.perform(delete("/publics/" + publicId)).andExpect(status().isNotFound());
        mvc.perform(get("/campagnes/" + id + "/publics")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void creerEtListerLesCanaux() throws Exception {
        creerCanal("EMAIL");
        mvc.perform(get("/canaux")).andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("EMAIL"));
        mvc.perform(post("/canaux").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"X\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/canaux").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"X\",\"type\":\"TELEGRAM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void associerEtRetirerUnCanal() throws Exception {
        String id = creerCampagne();
        String canal = creerCanal("EMAIL");
        mvc.perform(post("/campagnes/" + id + "/canaux").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canalId\":\"" + canal + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(canal));
        mvc.perform(post("/campagnes/" + id + "/canaux").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canalId\":\"" + canal + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/campagnes/" + id + "/canaux").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canalId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/campagnes/" + id + "/canaux/" + canal)).andExpect(status().isNoContent());
        mvc.perform(delete("/campagnes/" + id + "/canaux/" + canal)).andExpect(status().isNotFound());
    }

    @Test
    void canauxSeModifientSeulementEnBrouillon() throws Exception {
        String id = creerCampagne();
        String canal = creerCanal("EMAIL");
        mvc.perform(post("/campagnes/" + id + "/canaux").contentType(MediaType.APPLICATION_JSON)
                .content("{\"canalId\":\"" + canal + "\"}")).andExpect(status().isCreated());
        activer(id);
        mvc.perform(delete("/campagnes/" + id + "/canaux/" + canal)).andExpect(status().isConflict());
        String autre = creerCanal("FACEBOOK");
        mvc.perform(post("/campagnes/" + id + "/canaux").contentType(MediaType.APPLICATION_JSON)
                .content("{\"canalId\":\"" + autre + "\"}")).andExpect(status().isConflict());
    }
}
