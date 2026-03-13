package com.parking.management.infrastructure.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public class IsoToLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) return null;
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(text);
        }
    }
}
