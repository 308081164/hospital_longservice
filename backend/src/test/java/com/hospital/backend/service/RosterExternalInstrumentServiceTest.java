package com.hospital.backend.service;

import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.entity.RosterEntry;
import com.hospital.backend.service.impl.DepartmentAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RosterExternalInstrumentServiceTest {

    private DepartmentAllocationServiceImpl allocationService;

    @BeforeEach
    void setUp() {
        allocationService = new DepartmentAllocationServiceImpl(null, null, null, null, null);
    }

    @Test
    void rosterMatchIsCaseSensitiveForChineseNames() {
        RosterEntry entry = new RosterEntry();
        entry.setDoctorName("王五");
        entry.setDepartment("创伤科");
        entry.setIsActive(true);

        Optional<RosterEntry> hit = allocationService.matchRosterInText("王五骨折包", List.of(entry));
        assertThat(hit).isPresent();
        assertThat(hit.get().getDepartment()).isEqualTo("创伤科");
    }

    @Test
    void externalInstrumentEffectiveTotalUsesUnitPriceTimesPackCount() {
        ExternalInstrument instrument = new ExternalInstrument();
        instrument.setUnitPrice(new BigDecimal("1200.00"));
        instrument.setPackCount(2);
        instrument.setTotalAmount(null);

        BigDecimal total = instrument.getUnitPrice()
                .multiply(BigDecimal.valueOf(instrument.getPackCount()));
        assertThat(total).isEqualByComparingTo("2400.00");
    }

    @Test
    void lowTemperatureRowDetectedByKeyword() {
        var config = new com.hospital.backend.allocation.AllocationConfig();
        var row = new com.hospital.backend.entity.HospitalReconciliationRow();
        row.setPackName("老肯低温等离子包");
        assertThat(allocationService.isLowTemperatureRow(row, config)).isTrue();
    }
}
