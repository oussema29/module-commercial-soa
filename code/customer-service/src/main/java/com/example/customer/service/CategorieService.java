package com.example.customer.service;

import com.example.customer.dto.CategorieReponse;
import com.example.customer.dto.CategorieSaisie;
import com.example.customer.entity.CategorieClient;
import com.example.customer.exception.ApiException;
import com.example.customer.repository.CategorieRepository;
import com.example.customer.repository.ClientRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategorieService {

    private final CategorieRepository categories;
    private final ClientRepository clients;

    public CategorieService(CategorieRepository categories, ClientRepository clients) {
        this.categories = categories;
        this.clients = clients;
    }

    public CategorieReponse creer(CategorieSaisie saisie) {
        CategorieClient c = new CategorieClient();
        c.setNom(saisie.nom());
        c.setDescription(saisie.description());
        return CategorieReponse.de(categories.save(c));
    }

    @Transactional(readOnly = true)
    public List<CategorieReponse> lister() {
        return categories.findAll().stream().map(CategorieReponse::de).toList();
    }

    public CategorieReponse modifier(UUID id, CategorieSaisie saisie) {
        CategorieClient c = trouver(id);
        c.setNom(saisie.nom());
        c.setDescription(saisie.description());
        return CategorieReponse.de(c);
    }

    public void supprimer(UUID id) {
        CategorieClient c = trouver(id);
        if (clients.existsByCategorieId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Cette catégorie est utilisée par des clients");
        }
        categories.delete(c);
    }

    private CategorieClient trouver(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Catégorie introuvable"));
    }
}
