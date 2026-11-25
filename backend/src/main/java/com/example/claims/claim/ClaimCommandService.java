package com.example.claims.claim;

import com.example.claims.claim.ClaimRequests.ChangeReserve;
import com.example.claims.claim.ClaimRequests.Decide;
import com.example.claims.claim.ClaimRequests.SubmitClaim;
import com.example.claims.common.events.ClaimEventType;
import com.example.claims.common.web.ApiExceptions.BadRequestException;
import com.example.claims.common.web.ApiExceptions.BusinessRuleException;
import com.example.claims.common.web.ApiExceptions.ForbiddenException;
import com.example.claims.common.web.ApiExceptions.NotFoundException;
import com.example.claims.policy.Policy;
import com.example.claims.policy.PolicyRepository;
import com.example.claims.user.AppUserRepository;
import com.example.claims.user.CurrentUser;
import com.example.claims.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Every claim state change. Each public method is one transaction:
 * check access → change the aggregate (lifecycle-checked) → write history → flush (version bump)
 * → append the event to the outbox. Any exception rolls back the change and the event together.
 */
@Service
@Transactional
public class ClaimCommandService {

    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final AppUserRepository users;
    private final InfoRequestRepository infoRequests;
    private final ClaimNoteRepository notes;
    private final ReserveChangeRepository reserveChanges;
    private final DecisionRepository decisions;
    private final ClaimEventRecorder events;
    private final Clock clock;

    public ClaimCommandService(ClaimRepository claims, PolicyRepository policies, AppUserRepository users,
                               InfoRequestRepository infoRequests, ClaimNoteRepository notes,
                               ReserveChangeRepository reserveChanges, DecisionRepository decisions,
                               ClaimEventRecorder events, Clock clock) {
        this.claims = claims;
        this.policies = policies;
        this.users = users;
        this.infoRequests = infoRequests;
        this.notes = notes;
        this.reserveChanges = reserveChanges;
        this.decisions = decisions;
        this.events = events;
        this.clock = clock;
    }

    public UUID submit(SubmitClaim req, CurrentUser user) {
        if (!user.is(Role.CLAIMANT)) {
            throw new ForbiddenException("Only claimants can report a claim");
        }
        Policy policy = policies.findById(req.policyId())
                .filter(p -> p.isHeldBy(user.id()))
                .orElseThrow(() -> new NotFoundException("Policy " + req.policyId() + " not found"));
        if (!policy.covers(req.incidentDate())) {
            throw new BusinessRuleException("Incident date " + req.incidentDate() + " is outside the policy period "
                    + policy.getEffectiveFrom() + " to " + policy.getEffectiveTo());
        }
        requireWithinSumInsured(req.claimedAmount(), policy, "Claimed amount");

        String claimNumber = "CLM-%06d".formatted(claims.nextClaimNumber());
        Claim claim = Claim.submit(claimNumber, policy, user.id(), req.incidentDate(),
                req.description().strip(), req.claimedAmount(), clock.instant());
        claims.saveAndFlush(claim);
        events.record(ClaimEventType.CLAIM_SUBMITTED, claim, user, Map.of(
                "claimedAmount", req.claimedAmount(),
                "incidentDate", req.incidentDate().toString()));
        return claim.getId();
    }

    /**
     * SUBMITTED claim: first assignment (officer picks it up, or a manager assigns it).
     * Already-assigned open claim: reassignment, managers only, status unchanged.
     */
    public void assign(UUID claimId, UUID requestedOfficerId, CurrentUser user) {
        Claim claim = load(claimId);
        ClaimAccess.requireView(claim, user);
        UUID officerId = resolveAssignee(requestedOfficerId, user);
        Instant now = clock.instant();

        boolean reassignment = claim.getStatus() != ClaimStatus.SUBMITTED && user.is(Role.MANAGER);
        if (reassignment) {
            claim.reassignTo(officerId, now);
        } else {
            claim.assignTo(officerId, user.role(), now); // 403 for claimants, 409 if not SUBMITTED
        }
        claims.saveAndFlush(claim);
        events.record(ClaimEventType.CLAIM_ASSIGNED, claim, user, Map.of(
                "officerId", officerId,
                "reassignment", reassignment));
    }

    public void startAssessment(UUID claimId, CurrentUser user) {
        Claim claim = load(claimId);
        ClaimAccess.requireStaffAction(claim, user);
        claim.startAssessment(user.role(), clock.instant());
        claims.saveAndFlush(claim);
        events.record(ClaimEventType.CLAIM_ASSESSMENT_STARTED, claim, user, Map.of());
    }

    public UUID requestInfo(UUID claimId, String question, CurrentUser user) {
        Claim claim = load(claimId);
        ClaimAccess.requireStaffAction(claim, user);
        Instant now = clock.instant();
        claim.requestInfo(user.role(), now);
        InfoRequest request = infoRequests.save(InfoRequest.open(claimId, user.id(), question.strip(), now));
        claims.saveAndFlush(claim);
        events.record(ClaimEventType.INFORMATION_REQUESTED, claim, user, Map.of(
                "requestId", request.getId(),
                "question", request.getQuestion()));
        return request.getId();
    }

