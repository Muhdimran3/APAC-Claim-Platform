package com.example.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimNoteRepository extends JpaRepository<ClaimNote, UUID> {

    List<ClaimNote> findByClaimIdOrderByCreatedAt(UUID claimId);
}
