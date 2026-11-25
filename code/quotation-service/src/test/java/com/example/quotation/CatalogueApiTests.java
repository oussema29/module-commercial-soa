package com.example.quotation;

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
class CatalogueApiTests {

    @Autowired
    MockMvc mvc;

    private String creerTaxe(String taux) throws Exception {
        String json = mvc.perform(post("/taxes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tauxTVA\":" + taux + ",\"description\":\"TVA\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String corpsProduit(String nom, String prix, String taxeId) {
        return "{\"nom\":\"" + nom + "\",\"description\":\"Forfait\",\"prix\":" + prix + ",\"taxeId\":\"" + taxeId + "\"}";
    }

    @Test
    void creerEtListerDesTaxes() throws Exception {
        creerTaxe("19");
        mvc.perform(get("/taxes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tauxTVA").value(19));
    }

    @Test
    void tauxHorsDeZeroACentEst422() throws Exception {
        mvc.perform(post("/taxes").contentType(MediaType.APPLICATION_JSON).content("{\"tauxTVA\":120}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/taxes").contentType(MediaType.APPLICATION_JSON).content("{\"tauxTVA\":-1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void modifierUneTaxe() throws Exception {
        String id = creerTaxe("19");
        mvc.perform(put("/taxes/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"tauxTVA\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tauxTVA").value(7));
        mvc.perform(put("/taxes/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"tauxTVA\":7}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void creerUnProduitAvecUnPrixDecimal() throws Exception {
        String taxeId = creerTaxe("19");
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON)
                        .content(corpsProduit("Étude de faisabilité", "1250.500", taxeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prix").value(1250.5))
                .andExpect(jsonPath("$.taxeId").value(taxeId));
    }

    @Test
    void prixNegatifOuPlusDeTroisDecimalesEst422() throws Exception {
        String taxeId = creerTaxe("19");
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON).content(corpsProduit("A", "-5", taxeId)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON).content(corpsProduit("A", "1.2345", taxeId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void taxeInconnueEst404() throws Exception {
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON)
                        .content(corpsProduit("A", "10", UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void listeConsulterModifierSupprimer() throws Exception {
        String taxeId = creerTaxe("19");
        String json = mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON)
                        .content(corpsProduit("Audit", "500", taxeId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");
        mvc.perform(post("/produits").contentType(MediaType.APPLICATION_JSON).content(corpsProduit("Zeta", "10", taxeId)))
                .andExpect(status().isCreated());

        mvc.perform(get("/produits?page=0&taille=1"))
                .andExpect(jsonPath("$.contenu.length()").value(1))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/produits/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Audit"));
        mvc.perform(put("/produits/" + id).contentType(MediaType.APPLICATION_JSON).content(corpsProduit("Audit 2", "600", taxeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Audit 2"));
        mvc.perform(delete("/produits/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/produits/" + id)).andExpect(status().isNotFound());
    }
}
