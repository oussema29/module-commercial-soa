package com.example.campaign.service;

import com.example.campaign.dto.PerformanceReponse;
import com.example.campaign.entity.Campagne;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rapport de performance en PDF : les mêmes indicateurs que GET /campagnes/{id}/performance. */
@Service
@Transactional(readOnly = true)
public class RapportPdfService {

    private final PerformanceService performances;

    public RapportPdfService(PerformanceService performances) {
        this.performances = performances;
    }

    public byte[] generer(UUID campagneId) {
        Campagne c = performances.trouver(campagneId);
        PerformanceReponse p = performances.consulter(campagneId);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        com.lowagie.text.Document pdf = new com.lowagie.text.Document();
        try {
            PdfWriter.getInstance(pdf, sortie);
            pdf.open();
            pdf.add(new Paragraph("Rapport de performance : " + c.getNom()));
            pdf.add(new Paragraph("Objectif : " + c.getObjectif() + " - statut : " + c.getStatut()));
            pdf.add(new Paragraph("Du " + c.getDateDebut() + " au " + c.getDateFin()));
            pdf.add(new Paragraph(" "));
            pdf.add(new Paragraph("Budget prévu : " + valeur(p.montantPrevu())));
            pdf.add(new Paragraph("Montant dépensé : " + valeur(p.montantDepense())));
            pdf.add(new Paragraph("Impressions : " + p.impressions()));
            pdf.add(new Paragraph("Clics : " + p.clics()));
            pdf.add(new Paragraph("Prospects générés : " + p.prospectsGeneres()));
            pdf.add(new Paragraph("Clients intéressés : " + p.clientsInteresses()));
            pdf.add(new Paragraph("Devis acceptés : " + p.devisAcceptes()));
            pdf.add(new Paragraph("Chiffre d'affaires attribué : " + valeur(p.chiffreAffairesAttribue())));
            pdf.add(new Paragraph("Coût par clic : " + valeur(p.cpc())));
            pdf.add(new Paragraph("ROI : " + valeur(p.roi())));
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du PDF impossible", e);
        } finally {
            pdf.close();
        }
        return sortie.toByteArray();
    }

    private static String valeur(Object v) {
        return v == null ? "non calculable" : v.toString();
    }
}
