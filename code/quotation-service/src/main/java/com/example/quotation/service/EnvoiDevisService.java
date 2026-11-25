package com.example.quotation.service;

import com.example.quotation.dto.DevisReponse;
import com.example.quotation.entity.Devis;
import com.example.quotation.entity.Document;
import com.example.quotation.entity.StatutDevis;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.DevisRepository;
import com.example.quotation.repository.DocumentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EnvoiDevisService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final DevisRepository devis;
    private final DocumentRepository documents;
    private final GenerateurPdf generateurPdf;
    private final EnvoiEmail envoiEmail;
    private final Clock clock;
    private final String interfaceUrl;

    public EnvoiDevisService(DevisRepository devis, DocumentRepository documents, GenerateurPdf generateurPdf,
                             EnvoiEmail envoiEmail, Clock clock, @Value("${app.interface-url}") String interfaceUrl) {
        this.devis = devis;
        this.documents = documents;
        this.generateurPdf = generateurPdf;
        this.envoiEmail = envoiEmail;
        this.clock = clock;
        this.interfaceUrl = interfaceUrl;
    }

    public DevisReponse envoyer(UUID id) {
        Devis d = trouver(id);
        if (d.getStatut() != StatutDevis.BROUILLON) {
            throw new ApiException(HttpStatus.CONFLICT, "Seul un devis en brouillon peut être envoyé");
        }
        if (d.getClientEmail() == null || !EMAIL.matcher(d.getClientEmail()).matches()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "L'email du client est invalide");
        }
        if (d.getDateValidite().isBefore(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "La date de validité est dépassée");
        }

        d.setTokenReponse(JetonReponse.generer());
        byte[] pdf = generateurPdf.generer(d);
        try {
            envoiEmail.envoyer(d.getClientEmail(), "Votre devis " + d.getReference(), texteEmail(d), pdf);
        } catch (EmailException e) {
            // Le devis reste BROUILLON (la transaction est annulée) ; le commercial corrige l'email et recommence
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Envoi impossible : " + e.getMessage());
        }

        d.setStatut(StatutDevis.ENVOYE);
        Document trace = new Document();
        trace.setDevis(d);
        trace.setContenu(pdf);
        trace.setGenereLe(Instant.now(clock));
        trace.setEnvoyeLe(Instant.now(clock));
        trace.setDestinataire(d.getClientEmail());
        documents.save(trace);
        return DevisReponse.de(d);
    }

    @Transactional(readOnly = true)
    public FichierPdf pdf(UUID id) {
        Devis d = trouver(id);
        return new FichierPdf(d.getReference() + ".pdf", generateurPdf.generer(d));
    }

    private Devis trouver(UUID id) {
        return devis.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Devis introuvable"));
    }

    private String texteEmail(Devis d) {
        String lien = interfaceUrl + "/devis/reponse/" + d.getTokenReponse();
        return "Bonjour " + d.getClientNom() + ",\n\n"
                + "Votre devis " + d.getReference() + " est joint à ce message (valable jusqu'au "
                + d.getDateValidite() + ").\n\n"
                + "Accepter : " + lien + "?choix=accepter\n"
                + "Refuser : " + lien + "?choix=refuser\n";
    }
}
