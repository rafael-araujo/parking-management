package com.parking.management.application.mapper;

import com.parking.management.adapter.model.response.RevenueResponse;
import com.parking.management.domain.model.RevenueDTO;
import org.springframework.stereotype.Component;

@Component
public class RevenueMapper {

    public RevenueResponse toResponse(RevenueDTO dto) {
        return new RevenueResponse(dto.getAmount(), dto.getCurrency(), dto.getTimestamp());
    }
}
