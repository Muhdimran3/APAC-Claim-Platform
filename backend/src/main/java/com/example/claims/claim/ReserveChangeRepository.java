package com.example.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReserveChangeRepository extends JpaRepository<ReserveChange, UUID> {

    List<ReserveChange> findByClaimIdOrderByChangedAt(UUID claimId);
}
