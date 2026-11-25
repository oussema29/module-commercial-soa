package com.example.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class ClientApiTests {

    @Autowired
    MockMvc mvc;

    private String creerCategorie(String nom) throws Exception {
        String json = mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String creerClient(String nom, String email, String categorieId, String statut) throws Exception {
        String corps = "{\"type\":\"ENTREPRISE\",\"nom\":\"" + nom + "\",\"email\":\"" + email + "\""
                + (categorieId == null ? "" : ",\"categorieId\":\"" + categorieId + "\"")
                + (statut == null ? "" : ",\"statut\":\"" + statut + "\"") + "}";
        String json = mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void creationValideDonneUnProspect() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Société Atlas\",\"email\":\"contact@atlas.tn\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("PROSPECT"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void sansEmailOuEmailMalFormeEst422() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"pas-un-email\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emailEnDoubleSansTenirCompteDeLaCasseNiDesEspacesEst409() throws Exception {
        creerClient("Atlas", "Ali@Mail.tn", null, null);
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PARTICULIER\",\"nom\":\"Ali\",\"email\":\" ali@mail.tn \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void categorieInconnueEst404() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas\",\"email\":\"a@a.tn\",\"categorieId\":\""
                                + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void modifierNeChangePasLeStatut() throws Exception {
        String id = creerClient("Atlas", "a@atlas.tn", null, "CLIENT");
        mvc.perform(put("/clients/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Atlas 2\",\"email\":\"a@atlas.tn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Atlas 2"))
                .andExpect(jsonPath("$.statut").value("CLIENT"));
    }

    @Test
    void modifierAvecLEmailDUnAutreEst409() throws Exception {
        creerClient("Un", "un@x.tn", null, null);
        String id2 = creerClient("Deux", "deux@x.tn", null, null);
        mvc.perform(put("/clients/" + id2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTREPRISE\",\"nom\":\"Deux\",\"email\":\"UN@x.tn\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void changerLeStatut() throws Exception {
        String id = creerClient("Atlas", "a@atlas.tn", null, null);
        mvc.perform(patch("/clients/" + id + "/statut").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"CLIENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLIENT"));
        mvc.perform(patch("/clients/" + id + "/statut").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"INCONNU\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechercheAvecFiltresEtPagination() throws Exception {
        String vip = creerCategorie("VIP");
        creerClient("Alpha", "alpha@x.tn", vip, "CLIENT");
        creerClient("Beta", "beta@x.tn", vip, null);
        creerClient("Gamma", "gamma@x.tn", null, null);

        mvc.perform(get("/clients?statut=PROSPECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/clients?categorieId=" + vip))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/clients?q=ALPHA"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].nom").value("Alpha"));
        mvc.perform(get("/clients?page=0&taille=2"))
                .andExpect(jsonPath("$.contenu.length()").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.taille").value(2));
    }

    @Test
    void destinatairesSeulementLesClientsDeLaCategorie() throws Exception {
        String vip = creerCategorie("VIP");
        creerClient("Alpha", "alpha@x.tn", vip, "CLIENT");
        creerClient("Beta", "beta@x.tn", vip, null);          // PROSPECT : exclu
        creerClient("Gamma", "gamma@x.tn", null, "CLIENT");   // autre catégorie : exclu

        mvc.perform(get("/clients/destinataires?categorieId=" + vip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenu[0].nom").value("Alpha"))
                .andExpect(jsonPath("$.contenu[0].clientId").exists())
                .andExpect(jsonPath("$.contenu[0].email").value("alpha@x.tn"))
                .andExpect(jsonPath("$.contenu[0].telephone").doesNotExist());
    }

    @Test
    void destinatairesCategorieInconnueEst404EtCategorieVideDonneUneListeVide() throws Exception {
        mvc.perform(get("/clients/destinataires?categorieId=" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
        String vide = creerCategorie("Vide");
        mvc.perform(get("/clients/destinataires?categorieId=" + vide))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.contenu.length()").value(0));
    }

    @Test
    void consulterSupprimerEtIdentifiantMalForme() throws Exception {
        String id = creerClient("Atlas", "a@atlas.tn", null, null);
        mvc.perform(get("/clients/" + id)).andExpect(status().isOk());
        mvc.perform(delete("/clients/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/clients/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/clients/pas-un-uuid")).andExpect(status().isBadRequest());
    }
}
