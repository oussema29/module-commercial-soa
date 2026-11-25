package com.example.quotation.service;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.LigneDevis;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Component;

/** Fabrique le PDF d'un devis (mise en page simple : le but est de démontrer la génération). */
@Component
public class GenerateurPdf {

    public byte[] generer(Devis d) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        // Le nom complet évite la confusion avec notre entité Document
        com.lowagie.text.Document pdf = new com.lowagie.text.Document();
        try {
            PdfWriter.getInstance(pdf, sortie);
            pdf.open();
            pdf.add(new Paragraph("Devis " + d.getReference()));
            pdf.add(new Paragraph("Client : " + d.getClientNom() + " (" + d.getClientEmail() + ")"));
            pdf.add(new Paragraph("Date : " + d.getDate() + " - valable jusqu'au " + d.getDateValidite()));
            pdf.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(5);
            for (String titre : new String[]{"Désignation", "Prix unitaire", "Quantité", "Remise %", "Total HT"}) {
                table.addCell(titre);
            }
            for (LigneDevis l : d.getLignes()) {
                table.addCell(l.getDesignation());
                table.addCell(l.getPrixUnitaire().toPlainString());
                table.addCell(String.valueOf(l.getQuantite()));
                table.addCell(l.getRemise().toPlainString());
                table.addCell(l.getTotalLigneHT().toPlainString());
            }
            pdf.add(table);

            pdf.add(new Paragraph(" "));
            pdf.add(new Paragraph("Remise globale : " + d.getRemiseGlobale().toPlainString() + " %"));
            pdf.add(new Paragraph("Total HT : " + d.getTotalHT().toPlainString()));
            pdf.add(new Paragraph("TVA : " + d.getTotalTVA().toPlainString()));
            pdf.add(new Paragraph("Total TTC : " + d.getTotalTTC().toPlainString()));
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du PDF impossible", e);
        } finally {
            pdf.close();
        }
        return sortie.toByteArray();
    }
}
