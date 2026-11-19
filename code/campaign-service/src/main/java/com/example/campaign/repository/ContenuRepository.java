package com.example.campaign.repository;

import com.example.campaign.entity.ContenuPublicitaire;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContenuRepository extends JpaRepository<ContenuPublicitaire, UUID> {
}
