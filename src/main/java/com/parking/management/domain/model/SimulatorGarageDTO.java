package com.parking.management.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public class SimulatorGarageDTO {

    private List<GarageConfigDto> garageList;
    private List<SpotConfigDto> spotList;
    private List<GateConfigDto> gateList;

    public List<GarageConfigDto> getGarage() { return garageList; }
    public void setGarage(List<GarageConfigDto> garageList) { this.garageList = garageList; }
    public List<SpotConfigDto> getSpots() { return spotList; }
    public void setSpots(List<SpotConfigDto> spotList) { this.spotList = spotList; }
    public List<GateConfigDto> getGates() { return gateList; }
    public void setGates(List<GateConfigDto> gateList) { this.gateList = gateList; }

    public static class GarageConfigDto {
        private String sector;

        @JsonProperty("base_price")
        private BigDecimal basePrice;

        @JsonProperty("max_capacity")
        private int maxCapacity;

        @JsonProperty("control_type")
        private String controlType;

        public String getSector() { return sector; }
        public void setSector(String sector) { this.sector = sector; }
        public BigDecimal getBasePrice() { return basePrice; }
        public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
        public int getMaxCapacity() { return maxCapacity; }
        public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }
        public String getControlType() { return controlType; }
        public void setControlType(String controlType) { this.controlType = controlType; }
    }

    public static class SpotConfigDto {
        private Long id;
        private String sector;
        private BigDecimal lat;
        private BigDecimal lng;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getSector() { return sector; }
        public void setSector(String sector) { this.sector = sector; }
        public BigDecimal getLat() { return lat; }
        public void setLat(BigDecimal lat) { this.lat = lat; }
        public BigDecimal getLng() { return lng; }
        public void setLng(BigDecimal lng) { this.lng = lng; }
    }

    public static class GateConfigDto {
        private String id;
        private String sector;

        @JsonProperty("type")
        private String gateType;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSector() { return sector; }
        public void setSector(String sector) { this.sector = sector; }
        public String getGateType() { return gateType; }
        public void setGateType(String gateType) { this.gateType = gateType; }
    }
}
