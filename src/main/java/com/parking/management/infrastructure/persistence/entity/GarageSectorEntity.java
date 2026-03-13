package com.parking.management.infrastructure.persistence.entity;

import com.parking.management.domain.model.ControlTypeDTO;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity(name = "GarageSector")
@Table(name = "garage_sector")
public class GarageSectorEntity {

    @Id
    @Column(name = "sector", length = 10)
    private String sector;

    @Column(name = "base_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal basePrice;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_type", nullable = false)
    private ControlTypeDTO controlType = ControlTypeDTO.PHYSICAL;

    public GarageSectorEntity() {}

    public GarageSectorEntity(String sector, BigDecimal basePrice, int maxCapacity) {
        this.sector = sector;
        this.basePrice = basePrice;
        this.maxCapacity = maxCapacity;
    }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public int getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }
    public ControlTypeDTO getControlType() { return controlType; }
    public void setControlType(ControlTypeDTO controlType) { this.controlType = controlType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GarageSectorEntity that)) return false;
        return Objects.equals(sector, that.sector);
    }

    @Override
    public int hashCode() { return Objects.hash(sector); }
}
