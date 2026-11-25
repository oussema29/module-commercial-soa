package com.example.quotation.repository;

import com.example.quotation.entity.Produit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProduitRepository extends JpaRepository<Produit, UUID> {
}
