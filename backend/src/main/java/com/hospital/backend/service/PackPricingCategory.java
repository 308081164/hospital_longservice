package com.hospital.backend.service;

/**
 * Standard-pricing channel derived from package material (first) and bill type.
 */
public enum PackPricingCategory {
    /** Dressing item packed in paper-plastic (cotton ball tier, keep-original rows). */
    DRESSING_PAPER,
    /** Dressing item packed in non-woven (W-code tiers, tourniquet). */
    DRESSING_NONWOVEN,
    /** Instrument / extra pack in paper-plastic (per-piece + bag fee). */
    INSTRUMENT_PAPER,
    /** Instrument / extra pack in non-woven tiers. */
    INSTRUMENT_NONWOVEN,
    /** Material and type could not be mapped. */
    UNKNOWN
}
