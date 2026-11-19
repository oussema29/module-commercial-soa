package com.example.campaign.repository;

import com.example.campaign.entity.EvenementTraite;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvenementTraiteRepository extends JpaRepository<EvenementTraite, UUID> {
}
