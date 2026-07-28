package com.hospital.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 国药 kit BOM 导入占位：待铂康 BOM xlsx 到位后展开 component 行。
 * 当前仅解析表头并返回空列表，供 import 管线挂接。
 */
@Slf4j
@Service
public class KitBomImportService {

    public List<Map<String, Object>> importFromExcel(Path excelPath, String customerCode) {
        if (excelPath == null || customerCode == null || customerCode.isBlank()) {
            return Collections.emptyList();
        }
        log.info("Kit BOM import stub: path={} customer={} — 待 BOM 材料", excelPath, customerCode);
        return Collections.emptyList();
    }
}
