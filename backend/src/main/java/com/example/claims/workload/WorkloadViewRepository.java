package com.example.claims.workload;

import com.example.claims.common.events.ClaimEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.claims.common.jdbc.JdbcTime.instant;
import static com.example.claims.common.jdbc.JdbcTime.ts;

/** Plain SQL over workload_claim_view: the version-guarded upsert is one atomic statement. */
@Component
public class WorkloadViewRepository {

    private final JdbcTemplate jdbc;

    WorkloadViewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts or updates the claim's row only if this event is newer than what's stored.
     * Duplicates and stale events update nothing. Returns true if the row changed.
     * status_changed_at moves only when the status actually changes (e.g. not on reassignment).
     */
    boolean upsert(ClaimEvent e) {
        ClaimEvent.Snapshot s = e.payload().snapshot();
        int rows = jdbc.update("""
                INSERT INTO workload_claim_view (claim_id, claim_number, status, officer_id, market, product,
                                                 submitted_at, status_changed_at, closed_at, last_claim_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (claim_id) DO UPDATE SET
                    status             = EXCLUDED.status,
                    officer_id         = EXCLUDED.officer_id,
                    status_changed_at  = CASE WHEN workload_claim_view.status = EXCLUDED.status
                                              THEN workload_claim_view.status_changed_at
                                              ELSE EXCLUDED.status_changed_at END,
                    closed_at          = EXCLUDED.closed_at,
                    last_claim_version = EXCLUDED.last_claim_version
                WHERE workload_claim_view.last_claim_version < EXCLUDED.last_claim_version""",
                e.claimId(), s.claimNumber(), s.status(), s.officerId(), s.market(), s.product(),
                ts(s.submittedAt()), ts(e.occurredAt()), ts(s.closedAt()), e.claimVersion());
        return rows == 1;
    }

    /**
     * Per-officer figures. Open = not closed. "Aged" = open and submitted before agedBefore.
     * Performance = claims closed since closedSince and average days from submission to close.
     *
     * @param officerId null for all officers
     */
    List<OfficerStats> officerStats(UUID officerId, Instant agedBefore, Instant closedSince) {
        List<Object> args = new ArrayList<>(List.of(ts(agedBefore), ts(closedSince), ts(closedSince)));
        String filter = "";
        if (officerId != null) {
            filter = " AND officer_id = ?";
            args.add(officerId);
        }
        return jdbc.query("""
                SELECT officer_id,
                       count(*) FILTER (WHERE closed_at IS NULL)                              AS open_count,
                       count(*) FILTER (WHERE status = 'ASSIGNED')                            AS assigned,
                       count(*) FILTER (WHERE status = 'UNDER_ASSESSMENT')                    AS under_assessment,
                       count(*) FILTER (WHERE status = 'AWAITING_INFO')                       AS awaiting_info,
                       count(*) FILTER (WHERE status = 'APPROVED')                            AS approved,
                       count(*) FILTER (WHERE closed_at IS NULL AND submitted_at < ?)         AS aged,
                       min(submitted_at) FILTER (WHERE closed_at IS NULL)                     AS oldest_open,
                       count(*) FILTER (WHERE closed_at >= ?)                                 AS closed_recent,
                       -- EXTRACT returns numeric; cast so JDBC can read it as a Double
                       (avg(EXTRACT(EPOCH FROM closed_at - submitted_at) / 86400.0)
                           FILTER (WHERE closed_at >= ?))::double precision                   AS avg_days_to_close
                FROM workload_claim_view
                WHERE officer_id IS NOT NULL""" + filter + " GROUP BY officer_id",
                (rs, i) -> new OfficerStats(
                        rs.getObject("officer_id", UUID.class),
                        rs.getInt("open_count"),
                        Map.of("ASSIGNED", rs.getInt("assigned"),
                                "UNDER_ASSESSMENT", rs.getInt("under_assessment"),
                                "AWAITING_INFO", rs.getInt("awaiting_info"),
                                "APPROVED", rs.getInt("approved")),
                        rs.getInt("aged"),
                        instant(rs, "oldest_open"),
                        rs.getInt("closed_recent"),
                        rs.getObject("avg_days_to_close", Double.class)),
                args.toArray());
    }

    UnassignedQueue unassigned(Instant agedBefore) {
        return jdbc.queryForObject("""
                SELECT count(*)                                    AS open_count,
                       count(*) FILTER (WHERE submitted_at < ?)    AS aged,
                       min(submitted_at)                           AS oldest
                FROM workload_claim_view
                WHERE officer_id IS NULL AND closed_at IS NULL""",
                (rs, i) -> new UnassignedQueue(rs.getInt("open_count"), rs.getInt("aged"), instant(rs, "oldest")),
                ts(agedBefore));
    }

    public record OfficerStats(UUID officerId, int open, Map<String, Integer> openByStatus, int aged,
                        Instant oldestOpenSubmittedAt, int closedRecently, Double avgDaysToClose) {

        static OfficerStats empty(UUID officerId) {
            return new OfficerStats(officerId, 0,
                    Map.of("ASSIGNED", 0, "UNDER_ASSESSMENT", 0, "AWAITING_INFO", 0, "APPROVED", 0),
                    0, null, 0, null);
        }
    }

    public record UnassignedQueue(int open, int aged, Instant oldestSubmittedAt) {
    }
}
