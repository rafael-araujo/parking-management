package com.parking.management.application.usecase;

import com.parking.management.domain.model.RevenueDTO;

import java.time.LocalDate;

public interface RevenueUseCase {

    RevenueDTO getRevenue(String sector, LocalDate date);
}
