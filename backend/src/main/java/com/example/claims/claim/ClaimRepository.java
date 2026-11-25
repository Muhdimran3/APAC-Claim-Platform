package com.example.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumber();

    /** All filters optional (null = ignore). Newest first. */
    @Query("""
            SELECT c FROM Claim c
            WHERE (:claimantId IS NULL OR c.claimantId = :claimantId)
              AND (:officerId IS NULL OR c.assignedOfficerId = :officerId)
              AND (:unassignedOnly = FALSE OR c.assignedOfficerId IS NULL)
              AND (:status IS NULL OR c.status = :status)
            ORDER BY c.submittedAt DESC""")
    List<Claim> search(@Param("claimantId") UUID claimantId,
                       @Param("officerId") UUID officerId,
                       @Param("unassignedOnly") boolean unassignedOnly,
                       @Param("status") ClaimStatus status);
}
