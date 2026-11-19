package com.example.campaign.repository;

import com.example.campaign.entity.PublicCible;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicCibleRepository extends JpaRepository<PublicCible, UUID> {
}
