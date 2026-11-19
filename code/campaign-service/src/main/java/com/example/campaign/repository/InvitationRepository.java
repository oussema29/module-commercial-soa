package com.example.campaign.repository;

import com.example.campaign.entity.InvitationClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<InvitationClient, UUID> {

    Optional<InvitationClient> findByJeton(String jeton);

    List<InvitationClient> findByCampagneId(UUID campagneId);

    Page<InvitationClient> findByCampagneId(UUID campagneId, Pageable pageable);

    // Envois réussis
    Page<InvitationClient> findByCampagneIdAndEnvoyeLeIsNotNull(UUID campagneId, Pageable pageable);

    // Envois en échec : jamais partis, avec la cause
    Page<InvitationClient> findByCampagneIdAndEnvoyeLeIsNullAndErreurEnvoiIsNotNull(UUID campagneId, Pageable pageable);

    List<InvitationClient> findByCampagneIdAndEnvoyeLeIsNullAndErreurEnvoiIsNotNull(UUID campagneId);

    Page<InvitationClient> findByCampagneIdAndRepondueLeIsNotNull(UUID campagneId, Pageable pageable);

    long countByCampagneIdAndRepondueLeIsNotNull(UUID campagneId);
}
