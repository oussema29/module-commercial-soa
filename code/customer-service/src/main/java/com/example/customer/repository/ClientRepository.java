package com.example.customer.repository;

import com.example.customer.entity.Client;
import com.example.customer.entity.StatutClient;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    boolean existsByEmailNormalise(String emailNormalise);

    boolean existsByEmailNormaliseAndIdNot(String emailNormalise, UUID id);

    // Spring Data lit "CategorieId" comme categorie.id
    boolean existsByCategorieId(UUID categorieId);

    Page<Client> findByCategorieIdAndStatut(UUID categorieId, StatutClient statut, Pageable pageable);
}
