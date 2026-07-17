package com.hospital.backend.dto.request.allocation;

import com.hospital.backend.allocation.AllocationConfig;
import lombok.Data;

@Data
public class RunAllocationRequest {

    private AllocationConfig config;
}
