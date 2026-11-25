package com.example.claims.exposure;

import com.example.claims.common.events.ClaimEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Stores each claim's current reserve (one row per claim) and computes totals with SUM at read
 * time. There are no running counters, so a redelivered event can never add to a total twice.
 */
@Component
public class ExposureViewRepository {

    /** Mirrors ClaimStatus.isTerminal(); kept as strings so this module only depends on the event contract. */
    private static final Set<String> CLOSED = Set.of("SETTLED", "REJECTED");

    private final JdbcTemplate jdbc;

    ExposureViewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Version-guarded upsert, same rule as the workload view. Returns true if the row changed. */
    boolean upsert(ClaimEvent e) {
        ClaimEvent.Snapshot s = e.payload().snapshot();
        int rows = jdbc.update("""
                INSERT INTO exposure_claim_view (claim_id, market, product, currency, reserve_amount,
                                                 is_open, last_claim_version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (claim_id) DO UPDATE SET
                    reserve_amount     = EXCLUDED.reserve_amount,
                    is_open            = EXCLUDED.is_open,
                    last_claim_version = EXCLUDED.last_claim_version
                WHERE exposure_claim_view.last_claim_version < EXCLUDED.last_claim_version""",
                e.claimId(), s.market(), s.product(), s.currency(),
                s.reserveAmount() == null ? BigDecimal.ZERO : s.reserveAmount(),
                !CLOSED.contains(s.status()), e.claimVersion());
        return rows == 1;
    }

    /** Outstanding liability on open claims, per currency, market and product. No FX conversion. */
    List<ExposureLine> openExposure() {
        return jdbc.query("""
                SELECT currency, market, product,
                       count(*)                                      AS open_claims,
                       count(*) FILTER (WHERE reserve_amount = 0)    AS unreserved_claims,
                       sum(reserve_amount)                           AS outstanding_reserve
                FROM exposure_claim_view
                WHERE is_open
                GROUP BY currency, market, product
                ORDER BY currency, market, product""",
                (rs, i) -> new ExposureLine(
                        rs.getString("currency").trim(),
                        rs.getString("market").trim(),
                        rs.getString("product"),
                        rs.getInt("open_claims"),
                        rs.getInt("unreserved_claims"),
                        rs.getBigDecimal("outstanding_reserve")));
    }

    /**
     * @param unreservedClaims open claims with no reserve set yet: exposure that exists but isn't
     *                         estimated, which a manager should chase
     */
    public record ExposureLine(String currency, String market, String product, int openClaims,
                               int unreservedClaims, BigDecimal outstandingReserve) {
    }
}
