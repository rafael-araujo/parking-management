package com.parking.management.infrastructure.persistence.entity;

import com.parking.management.domain.model.SessionStatusDTO;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity(name = "VehicleSession")
@Table(name = "vehicle_session")
public class VehicleSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_plate", length = 20, nullable = false)
    private String licensePlate;

    @Column(name = "entry_time", nullable = false)
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id")
    private ParkingSpotEntity spot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector", nullable = false)
    private GarageSectorEntity sector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id")
    private ParkingGateEntity gate;

    @Column(name = "price_multiplier", precision = 4, scale = 2, nullable = false)
    private BigDecimal priceMultiplier;

    @Column(name = "amount_charged", precision = 10, scale = 2)
    private BigDecimal amountCharged;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatusDTO status;

    public VehicleSessionEntity() {}

    public Long getId() { return id; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public ParkingSpotEntity getSpot() { return spot; }
    public void setSpot(ParkingSpotEntity spot) { this.spot = spot; }
    public GarageSectorEntity getSector() { return sector; }
    public void setSector(GarageSectorEntity sector) { this.sector = sector; }
    public ParkingGateEntity getGate() { return gate; }
    public void setGate(ParkingGateEntity gate) { this.gate = gate; }
    public BigDecimal getPriceMultiplier() { return priceMultiplier; }
    public void setPriceMultiplier(BigDecimal priceMultiplier) { this.priceMultiplier = priceMultiplier; }
    public BigDecimal getAmountCharged() { return amountCharged; }
    public void setAmountCharged(BigDecimal amountCharged) { this.amountCharged = amountCharged; }
    public SessionStatusDTO getStatus() { return status; }
    public void setStatus(SessionStatusDTO status) { this.status = status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VehicleSessionEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
