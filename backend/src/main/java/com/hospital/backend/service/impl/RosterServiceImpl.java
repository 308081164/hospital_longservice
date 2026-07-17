package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.roster.SaveRosterEntryRequest;
import com.hospital.backend.dto.response.roster.RosterEntryResponse;
import com.hospital.backend.dto.response.roster.RosterImportResultResponse;
import com.hospital.backend.entity.RosterEntry;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.RosterEntryMapper;
import com.hospital.backend.service.RosterService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RosterServiceImpl implements RosterService {

    private final RosterEntryMapper rosterEntryMapper;
    private final CustomerMapper customerMapper;

    @Override
    public Result<List<RosterEntryResponse>> listEntries(Long customerId) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "Customer not found: " + customerId);
        }
        return Result.success(rosterEntryMapper.selectByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList());
    }

    @Override
    @Transactional
    public Result<RosterEntryResponse> createEntry(Long customerId, SaveRosterEntryRequest request) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "Customer not found: " + customerId);
        }
        RosterEntry entry = fromRequest(customerId, request);
        rosterEntryMapper.insert(entry);
        return Result.success(toResponse(rosterEntryMapper.selectById(entry.getId())));
    }

    @Override
    @Transactional
    public Result<RosterEntryResponse> updateEntry(Long customerId, Long entryId, SaveRosterEntryRequest request) {
        RosterEntry existing = rosterEntryMapper.selectById(entryId);
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            return Result.fail(404, "Roster entry not found");
        }
        existing.setDoctorName(request.getDoctorName().trim());
        existing.setDepartment(request.getDepartment().trim());
        existing.setSurgicalRoom(request.getSurgicalRoom());
        existing.setNotes(request.getNotes());
        existing.setIsActive(request.getIsActive());
        rosterEntryMapper.updateById(existing);
        return Result.success(toResponse(rosterEntryMapper.selectById(entryId)));
    }

    @Override
    @Transactional
    public Result<Boolean> deleteEntry(Long customerId, Long entryId) {
        RosterEntry existing = rosterEntryMapper.selectById(entryId);
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            return Result.fail(404, "Roster entry not found");
        }
        rosterEntryMapper.deleteById(entryId);
        return Result.success(true);
    }

    @Override
    @Transactional
    public Result<RosterImportResultResponse> importExcel(
            Long customerId, MultipartFile file, boolean replaceExisting) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "Customer not found: " + customerId);
        }
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "Excel file is required");
        }

        RosterImportResultResponse result = new RosterImportResultResponse();
        if (replaceExisting) {
            rosterEntryMapper.deleteByCustomerId(customerId);
        }

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return Result.fail(400, "Excel has no sheets");
            }

            Row header = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> colIndex = parseHeader(header);
            int doctorCol = colIndex.getOrDefault("doctor_name", colIndex.getOrDefault("医生", 0));
            int deptCol = colIndex.getOrDefault("department", colIndex.getOrDefault("科室", 1));
            int roomCol = colIndex.getOrDefault("surgical_room", colIndex.getOrDefault("手术室", -1));

            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String doctor = cellString(row.getCell(doctorCol));
                String department = cellString(row.getCell(deptCol));
                if (doctor.isBlank() || department.isBlank()) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }
                RosterEntry entry = new RosterEntry();
                entry.setCustomerId(customerId);
                entry.setDoctorName(doctor.trim());
                entry.setDepartment(department.trim());
                if (roomCol >= 0) {
                    entry.setSurgicalRoom(cellString(row.getCell(roomCol)));
                }
                entry.setIsActive(true);
                try {
                    rosterEntryMapper.insert(entry);
                    result.setImportedCount(result.getImportedCount() + 1);
                } catch (Exception e) {
                    result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage());
                    result.setSkippedCount(result.getSkippedCount() + 1);
                }
            }
        } catch (Exception e) {
            return Result.fail(400, "Failed to parse Excel: " + e.getMessage());
        }

        return Result.success(result);
    }

    private Map<String, Integer> parseHeader(Row header) {
        Map<String, Integer> map = new HashMap<>();
        if (header == null) {
            return map;
        }
        for (Cell cell : header) {
            String name = cellString(cell).trim().toLowerCase(Locale.ROOT);
            if (name.isBlank()) {
                continue;
            }
            map.put(normalizeHeader(name), cell.getColumnIndex());
            map.put(name, cell.getColumnIndex());
        }
        return map;
    }

    private String normalizeHeader(String header) {
        if (header.contains("医生") || header.contains("姓名")) {
            return "doctor_name";
        }
        if (header.contains("科室")) {
            return "department";
        }
        if (header.contains("手术")) {
            return "surgical_room";
        }
        return header;
    }

    private String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private RosterEntry fromRequest(Long customerId, SaveRosterEntryRequest request) {
        RosterEntry entry = new RosterEntry();
        entry.setCustomerId(customerId);
        entry.setDoctorName(request.getDoctorName().trim());
        entry.setDepartment(request.getDepartment().trim());
        entry.setSurgicalRoom(request.getSurgicalRoom());
        entry.setNotes(request.getNotes());
        entry.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        return entry;
    }

    private RosterEntryResponse toResponse(RosterEntry entry) {
        RosterEntryResponse response = new RosterEntryResponse();
        response.setId(entry.getId());
        response.setCustomerId(entry.getCustomerId());
        response.setDoctorName(entry.getDoctorName());
        response.setDepartment(entry.getDepartment());
        response.setSurgicalRoom(entry.getSurgicalRoom());
        response.setNotes(entry.getNotes());
        response.setIsActive(entry.getIsActive());
        response.setCreatedAt(entry.getCreatedAt());
        response.setUpdatedAt(entry.getUpdatedAt());
        return response;
    }
}
