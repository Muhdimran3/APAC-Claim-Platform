package com.example.claims.claim;

import com.example.claims.claim.ClaimViews.ClaimDetail;
import com.example.claims.claim.ClaimViews.ClaimSummary;
import com.example.claims.claim.ClaimViews.DecisionView;
import com.example.claims.claim.ClaimViews.InfoRequestView;
import com.example.claims.claim.ClaimViews.NoteView;
import com.example.claims.claim.ClaimViews.PolicyRef;
import com.example.claims.claim.ClaimViews.ReserveChangeView;
import com.example.claims.claim.ClaimViews.UserRef;
import com.example.claims.common.web.ApiExceptions.BadRequestException;
import com.example.claims.common.web.ApiExceptions.NotFoundException;
import com.example.claims.policy.Policy;
import com.example.claims.policy.PolicyRepository;
import com.example.claims.user.AppUser;
import com.example.claims.user.AppUserRepository;
import com.example.claims.user.CurrentUser;
import com.example.claims.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads claims directly from the write tables, so a claimant always sees the current state
 * (no eventual consistency here). Dashboards and timelines read Kafka-fed projections instead.
 */
@Service
@Transactional(readOnly = true)
public class ClaimQueryService {

    public enum AssigneeFilter { ME, UNASSIGNED, ALL }

    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final AppUserRepository users;
    private final InfoRequestRepository infoRequests;
    private final ClaimNoteRepository notes;
    private final ReserveChangeRepository reserveChanges;
    private final DecisionRepository decisions;

    public ClaimQueryService(ClaimRepository claims, PolicyRepository policies, AppUserRepository users,
                             InfoRequestRepository infoRequests, ClaimNoteRepository notes,
                             ReserveChangeRepository reserveChanges, DecisionRepository decisions) {
        this.claims = claims;
        this.policies = policies;
        this.users = users;
        this.infoRequests = infoRequests;
        this.notes = notes;
        this.reserveChanges = reserveChanges;
        this.decisions = decisions;
    }

    /**
     * Claimants: always their own claims (assignee ignored).
     * Staff: assignee = me | unassigned | all (default: me for officers, all for managers).
     */
    public List<ClaimSummary> list(CurrentUser user, String assignee, ClaimStatus status) {
        List<Claim> result;
        if (user.is(Role.CLAIMANT)) {
            result = claims.search(user.id(), null, false, status);
        } else {
            AssigneeFilter filter = parseAssignee(assignee, user);
            result = switch (filter) {
                case ME -> claims.search(null, user.id(), false, status);
                case UNASSIGNED -> claims.search(null, null, true, status);
                case ALL -> claims.search(null, null, false, status);
            };
        }
        Map<UUID, Policy> policyById = byId(policies.findAllById(
                result.stream().map(Claim::getPolicyId).collect(Collectors.toSet())), Policy::getId);
        Map<UUID, AppUser> userById = usersById(result.stream()
                .flatMap(c -> Stream.of(c.getClaimantId(), c.getAssignedOfficerId())));
        return result.stream()
                .map(c -> new ClaimSummary(c.getId(), c.getClaimNumber(), c.getStatus(),
                        policyRef(policyById.get(c.getPolicyId())), c.getCurrency(), c.getClaimedAmount(),
                        c.getIncidentDate(), c.getSubmittedAt(), c.getUpdatedAt(),
                        ref(userById, c.getClaimantId()), ref(userById, c.getAssignedOfficerId())))
                .toList();
    }

    public ClaimDetail detail(UUID claimId, CurrentUser user) {
        Claim c = claims.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim " + claimId + " not found"));
        ClaimAccess.requireView(c, user);
        boolean staff = user.isStaff();

        Policy policy = policies.findById(c.getPolicyId()).orElseThrow();
        List<InfoRequest> requests = infoRequests.findByClaimIdOrderByRequestedAt(claimId);
        Decision decision = decisions.findByClaimId(claimId).orElse(null);
        List<ReserveChange> reserves = staff ? reserveChanges.findByClaimIdOrderByChangedAt(claimId) : List.of();
        List<ClaimNote> claimNotes = staff ? notes.findByClaimIdOrderByCreatedAt(claimId) : List.of();

        Set<UUID> userIds = new HashSet<>();
        userIds.add(c.getClaimantId());
        userIds.add(c.getAssignedOfficerId());
        requests.forEach(r -> userIds.add(r.getRequestedBy()));
        if (decision != null) {
            userIds.add(decision.getDecidedBy());
        }
        reserves.forEach(r -> userIds.add(r.getChangedBy()));
        claimNotes.forEach(n -> userIds.add(n.getAuthorId()));
        Map<UUID, AppUser> u = usersById(userIds.stream());

        return new ClaimDetail(
                c.getId(), c.getClaimNumber(), c.getStatus(), policyRef(policy), c.getCurrency(),
                c.getClaimedAmount(), c.getIncidentDate(), c.getDescription(),
                c.getSubmittedAt(), c.getUpdatedAt(), c.getClosedAt(),
                ref(u, c.getClaimantId()), ref(u, c.getAssignedOfficerId()),
                c.getSettledAmount(), c.getVersion(),
                requests.stream().map(r -> new InfoRequestView(r.getId(), r.getQuestion(),
                        ref(u, r.getRequestedBy()), r.getRequestedAt(), r.getResponse(), r.getRespondedAt(),
                        r.isOpen())).toList(),
                decision == null ? null : new DecisionView(decision.getOutcome(), decision.getApprovedAmount(),
                        decision.getReason(), ref(u, decision.getDecidedBy()), decision.getDecidedAt()),
                ClaimAccess.availableActions(c, user),
                staff ? c.getReserveAmount() : null,
                staff ? reserves.stream().map(r -> new ReserveChangeView(r.getPreviousAmount(), r.getNewAmount(),
                        r.getReason(), ref(u, r.getChangedBy()), r.getChangedAt())).toList() : null,
                staff ? claimNotes.stream().map(n -> new NoteView(n.getId(), n.getBody(),
                        ref(u, n.getAuthorId()), n.getCreatedAt())).toList() : null);
    }

    private static AssigneeFilter parseAssignee(String value, CurrentUser user) {
        if (value == null || value.isBlank()) {
            return user.is(Role.OFFICER) ? AssigneeFilter.ME : AssigneeFilter.ALL;
        }
        try {
            return AssigneeFilter.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("assignee must be one of: me, unassigned, all");
        }
    }

    private Map<UUID, AppUser> usersById(Stream<UUID> ids) {
        Set<UUID> distinct = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return byId(users.findAllById(distinct), AppUser::getId);
    }

    private static <T> Map<UUID, T> byId(Collection<T> items, Function<T, UUID> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static UserRef ref(Map<UUID, AppUser> users, UUID id) {
        if (id == null) {
            return null;
        }
        AppUser u = users.get(id);
        return new UserRef(id, u == null ? null : u.getDisplayName());
    }

    private static PolicyRef policyRef(Policy p) {
        return new PolicyRef(p.getId(), p.getPolicyNumber(), p.getMarket(), p.getProduct());
    }
}
