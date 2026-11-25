package com.example.quotation.repository;

import com.example.quotation.entity.Taxe;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxeRepository extends JpaRepository<Taxe, UUID> {
}
