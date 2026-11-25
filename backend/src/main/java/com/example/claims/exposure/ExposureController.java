package com.example.claims.exposure;

import com.example.claims.common.web.ApiExceptions.ForbiddenException;
import com.example.claims.exposure.ExposureViewRepository.ExposureLine;
import com.example.claims.user.CurrentUser;
import com.example.claims.user.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Liability exposure (managers only), from the exposure projection, so eventually consistent. */
@RestController
@RequestMapping("/api/dashboard/exposure")
public class ExposureController {

    private final ExposureViewRepository views;
    private final Clock clock;

    public ExposureController(ExposureViewRepository views, Clock clock) {
        this.views = views;
        this.clock = clock;
    }

    @GetMapping
    public ExposureDashboard get(CurrentUser user) {
        if (!user.is(Role.MANAGER)) {
            throw new ForbiddenException("Only managers can view liability exposure");
        }
        List<ExposureLine> lines = views.openExposure();
        return new ExposureDashboard(clock.instant(), totalsByCurrency(lines), lines);
    }

    /** Totals per currency. Currencies are never added together (no FX conversion). */
    static List<CurrencyTotal> totalsByCurrency(List<ExposureLine> lines) {
        Map<String, CurrencyTotal> totals = new TreeMap<>();
        for (ExposureLine l : lines) {
            totals.merge(l.currency(),
                    new CurrencyTotal(l.currency(), l.openClaims(), l.unreservedClaims(), l.outstandingReserve()),
                    (a, b) -> new CurrencyTotal(a.currency(), a.openClaims() + b.openClaims(),
                            a.unreservedClaims() + b.unreservedClaims(),
                            a.outstandingReserve().add(b.outstandingReserve())));
        }
        return List.copyOf(totals.values());
    }

    public record ExposureDashboard(Instant generatedAt, List<CurrencyTotal> byCurrency,
                                    List<ExposureLine> breakdown) {
    }

    public record CurrencyTotal(String currency, int openClaims, int unreservedClaims,
                                BigDecimal outstandingReserve) {
    }
}
