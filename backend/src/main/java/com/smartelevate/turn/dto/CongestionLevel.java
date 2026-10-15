package com.smartelevate.turn.dto;

/** Nivel de congestión de un núcleo según su ocupación, con los umbrales de {@code app_config} (#220). */
public enum CongestionLevel {
    LOW,
    MEDIUM,
    HIGH;

    /** Ocupación en % (0–100): desde {@code mediumFrom} es MEDIUM y desde {@code highFrom}, HIGH (inclusive). */
    public static CongestionLevel of(double occupancyPercent, int mediumFrom, int highFrom) {
        if (occupancyPercent >= highFrom) {
            return HIGH;
        }
        return occupancyPercent >= mediumFrom ? MEDIUM : LOW;
    }
}
