package com.example.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CategorieApiTests {

    @Autowired
    MockMvc mvc;

    private String creerCategorie(String nom) throws Exception {
        String json = mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\",\"description\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    @Test
    void creerPuisLister() throws Exception {
        creerCategorie("VIP");
        mvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("VIP"));
    }

    @Test
    void sansNomEst422() throws Exception {
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void modifier() throws Exception {
        String id = creerCategorie("VIP");
        mvc.perform(put("/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Premium\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Premium"));
    }

    @Test
    void supprimerUneCategorieLibre() throws Exception {
        String id = creerCategorie("Libre");
        mvc.perform(delete("/categories/" + id)).andExpect(status().isNoContent());
        mvc.perform(put("/categories/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void supprimerUneCategorieUtiliseeEst409() throws Exception {
        String id = creerCategorie("Utilisee");
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"a@atlas.tn\",\"categorieId\":\"" + id + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/categories/" + id)).andExpect(status().isConflict());
    }
}
