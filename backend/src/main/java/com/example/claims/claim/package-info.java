/**
 * Claims core (write side). Owns the Claim aggregate and its status state machine:
 * submission, assignment, information requests, reserves, settlement/rejection.
 * Exposes REST commands/queries and publishes lifecycle events to the claim-events topic.
 * The only module allowed to change claim state.
 */
package com.example.claims.claim;
