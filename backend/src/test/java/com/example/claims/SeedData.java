package com.example.claims;

/** IDs from V4__seed_reference_data.sql. */
public final class SeedData {

    public static final String ALICE = "00000000-0000-0000-0000-000000000101";
    public static final String BEN = "00000000-0000-0000-0000-000000000102";
    public static final String OLIVIA = "00000000-0000-0000-0000-000000000201";
    public static final String OSCAR = "00000000-0000-0000-0000-000000000202";
    public static final String MAYA = "00000000-0000-0000-0000-000000000301";

    /** Alice, SG motor, SGD 80,000 sum insured, 2026-01-01..2027-12-31. */
    public static final String ALICE_SG_POLICY = "00000000-0000-0000-0000-000000001001";
    /** Ben, HK motor. */
    public static final String BEN_HK_POLICY = "00000000-0000-0000-0000-000000001003";

    private SeedData() {
    }
}
