package com.example.quotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.quotation.client.ClientCustomer;
import com.example.quotation.client.CustomerClient;
import com.example.quotation.entity.Devis;
import com.example.quotation.entity.Produit;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.entity.Taxe;
import com.example.quotation.messaging.DevisAccepteEvent;
import com.example.quotation.messaging.EditeurEvenements;
import com.example.quotation.repository.DevisRepository;
import com.example.quotation.repository.ProduitRepository;
import com.example.quotation.repository.TaxeRepository;
import com.example.quotation.service.EmailException;
import com.example.quotation.service.EnvoiEmail;
import com.example.quotation.service.ExpirationService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class EnvoiEtReponseApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TaxeRepository taxes;
    @Autowired
    ProduitRepository produits;
    @Autowired
    DevisRepository devisRepo;
    @Autowired
    ExpirationService expiration;

    // Les services voisins et l'envoi d'email sont SIMULÉS dans les tests
    @MockBean
    CustomerClient customer;
    @MockBean
    EnvoiEmail email;
    @MockBean
    EditeurEvenements evenements;

    final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void clientParDefaut() {
        when(customer.trouver(any())).thenReturn(
                new ClientCustomer(clientId, "Société Atlas", null, "contact@atlas.tn", "CLIENT"));
    }

    /** Crée un devis avec une ligne ; campagneId peut être null. */
    private String creerDevis(UUID campagneId) throws Exception {
        Taxe t = new Taxe();
        t.setTauxTVA(new BigDecimal("19"));
        taxes.save(t);
        Produit p = new Produit();
        p.setNom("Étude");
        p.setPrix(new BigDecimal("1000.000"));
        p.setTaxe(t);
        produits.save(p);

        String corps = "{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + LocalDate.now() + "\""
                + (campagneId == null ? "" : ",\"campagneId\":\"" + campagneId + "\"") + "}";
        String json = mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":1}"))
                .andExpect(status().isCreated());
        return id;
    }

    private String envoyer(String id) throws Exception {
        mvc.perform(post("/devis/" + id + "/envoyer")).andExpect(status().isOk());
        return devisRepo.findById(UUID.fromString(id)).orElseThrow().getTokenReponse();
    }

    @Test
    void envoyerPasseLeDevisAEnvoyeSansExposerLeJeton() throws Exception {
        String id = creerDevis(null);
        mvc.perform(post("/devis/" + id + "/envoyer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ENVOYE"))
                .andExpect(jsonPath("$.tokenReponse").doesNotExist());
        verify(email).envoyer(any(), any(), any(), any());
        assertThat(devisRepo.findById(UUID.fromString(id)).orElseThrow().getTokenReponse()).isNotBlank();
    }

    @Test
    void envoyerUnDevisDejaEnvoyeEst409() throws Exception {
        String id = creerDevis(null);
        envoyer(id);
        mvc.perform(post("/devis/" + id + "/envoyer")).andExpect(status().isConflict());
    }

    @Test
    void emailInvalideOuDateDepasseeEst422() throws Exception {
        Devis mauvaisEmail = new Devis();
        mauvaisEmail.setReference("DEV-TEST-0001");
        mauvaisEmail.setDate(LocalDate.now());
        mauvaisEmail.setDateValidite(LocalDate.now().plusDays(5));
        mauvaisEmail.setClientEmail("pas-un-email");
        devisRepo.save(mauvaisEmail);
        mvc.perform(post("/devis/" + mauvaisEmail.getId() + "/envoyer")).andExpect(status().isUnprocessableEntity());

        Devis perime = new Devis();
        perime.setReference("DEV-TEST-0002");
        perime.setDate(LocalDate.now().minusDays(10));
        perime.setDateValidite(LocalDate.now().minusDays(1));
        perime.setClientEmail("contact@atlas.tn");
        devisRepo.save(perime);
        mvc.perform(post("/devis/" + perime.getId() + "/envoyer")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void echecDEnvoiDonne503EtLeDevisResteBrouillon() throws Exception {
        String id = creerDevis(null);
        doThrow(new EmailException("Boîte pleine")).when(email).envoyer(any(), any(), any(), any());
        mvc.perform(post("/devis/" + id + "/envoyer"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Boîte pleine")));
        mvc.perform(get("/devis/" + id)).andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    void lePdfSeTelecharge() throws Exception {
        String id = creerDevis(null);
        byte[] pdf = mvc.perform(get("/devis/" + id + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void leClientConsulteSonDevisSansIdentite() throws Exception {
        String id = creerDevis(null);
        String token = envoyer(id);
        mvc.perform(get("/public/reponse/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ENVOYE"))
                .andExpect(jsonPath("$.clientNom").value("Société Atlas"))
                .andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.tokenReponse").doesNotExist());
        mvc.perform(get("/public/reponse/jeton-inconnu")).andExpect(status().isNotFound());
    }

    @Test
    void accepterUneSeuleFoisEtPublierLEvenementSiCampagne() throws Exception {
        UUID campagneId = UUID.randomUUID();
        String id = creerDevis(campagneId);
        String token = envoyer(id);

        mvc.perform(post("/public/reponse/" + token + "/accepter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTE"));

        ArgumentCaptor<DevisAccepteEvent> capture = ArgumentCaptor.forClass(DevisAccepteEvent.class);
        verify(evenements, times(1)).devisAccepte(capture.capture());
        DevisAccepteEvent e = capture.getValue();
        assertThat(e.type()).isEqualTo("DevisAccepte");
        assertThat(e.eventId()).isNotNull();
        assertThat(e.campagneId()).isEqualTo(campagneId);
        assertThat(e.clientId()).isEqualTo(clientId);
        assertThat(e.montantHT()).isEqualByComparingTo("1000.000");
        assertThat(e.montantTTC()).isEqualByComparingTo("1190.000");

        mvc.perform(post("/public/reponse/" + token + "/accepter"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ce devis ne peut plus être accepté"));
        mvc.perform(post("/public/reponse/" + token + "/refuser")).andExpect(status().isConflict());
        verify(evenements, times(1)).devisAccepte(any());
    }

    @Test
    void aucunEvenementSiLeDevisNaPasDeCampagne() throws Exception {
        String token = envoyer(creerDevis(null));
        mvc.perform(post("/public/reponse/" + token + "/accepter")).andExpect(status().isOk());
        verify(evenements, never()).devisAccepte(any());
    }

    @Test
    void refuserPuisPlusRien() throws Exception {
        String token = envoyer(creerDevis(UUID.randomUUID()));
        mvc.perform(post("/public/reponse/" + token + "/refuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSE"));
        mvc.perform(post("/public/reponse/" + token + "/accepter")).andExpect(status().isConflict());
        verify(evenements, never()).devisAccepte(any());
    }

    @Test
    void lendemainDeLaDateDeValiditeLeLienNeMarchePlusEtLeDevisExpire() throws Exception {
        String id = creerDevis(null);   // dateValidite = aujourd'hui : le jour même, le lien fonctionne encore
        String token = envoyer(id);
        Devis d = devisRepo.findById(UUID.fromString(id)).orElseThrow();
        d.setDateValidite(LocalDate.now().minusDays(1));   // on simule « le lendemain »

        mvc.perform(post("/public/reponse/" + token + "/accepter"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ce devis ne peut plus être accepté"));

        assertThat(expiration.expirerLesDevisEchus()).isEqualTo(1);
        assertThat(d.getStatut()).isEqualTo(StatutDevis.EXPIRE);
    }

    @Test
    void laTacheDExpirationNeTouchePasLesBrouillons() throws Exception {
        String id = creerDevis(null);
        Devis d = devisRepo.findById(UUID.fromString(id)).orElseThrow();
        d.setDateValidite(LocalDate.now().minusDays(1));
        assertThat(expiration.expirerLesDevisEchus()).isZero();
        assertThat(d.getStatut()).isEqualTo(StatutDevis.BROUILLON);
    }
}
