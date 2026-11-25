package com.example.claims.workload;

import com.example.claims.common.web.ApiExceptions.ForbiddenException;
import com.example.claims.user.AppUser;
import com.example.claims.user.AppUserRepository;
import com.example.claims.user.CurrentUser;
import com.example.claims.user.Role;
import com.example.claims.workload.WorkloadViewRepository.OfficerStats;
import com.example.claims.workload.WorkloadViewRepository.UnassignedQueue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Workload dashboard from the workload projection (eventually consistent, typically under a
 * second behind). Officers see their own row; managers see every officer. Both see the
 * unassigned queue, because officers pick up work from it.
 */
@RestController
@RequestMapping("/api/dashboard/workload")
public class WorkloadController {

    static final Duration AGED_AFTER = Duration.ofDays(14);
    static final Duration PERFORMANCE_WINDOW = Duration.ofDays(30);

    private final WorkloadViewRepository views;
    private final AppUserRepository users;
    private final Clock clock;

    public WorkloadController(WorkloadViewRepository views, AppUserRepository users, Clock clock) {
        this.views = views;
        this.users = users;
        this.clock = clock;
    }

    @GetMapping
    public WorkloadDashboard get(CurrentUser user) {
        if (!user.isStaff()) {
            throw new ForbiddenException("Only claims staff can view workload");
        }
        Instant now = clock.instant();
        Instant agedBefore = now.minus(AGED_AFTER);
        Instant closedSince = now.minus(PERFORMANCE_WINDOW);

        // Officers come from the user list, so a manager also sees officers with no claims.
        List<AppUser> officers = user.is(Role.MANAGER)
                ? users.findByRoleOrderByDisplayName(Role.OFFICER)
                : users.findById(user.id()).stream().toList();
        UUID only = user.is(Role.MANAGER) ? null : user.id();
        Map<UUID, OfficerStats> stats = views.officerStats(only, agedBefore, closedSince).stream()
                .collect(Collectors.toMap(OfficerStats::officerId, Function.identity()));

        List<OfficerWorkload> rows = officers.stream()
                .map(o -> OfficerWorkload.of(o, stats.getOrDefault(o.getId(), OfficerStats.empty(o.getId()))))
                .toList();
        return new WorkloadDashboard(now, AGED_AFTER.toDays(), PERFORMANCE_WINDOW.toDays(),
                views.unassigned(agedBefore), rows);
    }

    public record WorkloadDashboard(Instant generatedAt, long agedAfterDays, long performanceWindowDays,
                                    UnassignedQueue unassigned, List<OfficerWorkload> officers) {
    }

    public record OfficerWorkload(UUID officerId, String displayName, int open, Map<String, Integer> openByStatus,
                                  int aged, Instant oldestOpenSubmittedAt, int closedInWindow,
                                  Double avgDaysToClose) {
        static OfficerWorkload of(AppUser officer, OfficerStats s) {
            return new OfficerWorkload(officer.getId(), officer.getDisplayName(), s.open(), s.openByStatus(),
                    s.aged(), s.oldestOpenSubmittedAt(), s.closedRecently(), s.avgDaysToClose());
        }
    }
}
