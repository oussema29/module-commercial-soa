package com.example.campaign.repository;

import com.example.campaign.entity.Campagne;
import com.example.campaign.entity.StatutCampagne;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampagneRepository extends JpaRepository<Campagne, UUID> {

    List<Campagne> findByStatutInAndDateFinBefore(Collection<StatutCampagne> statuts, LocalDate date);
}
