package com.example.customer.repository;

import com.example.customer.entity.CategorieClient;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorieRepository extends JpaRepository<CategorieClient, UUID> {
}
