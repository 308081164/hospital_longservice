package com.hospital.backend.service;

import com.hospital.backend.dto.request.deptphysician.SaveDepartmentEntryRequest;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.DepartmentEntry;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.DepartmentEntryMapper;
import com.hospital.backend.service.impl.DepartmentEntryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentEntryServiceImplTest {

    @Mock
    private DepartmentEntryMapper departmentEntryMapper;

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private DepartmentEntryServiceImpl service;

    @Test
    void createEntry_insertsWhenCustomerExists() {
        when(customerMapper.selectById(1L)).thenReturn(new Customer());
        when(departmentEntryMapper.selectById(any())).thenAnswer(inv -> {
            DepartmentEntry entry = new DepartmentEntry();
            entry.setId(10L);
            entry.setCustomerId(1L);
            entry.setDepartmentName("骨科");
            return entry;
        });

        SaveDepartmentEntryRequest request = new SaveDepartmentEntryRequest();
        request.setDepartmentName("骨科");

        var result = service.createEntry(1L, request);

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<DepartmentEntry> captor = ArgumentCaptor.forClass(DepartmentEntry.class);
        verify(departmentEntryMapper).insert(captor.capture());
        assertThat(captor.getValue().getDepartmentName()).isEqualTo("骨科");
    }

    @Test
    void listEntries_returns404WhenCustomerMissing() {
        when(customerMapper.selectById(99L)).thenReturn(null);
        assertThat(service.listEntries(99L, null, null).getCode()).isEqualTo(404);
    }

    @Test
    void listEntries_returnsEmptyList() {
        when(customerMapper.selectById(1L)).thenReturn(new Customer());
        when(departmentEntryMapper.selectByCustomerId(1L)).thenReturn(List.of());
        assertThat(service.listEntries(1L, null, null).getData()).isEmpty();
    }
}
