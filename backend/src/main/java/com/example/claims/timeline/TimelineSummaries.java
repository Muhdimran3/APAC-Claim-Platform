package com.example.claims.timeline;

import com.example.claims.common.events.ClaimEvent;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/** Turns an event into a timeline line and decides whether the claimant may see it. */
final class TimelineSummaries {

    static final int MAX_SUMMARY = 500; // column size

    private TimelineSummaries() {
    }

    record Line(String summary, boolean visibleToClaimant) {
    }

    static Line describe(ClaimEvent e) {
        Map<String, Object> d = e.payload().details() == null ? Map.of() : e.payload().details();
        String ccy = e.payload().snapshot().currency();
        String number = e.payload().snapshot().claimNumber();
        Line line = switch (e.eventType()) {
            case CLAIM_SUBMITTED -> visible("Claim " + number + " submitted for " + money(ccy, d.get("claimedAmount")));
            case CLAIM_ASSIGNED -> visible(Boolean.TRUE.equals(d.get("reassignment"))
                    ? "Claim reassigned to another claims officer"
                    : "Claim assigned to a claims officer");
            case CLAIM_ASSESSMENT_STARTED -> visible("Assessment started");
            case INFORMATION_REQUESTED -> visible("Information requested: " + d.getOrDefault("question", ""));
            case INFORMATION_PROVIDED -> visible("Requested information provided");
            // Reserves are internal liability estimates: staff only.
            case RESERVE_CHANGED -> staffOnly("Reserve changed from " + money(ccy, d.get("previousAmount"))
                    + " to " + money(ccy, d.get("newAmount")) + " (" + d.getOrDefault("reason", "") + ")");
            case CLAIM_APPROVED -> visible("Claim approved for " + money(ccy, d.get("approvedAmount")));
            case CLAIM_REJECTED -> visible("Claim rejected: " + d.getOrDefault("reason", ""));
            case CLAIM_SETTLED -> visible("Claim settled: " + money(ccy, d.get("settledAmount")) + " paid");
        };
        return new Line(truncate(line.summary()), line.visibleToClaimant());
    }

    private static Line visible(String s) {
        return new Line(s, true);
    }

    private static Line staffOnly(String s) {
        return new Line(s, false);
    }

    static String money(String currency, Object amount) {
        if (amount == null) {
            return currency + " ?";
        }
        BigDecimal value = amount instanceof BigDecimal b ? b : new BigDecimal(amount.toString());
        return String.format(Locale.ROOT, "%s %,.2f", currency, value);
    }

    private static String truncate(String s) {
        return s.length() <= MAX_SUMMARY ? s : s.substring(0, MAX_SUMMARY - 1) + "…";
    }
}
