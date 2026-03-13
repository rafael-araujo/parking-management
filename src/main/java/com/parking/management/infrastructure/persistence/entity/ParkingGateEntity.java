package com.parking.management.infrastructure.persistence.entity;

import com.parking.management.domain.model.GateTypeDTO;
import jakarta.persistence.*;
import java.util.Objects;

@Entity(name = "ParkingGate")
@Table(name = "parking_gate")
public class ParkingGateEntity {

    @Id
    @Column(name = "id", length = 20)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector", nullable = false)
    private GarageSectorEntity sector;

    @Enumerated(EnumType.STRING)
    @Column(name = "gate_type", nullable = false)
    private GateTypeDTO gateType;

    public ParkingGateEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public GarageSectorEntity getSector() { return sector; }
    public void setSector(GarageSectorEntity sector) { this.sector = sector; }
    public GateTypeDTO getGateType() { return gateType; }
    public void setGateType(GateTypeDTO gateType) { this.gateType = gateType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParkingGateEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
