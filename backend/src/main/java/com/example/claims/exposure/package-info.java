/**
 * Liability exposure read model. Consumes claim-events (reserve changes, settlements,
 * rejections) and keeps outstanding reserve totals by market / product / currency for
 * the manager view. Never writes to claim tables.
 */
package com.example.claims.exposure;
