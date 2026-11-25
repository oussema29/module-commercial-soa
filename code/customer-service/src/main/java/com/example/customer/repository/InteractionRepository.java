package com.example.customer.repository;

import com.example.customer.entity.Interaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    // De la plus récente à la plus ancienne
    List<Interaction> findByClientIdOrderByDateDesc(UUID clientId);
}
