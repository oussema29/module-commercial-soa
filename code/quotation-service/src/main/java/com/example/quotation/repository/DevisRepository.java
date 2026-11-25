package com.example.quotation.repository;

import com.example.quotation.entity.Devis;
import com.example.quotation.entity.StatutDevis;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DevisRepository extends JpaRepository<Devis, UUID>, JpaSpecificationExecutor<Devis> {

    long countByReferenceStartingWith(String prefixe);

    Optional<Devis> findByTokenReponse(String tokenReponse);

    List<Devis> findByStatutAndDateValiditeBefore(StatutDevis statut, LocalDate date);
}
