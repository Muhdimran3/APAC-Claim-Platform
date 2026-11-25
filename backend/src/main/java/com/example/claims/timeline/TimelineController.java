package com.example.claims.timeline;

import com.example.claims.timeline.TimelineRepository.Entry;
import com.example.claims.user.AppUser;
import com.example.claims.user.AppUserRepository;
import com.example.claims.user.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Claim timeline from the timeline projection (eventually consistent: a just-made change shows
 * up within about a second). Claimants get the claimant-visible entries of their own claims,
 * staff get everything. For a claim that isn't theirs, a claimant gets an empty list, the same
 * as for an unknown claim, so nothing is revealed.
 */
@RestController
public class TimelineController {

    private final TimelineRepository timeline;
    private final AppUserRepository users;

    public TimelineController(TimelineRepository timeline, AppUserRepository users) {
        this.timeline = timeline;
        this.users = users;
    }

    @GetMapping("/api/claims/{id}/timeline")
    public List<TimelineEntry> get(@PathVariable UUID id, CurrentUser user) {
        List<Entry> entries = user.isStaff() ? timeline.forStaff(id) : timeline.forClaimant(id, user.id());
        Map<UUID, String> names = users.findAllById(entries.stream().map(Entry::actorId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        return entries.stream()
                .map(e -> new TimelineEntry(e.eventId(), e.eventType(), e.occurredAt(), e.summary(),
                        names.get(e.actorId())))
                .toList();
    }

    public record TimelineEntry(UUID eventId, String eventType, Instant occurredAt, String summary, String actor) {
    }
}
