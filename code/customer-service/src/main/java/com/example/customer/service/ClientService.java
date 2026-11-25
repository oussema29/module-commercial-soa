package com.example.customer.service;

import com.example.customer.dto.ChangementStatut;
import com.example.customer.dto.ClientCreation;
import com.example.customer.dto.ClientModification;
import com.example.customer.dto.ClientReponse;
import com.example.customer.dto.Destinataire;
import com.example.customer.dto.PageResponse;
import com.example.customer.entity.CategorieClient;
import com.example.customer.entity.Client;
import com.example.customer.entity.StatutClient;
import com.example.customer.entity.TypeClient;
import com.example.customer.exception.ApiException;
import com.example.customer.repository.CategorieRepository;
import com.example.customer.repository.ClientRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService {

    private final ClientRepository clients;
    private final CategorieRepository categories;

    public ClientService(ClientRepository clients, CategorieRepository categories) {
        this.clients = clients;
        this.categories = categories;
    }

    public ClientReponse creer(ClientCreation saisie) {
        String emailNormalise = normaliser(saisie.email());
        if (clients.existsByEmailNormalise(emailNormalise)) {
            throw emailDejaUtilise();
        }
        Client c = new Client();
        c.setType(saisie.type());
        c.setStatut(saisie.statut() == null ? StatutClient.PROSPECT : saisie.statut());
        c.setNom(saisie.nom());
        c.setPrenom(saisie.prenom());
        c.setEmail(saisie.email().trim());
        c.setEmailNormalise(emailNormalise);
        c.setTelephone(saisie.telephone());
        c.setAdresse(saisie.adresse());
        c.setCategorie(trouverCategorie(saisie.categorieId()));
        return ClientReponse.de(clients.save(c));
    }

    @Transactional(readOnly = true)
    public ClientReponse consulter(UUID id) {
        return ClientReponse.de(trouver(id));
    }

    public ClientReponse modifier(UUID id, ClientModification saisie) {
        Client c = trouver(id);
        String emailNormalise = normaliser(saisie.email());
        if (clients.existsByEmailNormaliseAndIdNot(emailNormalise, id)) {
            throw emailDejaUtilise();
        }
        // Le statut et campagneSourceId ne changent jamais ici
        c.setType(saisie.type());
        c.setNom(saisie.nom());
        c.setPrenom(saisie.prenom());
        c.setEmail(saisie.email().trim());
        c.setEmailNormalise(emailNormalise);
        c.setTelephone(saisie.telephone());
        c.setAdresse(saisie.adresse());
        c.setCategorie(trouverCategorie(saisie.categorieId()));
        return ClientReponse.de(c);
    }

    public ClientReponse changerStatut(UUID id, ChangementStatut changement) {
        Client c = trouver(id);
        c.setStatut(changement.statut());
        return ClientReponse.de(c);
    }

    public void supprimer(UUID id) {
        clients.delete(trouver(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientReponse> rechercher(TypeClient type, StatutClient statut, UUID categorieId,
                                                  String q, int page, int taille) {
        Specification<Client> spec = (root, query, cb) -> cb.conjunction();
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (statut != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statut"), statut));
        }
        if (categorieId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categorie").get("id"), categorieId));
        }
        if (q != null && !q.isBlank()) {
            String motif = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nom")), motif),
                    cb.like(cb.lower(cb.coalesce(root.<String>get("prenom"), "")), motif),
                    cb.like(cb.lower(root.get("email")), motif)));
        }
        return PageResponse.of(clients.findAll(spec, pagination(page, taille)).map(ClientReponse::de));
    }

    /** Liste réduite lue par Campaign : seulement les clients de statut CLIENT de la catégorie. */
    @Transactional(readOnly = true)
    public PageResponse<Destinataire> destinataires(UUID categorieId, int page, int taille) {
        if (!categories.existsById(categorieId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Catégorie introuvable");
        }
        return PageResponse.of(clients
                .findByCategorieIdAndStatut(categorieId, StatutClient.CLIENT, pagination(page, taille))
                .map(Destinataire::de));
    }

    // ---- méthodes internes

    Client trouver(UUID id) {
        return clients.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Client introuvable"));
    }

    private CategorieClient trouverCategorie(UUID categorieId) {
        if (categorieId == null) {
            return null;
        }
        return categories.findById(categorieId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Catégorie introuvable"));
    }

    /** L'unicité ignore les majuscules et les espaces autour. */
    static String normaliser(String email) {
        return email.trim().toLowerCase();
    }

    private static ApiException emailDejaUtilise() {
        return new ApiException(HttpStatus.CONFLICT, "Un client avec cet email existe déjà");
    }

    private static Pageable pagination(int page, int taille) {
        int tailleOk = Math.min(Math.max(taille, 1), 100);
        return PageRequest.of(Math.max(page, 0), tailleOk, Sort.by("nom"));
    }
}
