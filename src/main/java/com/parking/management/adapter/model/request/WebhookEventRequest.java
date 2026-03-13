package com.parking.management.adapter.model.request;

import com.parking.management.domain.model.enums.EventTypeEnum;
import com.parking.management.infrastructure.config.IsoToLocalDateTimeDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Parking event sent by the simulator")
public class WebhookEventRequest {

    @NotBlank
    @Schema(description = "Vehicle license plate", example = "ABC-1234", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("license_plate")
    private String licensePlate;

    @Schema(description = "Entry timestamp (ISO-8601 UTC); required for ENTRY events", example = "2024-03-11T08:00:00Z")
    @JsonProperty("entry_time")
    @JsonDeserialize(using = IsoToLocalDateTimeDeserializer.class)
    private LocalDateTime entryTime;

    @Schema(description = "Exit timestamp (ISO-8601 UTC); required for EXIT events", example = "2024-03-11T10:30:00Z")
    @JsonProperty("exit_time")
    @JsonDeserialize(using = IsoToLocalDateTimeDeserializer.class)
    private LocalDateTime exitTime;

    @Schema(description = "Latitude of the parked spot; required for PARKED events", example = "-23.5505")
    private BigDecimal lat;

    @Schema(description = "Longitude of the parked spot; required for PARKED events", example = "-46.6333")
    private BigDecimal lng;

    @NotNull(message = "event_type is required and must be ENTRY, PARKED, or EXIT")
    @Schema(description = "Event type: ENTRY, PARKED, or EXIT", example = "ENTRY", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("event_type")
    private EventTypeEnum eventType;

    @Schema(description = "Gate identifier through which the vehicle entered; required for ENTRY events", example = "GATE-A1")
    @JsonProperty("gate_id")
    private String gateId;

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public BigDecimal getLat() { return lat; }
    public void setLat(BigDecimal lat) { this.lat = lat; }
    public BigDecimal getLng() { return lng; }
    public void setLng(BigDecimal lng) { this.lng = lng; }
    public EventTypeEnum getEventType() { return eventType; }
    public void setEventType(EventTypeEnum eventType) { this.eventType = eventType; }
    public String getGateId() { return gateId; }
    public void setGateId(String gateId) { this.gateId = gateId; }
}
