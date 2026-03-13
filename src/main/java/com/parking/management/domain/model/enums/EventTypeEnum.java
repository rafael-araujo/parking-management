package com.parking.management.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EventTypeEnum {
    ENTRY, PARKED, EXIT;

    @JsonCreator
    public static EventTypeEnum from(String value) {
        if (value == null) return null;
        for (EventTypeEnum t : values()) {
            if (t.name().equalsIgnoreCase(value)) return t;
        }
        return null;
    }
}
