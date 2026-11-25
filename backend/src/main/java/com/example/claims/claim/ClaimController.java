package com.example.claims.claim;

import com.example.claims.claim.ClaimRequests.AddNote;
import com.example.claims.claim.ClaimRequests.AssignClaim;
import com.example.claims.claim.ClaimRequests.ChangeReserve;
import com.example.claims.claim.ClaimRequests.Decide;
import com.example.claims.claim.ClaimRequests.ProvideInfo;
import com.example.claims.claim.ClaimRequests.RequestInfo;
import com.example.claims.claim.ClaimRequests.Settle;
import com.example.claims.claim.ClaimRequests.SubmitClaim;
import com.example.claims.claim.ClaimViews.ClaimDetail;
import com.example.claims.claim.ClaimViews.ClaimSummary;
import com.example.claims.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * One endpoint per command, not PATCH /status: each command has its own validation,
 * permission check and event. Commands return the updated claim detail, so the client sees
 * the new state (and the actions now available) without a second call.
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimCommandService commands;
    private final ClaimQueryService queries;

    public ClaimController(ClaimCommandService commands, ClaimQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    public List<ClaimSummary> list(CurrentUser user,
                                   @RequestParam(required = false) String assignee,
                                   @RequestParam(required = false) ClaimStatus status) {
        return queries.list(user, assignee, status);
    }

    @GetMapping("/{id}")
    public ClaimDetail get(@PathVariable UUID id, CurrentUser user) {
        return queries.detail(id, user);
    }

    @PostMapping
    public ResponseEntity<ClaimDetail> submit(@Valid @RequestBody SubmitClaim body, CurrentUser user) {
        UUID id = commands.submit(body, user);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(queries.detail(id, user));
    }

    @PostMapping("/{id}/assign")
    public ClaimDetail assign(@PathVariable UUID id, @RequestBody(required = false) AssignClaim body,
                              CurrentUser user) {
        commands.assign(id, body == null ? null : body.officerId(), user);
        return queries.detail(id, user);
    }

    @PostMapping("/{id}/start-assessment")
    public ClaimDetail startAssessment(@PathVariable UUID id, CurrentUser user) {
        commands.startAssessment(id, user);
        return queries.detail(id, user);
    }

    @PostMapping("/{id}/info-requests")
    public ClaimDetail requestInfo(@PathVariable UUID id, @Valid @RequestBody RequestInfo body, CurrentUser user) {
        commands.requestInfo(id, body.question(), user);
        return queries.detail(id, user);
    }

    @PostMapping("/{id}/info-requests/{requestId}/response")
    public ClaimDetail provideInfo(@PathVariable UUID id, @PathVariable UUID requestId,
                                   @Valid @RequestBody ProvideInfo body, CurrentUser user) {
        commands.provideInfo(id, requestId, body.response(), user);
        return queries.detail(id, user);
    }

    @PutMapping("/{id}/reserve")
    public ClaimDetail changeReserve(@PathVariable UUID id, @Valid @RequestBody ChangeReserve body,
                                     CurrentUser user) {
        commands.changeReserve(id, body, user);
        return queries.detail(id, user);
    }

    @PostMapping("/{id}/decision")
    public ClaimDetail decide(@PathVariable UUID id, @Valid @RequestBody Decide body, CurrentUser user) {
        commands.decide(id, body, user);
        return queries.detail(id, user);
    }

    @PostMapping("/{id}/settlement")
    public ClaimDetail settle(@PathVariable UUID id, @Valid @RequestBody Settle body, CurrentUser user) {
        commands.settle(id, body.amount(), user);
        return queries.detail(id, user);
    }

    @PostMapping("/{id}/notes")
    public ClaimDetail addNote(@PathVariable UUID id, @Valid @RequestBody AddNote body, CurrentUser user) {
        commands.addNote(id, body.body(), user);
        return queries.detail(id, user);
    }
}
