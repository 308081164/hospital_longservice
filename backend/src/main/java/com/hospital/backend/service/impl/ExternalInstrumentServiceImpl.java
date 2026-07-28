package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.external.SaveExternalInstrumentRequest;
import com.hospital.backend.dto.response.external.ExternalInstrumentResponse;
import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.ExternalInstrument;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.mapper.CustomerMapper;
import com.hospital.backend.mapper.ExternalInstrumentMapper;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.service.CustomerResolver;
import com.hospital.backend.service.ExternalInstrumentService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExternalInstrumentServiceImpl implements ExternalInstrumentService {

    private final ExternalInstrumentMapper externalInstrumentMapper;
    private final CustomerMapper customerMapper;
    private final HospitalReconciliationJobMapper jobMapper;
    private final CustomerResolver customerResolver;

    @Override
    public Result<List<ExternalInstrumentResponse>> listCatalog(Long customerId) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "Customer not found: " + customerId);
        }
        return Result.success(externalInstrumentMapper.selectCatalogByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList());
    }

    @Override
    public Result<List<ExternalInstrumentResponse>> listByJob(Long jobId) {
        if (jobMapper.selectById(jobId) == null) {
            return Result.fail(404, "Job not found: " + jobId);
        }
        return Result.success(externalInstrumentMapper.selectByJobId(jobId).stream()
                .map(this::toResponse)
                .toList());
    }

    @Override
    @Transactional
    public Result<ExternalInstrumentResponse> createCatalogEntry(
            Long customerId, SaveExternalInstrumentRequest request) {
        if (customerMapper.selectById(customerId) == null) {
            return Result.fail(404, "Customer not found: " + customerId);
        }
        ExternalInstrument instrument = fromRequest(customerId, null, request);
        externalInstrumentMapper.insert(instrument);
        return Result.success(toResponse(externalInstrumentMapper.selectById(instrument.getId())));
    }

    @Override
    @Transactional
    public Result<ExternalInstrumentResponse> createJobEntry(Long jobId, SaveExternalInstrumentRequest request) {
        Optional<Long> customerId = resolveCustomerIdForJob(jobId);
        if (customerId.isEmpty()) {
            return Result.fail(404, "Job or customer not found");
        }
        ExternalInstrument instrument = fromRequest(customerId.get(), jobId, request);
        externalInstrumentMapper.insert(instrument);
        return Result.success(toResponse(externalInstrumentMapper.selectById(instrument.getId())));
    }

    @Override
    @Transactional
    public Result<ExternalInstrumentResponse> updateEntry(Long id, SaveExternalInstrumentRequest request) {
        ExternalInstrument existing = externalInstrumentMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, "External instrument not found");
        }
        applyRequest(existing, request);
        externalInstrumentMapper.updateById(existing);
        return Result.success(toResponse(externalInstrumentMapper.selectById(id)));
    }

    @Override
    @Transactional
    public Result<Boolean> deleteEntry(Long id) {
        ExternalInstrument existing = externalInstrumentMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, "External instrument not found");
        }
        externalInstrumentMapper.deleteById(id);
        return Result.success(true);
    }

    @Override
    @Transactional
    public Result<Integer> importJobExcel(Long jobId, MultipartFile file) {
        Optional<Long> customerId = resolveCustomerIdForJob(jobId);
        if (customerId.isEmpty()) {
            return Result.fail(404, "Job or customer not found");
        }
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "Excel file is required");
        }

        int imported = 0;
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> cols = parseHeader(header);

            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String categoryNo = cellString(safeGetCell(row, cols.get("category_no")));
                String packName = cellString(safeGetCell(row, cols.get("pack_name")));
                if (categoryNo.isBlank() && packName.isBlank()) {
                    continue;
                }

                SaveExternalInstrumentRequest req = new SaveExternalInstrumentRequest();
                req.setCategoryNo(categoryNo.isBlank() ? packName : categoryNo);
                req.setPackName(packName.isBlank() ? categoryNo : packName);
                req.setDepartment(cellString(safeGetCell(row, cols.get("department"))));
                req.setPackageMaterial(cellString(safeGetCell(row, cols.get("package_material"))));
                req.setPatientName(cellString(safeGetCell(row, cols.get("patient_name"))));
                req.setUsageDate(parseDate(safeGetCell(row, cols.get("usage_date"))));
                req.setPackCount(parseInt(safeGetCell(row, cols.get("pack_count")), 1));
                req.setInstrumentCount(parseInt(safeGetCell(row, cols.get("instrument_count")), 0));

                BigDecimal unitPrice = parseDecimal(safeGetCell(row, cols.get("unit_price")));
                BigDecimal totalAmount = parseDecimal(safeGetCell(row, cols.get("total_amount")));
                if (unitPrice == null) {
                    ExternalInstrument catalog = externalInstrumentMapper.selectByCustomerAndCategoryNo(
                            customerId.get(), req.getCategoryNo());
                    unitPrice = catalog != null ? catalog.getUnitPrice() : BigDecimal.ZERO;
                }
                req.setUnitPrice(unitPrice);
                if (totalAmount != null) {
                    req.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
                } else {
                    req.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(req.getPackCount()))
                            .setScale(2, RoundingMode.HALF_UP));
                }

                ExternalInstrument instrument = fromRequest(customerId.get(), jobId, req);
                externalInstrumentMapper.insert(instrument);
                imported++;
            }
        } catch (Exception e) {
            return Result.fail(400, "Import failed: " + e.getMessage());
        }
        return Result.success(imported);
    }

    @Override
    public double sumJobTotal(Long jobId) {
        return externalInstrumentMapper.selectByJobId(jobId).stream()
                .map(this::effectiveTotal)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
    }

    private Optional<Long> resolveCustomerIdForJob(Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Optional.empty();
        }
        return customerResolver.resolveByName(job.getHospitalName()).map(Customer::getId);
    }

    private ExternalInstrument fromRequest(
            Long customerId, Long jobId, SaveExternalInstrumentRequest request) {
        ExternalInstrument instrument = new ExternalInstrument();
        instrument.setCustomerId(customerId);
        instrument.setReconciliationJobId(jobId);
        applyRequest(instrument, request);
        if (instrument.getTotalAmount() == null && instrument.getUnitPrice() != null) {
            int packs = instrument.getPackCount() != null ? instrument.getPackCount() : 1;
            instrument.setTotalAmount(instrument.getUnitPrice()
                    .multiply(BigDecimal.valueOf(packs))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return instrument;
    }

    private void applyRequest(ExternalInstrument instrument, SaveExternalInstrumentRequest request) {
        instrument.setCategoryNo(request.getCategoryNo().trim());
        instrument.setPackName(request.getPackName().trim());
        instrument.setDepartment(request.getDepartment());
        instrument.setPackageMaterial(request.getPackageMaterial());
        instrument.setPatientName(request.getPatientName());
        instrument.setUsageDate(request.getUsageDate());
        instrument.setPackCount(request.getPackCount() != null ? request.getPackCount() : 1);
        instrument.setInstrumentCount(request.getInstrumentCount() != null ? request.getInstrumentCount() : 0);
        instrument.setUnitPrice(request.getUnitPrice());
        instrument.setTotalAmount(request.getTotalAmount());
        instrument.setNotes(request.getNotes());
        instrument.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
    }

    private BigDecimal effectiveTotal(ExternalInstrument instrument) {
        if (instrument.getTotalAmount() != null) {
            return instrument.getTotalAmount();
        }
        int packs = instrument.getPackCount() != null ? instrument.getPackCount() : 1;
        BigDecimal unit = instrument.getUnitPrice() != null ? instrument.getUnitPrice() : BigDecimal.ZERO;
        return unit.multiply(BigDecimal.valueOf(packs)).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Integer> parseHeader(Row header) {
        Map<String, Integer> map = new HashMap<>();
        if (header == null) {
            return map;
        }
        for (Cell cell : header) {
            String raw = cellString(cell).trim().toLowerCase(Locale.ROOT);
            if (raw.contains("包类别") || raw.equals("category_no")) {
                map.put("category_no", cell.getColumnIndex());
            } else if (raw.contains("包名") || raw.contains("pack")) {
                map.put("pack_name", cell.getColumnIndex());
            } else if (raw.contains("科室") || raw.contains("department")) {
                map.put("department", cell.getColumnIndex());
            } else if (raw.contains("材料") || raw.contains("material")) {
                map.put("package_material", cell.getColumnIndex());
            } else if (raw.contains("患者") || raw.contains("patient")) {
                map.put("patient_name", cell.getColumnIndex());
            } else if (raw.contains("日期") || raw.contains("date")) {
                map.put("usage_date", cell.getColumnIndex());
            } else if (raw.contains("包数") || raw.contains("pack_count")) {
                map.put("pack_count", cell.getColumnIndex());
            } else if ((raw.contains("器械数") || raw.contains("instrument_count"))
                    && !raw.contains("外来器械")) {
                map.put("instrument_count", cell.getColumnIndex());
            } else if (raw.contains("单价") || raw.contains("unit_price")) {
                map.put("unit_price", cell.getColumnIndex());
            } else if (raw.contains("总价") || raw.contains("total")) {
                map.put("total_amount", cell.getColumnIndex());
            }
        }
        return map;
    }

    private Cell safeGetCell(Row row, Integer index) {
        if (row == null || index == null || index < 0) {
            return null;
        }
        return row.getCell(index);
    }

    private String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private LocalDate parseDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = cellString(cell);
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, Math.min(10, text.length())));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(Cell cell, int defaultValue) {
        if (cell == null) {
            return defaultValue;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            }
            String text = cellString(cell);
            return text.isBlank() ? defaultValue : Integer.parseInt(text.split("\\.")[0]);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BigDecimal parseDecimal(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
            }
            String text = cellString(cell);
            return text.isBlank() ? null : new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private ExternalInstrumentResponse toResponse(ExternalInstrument instrument) {
        ExternalInstrumentResponse response = new ExternalInstrumentResponse();
        response.setId(instrument.getId());
        response.setCustomerId(instrument.getCustomerId());
        response.setReconciliationJobId(instrument.getReconciliationJobId());
        response.setCategoryNo(instrument.getCategoryNo());
        response.setPackName(instrument.getPackName());
        response.setDepartment(instrument.getDepartment());
        response.setPackageMaterial(instrument.getPackageMaterial());
        response.setPatientName(instrument.getPatientName());
        response.setUsageDate(instrument.getUsageDate());
        response.setPackCount(instrument.getPackCount());
        response.setInstrumentCount(instrument.getInstrumentCount());
        response.setUnitPrice(instrument.getUnitPrice());
        response.setTotalAmount(instrument.getTotalAmount());
        response.setNotes(instrument.getNotes());
        response.setIsActive(instrument.getIsActive());
        response.setCreatedAt(instrument.getCreatedAt());
        response.setUpdatedAt(instrument.getUpdatedAt());
        return response;
    }
}
