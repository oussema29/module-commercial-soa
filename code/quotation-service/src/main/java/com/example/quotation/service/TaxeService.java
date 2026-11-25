package com.example.quotation.service;

import com.example.quotation.dto.TaxeReponse;
import com.example.quotation.dto.TaxeSaisie;
import com.example.quotation.entity.Taxe;
import com.example.quotation.exception.ApiException;
import com.example.quotation.repository.TaxeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaxeService {

    private final TaxeRepository taxes;

    public TaxeService(TaxeRepository taxes) {
        this.taxes = taxes;
    }

    public TaxeReponse creer(TaxeSaisie saisie) {
        Taxe t = new Taxe();
        t.setTauxTVA(saisie.tauxTVA());
        t.setDescription(saisie.description());
        return TaxeReponse.de(taxes.save(t));
    }

    @Transactional(readOnly = true)
    public List<TaxeReponse> lister() {
        return taxes.findAll().stream().map(TaxeReponse::de).toList();
    }

    public TaxeReponse modifier(UUID id, TaxeSaisie saisie) {
        Taxe t = taxes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Taxe introuvable"));
        t.setTauxTVA(saisie.tauxTVA());
        t.setDescription(saisie.description());
        return TaxeReponse.de(t);
    }
}