    public void provideInfo(UUID claimId, UUID requestId, String response, CurrentUser user) {
        Claim claim = load(claimId);
        ClaimAccess.requireOwner(claim, user);
        InfoRequest request = infoRequests.findById(requestId)
                .filter(r -> r.getClaimId().equals(claimId))
                .orElseThrow(() -> new NotFoundException("Information request " + requestId + " not found"));
        Instant now = clock.instant();
        request.answer(response.strip(), user.id(), now);  // 409 if already answered
        claim.infoProvided(user.role(), now);              // 409 if not AWAITING_INFO
        claims.saveAndFlush(claim);
        events.record(ClaimEventType.INFORMATION_PROVIDED, claim, user, Map.of("requestId", requestId));
    }

    public void changeReserve(UUID claimId, ChangeReserve req, CurrentUser user) {
        Claim claim = load(claimId);
        ClaimAccess.requireStaffAction(claim, user);
        Instant now = clock.instant();
        BigDecimal previous = claim.getReserveAmount();
        claim.changeReserve(req.amount(), now);            // 409 if closed or unchanged
        requireWithinSumInsured(req.amount(), policyOf(claim), "Reserve");
        reserveChanges.save(new ReserveChange(claimId, previous, req.amount(), req.reason().strip(), user.id(), now));
        claims.saveAndFlush(claim);
        events.record(ClaimEventType.RESERVE_CHANGED, claim, user, Map.of(
                "previousAmount", previous,
                "newAmount", req.amount(),
                "reason", req.reason().strip()));
    }

    public void decide(UUID claimId, Decide req, CurrentUser user) {
        Claim claim = load(claimId);
        ClaimAccess.requireStaffAction(claim, user);
        Instant now = clock.instant();
        BigDecimal previousReserve = claim.getReserveAmount();
        String reason = req.reason().strip();

        ClaimEventType eventType;
        if (req.outcome() == Decision.Outcome.APPROVED) {
            claim.approve(req.amount(), user.role(), now);  // 409 first; a 422 below still rolls back
            requireWithinSumInsured(req.amount(), policyOf(claim), "Approved amount");
            eventType = ClaimEventType.CLAIM_APPROVED;
        } else {
            claim.reject(user.role(), now);
            eventType = ClaimEventType.CLAIM_REJECTED;
        }
        decisions.save(new Decision(claimId, req.outcome(), req.amount(), reason, user.id(), now));
        recordReserveMovement(claim, previousReserve, "Decision: " + req.outcome(), user, now);
        claims.saveAndFlush(claim);

        Map<String, Object> details = req.amount() == null
                ? Map.of("reason", reason)
                : Map.of("reason", reason, "approvedAmount", req.amount());
        events.record(eventType, claim, user, details);
    }

    public void settle(UUID claimId, BigDecimal amount, CurrentUser user) {
        Claim claim = load(claimId);
        ClaimAccess.requireStaffAction(claim, user);
        Instant now = clock.instant();
        BigDecimal previousReserve = claim.getReserveAmount();
        claim.settle(amount, user.role(), now);            // 409 unless APPROVED
        BigDecimal approved = decisions.findByClaimId(claimId).orElseThrow().getApprovedAmount();
        if (amount.compareTo(approved) > 0) {
            throw new BusinessRuleException("Settlement " + amount + " exceeds the approved amount " + approved);
        }
        recordReserveMovement(claim, previousReserve, "Released on settlement", user, now);
        claims.saveAndFlush(claim);
        events.record(ClaimEventType.CLAIM_SETTLED, claim, user, Map.of("settledAmount", amount));
    }

    /** Internal note: no status change, no event (notes are never shown to claimants). */
    public UUID addNote(UUID claimId, String body, CurrentUser user) {
        Claim claim = load(claimId);
        ClaimAccess.requireStaffAction(claim, user);
        return notes.save(new ClaimNote(claimId, user.id(), body.strip(), clock.instant())).getId();
    }

    private Claim load(UUID claimId) {
        return claims.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim " + claimId + " not found"));
    }

    private Policy policyOf(Claim claim) {
        return policies.findById(claim.getPolicyId()).orElseThrow();
    }

    private UUID resolveAssignee(UUID requested, CurrentUser user) {
        return switch (user.role()) {
            case CLAIMANT -> throw new ClaimActionNotPermittedException(ClaimAction.ASSIGN, Role.CLAIMANT);
            case OFFICER -> {
                if (requested != null && !requested.equals(user.id())) {
                    throw new ForbiddenException("Officers can only assign claims to themselves");
                }
                yield user.id();
            }
            case MANAGER -> {
                if (requested == null) {
                    throw new BadRequestException("officerId is required");
                }
                boolean isOfficer = users.findById(requested).map(u -> u.getRole() == Role.OFFICER).orElse(false);
                if (!isOfficer) {
                    throw new BusinessRuleException("User " + requested + " is not a claims officer");
                }
                yield requested;
            }
        };
    }

    /** Keeps reserve_change complete when a decision or settlement moves the reserve. */
    private void recordReserveMovement(Claim claim, BigDecimal previous, String reason, CurrentUser user, Instant now) {
        if (previous.compareTo(claim.getReserveAmount()) != 0) {
            reserveChanges.save(new ReserveChange(claim.getId(), previous, claim.getReserveAmount(), reason, user.id(), now));
        }
    }

    private static void requireWithinSumInsured(BigDecimal amount, Policy policy, String what) {
        if (amount.compareTo(policy.getSumInsured()) > 0) {
            throw new BusinessRuleException(what + " " + amount + " exceeds the sum insured "
                    + policy.getSumInsured() + " " + policy.getCurrency());
        }
    }
}
