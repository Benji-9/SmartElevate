package com.smartelevate.turn.dto;

/** Período del panel admin: días de calendario de Buenos Aires que terminan hoy (inclusive). */
public enum AdminPeriod {
    TODAY(1),
    WEEK(7),
    MONTH(30);

    private final int days;

    AdminPeriod(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }
}
