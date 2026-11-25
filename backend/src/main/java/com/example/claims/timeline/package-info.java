/**
 * Claim timeline read model. Consumes claim-events and stores one human-readable entry per
 * event, flagged as claimant-visible or staff-only. Authorises claimants using the claimant
 * id carried on the event, so it never reads the claim tables.
 */
package com.example.claims.timeline;
