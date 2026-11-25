package com.example.claims.common.events;

import com.fasterxml.jackson.annotation.JsonValue;

/** Event types on the claim-events topic. The wire name is what goes into JSON and outbox_event.event_type. */
public enum ClaimEventType {
    CLAIM_SUBMITTED("ClaimSubmitted"),
    CLAIM_ASSIGNED("ClaimAssigned"),
    CLAIM_ASSESSMENT_STARTED("ClaimAssessmentStarted"),
    INFORMATION_REQUESTED("InformationRequested"),
    INFORMATION_PROVIDED("InformationProvided"),
    RESERVE_CHANGED("ReserveChanged"),
    CLAIM_APPROVED("ClaimApproved"),
    CLAIM_REJECTED("ClaimRejected"),
    CLAIM_SETTLED("ClaimSettled");

    private final String wireName;

    ClaimEventType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }
}
