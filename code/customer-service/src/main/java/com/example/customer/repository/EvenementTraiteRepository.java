package com.example.customer.repository;

import com.example.customer.entity.EvenementTraite;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvenementTraiteRepository extends JpaRepository<EvenementTraite, UUID> {
}
