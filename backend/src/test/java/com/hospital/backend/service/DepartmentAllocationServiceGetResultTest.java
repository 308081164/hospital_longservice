package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.allocation.AllocationResult;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.mapper.ExternalInstrumentMapper;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import com.hospital.backend.mapper.RosterEntryMapper;
import com.hospital.backend.service.impl.DepartmentAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentAllocationServiceGetResultTest {

    @Mock
    private HospitalReconciliationJobMapper jobMapper;
    @Mock
    private HospitalReconciliationRowMapper rowMapper;
    @Mock
    private RosterEntryMapper rosterEntryMapper;
    @Mock
    private ExternalInstrumentMapper externalInstrumentMapper;
    @Mock
    private CustomerResolver customerResolver;

    private DepartmentAllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DepartmentAllocationServiceImpl(
                jobMapper, rowMapper, rosterEntryMapper, externalInstrumentMapper, customerResolver);
    }

    @Test
    void getAllocationResultReturnsSuccessWhenNotYetAllocated() {
        HospitalReconciliationJob job = new HospitalReconciliationJob();
        job.setId(610L);
        job.setAllocationResult(null);
        when(jobMapper.selectById(610L)).thenReturn(job);

        Result<AllocationResult> result = service.getAllocationResult(610L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNull();
    }
}
