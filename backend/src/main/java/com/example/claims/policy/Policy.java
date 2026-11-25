package com.example.claims.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Seeded reference data (V4 migration); read-only in this application. */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    private UUID id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    /** Referenced by id, not @ManyToOne: aggregates refer to each other by identity only. */
    @Column(name = "holder_id", nullable = false)
    private UUID holderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "bpchar(2)")
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Product product;

    @Column(nullable = false, columnDefinition = "bpchar(3)")
    private String currency;

    @Column(name = "sum_insured", nullable = false)
    private BigDecimal sumInsured;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private LocalDate effectiveTo;

    protected Policy() {
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(effectiveFrom) && !date.isAfter(effectiveTo);
    }

    public boolean isHeldBy(UUID userId) {
        return holderId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public UUID getHolderId() {
        return holderId;
    }

    public Market getMarket() {
        return market;
    }

    public Product getProduct() {
        return product;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getSumInsured() {
        return sumInsured;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }
}
