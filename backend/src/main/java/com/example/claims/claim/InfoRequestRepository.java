package com.example.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InfoRequestRepository extends JpaRepository<InfoRequest, UUID> {

    List<InfoRequest> findByClaimIdOrderByRequestedAt(UUID claimId);
}
