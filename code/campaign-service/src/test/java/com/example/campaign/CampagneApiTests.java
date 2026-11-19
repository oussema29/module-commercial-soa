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

import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.repository.CampagneRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CampagneApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    CampagneRepository repo;

    String corps(String nom, LocalDate debut, LocalDate fin) {
        return "{\"nom\":\"" + nom + "\",\"objectif\":\"FIDELISATION\",\"dateDebut\":\"" + debut + "\",\"dateFin\":\"" + fin + "\"}";
    }

    String creer() throws Exception {
        String json = mvc.perform(post("/campagnes").contentType(MediaType.APPLICATION_JSON)
                        .content(corps("Offre fin d'année", LocalDate.now(), LocalDate.now().plusDays(30))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    /** Passe la campagne dans un état donné sans passer par le lancement (étape A3). */
    void forcerStatut(String id, StatutCampagne statut) {
        Campagne c = repo.findById(UUID.fromString(id)).orElseThrow();
        c.setStatut(statut);
    }

    @Test
    void creationDonneUnBrouillonAvecSaPerformance() throws Exception {
        mvc.perform(post("/campagnes").contentType(MediaType.APPLICATION_JSON)
                        .content(corps("Offre", LocalDate.now(), LocalDate.now().plusDays(30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.creePar").value("00000000-0000-0000-0000-000000000001"));
        String id = creer();
        // la ligne de performance existe dès la création : le budget prévu est vide, le dépensé à 0
        mvc.perform(get("/campagnes/" + id + "/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montantPrevu").doesNotExist())
                .andExpect(jsonPath("$.montantDepense").value(0));
    }

    @Test
    void dateDeFinAvantLeDebutOuChampAbsentEst422() throws Exception {
        mvc.perform(post("/campagnes").contentType(MediaType.APPLICATION_JSON)
                        .content(corps("X", LocalDate.now().plusDays(5), LocalDate.now())))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/campagnes").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"X\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listerConsulterEtInconnu() throws Exception {
        String id = creer();
        mvc.perform(get("/campagnes")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/campagnes/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Offre fin d'année"));
        mvc.perform(get("/campagnes/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void modifierSeulementEnBrouillon() throws Exception {
        String id = creer();
        mvc.perform(put("/campagnes/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(corps("Nouveau nom", LocalDate.now(), LocalDate.now().plusDays(60))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Nouveau nom"));
        forcerStatut(id, StatutCampagne.ACTIVE);
        mvc.perform(put("/campagnes/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(corps("Autre", LocalDate.now(), LocalDate.now().plusDays(60))))
                .andExpect(status().isConflict());
    }

    @Test
    void supprimerUnBrouillonSupprimeToutEtRefuseLesAutres() throws Exception {
        String id = creer();
        mvc.perform(post("/campagnes/" + id + "/contenus").contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"T\"}")).andExpect(status().isCreated());
        mvc.perform(delete("/campagnes/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/campagnes/" + id)).andExpect(status().isNotFound());

        String actif = creer();
        forcerStatut(actif, StatutCampagne.ACTIVE);
        mvc.perform(delete("/campagnes/" + actif)).andExpect(status().isConflict());
    }

    @Test
    void cycleDeVie() throws Exception {
        String id = creer();
        // un brouillon ne se suspend pas, ne reprend pas, ne s'arrête pas
        mvc.perform(post("/campagnes/" + id + "/suspendre")).andExpect(status().isConflict());
        mvc.perform(post("/campagnes/" + id + "/reprendre")).andExpect(status().isConflict());
        mvc.perform(post("/campagnes/" + id + "/arreter")).andExpect(status().isConflict());

        forcerStatut(id, StatutCampagne.ACTIVE);
        mvc.perform(post("/campagnes/" + id + "/suspendre")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SUSPENDUE"));
        mvc.perform(post("/campagnes/" + id + "/suspendre")).andExpect(status().isConflict());
        mvc.perform(post("/campagnes/" + id + "/reprendre")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ACTIVE"));
        mvc.perform(post("/campagnes/" + id + "/suspendre")).andExpect(status().isOk());
        mvc.perform(post("/campagnes/" + id + "/arreter")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TERMINEE"));
        // la fin est définitive
        mvc.perform(post("/campagnes/" + id + "/reprendre")).andExpect(status().isConflict());
        mvc.perform(post("/campagnes/" + id + "/arreter")).andExpect(status().isConflict());
    }

    @Test
    void reprendreRefuseApresLaDateDeFin() throws Exception {
        String id = creer();
        forcerStatut(id, StatutCampagne.SUSPENDUE);
        repo.findById(UUID.fromString(id)).orElseThrow().setDateFin(LocalDate.now().minusDays(1));
        mvc.perform(post("/campagnes/" + id + "/reprendre")).andExpect(status().isConflict());
    }
}
