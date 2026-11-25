package com.example.quotation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.quotation.client.ClientCustomer;
import com.example.quotation.client.CustomerClient;
import com.example.quotation.entity.Produit;
import com.example.quotation.entity.Taxe;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.ProduitRepository;
import com.example.quotation.repository.TaxeRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DevisApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TaxeRepository taxes;
    @Autowired
    ProduitRepository produits;

    // Customer est SIMULÉ : les tests ne dépendent jamais du vrai service
    @MockBean
    CustomerClient customer;

    final UUID clientId = UUID.randomUUID();
    final String demain = LocalDate.now().plusDays(10).toString();

    @BeforeEach
    void clientParDefaut() {
        when(customer.trouver(any())).thenReturn(
                new ClientCustomer(clientId, "Société Atlas", null, "contact@atlas.tn", "CLIENT"));
    }

    private String creerDevis() throws Exception {
        String json = mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private Produit creerProduit(String prix, String tva) {
        Taxe t = new Taxe();
        t.setTauxTVA(new BigDecimal(tva));
        taxes.save(t);
        Produit p = new Produit();
        p.setNom("Étude");
        p.setPrix(new BigDecimal(prix));
        p.setTaxe(t);
        return produits.save(p);
    }

    @Test
    void creationCopieLesCoordonneesDuClient() throws Exception {
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.clientNom").value("Société Atlas"))
                .andExpect(jsonPath("$.clientEmail").value("contact@atlas.tn"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("DEV-" + LocalDate.now().getYear() + "-")))
                .andExpect(jsonPath("$.totalTTC").value(0))
                .andExpect(jsonPath("$.tokenReponse").doesNotExist());
    }

    @Test
    void lesReferencesSAugmentent() throws Exception {
        creerDevis();
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.endsWith("-0002")));
    }

    @Test
    void clientInactifEst422() throws Exception {
        when(customer.trouver(any())).thenReturn(new ClientCustomer(clientId, "Atlas", null, "a@a.tn", "INACTIF"));
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void clientIntrouvableEst404() throws Exception {
        when(customer.trouver(any())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Client introuvable"));
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerEnPanneDonne503EtNeCreeRien() throws Exception {
        when(customer.trouver(any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service Customer indisponible"));
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
        mvc.perform(get("/devis")).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void dateDeValiditeDansLePasseEst422() throws Exception {
        mvc.perform(post("/devis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"dateValidite\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ajouterUneLigneCopieLeCatalogueEtCalculeLesTotaux() throws Exception {
        Produit p = creerProduit("100.000", "19");
        String id = creerDevis();
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":2,\"remise\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.lignes[0].designation").value("Étude"))
                .andExpect(jsonPath("$.lignes[0].totalLigneHT").value(180))
                .andExpect(jsonPath("$.totalHT").value(180))
                .andExpect(jsonPath("$.totalTVA").value(34.2))
                .andExpect(jsonPath("$.totalTTC").value(214.2));

        // Le prix du catalogue change : la ligne du devis garde sa copie
        p.setPrix(new BigDecimal("999.000"));
        produits.save(p);
        mvc.perform(get("/devis/" + id))
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(100))
                .andExpect(jsonPath("$.totalTTC").value(214.2));
    }

    @Test
    void produitInconnuEst404EtQuantiteNulleEst422() throws Exception {
        Produit p = creerProduit("100", "19");
        String id = creerDevis();
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + UUID.randomUUID() + "\",\"quantite\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":0}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":1,\"remise\":150}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void modifierEtSupprimerUneLigneRecalculentLesTotaux() throws Exception {
        Produit p = creerProduit("100.000", "19");
        String id = creerDevis();
        String json = mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ligneId = JsonPath.read(json, "$.lignes[0].id");

        mvc.perform(put("/devis/" + id + "/lignes/" + ligneId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantite\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHT").value(300));

        mvc.perform(delete("/devis/" + id + "/lignes/" + ligneId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes.length()").value(0))
                .andExpect(jsonPath("$.totalTTC").value(0));
        mvc.perform(delete("/devis/" + id + "/lignes/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void modifierLeDevisEnBrouillon() throws Exception {
        String id = creerDevis();
        String autre = LocalDate.now().plusDays(30).toString();
        mvc.perform(put("/devis/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateValidite\":\"" + autre + "\",\"remiseGlobale\":5,\"clientEmail\":\"nouveau@atlas.tn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateValidite").value(autre))
                .andExpect(jsonPath("$.remiseGlobale").value(5))
                .andExpect(jsonPath("$.clientEmail").value("nouveau@atlas.tn"));
    }

    @Test
    void unDevisAnnuleNeSeModifiePlusEtNeSAnnuleQuUneFois() throws Exception {
        Produit p = creerProduit("100", "19");
        String id = creerDevis();
        mvc.perform(post("/devis/" + id + "/annuler")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ANNULE"));
        mvc.perform(post("/devis/" + id + "/lignes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produitId\":\"" + p.getId() + "\",\"quantite\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/devis/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateValidite\":\"" + demain + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/devis/" + id + "/annuler")).andExpect(status().isConflict());
    }

    @Test
    void listeAvecFiltres() throws Exception {
        String a = creerDevis();
        creerDevis();
        mvc.perform(post("/devis/" + a + "/annuler")).andExpect(status().isOk());

        mvc.perform(get("/devis")).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/devis?statut=ANNULE")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/devis?clientId=" + clientId)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/devis?clientId=" + UUID.randomUUID())).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/devis?page=0&taille=1")).andExpect(jsonPath("$.contenu.length()").value(1));
        mvc.perform(get("/devis/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
