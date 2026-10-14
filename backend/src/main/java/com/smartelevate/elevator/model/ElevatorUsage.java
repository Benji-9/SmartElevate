package com.smartelevate.elevator.model;

import com.smartelevate.priority.model.PriorityCategory;

/** Quién puede usar un ascensor: todos, o solo un tipo de prioritario (ascensor dedicado). */
public enum ElevatorUsage {
    COMMON,
    TEACHERS,
    REDUCED_MOBILITY;

    /** Un dedicado solo admite su categoría (asignacion.md#ascensores-dedicados); un común, a cualquiera. */
    public boolean allows(PriorityCategory priority) {
        return switch (this) {
            case COMMON -> true;
            case TEACHERS -> priority == PriorityCategory.TEACHER;
            case REDUCED_MOBILITY -> priority == PriorityCategory.REDUCED_MOBILITY;
        };
    }
}
