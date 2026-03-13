package com.parking.management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity(name = "ParkingSpot")
@Table(name = "parking_spot")
public class ParkingSpotEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector", nullable = false)
    private GarageSectorEntity sector;

    @Column(name = "lat", precision = 10, scale = 8, nullable = false)
    private BigDecimal lat;

    @Column(name = "lng", precision = 11, scale = 8, nullable = false)
    private BigDecimal lng;

    @Column(name = "occupied", nullable = false)
    private boolean occupied = false;

    public ParkingSpotEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public GarageSectorEntity getSector() { return sector; }
    public void setSector(GarageSectorEntity sector) { this.sector = sector; }
    public BigDecimal getLat() { return lat; }
    public void setLat(BigDecimal lat) { this.lat = lat; }
    public BigDecimal getLng() { return lng; }
    public void setLng(BigDecimal lng) { this.lng = lng; }
    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParkingSpotEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
