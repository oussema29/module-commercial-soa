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
class ContenuBudgetApiTests {

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

    String creerContenu(String campagneId) throws Exception {
        String json = mvc.perform(post("/campagnes/" + campagneId + "/contenus").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titre\":\"Offre\",\"texte\":\"-20 %\",\"videoUrl\":\"https://video.example/1\",\"lien\":\"https://atlas.example\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void ajouterListerModifierSupprimerUnContenu() throws Exception {
        String id = creerCampagne();
        String contenu = creerContenu(id);
        mvc.perform(get("/campagnes/" + id + "/contenus"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].campagneId").value(id))
                .andExpect(jsonPath("$[0].produits.length()").value(0));
        mvc.perform(put("/contenus/" + contenu).contentType(MediaType.APPLICATION_JSON).content("{\"titre\":\"Offre 2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.titre").value("Offre 2"));
        mvc.perform(post("/campagnes/" + id + "/contenus").contentType(MediaType.APPLICATION_JSON).content("{\"texte\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/contenus/" + contenu)).andExpect(status().isNoContent());
        mvc.perform(delete("/contenus/" + contenu)).andExpect(status().isNotFound());
    }

    @Test
    void lesContenusRestentModifiablesMemeSurUneCampagneActive() throws Exception {
        String id = creerCampagne();
        String contenu = creerContenu(id);
        repo.findById(UUID.fromString(id)).orElseThrow().setStatut(StatutCampagne.ACTIVE);
        mvc.perform(put("/contenus/" + contenu).contentType(MediaType.APPLICATION_JSON).content("{\"titre\":\"Corrigé\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void produitsMisEnAvantCopiesEtRetires() throws Exception {
        String id = creerCampagne();
        String contenu = creerContenu(id);
        UUID produit = UUID.randomUUID();
        mvc.perform(post("/contenus/" + contenu + "/produits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + produit + "\",\"produitNom\":\"Étude de faisabilité\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produits.length()").value(1))
                .andExpect(jsonPath("$.produits[0].produitNom").value("Étude de faisabilité"));
        mvc.perform(post("/contenus/" + contenu + "/produits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + produit + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/contenus/" + contenu + "/produits/" + produit)).andExpect(status().isNoContent());
        mvc.perform(delete("/contenus/" + contenu + "/produits/" + produit)).andExpect(status().isNotFound());
    }

    @Test
    void definirEtConsulterLeBudgetPrevu() throws Exception {
        String id = creerCampagne();
        mvc.perform(put("/campagnes/" + id + "/budget").contentType(MediaType.APPLICATION_JSON).content("{\"montantPrevu\":500.250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montantPrevu").value(500.25))
                .andExpect(jsonPath("$.montantDepense").value(0));
        // 0 est un budget valide
        mvc.perform(put("/campagnes/" + id + "/budget").contentType(MediaType.APPLICATION_JSON).content("{\"montantPrevu\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.montantPrevu").value(0));
        mvc.perform(put("/campagnes/" + id + "/budget").contentType(MediaType.APPLICATION_JSON).content("{\"montantPrevu\":-1}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/campagnes/" + id + "/budget").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/campagnes/" + UUID.randomUUID() + "/budget")).andExpect(status().isNotFound());
    }
}
