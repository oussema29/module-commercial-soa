package com.example.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.InvitationClient;
import com.example.campaign.entity.StatutCampagne;
import com.example.campaign.repository.CampagneRepository;
import com.example.campaign.service.FinCampagneService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PerformanceApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    CampagneRepository campagnes;
    @Autowired
    FinCampagneService fin;

    String creerCampagne() throws Exception {
        String json = mvc.perform(post("/campagnes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Offre\",\"objectif\":\"VENTES\",\"dateDebut\":\"" + LocalDate.now() + "\",\"dateFin\":\""
                                + LocalDate.now().plusDays(30) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void mesuresRemplacentLesTotauxEtDonnentLeMemeResultatDeuxFois() throws Exception {
        String id = creerCampagne();
        String corps = "{\"impressions\":10000,\"clics\":250,\"montantDepense\":100}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/campagnes/" + id + "/mesures").contentType(MediaType.APPLICATION_JSON).content(corps))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.impressions").value(10000))
                    .andExpect(jsonPath("$.clics").value(250))
                    .andExpect(jsonPath("$.montantDepense").value(100))
                    .andExpect(jsonPath("$.cpc").value(0.4));
        }
    }

    @Test
    void mesuresNegativesOuAbsentesSontRefusees() throws Exception {
        String id = creerCampagne();
        mvc.perform(put("/campagnes/" + id + "/mesures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impressions\":-1,\"clics\":0,\"montantDepense\":0}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/campagnes/" + id + "/mesures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impressions\":1,\"clics\":1,\"montantDepense\":-5}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/campagnes/" + id + "/mesures").contentType(MediaType.APPLICATION_JSON).content("{\"clics\":1}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/campagnes/" + UUID.randomUUID() + "/mesures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impressions\":1,\"clics\":1,\"montantDepense\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void performanceCalculeRoiCpcEtClientsInteresses() throws Exception {
        String id = creerCampagne();
        Campagne c = campagnes.findById(UUID.fromString(id)).orElseThrow();
        // 3 invitations dont 2 répondues ; 2 devis acceptés pour 350 de chiffre d'affaires
        for (int i = 0; i < 3; i++) {
            InvitationClient inv = new InvitationClient();
            inv.setCampagne(c);
            inv.setClientId(UUID.randomUUID());
            inv.setJeton("jeton-" + i);
            if (i < 2) {
                inv.setRepondueLe(Instant.now());
            }
            c.getInvitations().add(inv);
        }
        c.getPerformance().setDevisAcceptes(2);
        c.getPerformance().setChiffreAffairesAttribue(new BigDecimal("350"));
        c.getPerformance().setProspectsGeneres(4);
        mvc.perform(put("/campagnes/" + id + "/mesures").contentType(MediaType.APPLICATION_JSON)
                .content("{\"impressions\":1000,\"clics\":50,\"montantDepense\":100}")).andExpect(status().isOk());
        mvc.perform(put("/campagnes/" + id + "/budget").contentType(MediaType.APPLICATION_JSON).content("{\"montantPrevu\":500}"));
        campagnes.flush();

        mvc.perform(get("/campagnes/" + id + "/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campagneId").value(id))
                .andExpect(jsonPath("$.montantPrevu").value(500))
                .andExpect(jsonPath("$.clientsInteresses").value(2))
                .andExpect(jsonPath("$.prospectsGeneres").value(4))
                .andExpect(jsonPath("$.devisAcceptes").value(2))
                .andExpect(jsonPath("$.chiffreAffairesAttribue").value(350))
                .andExpect(jsonPath("$.cpc").value(2))
                .andExpect(jsonPath("$.roi").value(2.5));
    }

    @Test
    void roiEtCpcSontNulsQuandOnNePeutPasLesCalculer() throws Exception {
        String id = creerCampagne();
        mvc.perform(get("/campagnes/" + id + "/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpc").doesNotExist())
                .andExpect(jsonPath("$.roi").doesNotExist())
                .andExpect(jsonPath("$.clientsInteresses").value(0));
        mvc.perform(get("/campagnes/" + UUID.randomUUID() + "/performance")).andExpect(status().isNotFound());
    }

    @Test
    void lePdfDuRapportSeTelecharge() throws Exception {
        String id = creerCampagne();
        byte[] pdf = mvc.perform(get("/campagnes/" + id + "/rapport"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        mvc.perform(get("/campagnes/" + UUID.randomUUID() + "/rapport")).andExpect(status().isNotFound());
    }

    @Test
    void laTacheDeFinTermineLesCampagnesActivesOuSuspenduesEchues() throws Exception {
        String active = creerCampagne();
        String suspendue = creerCampagne();
        String brouillon = creerCampagne();
        String nonEchue = creerCampagne();
        campagnes.findById(UUID.fromString(active)).orElseThrow().setStatut(StatutCampagne.ACTIVE);
        campagnes.findById(UUID.fromString(suspendue)).orElseThrow().setStatut(StatutCampagne.SUSPENDUE);
        campagnes.findById(UUID.fromString(nonEchue)).orElseThrow().setStatut(StatutCampagne.ACTIVE);
        for (String id : new String[]{active, suspendue, brouillon}) {
            campagnes.findById(UUID.fromString(id)).orElseThrow().setDateFin(LocalDate.now().minusDays(1));
            campagnes.findById(UUID.fromString(id)).orElseThrow().setDateDebut(LocalDate.now().minusDays(10));
        }
        campagnes.flush();

        assertThat(fin.terminerLesCampagnesEchues()).isEqualTo(2);
        mvc.perform(get("/campagnes/" + active)).andExpect(jsonPath("$.statut").value("TERMINEE"));
        mvc.perform(get("/campagnes/" + suspendue)).andExpect(jsonPath("$.statut").value("TERMINEE"));
        mvc.perform(get("/campagnes/" + brouillon)).andExpect(jsonPath("$.statut").value("BROUILLON"));
        mvc.perform(get("/campagnes/" + nonEchue)).andExpect(jsonPath("$.statut").value("ACTIVE"));
        // relancée, elle ne change rien de plus
        assertThat(fin.terminerLesCampagnesEchues()).isZero();
    }
}
