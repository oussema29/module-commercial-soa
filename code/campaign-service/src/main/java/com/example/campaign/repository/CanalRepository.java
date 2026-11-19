package com.example.campaign.repository;

import com.example.campaign.entity.CanalPublicitaire;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanalRepository extends JpaRepository<CanalPublicitaire, UUID> {
}
