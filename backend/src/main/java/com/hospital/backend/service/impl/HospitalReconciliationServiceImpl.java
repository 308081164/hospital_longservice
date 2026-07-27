package com.hospital.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.common.Result;
import com.hospital.backend.service.PricingEngine;
import com.hospital.backend.service.PricingRuleCompiler;
import com.hospital.backend.service.RowSplitter;
import com.hospital.backend.service.CustomerResolver;
import com.hospital.backend.service.LogisticsFeeCalculator;
import com.hospital.backend.service.LogisticsPipelineService;
import com.hospital.backend.service.MonthlySettlementCalculator;
import com.hospital.backend.dto.response.logistics.LogisticsAllocationPreviewResponse;
import com.hospital.backend.service.LogisticsAllocationService;
import com.hospital.backend.service.LogisticsImportService;
import com.hospital.backend.export.BillExportPriceResolver;
import com.hospital.backend.export.SheetOrchestrator;
import com.hospital.backend.service.UrgentFeeCalculator;
import com.hospital.backend.service.DeductionCalculator;
import com.hospital.backend.service.ProductMatchService;
import com.hospital.backend.service.SettlementJobFieldsApplier;
import com.hospital.backend.service.ExternalInstrumentService;
import com.hospital.backend.service.HospitalReconciliationService;
import com.hospital.backend.service.ReconciliationVersionGroup;
import com.hospital.backend.export.BillExportLayoutResolver;
import com.hospital.backend.export.D8DisplayNameResolver;
import com.hospital.backend.export.ExportEngineService;
import com.hospital.backend.export.ExportTemplateResolver;
import com.hospital.backend.export.ReconciliationLegacyExportBridge;
import com.hospital.backend.export.ExportType;
import com.hospital.backend.export.model.ColumnMappingConfig;
import com.hospital.backend.export.model.ResolvedExportTemplate;
import com.hospital.backend.dto.request.hospital.*;
import com.hospital.backend.dto.response.hospital.ReconciliationExportLogResponse;
import com.hospital.backend.dto.response.hospital.ReconciliationJobResponse;
import com.hospital.backend.dto.response.hospital.TemplateRefResponse;
import com.hospital.backend.entity.HospitalPricingRule;
import com.hospital.backend.entity.HospitalReconciliationExportLog;
import com.hospital.backend.entity.HospitalReconciliationJob;
import com.hospital.backend.entity.HospitalReconciliationRow;
import com.hospital.backend.mapper.HospitalPricingRuleMapper;
import com.hospital.backend.mapper.HospitalReconciliationExportLogMapper;
import com.hospital.backend.mapper.HospitalReconciliationJobMapper;
import com.hospital.backend.mapper.HospitalReconciliationRowMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 医院 Excel 核对控制器
 *
 * CSSD 消毒供应中心灭菌计费核对系统的核心控制器。
 * 负责处理医院 Excel 核对任务的完整生命周期管理，
 * 完全对齐 FastAPI 实现（hospital_reconciliations.py）的行为模式。
 *
 * ========================================================================
 *                         系统功能总览
 * ========================================================================
 *
 * 【1. Excel 上传与核对任务创建】
 *   前端上传医院 Excel 原始数据 → 前端调用对账引擎进行规则匹配 →
 *   将匹配结果（含原始数据、校正数据、差异金额）提交给后端持久化。
 *   后端负责文件存储、任务记录、行级明细保存。
 *
 * 【2. 核对结果查询】
 *   支持按医院名称筛选的任务列表、单个任务完整详情（含行明细）。
 *   提供导出日志记录，追踪文件的导出历史。
 *
 * 【3. 审核工作流（pending → approved / rejected）】
 *   财务人员审核核对结果。审核状态流转：
 *   - pending（待审核）：初始状态，Excel 上传后的默认状态
 *   - approved（已通过）：审核通过，确认差异数据有效
 *   - rejected（已驳回）：审核不通过，需要重新核对
 *   状态为终态，不可重复审核（防重复操作）。
 *
 * 【4. 模板化导出（Apache POI 操作）】
 *   基于预制的 xlsx 模板生成标准化文件：
 *   - 账单导出（bill.xlsx）：按科室分 sheet 的发货明细账单
 *   - 结款函导出（settlement.xlsx）：给医院的正式结款通知书
 *   模板操作为核心能力：插入/删除行、合并/取消合并单元格、克隆样式。
 *
 * 【5. HTML 打印】
 *   生成可直接打印或下载的 HTML 版本：
 *   - 账单打印 HTML（发货单汇总表）
 *   - 结款函打印 HTML（含公司信息、银行账号、免责条款）
 *   内置响应式表格样式和 A4 打印分页控制。
 *
 * 【6. 模板预览】
 *   提供内置默认结算模板的前端预览功能。
 *
 * ========================================================================
 *                       完整数据流（端到端）
 * ========================================================================
 *
 *  前端 Excel 文件
 *       │
 *       ▼
 *  [前端解析 Excel → 提取行数据]
 *       │
 *       ▼
 *  [前端调用对账引擎：每行数据 × 激活的计费规则]
 *       │  遍历定价规则中的 packTypes → 按包名匹配单价
 *       │  按 materialRules 应用材质加价系数
 *       │  计算 expectedUnitPrice（期望单价）
 *       │  计算 correctedTotalPrice（校正后总价）
 *       │  计算 difference = totalPrice - correctedTotalPrice（差异金额）
 *       │
 *       ▼
 *  [前端生成本次核对任务的 payload_json（含摘要+行数据）]
 *       │
 *       ▼
 *  POST /api/hospital-reconciliations  ───►  后端
 *       │   (payload_json + source_file)         │
 *       │                                        ├── 保存原始 Excel 到磁盘
 *       │                                        ├── 创建 HospitalReconciliationJob
 *       │                                        ├── 保存每行至 HospitalReconciliationRow
 *       │                                        └── 返回 jobId + 任务详情
 *       │
 *       ▼
 *  [财务审核]  PATCH /{jobId}/review
 *       │   (approved / rejected)
 *       │
 *       ▼
 *  [导出操作]
 *       ├── export-template-bill    → 基于模板生成账单 xlsx
 *       ├── export-template-settlement → 基于模板生成结款函 xlsx
 *       ├── export-html-settlement  → 下载 HTML 版结款函
 *       ├── print-template-bill     → 直接打印账单 HTML
 *       └── print-template-settlement → 直接打印结款函 HTML
 *
 * ========================================================================
 *                      第三方库与关键技术
 * ========================================================================
 * - Apache POI (XSSFWorkbook): Excel 模板读写，支持 .xlsx 格式
 * - Apache POI (CellRangeAddress): 单元格合并/取消合并管理
 * - Jackson (JsonUtils): payload JSON 的序列化/反序列化
 * - Spring @Transactional: 保证数据库操作的原子性
 * - Spring ResponseEntity: 支持二进制文件流和 HTML 文本的 HTTP 响应
 *
 * 前端对应路径：/api/hospital-reconciliations/**
 * 所有接口需要 JWT 认证（由 SecurityConfig 统一控制）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalReconciliationServiceImpl implements HospitalReconciliationService, ReconciliationLegacyExportBridge {

    /** 核对任务主表 Mapper（一条任务对应一次 Excel 上传核对） */
    private final HospitalReconciliationJobMapper jobMapper;

    /** 核对行明细 Mapper（一个任务包含多条行记录，对应 Excel 中的行） */
    private final HospitalReconciliationRowMapper rowMapper;

    /** 导出日志 Mapper（记录所有文件的导出历史） */
    private final HospitalReconciliationExportLogMapper exportLogMapper;

    /** 计费规则 Mapper（用于获取激活的计价规则） */
    private final HospitalPricingRuleMapper pricingRuleMapper;

    private final PricingRuleCompiler pricingRuleCompiler;

    private final ProductMatchService productMatchService;

    private final CustomerResolver customerResolver;

    private final LogisticsPipelineService logisticsPipelineService;

    private final SettlementJobFieldsApplier settlementJobFieldsApplier;

    private final LogisticsImportService logisticsImportService;

    private final ExternalInstrumentService externalInstrumentService;

    private final SheetOrchestrator sheetOrchestrator;

    private final BillExportLayoutResolver billExportLayoutResolver;

    private final D8DisplayNameResolver d8DisplayNameResolver;

    private final ExportTemplateResolver exportTemplateResolver;

    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private ExportEngineService exportEngineService;

    /** 数值格式样式缓存：避免为每个单元格重复创建 0.00 格式的 CellStyle */
    private final Map<String, CellStyle> numericStyleCache = new java.util.concurrent.ConcurrentHashMap<>();

    // ==================================================================
    //                   配置属性（application.yml 注入）
    // ==================================================================

    /** Excel 文件上传存储目录（默认：./uploads/hospital-reconciliations） */
    @Value("${app.upload.dir:./uploads/hospital-reconciliations}")
    private String uploadDir;

    @PostConstruct
    public void initUploadDir() {
        Path path = Paths.get(uploadDir);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(uploadDir).normalize();
        }
        try {
            Files.createDirectories(path);
            // 更新为绝对路径，后续 saveUploadFile 直接使用
            this.uploadDir = path.toString();
            log.info("上传目录已就绪: {}", this.uploadDir);
        } catch (IOException e) {
            log.warn("无法创建上传目录: {}, 保存文件时将重试: {}", path, e.getMessage());
        }
    }

    /** 账单模板文件路径（xlsx 模板，用于生成格式化账单） */
    @Value("${app.template.bill:}")
    private String billTemplatePath;

    /** 结款函模板文件路径（xlsx 模板，用于生成格式化结款函） */
    @Value("${app.template.settlement:}")
    private String settlementTemplatePath;

    /** 存储目录（保留字段，当前未使用，对应 FastAPI 的 storage_dir） */
    @Value("${app.storage.dir:./storage/hospital-reconciliations}")
    @SuppressWarnings("unused")
    private String storageDir;

    // ==================================================================
    //                   公司信息常量（用于结款函生成）
    // ==================================================================
    // 对应 FastAPI _build_settlement_print_html 中的公司信息
    // 这些信息会渲染到结款函 HTML 的"付款信息"段落中

    /** 灭菌服务公司全称 */
    @Value("${app.company.name:黑龙江省铂康医疗灭菌有限公司}")
    private String companyName;

    /** 公司银行账号 */
    @Value("${app.company.bank-account:}")
    private String bankAccount;

    /** 开户银行名称 */
    @Value("${app.company.bank-name:}")
    private String bankName;

    // ==================================================================
    //                   默认模板 ID 常量
    // ==================================================================

    /** 默认结款函模板标识 */
    public static final String DEFAULT_SETTLEMENT_TEMPLATE_ID = "default_settlement";

    /** 默认账单模板标识 */
    public static final String DEFAULT_BILL_TEMPLATE_ID = "default_bill";

    private PricingEngine buildPricingEngine(JsonNode baseRules, String hospitalName) {
        JsonNode compiled = pricingRuleCompiler.compile(baseRules, hospitalName);
        PricingEngine engine = new PricingEngine(compiled);
        engine.enableStructuredProductMatch(productMatchService);
        return engine;
    }

    private void applyLogisticsToJob(
            HospitalReconciliationJob job,
            JsonNode baseRules,
            String hospitalName,
            List<Map<String, Object>> rows) {
        if (baseRules == null || hospitalName == null || hospitalName.isBlank()) {
            return;
        }
        try {
            JsonNode compiled = pricingRuleCompiler.compile(baseRules, hospitalName);
            settlementJobFieldsApplier.applyAllFromMaps(job, compiled, rows, true);
        } catch (Exception e) {
            log.warn("物流费计算失败: {}", e.getMessage());
        }
    }

    private String resolveBillingMonth(HospitalReconciliationJob job) {
        if (job.getSourceDateRange() != null && job.getSourceDateRange().length() >= 7) {
            String prefix = job.getSourceDateRange().substring(0, 7);
            if (prefix.matches("\\d{4}-\\d{2}")) {
                return prefix;
            }
        }
        if (job.getCreatedAt() != null) {
            return job.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return null;
    }

    private void finalizeJobLogistics(
            HospitalReconciliationJob job,
            JsonNode baseRules,
            String hospitalName,
            List<Map<String, Object>> rows) {
        if (job.getId() == null || baseRules == null || hospitalName == null || hospitalName.isBlank()) {
            return;
        }
        Long customerId = customerResolver.resolveByName(hospitalName).map(c -> c.getId()).orElse(null);
        if (customerId != null) {
            logisticsImportService.linkImportsToJob(customerId, resolveBillingMonth(job), job.getId());
        }
        applyLogisticsToJob(job, baseRules, hospitalName, rows);
        jobMapper.updateById(job);
    }

    @Override
    public Result<LogisticsAllocationPreviewResponse> getLogisticsAllocationPreview(Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "Reconciliation job not found");
        }
        try {
            JsonNode baseRules = loadRulesForJob(job);
            if (baseRules == null) {
                return Result.fail(400, "无法加载计价规则");
            }
            JsonNode compiled = pricingRuleCompiler.compile(baseRules, job.getHospitalName());
            List<Map<String, Object>> rows = loadAllRowsForJob(job);
            Long customerId = customerResolver.resolveByName(job.getHospitalName())
                    .map(c -> c.getId()).orElse(null);
            Map<String, Object> breakdown = logisticsPipelineService.buildBreakdownForJob(
                    customerId, jobId, resolveBillingMonth(job), compiled, rows, false);
            double totalFee = job.getLogisticsFee() != null
                    ? job.getLogisticsFee()
                    : breakdown.get("total") instanceof Number n ? n.doubleValue() : 0;
            LogisticsAllocationService.AllocationResult allocation =
                    logisticsPipelineService.previewDeptAllocation(compiled, rows, totalFee);
            return Result.success(LogisticsAllocationPreviewResponse.builder()
                    .jobId(jobId)
                    .totalLogisticsFee(totalFee)
                    .allocationSum(allocation.allocatedSum())
                    .deptAllocations(LogisticsAllocationService.toBreakdownList(allocation))
                    .logisticsBreakdown(breakdown)
                    .build());
        } catch (Exception e) {
            log.warn("物流分摊预览失败 jobId={}: {}", jobId, e.getMessage());
            return Result.fail(500, "物流分摊预览失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<byte[]> exportLogisticsAllocation(Long jobId) {
        Result<LogisticsAllocationPreviewResponse> preview = getLogisticsAllocationPreview(jobId);
        if (preview.getCode() != 200 || preview.getData() == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            HospitalReconciliationJob job = jobMapper.selectById(jobId);
            byte[] content = sheetOrchestrator.buildLogisticsAllocationWorkbook(
                    job != null ? job.getHospitalName() : null,
                    preview.getData().getDeptAllocations());
            String filename = (job != null ? safeName(job.getHospitalName()) : "hospital")
                    + "_物流分摊_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(asciiDownloadName(filename)))
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(content);
        } catch (Exception e) {
            log.error("导出物流分摊失败 jobId={}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private JsonNode loadRulesForJob(HospitalReconciliationJob job) {
        if (job.getRuleId() == null) {
            return null;
        }
        HospitalPricingRule rule = pricingRuleMapper.selectById(job.getRuleId());
        if (rule == null || rule.getRulesJson() == null || rule.getRulesJson().isBlank()) {
            return null;
        }
        try {
            return JsonUtils.getObjectMapper().readTree(rule.getRulesJson());
        } catch (Exception e) {
            log.warn("解析计价规则失败 jobId={}: {}", job.getId(), e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> loadAllRowsForJob(HospitalReconciliationJob job) {
        if (job.getRowsJson() != null && !job.getRowsJson().isBlank()) {
            List<?> parsed = JsonUtils.parseToList(job.getRowsJson(), Map.class);
            if (parsed != null) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object item : parsed) {
                    if (item instanceof Map<?, ?> map) {
                        rows.add(castRowMap(map));
                    }
                }
                return rows;
            }
        }
        return rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(job.getId()).stream()
                .map(this::rowEntityToMap)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castRowMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }


    private void recomputeJobPriceTotals(HospitalReconciliationJob job, List<Map<String, Object>> rows) {
        double originalTotal = 0.0;
        double correctedTotal = 0.0;
        for (Map<String, Object> row : rows) {
            Double tp = safeGetDoubleObj(row, "totalPrice");
            if (tp != null) {
                originalTotal += tp;
            }
            Double ctp = safeGetDoubleObj(row, "correctedTotalPrice");
            if (ctp != null) {
                correctedTotal += ctp;
            }
        }
        job.setOriginalTotalPrice(Math.round(originalTotal * 100.0) / 100.0);
        job.setCorrectedTotalPrice(Math.round(correctedTotal * 100.0) / 100.0);
    }

    private Map<String, Object> parseLogisticsBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<String, Object> parsed = JsonUtils.parseToMap(json);
        return parsed == null || parsed.isEmpty() ? null : parsed;
    }

    private Map<String, Object> parseMonthlyBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<String, Object> parsed = JsonUtils.parseToMap(json);
        return parsed == null || parsed.isEmpty() ? null : parsed;
    }

    // ========================================================================
    //  第一节：核心操作 —— 核对任务 CRUD
    //  Section 1: Core CRUD for Reconciliation Jobs
    // ========================================================================
    //
    // 核对任务是系统的核心实体。每次 Excel 上传和规则匹配产生一个任务。
    // 任务包含：摘要统计、文件信息、规则引用、审核状态、行明细数据。
    //
    // 行数据通过 rowsJson 字段以 JSON 文本形式存储在任务主表中，
    // 同时在 HospitalReconciliationRow 表中做结构化的副本存储。
    // rowsJson 用于列表展示和前端快速访问；
    // 结构化行表用于后续的统计分析（如按医院统计差异金额等）。
    //
    // ========================================================================

    /**
     * 【创建核对任务】—— 核心写入端点
     *
     * POST /api/hospital-reconciliations
     *
     * ===== 触发场景 =====
     * 用户在前端上传医院 Excel 文件后，前端调用对账引擎完成规则匹配，
     * 将匹配结果和原始 Excel 文件一起提交到后端进行持久化存储。
     *
     * ===== 入参说明 =====
     * 该接口使用 multipart/form-data 格式，同时接收两个参数：
     *
     * 1. payload_json（字符串）：前端对账引擎生成的 JSON 字符串
     *    结构示例：
     *    {
     *      "hospitalName": "哈尔滨市第一医院",
     *      "operatorName": "张三",
     *      "ruleId": 1,
     *      "ruleName": "2024年灭菌计费标准",
     *      "ruleVersion": "v2.0",
     *      "summary": {
     *        "total": 120,          // 总行数
     *        "corrected": 15,       // 被校正的行数（价格不一致）
     *        "unchanged": 100,      // 未变化行数
     *        "warning": 3,          // 警告行数
     *        "skipped": 2,          // 跳过行数（无法识别的包）
     *        "totalDifference": 285.50  // 总差异金额（元）
     *      },
     *      "rows": [ ... ]         // 行明细数组
     *    }
     *
     * 2. source_file（MultipartFile）：原始 Excel 文件
     *
     * ===== 执行流程 =====
     * 1. 解析 payload_json 为 Map<String, Object>
     * 2. 提取医院名称、操作人、规则信息、摘要统计
     * 3. 生成版本号（按医院自增）
     * 4. 保存原始 Excel 到服务器磁盘
     * 5. 创建 HospitalReconciliationJob 主记录（含 rowsJson）
     * 6. 创建 HospitalReconciliationRow 明细记录（结构化存储）
     * 7. 返回完整任务响应
     *
     * ===== 事务边界 =====
     * @Transactional 保证 job 创建、row 保存、文件路径记录在同一个事务中。
     * 文件保存到磁盘的操作也在事务内，但磁盘 I/O 异常不会回滚数据库操作。
     * 这是一种"尽力写入"策略。
     *
     * @param payloadJson 前端对账引擎提交的 JSON 字符串（含摘要和行级数据）
     * @param sourceFile  原始上传的 Excel 文件（用于存档追溯）
     * @return 完整创建的核对任务响应（含行明细）
     */
    @Transactional
    public Result<ReconciliationJobResponse> createReconciliation(
            String payloadJson,
            MultipartFile sourceFile) {

        if (!isValidExcelFile(sourceFile)) {
            return Result.fail(400, "仅支持上传 .xlsx 或 .xls 格式的 Excel 文件");
        }

        try {
            // ===== 第一步：解析 JSON 请求负载 =====
            // payload_json 是前端对账引擎生成的完整结果
            Map<String, Object> payload = JsonUtils.parseToMap(payloadJson);
            if (payload == null) {
                return Result.fail(400, "无效的请求参数");
            }

            // ---- 提取元数据 ----
            // 医院名称：用于分类索引和文件目录命名
            String hospitalName = valueToString(payload.get("hospitalName"), "");
            // 操作人：记录是谁执行了本次核对操作
            String operatorName = valueToString(payload.get("operatorName"), "");
            // 规则信息：本次核对使用的是哪条计费规则
            Long ruleId = payload.get("ruleId") != null ? ((Number) payload.get("ruleId")).longValue() : null;
            String ruleName = valueToString(payload.get("ruleName"), null);
            String ruleVersion = valueToString(payload.get("ruleVersion"), null);

            // ---- 提取摘要统计数据 ----
            // summary 由前端对账引擎计算，后端直接存储不做二次计算
            Map<String, Object> summary = safeGetMap(payload, "summary");
            int totalRows = safeGetInt(summary, "total", 0);              // Excel 总数据行数
            int correctedRows = safeGetInt(summary, "corrected", 0);      // 被校正价格的行数
            int unchangedRows = safeGetInt(summary, "unchanged", 0);      // 无需校正的行数
            int warningRows = safeGetInt(summary, "warning", 0);          // 存在警告的行数
            int skippedRows = safeGetInt(summary, "skipped", 0);          // 无法匹配规则被跳过的行数
            double totalDifference = safeGetDouble(summary, "totalDifference", 0.0); // 总差异金额

            // ---- 提取行级数据 ----
            // rows 包含每一行的原始数据和校正数据
            List<Map<String, Object>> rowsData = safeGetList(payload, "rows");

            // ===== 第二步：生成版本号 =====
            // 按「医院 + 源文件」维度递增，不同月份/不同上传文件拥有独立版本链
            String sourceFileName = ReconciliationVersionGroup.normalizeSourceFileName(sourceFile.getOriginalFilename());
            int versionNo = nextVersionNo(hospitalName, sourceFileName);

            // ===== 第三步：保存上传的 Excel 文件到磁盘 =====
            String storedPath = saveUploadFile(sourceFile, hospitalName, versionNo);
            if (storedPath == null) {
                return Result.fail(500, "文件保存失败");
            }

            // ===== 第四步：创建核对任务主记录 =====
            HospitalReconciliationJob job = new HospitalReconciliationJob();
            job.setHospitalName(hospitalName);                    // 医院名称（用于分组和筛选）
            job.setSourceFileName(sourceFileName); // 原始文件名
            job.setSourceFilePath(storedPath);                    // 服务器存储路径
            job.setSourceFileSize(sourceFile.getSize());          // 文件大小（字节）
            job.setRuleId(ruleId);                                // 使用的计费规则 ID
            job.setRuleName(ruleName);                            // 规则名称（冗余存储）
            job.setRuleVersion(ruleVersion);                      // 规则版本（冗余存储）
            job.setPlanName(valueToString(payload.get("planName"), null)); // 方案名称
            job.setVersionNo(versionNo);                          // 该医院的第几次核对
            job.setTotalRows(totalRows);                          // 总行数
            job.setCorrectedRows(correctedRows);                  // 校正行数
            job.setUnchangedRows(unchangedRows);                  // 未变行数
            job.setWarningRows(warningRows);                      // 警告行数
            job.setSkippedRows(skippedRows);                      // 跳过行数
            job.setTotalDifference(totalDifference);              // 差异总金额
            job.setReviewStatus("pending");                       // 初始审核状态：待审核
            job.setOperatorName(operatorName);                    // 操作人
            job.setSourceDateRange(valueToString(payload.get("sourceDateRange"), null)); // 原始日期文本

            // 根据发货日期计算物流费：客户 LOGISTICS 策略优先于全局 logistics.feePerTrip
            if (ruleId != null) {
                HospitalPricingRule ruleEntity = pricingRuleMapper.selectById(ruleId);
                if (ruleEntity != null) {
                    try {
                        JsonNode rulesJson = JsonUtils.getObjectMapper().readTree(ruleEntity.getRulesJson());
                        recomputeJobPriceTotals(job, rowsData);
                        applyLogisticsToJob(job, rulesJson, hospitalName, rowsData);
                    } catch (Exception e) {
                        log.warn("物流费计算失败: {}", e.getMessage());
                    }
                }
            }

            // ===== 关键存储：行数据以 JSON 文本存储 =====
            // rowsJson 存储完整的行数据 JSON 数组，用于列表快速展示
            job.setRowsJson(JsonUtils.toJson(rowsData));
            computeSheetStats(job, rowsData);
            jobMapper.insert(job);

            // ===== 第五步：保存行明细到结构化表 =====
            // 同时在 HospitalReconciliationRow 表中保存结构化的行级数据
            // 便于后续的数据库查询和分析（如按医院统计总差异）
            saveReconciliationRows(job.getId(), rowsData);

            // ===== 第六步：返回响应（不含行数据，前端通过分页接口按需加载） =====
            return Result.success(buildJobResponse(job, false));

        } catch (Exception e) {
            log.error("创建核对任务失败: {}", e.getMessage(), e);
            return Result.fail(500, "创建核对任务失败: " + e.getMessage());
        }
    }

    /**
     * 【后端引擎导入：Excel 读取 → 规则校对 → 保存，一步完成】
     *
     * POST /api/hospital-reconciliations/import
     *
     * 替代前端逐行校对的性能瓶颈：后端用 Java 引擎并行处理所有行，
     * 省去前端序列化大量 JSON 的开销，整体速度提升 10-50 倍。
     */
    @Transactional
    public Result<ReconciliationJobResponse> importAndProcess(
            MultipartFile sourceFile,
            Long ruleId,
            String operatorName,
            String hospitalNameParam) {

        if (!isValidExcelFile(sourceFile)) {
            return Result.fail(400, "仅支持上传 .xlsx 或 .xls 格式的 Excel 文件");
        }

        try {
            // 1. 读取 Excel 所有行
            List<Map<String, Object>> allRows = new ArrayList<>();
            String dateRangeText = "";
            String firstSheetHospitalName = "";

            try (org.apache.poi.ss.usermodel.Workbook poiWorkbook =
                         org.apache.poi.ss.usermodel.WorkbookFactory.create(sourceFile.getInputStream())) {

                for (int s = 0; s < poiWorkbook.getNumberOfSheets(); s++) {
                    org.apache.poi.ss.usermodel.Sheet sheet = poiWorkbook.getSheetAt(s);
                    String sheetName = sheet.getSheetName();

                    // 收集所有行数据为 matrix
                    List<List<Object>> matrix = new ArrayList<>();
                    for (org.apache.poi.ss.usermodel.Row row : sheet) {
                        List<Object> rowData = new ArrayList<>();
                        for (int c = 0; c < row.getLastCellNum(); c++) {
                            org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                            if (cell == null) { rowData.add(""); continue; }
                            switch (cell.getCellType()) {
                                case NUMERIC:
                                    if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                                        rowData.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
                                    } else {
                                        double v = cell.getNumericCellValue();
                                        rowData.add(v == Math.floor(v) && !Double.isInfinite(v) ? (long) v : v);
                                    }
                                    break;
                                case STRING: rowData.add(cell.getStringCellValue()); break;
                                case BOOLEAN: rowData.add(cell.getBooleanCellValue()); break;
                                default: rowData.add("");
                            }
                        }
                        matrix.add(rowData);
                    }

                    if (matrix.isEmpty()) continue;

                    // 找表头行
                    int headerIdx = -1;
                    for (int r = 0; r < matrix.size(); r++) {
                        List<Object> row = matrix.get(r);
                        Set<String> norm = new java.util.LinkedHashSet<>();
                        for (Object cell : row) norm.add(normalizeCellText(cell));
                        if (norm.contains(normalizeCellText("发货日期")) &&
                            norm.contains(normalizeCellText("包名")) &&
                            norm.contains(normalizeCellText("包装材料")) &&
                            norm.contains(normalizeCellText("器械数")) &&
                            norm.contains(normalizeCellText("单价")) &&
                            norm.contains(normalizeCellText("总价"))) {
                            headerIdx = r;
                            break;
                        }
                    }
                    if (headerIdx < 0) continue;

                    // 创建表头映射
                    List<Object> headerRow = matrix.get(headerIdx);
                    Map<String, Integer> headerMap = new java.util.LinkedHashMap<>();
                    for (int c = 0; c < headerRow.size(); c++) {
                        String key = normalizeCellText(headerRow.get(c));
                        if (!key.isEmpty() && !headerMap.containsKey(key)) {
                            headerMap.put(key, c);
                        }
                    }

                    // 提取 B4 日期文本（仅第一个 sheet）
                    if (dateRangeText.isEmpty()) {
                        if (matrix.size() > 3) {
                            List<Object> row4 = matrix.get(3);
                            if (row4.size() > 1) {
                                dateRangeText = String.valueOf(row4.get(1)).trim();
                            }
                        }
                    }

                    // 提取医院名称
                    if (firstSheetHospitalName.isEmpty()) {
                        for (int r = 0; r < headerIdx && r < matrix.size(); r++) {
                            String t = findFirstMatchInRow(matrix.get(r), "医院");
                            if (!t.isEmpty()) { firstSheetHospitalName = t; break; }
                        }
                    }

                    // 逐行提取
                    for (int r = headerIdx + 1; r < matrix.size(); r++) {
                        List<Object> row = matrix.get(r);
                        Object deliveryDateRaw = getCellByHeader(row, headerMap, "发货日期");
                        String orderNo = sanitizeStr(getCellByHeader(row, headerMap, "发货单号"));
                        String type = sanitizeStr(getCellByHeader(row, headerMap, "类型"));
                        String categoryNo = sanitizeStr(getCellByHeader(row, headerMap, "包类别号"));
                        String packName = sanitizeStr(getCellByHeader(row, headerMap, "包名"));
                        String packageMaterial = sanitizeStr(getCellByHeader(row, headerMap, "包装材料"));
                        double packCount = toDoubleVal(getCellByHeader(row, headerMap, "包数"));
                        double instrumentCount = toDoubleVal(getCellByHeader(row, headerMap, "器械数"));
                        Object unitPriceRaw = getCellByHeader(row, headerMap, "单价");
                        Object totalPriceRaw = getCellByHeader(row, headerMap, "总价");

                        boolean hasDate = (deliveryDateRaw instanceof Number && ((Number) deliveryDateRaw).doubleValue() > 40000)
                                || String.valueOf(deliveryDateRaw).matches(".*\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}.*");
                        boolean hasKeyFields = !type.isEmpty() && !packName.isEmpty();
                        boolean looksInvalid = type.isEmpty() || packName.isEmpty();
                        if (!hasDate || !hasKeyFields || looksInvalid) continue;

                        Map<String, Object> rowData = new java.util.LinkedHashMap<>();
                        rowData.put("sheetName", sheetName);
                        rowData.put("rowNumber", r + 1);
                        rowData.put("deliveryDate", formatExcelDate(deliveryDateRaw));
                        rowData.put("orderNo", orderNo);
                        rowData.put("type", type);
                        rowData.put("categoryNo", categoryNo);
                        rowData.put("packName", packName);
                        rowData.put("packageMaterial", packageMaterial);
                        rowData.put("packCount", (int) packCount);
                        rowData.put("instrumentCount", (int) instrumentCount);
                        rowData.put("unitPrice", toDoubleOrNull(unitPriceRaw));
                        rowData.put("totalPrice", toDoubleOrNull(totalPriceRaw));
                        allRows.add(rowData);
                    }
                }
            }

            if (allRows.isEmpty()) {
                return Result.fail(400, "没有识别到有效明细行，请确认 Excel 格式与示例一致。");
            }

            // 2. 获取计费规则
            HospitalPricingRule ruleEntity = pricingRuleMapper.selectById(ruleId);
            if (ruleEntity == null) {
                return Result.fail(404, "计费规则不存在");
            }
            JsonNode rulesJson = JsonUtils.getObjectMapper().readTree(ruleEntity.getRulesJson());
            if (rulesJson == null) {
                return Result.fail(500, "规则数据解析失败");
            }

            // 3. 确定医院名称。定价引擎中的医院特例规则需要先拿到医院名。
            String hospitalName = (hospitalNameParam != null && !hospitalNameParam.isBlank())
                    ? hospitalNameParam
                    : (firstSheetHospitalName.isEmpty() ? "未命名医院" : firstSheetHospitalName);

            // 4. 逐行处理（含 FOLD 拆行）
            JsonNode compiledRules = pricingRuleCompiler.compile(rulesJson, hospitalName);
            PricingEngine engine = new PricingEngine(compiledRules);
            engine.enableStructuredProductMatch(productMatchService);
            List<Map<String, Object>> rowsToPrice = new ArrayList<>();
            for (Map<String, Object> row : allRows) {
                row.put("hospitalName", hospitalName);
                rowsToPrice.addAll(RowSplitter.expandRow(row, compiledRules));
            }
            List<Map<String, Object>> processedRows = new ArrayList<>();
            int corrected = 0, unchanged = 0, warning = 0, skipped = 0;
            double totalDiff = 0.0;
            double originalTotal = 0.0;
            double correctedTotal = 0.0;

            for (Map<String, Object> row : rowsToPrice) {
                enrichProductMatch(row);
                PricingEngine.ProcessedResult pr = engine.processRow(row);
                row.put("expectedUnitPrice", pr.expectedUnitPrice);
                row.put("correctedTotalPrice", pr.correctedTotalPrice);
                row.put("difference", pr.difference);
                row.put("status", pr.status);
                row.put("pricingRule", pr.pricingRule);
                row.put("notes", pr.notes);
                row.put("matchedRuleId", pr.matchedRuleId);
                row.put("matchedPriceOption", pr.matchedPriceOption);
                row.put("billingNotes", pr.billingNotes);
                processedRows.add(row);

                switch (pr.status) {
                    case "corrected": corrected++; break;
                    case "unchanged": unchanged++; break;
                    case "warning": warning++; break;
                    case "skipped": skipped++; break;
                }
                if ("warning".equals(pr.status) && pr.difference != null) totalDiff += pr.difference;
                Double tp = row.get("totalPrice") instanceof Number ? ((Number) row.get("totalPrice")).doubleValue() : null;
                if (tp != null) originalTotal += tp;
                if (pr.correctedTotalPrice != null) correctedTotal += pr.correctedTotalPrice;
            }

            // 5. 生成版本号（按医院 + 源文件独立递增）
            String sourceFileName = ReconciliationVersionGroup.normalizeSourceFileName(sourceFile.getOriginalFilename());
            int versionNo = nextVersionNo(hospitalName, sourceFileName);

            // 6. 保存文件
            String storedPath = saveUploadFile(sourceFile, hospitalName, versionNo);
            if (storedPath == null) {
                return Result.fail(500, "文件保存失败");
            }

            // 7. 保存任务
            HospitalReconciliationJob job = new HospitalReconciliationJob();
            job.setHospitalName(hospitalName);
            job.setSourceFileName(sourceFileName);
            job.setSourceFilePath(storedPath);
            job.setSourceFileSize(sourceFile.getSize());
            job.setRuleId(ruleId);
            job.setRuleName(ruleEntity.getName());
            job.setRuleVersion(ruleEntity.getVersion());
            job.setPlanName(ruleEntity.getPlanName());
            job.setVersionNo(versionNo);
            job.setTotalRows(allRows.size());
            job.setCorrectedRows(corrected);
            job.setUnchangedRows(unchanged);
            job.setWarningRows(warning);
            job.setSkippedRows(skipped);
            job.setTotalDifference(Math.round(totalDiff * 100.0) / 100.0);
            job.setOriginalTotalPrice(Math.round(originalTotal * 100.0) / 100.0);
            job.setCorrectedTotalPrice(Math.round(correctedTotal * 100.0) / 100.0);
            job.setReviewStatus("pending");
            job.setOperatorName(operatorName);
            job.setSourceDateRange(dateRangeText);

            // 8. 保存任务后关联物流导入并计算物流费
            job.setRowsJson(JsonUtils.toJson(processedRows));
            computeSheetStats(job, processedRows);
            jobMapper.insert(job);
            finalizeJobLogistics(job, rulesJson, hospitalName, allRows);

            // 9. 保存行明细
            saveReconciliationRows(job.getId(), processedRows);

            return Result.success(buildJobResponse(job, false));

        } catch (Exception e) {
            log.error("导入核对失败: {}", e.getMessage(), e);
            return Result.fail(500, "导入核对失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> importExternalInstruments(Long jobId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "请上传外来器械 Excel 文件");
        }
        var importResult = externalInstrumentService.importJobExcel(jobId, file);
        if (importResult.getCode() != 200) {
            return Result.fail(importResult.getCode(), importResult.getMsg());
        }
        return Result.success(Map.of(
                "jobId", jobId,
                "importedCount", importResult.getData() != null ? importResult.getData() : 0));
    }

    // ---- Excel 读取辅助方法 ----

    private String normalizeCellText(Object value) {
        return String.valueOf(value).replaceAll("\\s+", "").trim();
    }

    private Object getCellByHeader(List<Object> row, Map<String, Integer> headerMap, String headerName) {
        Integer idx = headerMap.get(normalizeCellText(headerName));
        if (idx == null || idx >= row.size()) return null;
        return row.get(idx);
    }

    private String sanitizeStr(Object value) {
        if (value == null) return "";
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isInfinite(d) || Double.isNaN(d)) return "";
            if (d == Math.floor(d)) {
                return String.valueOf((long) d);
            }
            return new java.text.DecimalFormat("#.##########").format(d);
        }
        return String.valueOf(value).trim();
    }

    private double toDoubleVal(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).replace(",", "").trim());
            } catch (Exception e) { }
        }
        return 0;
    }

    private Double toDoubleOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                String s = ((String) value).replace(",", "").replace("￥", "").trim();
                if (s.isEmpty()) return null;
                return Double.parseDouble(s);
            } catch (Exception e) { return null; }
        }
        return null;
    }

    private String formatExcelDate(Object value) {
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d > 40000 && d < 60000) {
                // Excel serial date
                java.util.Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(d);
                if (date != null) {
                    return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
                }
            }
        }
        return String.valueOf(value != null ? value : "").trim();
    }

    private String findFirstMatchInRow(List<Object> row, String keyword) {
        for (Object cell : row) {
            String text = String.valueOf(cell).trim();
            if (text.contains(keyword)) return text;
        }
        return "";
    }

    /**
     * 【获取核对任务列表】
     *
     * GET /api/hospital-reconciliations?hospital_name=
     *
     * ===== 查询逻辑 =====
     * - 如果提供了 hospital_name 参数：按医院名称精确筛选
     * - 如果未提供：返回所有医院的全部任务
     * 结果均按创建时间降序排列（最新的在前）。
     *
     * ===== 行数据处理 =====
     * 列表查询时 includeRows=false，不返回行明细数据，
     * 以减小响应体积（多条任务时行数据可能非常大）。
     * 前端只有在点击"查看详情"时才加载完整行数据。
     *
     * @param hospitalName 可选的医院名称筛选参数
     * @return 核对任务列表（不含行明细）
     */
    public Result<List<ReconciliationJobResponse>> listReconciliations(
            String hospitalName) {

        // 根据是否提供 hospital_name 参数执行不同的查询逻辑
        List<HospitalReconciliationJob> jobs;
        if (hospitalName != null && !hospitalName.isBlank()) {
            // 按医院筛选（用于"某医院的历史核对记录"场景）
            jobs = jobMapper.selectByHospitalNameOrderByCreatedAtDesc(hospitalName);
        } else {
            // 查询全部（用于全局核对任务列表）
            jobs = jobMapper.selectAllOrderByCreatedAtDesc();
        }

        // 列表查询不包含行数据（includeRows=false），减小网络传输
        List<ReconciliationJobResponse> data = jobs.stream()
                .map(job -> buildJobResponse(job, false))
                .collect(Collectors.toList());

        return Result.success(data);
    }

    /**
     * 【获取核对任务详情】—— 含完整的行级数据
     *
     * GET /api/hospital-reconciliations/{jobId}
     *
     * 与列表查询的关键区别：includeRows=true，返回该任务全部行明细。
     * 行数据从 job 表的 rowsJson 字段中 JSON 反序列化而来。
     *
     * 前端使用场景：
     * - 点击任务进入"核对详情页"
     * - 查看每行数据的原始价格、期望价格、校正价格、差异
     * - 审核前逐行确认数据的准确性
     * - 导出操作前的数据预览
     *
     * @param jobId 核对任务 ID
     * @return 任务详情（含行级数据、导出日志等完整信息）
     */
    public Result<ReconciliationJobResponse> getReconciliation(Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "核对任务不存在");
        }
        return Result.success(buildJobResponse(job, false));
    }

    /**
     * 【分页获取核对明细行】—— 按页查询，避免一次加载全部行
     *
     * GET /api/hospital-reconciliations/{jobId}/rows?page=1&size=200
     *
     * 从 hospital_reconciliation_row 表直接分页查询，不经过 rowsJson。
     */
    public Result<Map<String, Object>> getReconciliationRows(
            Long jobId,
            int page,
            int size) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "核对任务不存在");
        }
        int total = job.getTotalRows() != null ? job.getTotalRows() : 0;
        int offset = (page - 1) * size;
        List<HospitalReconciliationRow> pageRows =
                rowMapper.selectPageByJobId(jobId, offset, size);

        List<Map<String, Object>> rows = pageRows.stream()
                .map(this::rowEntityToMap)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    private Map<String, Object> rowEntityToMap(HospitalReconciliationRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("sheetName", r.getSheetName());
        m.put("rowNumber", r.getRowNumber());
        m.put("deliveryDate", r.getDeliveryDate());
        m.put("orderNo", r.getOrderNo());
        m.put("type", r.getType());
        m.put("categoryNo", r.getCategoryNo());
        m.put("packName", r.getPackName());
        m.put("packageMaterial", r.getPackageMaterial());
        m.put("packCount", r.getPackCount());
        m.put("instrumentCount", r.getInstrumentCount());
        m.put("unitPrice", r.getUnitPrice());
        m.put("totalPrice", r.getTotalPrice());
        m.put("expectedUnitPrice", r.getExpectedUnitPrice());
        m.put("correctedTotalPrice", r.getCorrectedTotalPrice());
        m.put("difference", r.getDifference());
        m.put("status", r.getStatus());
        m.put("pricingRule", r.getPricingRule());
        m.put("notes", JsonUtils.parseToList(r.getNotesJson(), String.class));
        m.put("matchedRuleId", r.getMatchedRuleId());
        m.put("matchedPriceOption", r.getMatchedPriceOption());
        m.put("isUrgent", Boolean.TRUE.equals(r.getIsUrgent()));
        if (r.getBillingNotes() != null && !r.getBillingNotes().isBlank()) {
            try {
                m.put("billingNotes", JsonUtils.getObjectMapper().readValue(r.getBillingNotes(), Map.class));
            } catch (Exception ignored) {
                m.put("billingNotes", null);
            }
        }
        return m;
    }

    /**
     * 【审核核对任务】—— 审批工作流核心操作
     *
     * PATCH /api/hospital-reconciliations/{jobId}/review
     *
     * ===== 审核工作流 =====
     * 核对任务创建后的状态流转：
     *
     *   ┌─────────┐
     *   │ pending │  ← 初始状态（Excel 上传后）
     *   └────┬────┘
     *        │
     *        ├─ 审核通过 →  approved（已确认，可以导出结款函/账单）
     *        │
     *        └─ 审核驳回 →  rejected（数据有误，需重新核对上传）
     *
     * ===== 状态约束 =====
     * - 只有 pending 状态的任务可以审核（防止重复审核或修改已确认的数据）
     * - 审核是一次性操作：一旦 approved 或 rejected，不可再修改
     * - 如果数据需要重新核对，用户需要创建新的核对任务（上传新的 Excel）
     *
     * ===== 审核逻辑 =====
     * 审核不涉及价格数据的再次计算，只是确认前端对账引擎的结果。
     * 财务人员逐行核对差异数据后，确认差异合理则通过（approved），
     * 发现异常数据则驳回（rejected）并要求重新上传核对。
     *
     * @param jobId  要审核的核对任务 ID
     * @param request 审核请求体（审核状态 + 审核意见 + 审核人姓名）
     * @return 审核后的任务详情（不含行数据）
     */
    @Transactional
    public Result<ReconciliationJobResponse> reviewReconciliation(
            Long jobId,
            ReconciliationReviewRequest request) {

        // 第一步：校验任务是否存在
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "核对任务不存在");
        }

        // 第二步：防重复审核校验
        // 只有 pending 状态允许审核；approved/rejected 为终态不可逆
        if (!"pending".equals(job.getReviewStatus())) {
            return Result.fail(400, "该任务已审核，不可重复操作");
        }

        // 第三步：更新审核信息
        job.setReviewStatus(request.getReviewStatus());       // 审核结果（approved/rejected）
        job.setReviewComment(request.getReviewComment());     // 审核意见（如："数据有误，包名不匹配"）
        job.setReviewerName(request.getReviewerName());       // 审核人姓名
        jobMapper.updateById(job);

        // 返回审核结果（不含行数据）
        return Result.success(buildJobResponse(job, false));
    }

    /**
     * 【批量更新核对行数据】—— 一键修正/手工调整后保存
     *
     * PUT /api/hospital-reconciliations/{jobId}/rows
     *
     * 用户在详情页点击"一键修正"或手动编辑行数据后，调用此接口
     * 批量更新所有行数据并重新计算汇总统计。
     *
     * ===== 更新逻辑 =====
     * 1. 接收前端传入的完整行数据列表（替换式更新）
     * 2. 重新序列化为 JSON 字符串更新 rowsJson 字段
     * 3. 遍历行数据重新计算汇总统计（已修正/未变更/复核/跳过行数 + 总差额）
     * 4. rowsJson 更新后，getReconciliation 端点读取的就是最新数据
     *
     * @param jobId       核对任务 ID
     * @param updatedRows 更新后的完整行数据列表
     * @return 更新后的任务详情（含行数据）
     */
    @Transactional
    public Result<ReconciliationJobResponse> updateRows(
            Long jobId,
            List<Map<String, Object>> updatedRows) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "核对任务不存在");
        }
        if (!"pending".equals(job.getReviewStatus())) {
            return Result.fail(400, "该版本已审核，不可修改");
        }

        List<HospitalReconciliationRow> existingEntities =
                rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
        List<Map<String, Object>> existingRows = existingEntities.stream()
                .map(this::rowEntityToMap)
                .collect(Collectors.toList());

        if (rowsDataEquals(existingRows, updatedRows)) {
            return Result.success(buildJobResponse(job, false));
        }

        HospitalReconciliationJob savedJob = createNewVersionFromJob(job, updatedRows);
        return Result.success(buildJobResponse(savedJob, false));
    }

    /**
     * 批量标记/取消加急行。
     *
     * PATCH /api/hospital-reconciliations/{jobId}/rows/urgent
     */
    @Transactional
    public Result<ReconciliationJobResponse> updateRowsUrgent(
            Long jobId,
            com.hospital.backend.dto.request.hospital.UpdateRowsUrgentRequest request) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "核对任务不存在");
        }
        if (!"pending".equals(job.getReviewStatus())) {
            return Result.fail(400, "该版本已审核，不可修改");
        }
        if (request.getIsUrgent() == null) {
            return Result.fail(400, "isUrgent 不能为空");
        }

        List<HospitalReconciliationRow> existingEntities =
                rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
        if (existingEntities.isEmpty()) {
            return Result.fail(400, "任务无明细行");
        }

        boolean matchedAny = false;
        for (HospitalReconciliationRow row : existingEntities) {
            if (!matchesUrgentTarget(row, request)) {
                continue;
            }
            row.setIsUrgent(request.getIsUrgent());
            matchedAny = true;
        }
        if (!matchedAny) {
            return Result.fail(400, "未匹配到任何行");
        }

        List<Map<String, Object>> updatedRows = existingEntities.stream()
                .map(this::rowEntityToMap)
                .collect(Collectors.toList());
        HospitalReconciliationJob savedJob = createNewVersionFromJob(job, updatedRows);
        return Result.success(buildJobResponse(savedJob, false));
    }

    private boolean matchesUrgentTarget(
            HospitalReconciliationRow row,
            com.hospital.backend.dto.request.hospital.UpdateRowsUrgentRequest request) {
        if (request.getRowIds() != null && !request.getRowIds().isEmpty()) {
            return row.getId() != null && request.getRowIds().contains(row.getId());
        }
        if (request.getRows() == null || request.getRows().isEmpty()) {
            return false;
        }
        for (com.hospital.backend.dto.request.hospital.UpdateRowsUrgentRequest.RowRef ref : request.getRows()) {
            if (ref.getSheetName() != null
                    && ref.getSheetName().equals(row.getSheetName())
                    && ref.getRowNumber() != null
                    && ref.getRowNumber().equals(row.getRowNumber())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 【重新定价】—— 使用任务关联的计费规则重新计算所有行
     *
     * POST /api/hospital-reconciliations/{jobId}/reprice
     *
     * 前端"一键修正"按钮调用此接口，后端 PricingEngine 是唯一的定价逻辑源。
     * 不修改数据库，仅返回重新定价后的行数据和摘要统计。
     *
     * @param jobId 核对任务 ID
     * @return 重新定价后的行数据 + 摘要统计
     */
    public Result<Map<String, Object>> reprice(Long jobId) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail(404, "核对任务不存在");
        }
        Long ruleId = job.getRuleId();
        if (ruleId == null) {
            return Result.fail(400, "该任务未关联计费规则，无法重新定价");
        }
        HospitalPricingRule ruleEntity = pricingRuleMapper.selectById(ruleId);
        if (ruleEntity == null) {
            return Result.fail(404, "关联的计费规则不存在");
        }
        try {
            JsonNode rulesJson = JsonUtils.getObjectMapper().readTree(ruleEntity.getRulesJson());
            if (rulesJson == null) {
                return Result.fail(500, "规则数据解析失败");
            }

            PricingEngine engine = buildPricingEngine(rulesJson, job.getHospitalName());
            List<HospitalReconciliationRow> rawRows =
                    rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);

            List<Map<String, Object>> pricedRows = new ArrayList<>();
            int corrected = 0, unchanged = 0, warning = 0, skipped = 0;
            double totalDiff = 0.0;

            for (HospitalReconciliationRow row : rawRows) {
                Map<String, Object> rowMap = rowEntityToMap(row);
                rowMap.put("hospitalName", job.getHospitalName());
                PricingEngine.ProcessedResult pr = engine.processRow(rowMap);
                applyBatchCorrection(rowMap, pr);
                pricedRows.add(rowMap);

                String status = valueToString(rowMap.get("status"), "unchanged");
                switch (status) {
                    case "corrected": corrected++; break;
                    case "unchanged": unchanged++; break;
                    case "warning": warning++; break;
                    case "skipped": skipped++; break;
                }
                if ("warning".equals(status)) {
                    Double diff = safeGetDoubleObj(rowMap, "difference");
                    if (diff != null) totalDiff += diff;
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rows", pricedRows);
            result.put("summary", Map.of(
                    "total", pricedRows.size(),
                    "corrected", corrected,
                    "unchanged", unchanged,
                    "warning", warning,
                    "skipped", skipped,
                    "totalDifference", Math.round(totalDiff * 100.0) / 100.0
            ));
            return Result.success(result);
        } catch (Exception e) {
            log.error("重新定价失败: {}", e.getMessage(), e);
            return Result.fail(500, "重新定价失败: " + e.getMessage());
        }
    }

    // ========================================================================
    //  第二节：导出日志记录
    //  Section 2: Export Logging
    // ========================================================================
    //
    // 导出日志用于追踪所有文件导出操作的审计记录。
    // 每次成功导出账单或结款函后，前端调用此接口记录导出操作。
    // 日志包含：导出类型（bill/settlement）、文件名、操作人、时间。
    // 日志在任务详情页展示，用户可以查看历史导出记录。
    //
    // ========================================================================

    /**
     * 【创建导出日志】—— 记录文件导出历史
     *
     * POST /api/hospital-reconciliations/{jobId}/exports
     *
     * 前端在成功下载文件后调用此接口，将导出操作记录到数据库。
     * 这与文件的实际生成和下载分离（生成下载在另外的端点中完成）。
     *
     * ===== 使用场景 =====
     * - 前端下载账单 xlsx 后 → 调用此接口记录"bill"类型导出
     * - 前端下载结款函 xlsx 后 → 调用此接口记录"settlement"类型导出
     * - 前端查看任务详情时 → 加载该任务的导出日志列表
     *
     * @param jobId   核对任务 ID（关联到哪个任务）
     * @param request 导出日志请求（含 exportType, fileName, operatorName）
     * @return 创建的导出日志记录
     */
    @Transactional
    public Result<ReconciliationExportLogResponse> createExportLog(
            Long jobId,
            CreateExportLogRequest request) {

        // 校验任务存在（外键约束逻辑校验）
        if (!jobMapper.existsById(jobId)) {
            return Result.fail(404, "核对任务不存在");
        }

        // 创建导出日志记录
        HospitalReconciliationExportLog exportLog = new HospitalReconciliationExportLog();
        exportLog.setJobId(jobId);                              // 关联的任务 ID
        exportLog.setExportType(request.getExportType());       // 导出类型：bill / settlement
        exportLog.setFileName(request.getFileName());           // 导出的文件名
        exportLog.setFilePath(null);                            // 文件路径暂不存储（当前无服务器端文件保留）
        exportLog.setOperatorName(request.getOperatorName());   // 执行导出操作的人
        exportLog.setCreatedAt(LocalDateTime.now());            // 导出时间
        exportLogMapper.insert(exportLog);

        // 构造响应 DTO
        ReconciliationExportLogResponse response = new ReconciliationExportLogResponse(
                exportLog.getId(), exportLog.getExportType(), exportLog.getFileName(),
                exportLog.getFilePath(), exportLog.getOperatorName(), exportLog.getCreatedAt());

        return Result.success(response);
    }

    // ========================================================================
    //  第三节：模板列表与预览
    //  Section 3: Template Listing and Preview
    // ========================================================================
    //
    // 系统目前使用内置的默认模板（模板定义在后端的 HTML 生成代码中）。
    // 未来可扩展为从配置目录加载用户自定义的 xlsx 模板文件。
    //
    // 模板预览功能让用户在导出前可以看到模板样式和内容布局，
    // 方便确认模板是否符合需求。
    //
    // 模板类型：
    // - settlement（结款函）：正式结款通知函，含公司信息、费用明细、银行账户
    // - bill（账单）：发货明细账单，含各科室的灭菌包明细
    //
    // ========================================================================

    /**
     * 【获取结款函模板列表】
     *
     * GET /api/hospital-reconciliations/templates/settlement
     *
     * 返回系统可用的结款函模板列表。
     * 当前版本仅返回内置默认模板，后续可扩展为从文件系统加载。
     *
     * @return 模板列表（ID + 名称 + 描述）
     */
    public Result<List<TemplateRefResponse>> listSettlementTemplates() {
        // 内置默认模板（匹配 FastAPI registry fallback 逻辑）
        List<TemplateRefResponse> templates = new ArrayList<>();
        templates.add(new TemplateRefResponse(
                DEFAULT_SETTLEMENT_TEMPLATE_ID,   // 模板 ID: "default_settlement"
                "默认结款函模板",                  // 显示名称
                "标准结款通知函模板"));             // 描述信息
        return Result.success(templates);
    }

    /**
     * 【获取账单模板列表】
     *
     * GET /api/hospital-reconciliations/templates/bill
     *
     * @return 账单模板列表
     */
    public Result<List<TemplateRefResponse>> listBillTemplates() {
        List<TemplateRefResponse> templates = new ArrayList<>();
        templates.add(new TemplateRefResponse(
                DEFAULT_BILL_TEMPLATE_ID,       // 模板 ID: "default_bill"
                "默认账单模板",                   // 显示名称
                "标准账单导出模板"));              // 描述信息
        return Result.success(templates);
    }

    /**
     * 【预览结款函模板】
     *
     * GET /api/hospital-reconciliations/templates/settlement/{templateId}/preview
     *
     * 返回内置默认模板的渲染 HTML（含示例数据）。
     * 用户在导出前可以通过此接口预览模板样式。
     *
     * ===== 参数说明 =====
     * - default_settlement：默认模板，生成含示例费用的预览 HTML
     * - 其他 ID：返回 404（暂不支持自定义模板预览）
     *
     * @param templateId 模板 ID（当前仅支持 "default_settlement"）
     * @return 结款函预览 HTML 页面
     */
    public ResponseEntity<String> previewSettlementTemplate(String templateId) {
        // 只支持默认模板；扩展模板不支持预览时返回 404
        if (!DEFAULT_SETTLEMENT_TEMPLATE_ID.equals(templateId)) {
            return ResponseEntity.status(404).body("模板不存在: " + templateId);
        }

        // 构建示例数据的预览 HTML（匹配 FastAPI preview_settlement_template）
        String sampleFeeTable = buildSampleFeeTableHtml();  // 示例费用表格
        String logoDataUri = "";                             // Logo（当前未实现）
        String html = buildSettlementPreviewHtml(sampleFeeTable, logoDataUri);

        // 以完整 HTML 页面形式返回
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    // ========================================================================
    //  第四节：导出操作 —— 账单 Excel（Apache POI 模板操作）
    //  Section 4: Export — Bill Excel (Template-based with Apache POI)
    // ========================================================================
    //
    // 账单导出是系统的核心功能之一。生成的 xlsx 文件包含：
    // - 各科室（如手术室、供应室）分 sheet 展示
    // - 每个 sheet 包含：标题区、日期范围、医院名称、汇总统计
    // - 明细数据区：发货日期、单号、类型、包类别号、包名、包数、单价、总价
    // - 自动筛选、合并单元格、logo 图片
    //
    // 如果预设的 xlsx 模板文件存在，则进行模板操作（插入行/删除行/合并单元格）；
    // 如果模板文件不存在，降级生成简单 Excel（仅表头 + 数据行）。
    //
    // ========================================================================

    /**
     * 【导出账单 Excel（模板方式）】
     *
     * POST /api/hospital-reconciliations/export-template-bill
     *
     * ===== 执行流程 =====
     * 1. 接收前端提交的完整账单数据请求（含行数据、sheet 元数据）
     * 2. 检查模板 xlsx 文件是否存在（由配置 app.template.bill 指定）
     * 3. 存在 → 加载模板 → 写入数据 → 返回字节流
     * 4. 不存在 → 生成简单 Excel 降级
     * 5. 返回 Content-Disposition: attachment 的文件下载响应
     *
     * ===== 模板操作详细步骤 =====
     * (详见 createBillTemplateWorkbook 方法)
     *
     * @param request 账单导出请求体（fees, sheetMetas, hospitalName 等）
     * @return xlsx 二进制文件流
     */
    public ResponseEntity<byte[]> exportTemplateBill(
            HospitalBillTemplateExportRequest request) {
        return exportEngineService.exportBill(request);
    }

    /**
     * 【导出结款函 Excel（模板方式）】
     *
     * POST /api/hospital-reconciliations/export-template-settlement
     *
     * 结款函是给医院的正式结款通知，内容包括：
     * - 标题：结款通知函
     * - 医院名称与结算期间
     * - 费用明细表（序号/条目/费用/备注）
     * - 合计金额与中文大写金额
     * - 落款与公司信息
     *
     * 与账单导出一样，优先使用 xlsx 模板，模板不存在则降级。
     *
     * @param request 结款函导出请求体（feeRows, totalAmount, uppercaseTotal 等）
     * @return xlsx 二进制文件流
     */
    public ResponseEntity<byte[]> exportTemplateSettlement(
            HospitalSettlementTemplateExportRequest request) {
        return exportEngineService.exportSettlement(request);
    }

    // ========================================================================
    //  第五节：导出操作 —— 分科室价格汇总
    //  Section 5: Export — Department Summary
    // ========================================================================

    /**
     * 【导出分科室价格汇总】
     *
     * POST /api/hospital-reconciliations/{jobId}/export-department-summary
     *
     * 按导入 Excel 的工作表（科室）分组，汇总每个科室的灭菌总价，
     * 使用模板 xlsx 生成格式化的分科室价格汇总表。
     *
     * ===== 数据来源 =====
     * 从数据库加载该任务的全部行数据，按 sheet_name 分组，
     * 优先取 correctedTotalPrice，若为 null 则回退到 totalPrice。
     *
     * @param jobId 核对任务 ID
     * @return xlsx 二进制文件流
     */
    public ResponseEntity<byte[]> exportDepartmentSummary(Long jobId) {
        try {
            HospitalReconciliationJob job = jobMapper.selectById(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }

            // 按科室汇总价格
            Map<String, Double> deptSums = new LinkedHashMap<>();
            List<HospitalReconciliationRow> rows = rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
            for (HospitalReconciliationRow row : rows) {
                String sheet = row.getSheetName() != null && !row.getSheetName().isBlank()
                        ? row.getSheetName() : "(默认)";
                Double price = row.getCorrectedTotalPrice() != null
                        ? row.getCorrectedTotalPrice()
                        : row.getTotalPrice();
                deptSums.merge(sheet, price != null ? price : 0.0, Double::sum);
            }

            if (deptSums.isEmpty()) {
                return ResponseEntity.badRequest().body("该任务没有可汇总的数据".getBytes());
            }

            // 按科室名排序
            List<Map.Entry<String, Double>> sortedDepts = new ArrayList<>(deptSums.entrySet());
            sortedDepts.sort(Map.Entry.comparingByKey(java.text.Collator.getInstance(java.util.Locale.CHINA)));

            // 计算总价
            double grandTotal = sortedDepts.stream().mapToDouble(Map.Entry::getValue).sum();

            // 直接从零创建 xlsx，不依赖模板
            byte[] content;
            try (XSSFWorkbook workbook = createDepartmentSummaryWorkbook(
                    job.getHospitalName(), job, sortedDepts, grandTotal)) {
                content = writeWorkbookToBytes(workbook);
            }

            // 记录导出日志
            String filename = asciiDownloadName(
                    safeName(job.getHospitalName() != null ? job.getHospitalName() : "hospital")
                    + "_分科室汇总_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + ".xlsx");
            try {
                HospitalReconciliationExportLog exportLog = new HospitalReconciliationExportLog();
                exportLog.setJobId(jobId);
                exportLog.setExportType("department_summary");
                exportLog.setFileName(filename);
                exportLog.setFilePath("");
                exportLog.setOperatorName(job.getOperatorName());
                exportLogMapper.insert(exportLog);
            } catch (Exception e) {
                log.warn("记录导出日志失败: {}", e.getMessage());
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(filename))
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(content);
        } catch (Exception e) {
            log.error("导出分科室汇总失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 【导出异常明细】
     *
     * POST /api/hospital-reconciliations/{jobId}/export-anomalies
     *
     * 导出所有差额不为 0 的异常行，格式与账单类似，但额外显示原价与修正价格的对比。
     * 包含列：序号、发货日期、发货单号、类型、包类别号、包名、包装材料、包数、
     *         器械数、原单价、原总价、修正单价、修正总价、差额、规则说明、备注
     *
     * @param jobId 核对任务 ID
     * @return xlsx 二进制文件流
     */
    public ResponseEntity<byte[]> exportAnomalies(Long jobId) {
        try {
            HospitalReconciliationJob job = jobMapper.selectById(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }

            List<BillRowItem> allRows = loadBillRowsFromDb(jobId);
            List<BillRowItem> anomalies = allRows.stream()
                    .filter(r -> r.getDifference() != null && Math.abs(r.getDifference()) > 0.001)
                    .collect(Collectors.toList());

            if (anomalies.isEmpty()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition("无异常数据.xlsx"))
                        .contentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(generateSimpleExcel("无异常数据", java.util.Arrays.asList(
                                java.util.Arrays.asList("无异常数据"))));
            }

            // 构建表头和数据行
            List<List<String>> rows = new ArrayList<>();
            rows.add(Arrays.asList(
                    "行号", "发货日期", "发货单号", "类型", "包类别号", "包名",
                    "包装材料", "包数", "器械数",
                    "原单价", "原总价", "修正单价", "修正总价", "差额"
            ));

            for (BillRowItem row : anomalies) {
                String origUnitPrice = row.getUnitPrice() != null ? String.format("%.2f", row.getUnitPrice()) : "";
                String origTotalPrice = row.getTotalPrice() != null ? String.format("%.2f", row.getTotalPrice()) : "";
                String corrUnitPrice = row.getExpectedUnitPrice() != null ? String.format("%.2f", row.getExpectedUnitPrice()) : "";
                String corrTotalPrice = row.getCorrectedTotalPrice() != null ? String.format("%.2f", row.getCorrectedTotalPrice()) : "";
                String diff = row.getDifference() != null ? String.format("%.2f", row.getDifference()) : "";

                rows.add(Arrays.asList(
                        row.getRowNumber() != null ? String.valueOf(row.getRowNumber()) : "",
                        row.getDeliveryDate() != null ? row.getDeliveryDate() : "",
                        formatIntegerString(row.getOrderNo()),
                        row.getType() != null ? row.getType() : "",
                        formatIntegerString(row.getCategoryNo()),
                        row.getPackName() != null ? row.getPackName() : "",
                        row.getPackageMaterial() != null ? row.getPackageMaterial() : "",
                        row.getPackCount() != null ? String.valueOf(row.getPackCount()) : "",
                        row.getInstrumentCount() != null ? String.valueOf(row.getInstrumentCount()) : "",
                        origUnitPrice,
                        origTotalPrice,
                        corrUnitPrice,
                        corrTotalPrice,
                        diff
                ));
            }

            byte[] content = generateSimpleExcel("异常明细", rows);

            String filename = asciiDownloadName(
                    safeName(job.getHospitalName() != null ? job.getHospitalName() : "hospital")
                    + "_异常明细_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + ".xlsx");

            // 记录导出日志
            try {
                HospitalReconciliationExportLog exportLog = new HospitalReconciliationExportLog();
                exportLog.setJobId(jobId);
                exportLog.setExportType("anomaly");
                exportLog.setFileName(filename);
                exportLog.setFilePath("");
                exportLog.setOperatorName(job.getOperatorName());
                exportLogMapper.insert(exportLog);
            } catch (Exception e) {
                log.warn("记录导出日志失败: {}", e.getMessage());
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(filename))
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(content);
        } catch (Exception e) {
            log.error("导出异常明细失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 从零创建分科室价格汇总 xlsx，不依赖任何模板。
     *
     * 布局（与期望模板完全一致）：
     *   Row 1: 空白
     *   Row 2: B2:E2 合并 — 标题（方案名各科室+年月+灭菌价格汇总）
     *   Row 3: B3=科室 C3=价格 D3=科室 E3=价格（表头加粗）
     *   Row 4..N: 数据行，左栏(B-C) + 右栏(D-E)，两栏均分科室
     *   Row N+1: 合计行，D/E 合并填中文大写金额
     *   Row N+2: 空白
     *   Row N+3: B:E 合并 — 制表人/核算人/甲方核算人
     *   Row N+4: B:E 合并 — 公司名 + 当天日期
     */
    private XSSFWorkbook createDepartmentSummaryWorkbook(String hospitalName,
                                                          HospitalReconciliationJob job,
                                                          List<Map.Entry<String, Double>> sortedDepts,
                                                          double grandTotal) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet(hospitalName != null ? hospitalName : "分科室汇总");
        sheet.setDisplayGridlines(false);

        // -- 列宽 --
        sheet.setColumnWidth(0, (int) (1.7 * 256));
        sheet.setColumnWidth(1, (int) (30.2 * 256));
        sheet.setColumnWidth(2, (int) (12.8 * 256));
        sheet.setColumnWidth(3, (int) (29.6 * 256));
        sheet.setColumnWidth(4, (int) (12.5 * 256));

        // -- 字体 --
        XSSFFont font12 = wb.createFont();        // 12pt 数据/表头
        font12.setFontName("宋体");
        font12.setFontHeightInPoints((short) 12);

        XSSFFont font12b = wb.createFont();       // 12pt 加粗
        font12b.setFontName("宋体");
        font12b.setFontHeightInPoints((short) 12);
        font12b.setBold(true);

        XSSFFont font14b = wb.createFont();       // 14pt 加粗 标题
        font14b.setFontName("宋体");
        font14b.setFontHeightInPoints((short) 14);
        font14b.setBold(true);

        XSSFFont font10 = wb.createFont();        // 10pt 签名/空白
        font10.setFontName("宋体");
        font10.setFontHeightInPoints((short) 10);

        // -- 样式 --
        // 数据/文字格（12pt 居中，四边细边框）
        XSSFCellStyle textStyle = wb.createCellStyle();
        textStyle.setFont(font12);
        textStyle.setAlignment(HorizontalAlignment.CENTER);
        textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        textStyle.setBorderTop(BorderStyle.THIN);
        textStyle.setBorderBottom(BorderStyle.THIN);
        textStyle.setBorderLeft(BorderStyle.THIN);
        textStyle.setBorderRight(BorderStyle.THIN);

        // 表头（同 textStyle + 加粗）
        XSSFCellStyle headerStyle = wb.createCellStyle();
        headerStyle.cloneStyleFrom(textStyle);
        headerStyle.setFont(font12b);

        // 标题（14pt 加粗，居中，无边框）
        XSSFCellStyle titleStyle = wb.createCellStyle();
        titleStyle.setFont(font14b);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // 空白行和签名行（10pt 无边框）
        XSSFCellStyle footerLeft = wb.createCellStyle();
        footerLeft.setFont(font10);
        footerLeft.setAlignment(HorizontalAlignment.LEFT);
        footerLeft.setVerticalAlignment(VerticalAlignment.TOP);

        XSSFCellStyle footerRight = wb.createCellStyle();
        footerRight.setFont(font10);
        footerRight.setAlignment(HorizontalAlignment.RIGHT);
        footerRight.setVerticalAlignment(VerticalAlignment.TOP);

        // 空白格专用（避免默认 Calibri 字体出现在空单元格）
        XSSFCellStyle emptyStyle = wb.createCellStyle();
        XSSFFont font11 = wb.createFont();
        font11.setFontName("宋体");
        font11.setFontHeightInPoints((short) 11);
        emptyStyle.setFont(font11);

        // -- 数据 --
        String planName = job.getPlanName() != null && !job.getPlanName().isBlank()
                ? job.getPlanName() : hospitalName;
        String title = buildDeptSummaryTitle(planName, job);

        // 取结算月份最后一天作为落款日期
        int[] ym = extractYearMonthInts(job);
        java.time.LocalDate closingDate;
        if (ym != null) {
            closingDate = java.time.YearMonth.of(ym[0], ym[1]).atEndOfMonth();
        } else {
            closingDate = java.time.LocalDate.now();
        }
        String closingDateStr = closingDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"));

        int half = (int) Math.ceil(sortedDepts.size() / 2.0);
        List<Map.Entry<String, Double>> leftCol = sortedDepts.subList(0, Math.min(half, sortedDepts.size()));
        List<Map.Entry<String, Double>> rightCol = sortedDepts.subList(Math.min(half, sortedDepts.size()), sortedDepts.size());
        int dataRows = Math.max(1, Math.max(leftCol.size(), rightCol.size()));

        int r = 0;

        // Row 0: 空白
        XSSFRow row0 = sheet.createRow(r++);
        row0.setHeightInPoints(15);
        for (int c = 0; c <= 4; c++) { XSSFCell ec = row0.createCell(c); ec.setCellStyle(emptyStyle); }

        // Row 1: 标题 B:E 合并
        XSSFRow titleRow = sheet.createRow(r++);
        titleRow.setHeightInPoints(25.5f);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 1, 4));
        XSSFCell titleCell = titleRow.createCell(1);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);
        for (int c = 0; c <= 4; c++) {
            if (c != 1) { XSSFCell ec = titleRow.createCell(c); ec.setCellStyle(emptyStyle); }
        }

        // Row 2: 表头
        XSSFRow headerRow = sheet.createRow(r++);
        headerRow.setHeightInPoints(15);
        String[] headers = {"科室", "价格", "科室", "价格"};
        for (int c = 0; c < 4; c++) {
            XSSFCell hc = headerRow.createCell(c + 1);
            hc.setCellValue(headers[c]);
            hc.setCellStyle(headerStyle);
        }
        { XSSFCell ec = headerRow.createCell(0); ec.setCellStyle(emptyStyle); }

        // 数据行
        for (int i = 0; i < dataRows; i++) {
            XSSFRow dataRow = sheet.createRow(r++);
            dataRow.setHeightInPoints(15);
            { XSSFCell ec = dataRow.createCell(0); ec.setCellStyle(emptyStyle); }

            XSSFCell cellB = dataRow.createCell(1); cellB.setCellStyle(textStyle);
            XSSFCell cellC = dataRow.createCell(2); cellC.setCellStyle(textStyle);
            if (i < leftCol.size()) {
                cellB.setCellValue(leftCol.get(i).getKey());
                setPriceValue(cellC, leftCol.get(i).getValue());
            }

            XSSFCell cellD = dataRow.createCell(3); cellD.setCellStyle(textStyle);
            XSSFCell cellE = dataRow.createCell(4); cellE.setCellStyle(textStyle);
            if (i < rightCol.size()) {
                cellD.setCellValue(rightCol.get(i).getKey());
                setPriceValue(cellE, rightCol.get(i).getValue());
            }
        }

        // 合计行
        XSSFRow totalRow = sheet.createRow(r++);
        totalRow.setHeightInPoints(15);
        { XSSFCell ec = totalRow.createCell(0); ec.setCellStyle(emptyStyle); }
        XSSFCell totalLabel = totalRow.createCell(1); totalLabel.setCellValue("合计"); totalLabel.setCellStyle(headerStyle);
        XSSFCell totalVal = totalRow.createCell(2); setPriceValue(totalVal, grandTotal); totalVal.setCellStyle(textStyle);
        XSSFCell totalCN = totalRow.createCell(3); totalCN.setCellValue(amountToChineseUpper(grandTotal)); totalCN.setCellStyle(textStyle);
        // E 列必须创建 cell 并设置边框（D:E 合并后右框线由 E 列承载）
        XSSFCellStyle mergedRightStyle = wb.createCellStyle();
        mergedRightStyle.cloneStyleFrom(textStyle);
        mergedRightStyle.setBorderLeft(BorderStyle.NONE);
        XSSFCell totalE = totalRow.createCell(4); totalE.setCellStyle(mergedRightStyle);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 3, 4));

        // 空白行
        XSSFRow blankRow2 = sheet.createRow(r++);
        blankRow2.setHeightInPoints(15);
        for (int c = 0; c <= 4; c++) {
            XSSFCell ec = blankRow2.createCell(c);
            ec.setCellStyle(footerLeft); // 10pt, left+top
        }

        // 制表人行
        XSSFRow signRow1 = sheet.createRow(r++);
        signRow1.setHeightInPoints(18);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 1, 4));
        XSSFCell signCell1 = signRow1.createCell(1);
        signCell1.setCellValue("制表人：　　　　　　　　　"
                + "核算人：　　　　　　　　甲方核算人：");
        signCell1.setCellStyle(footerLeft);
        for (int c = 0; c <= 4; c++) {
            if (c != 1) { XSSFCell ec = signRow1.createCell(c); ec.setCellStyle(footerLeft); }
        }

        // 公司+日期行
        XSSFRow signRow2 = sheet.createRow(r++);
        signRow2.setHeightInPoints(27.75f);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 1, 4));
        XSSFCell signCell2 = signRow2.createCell(1);
        signCell2.setCellValue(companyName + "\n" + closingDateStr);
        signCell2.setCellStyle(footerRight);
        for (int c = 0; c <= 4; c++) {
            if (c != 1) { XSSFCell ec = signRow2.createCell(c); ec.setCellStyle(footerRight); }
        }

        return wb;
    }

    /** 设置价格单元格：整数时不显示小数位 */
    private void setPriceValue(XSSFCell cell, double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.floor(rounded) && !Double.isInfinite(rounded)) {
            cell.setCellValue((long) rounded);
        } else {
            cell.setCellValue(rounded);
        }
    }

    private String buildDeptSummaryTitle(String planName, HospitalReconciliationJob job) {
        String name = planName != null ? planName : "";
        String period = extractYearMonth(job);
        return name + "各科室" + period + "灭菌价格汇总";
    }

    /**
     * 提取结算年月 int[year, month]，用于计算月末日期。
     * 优先从文件名提取 → sourceDateRange → null。
     */
    private int[] extractYearMonthInts(HospitalReconciliationJob job) {
        String fromFile = extractYearMonthFromFileName(job.getSourceFileName(), job);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})年(\\d{1,2})月").matcher(fromFile);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        String raw = job.getSourceDateRange();
        if (raw != null && !raw.isBlank()) {
            java.util.regex.Matcher dm = java.util.regex.Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-]\\d{1,2}").matcher(raw);
            if (dm.find()) {
                return new int[]{Integer.parseInt(dm.group(1)), Integer.parseInt(dm.group(2))};
            }
        }
        return null;
    }

    /**
     * 提取"2026年4月"格式的年月。
     * 优先从导入文件名中提取，保证导出月份与原始文件一致；
     * 其次从 sourceDateRange 提取；最后回退到任务创建时间。
     */
    private String extractYearMonth(HospitalReconciliationJob job) {
        String fromFile = extractYearMonthFromFileName(job.getSourceFileName(), job);
        if (!fromFile.isEmpty()) {
            return fromFile;
        }
        String raw = job.getSourceDateRange();
        if (raw != null && !raw.isBlank()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-]\\d{1,2}")
                    .matcher(raw);
            if (m.find()) {
                int year = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                return year + "年" + month + "月";
            }
        }
        if (job.getCreatedAt() != null) {
            return job.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy年M月"));
        }
        return "";
    }

    private String extractYearMonthFromFileName(String fileName, HospitalReconciliationJob job) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        // 完整年月格式：2026年5月
        java.util.regex.Matcher full = java.util.regex.Pattern.compile("(\\d{4})年(\\d{1,2})月")
                .matcher(fileName);
        if (full.find()) {
            int year = Integer.parseInt(full.group(1));
            int month = Integer.parseInt(full.group(2));
            return year + "年" + month + "月";
        }
        // 仅有月份格式：5月 → 年份取任务创建时间
        java.util.regex.Matcher monthOnly = java.util.regex.Pattern.compile("(\\d{1,2})月")
                .matcher(fileName);
        if (monthOnly.find()) {
            int month = Integer.parseInt(monthOnly.group(1));
            int year = job.getCreatedAt() != null
                    ? job.getCreatedAt().getYear()
                    : java.time.LocalDate.now().getYear();
            return year + "年" + month + "月";
        }
        return "";
    }

    /**
     * 金额转中文大写（如 435169.5 → "肆拾叁万伍仟壹佰陆拾玖元伍角整"）
     */
    private String amountToChineseUpper(double amount) {
        if (amount < 0) return "负" + amountToChineseUpper(-amount);
        if (amount == 0) return "零元整";

        String[] digits = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
        String[] radices = {"", "拾", "佰", "仟"};
        String[] bigRadices = {"", "万", "亿"};

        long yuan = (long) amount;
        int jiao = (int) Math.round((amount - yuan) * 10);
        int fen = (int) Math.round((amount - yuan - jiao * 0.1) * 100);

        StringBuilder sb = new StringBuilder();

        // 整数部分
        if (yuan == 0) {
            sb.append("零");
        } else {
            String yuanStr = String.valueOf(yuan);
            int len = yuanStr.length();
            boolean needZero = false;
            for (int i = 0; i < len; i++) {
                int digit = yuanStr.charAt(i) - '0';
                int pos = len - i - 1;
                int radixIdx = pos % 4;
                int bigRadixIdx = pos / 4;

                if (digit == 0) {
                    needZero = true;
                } else {
                    if (needZero) {
                        sb.append("零");
                        needZero = false;
                    }
                    sb.append(digits[digit]).append(radices[radixIdx]);
                }
                if (radixIdx == 0 && bigRadixIdx > 0) {
                    // 检查当前4位段是否全为零，不全为零则输出万/亿
                    int segmentStart = Math.max(0, i - 3);
                    boolean segmentAllZero = true;
                    for (int j2 = segmentStart; j2 <= i; j2++) {
                        if (yuanStr.charAt(j2) != '0') { segmentAllZero = false; break; }
                    }
                    if (!segmentAllZero) {
                        sb.append(bigRadices[bigRadixIdx]);
                    }
                    needZero = false;
                }
            }
        }
        sb.append("元");

        // 角分部分
        if (jiao == 0 && fen == 0) {
            sb.append("整");
        } else {
            if (jiao > 0) sb.append(digits[jiao]).append("角");
            if (fen > 0) sb.append(digits[fen]).append("分");
        }
        return sb.toString();
    }

    // ========================================================================
    //  第六节：导出操作 —— HTML 结款函下载
    //  Section 6: Export — HTML Settlement Download
    // ========================================================================
    //
    // 支持以 HTML 页面形式导出结款函，方便用户直接通过浏览器打印或保存。
    // HTML 版本包含完整的样式定义、公司信息、银行账号、免责条款。
    // 内置自动打印脚本（页面加载后自动弹出打印对话框）。
    //
    // ========================================================================

    /**
     * 【导出 HTML 格式结款函（可下载）】
     *
     * POST /api/hospital-reconciliations/export-html-settlement
     *
     * 返回格式良好的 HTML 文档，通过 Content-Disposition 触发浏览器下载。
     * 用户在浏览器中打开 HTML 后，页面自动弹出打印对话框（window.print）。
     * HTML 包含完整的 A4 纸打印样式（@page size 和 margin 设置）。
     *
     * @param request 结款函请求体（与 Excel 导出使用相同的数据结构）
     * @return HTML 文档（可下载的附件）
     */
    public ResponseEntity<String> exportHtmlSettlement(
            HospitalSettlementTemplateExportRequest request) {
        try {
            // 服务端重新计算大写金额，不依赖前端传入的值
            if (request.getTotalAmount() != null) {
                request.setUppercaseTotal(amountToChineseUpper(request.getTotalAmount()));
            }
            // 构建完整的结款函 HTML（匹配 FastAPI _build_settlement_print_html）
            String html = buildSettlementPrintHtml(request);

            // 构造下载文件名：hospitalName_settlement.html
            String filename = asciiDownloadName(
                    safeName(request.getHospitalName() != null ? request.getHospitalName() : "hospital")
                    + "_settlement.html");

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(filename))
                    .body(html);
        } catch (Exception e) {
            log.error("导出 HTML 结款函失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    //  第六节：打印操作 —— HTML 格式（浏览器直接打印）
    //  Section 6: Print — HTML Format (Browser Print)
    // ========================================================================
    //
    // 与"下载 HTML"不同，这些端点直接返回 HTML 内容（不触发下载）。
    // 浏览器接收 HTML 后渲染页面并自动调用 window.print() 弹出打印对话框。
    // 适合用户在"点击打印"按钮时的即时操作。
    //
    // 打印样式专为 A4 纸优化：
    // - @page size: A4 portrait
    // - print-color-adjust: exact（保留背景色）
    // - page-break-after: always（多 sheet 时自动分页）
    //
    // ========================================================================

    /**
     * 【打印账单 HTML】
     *
     * POST /api/hospital-reconciliations/print-template-bill
     *
     * 生成可直接打印的账单 HTML 页面。
     * 浏览器打开后自动弹出打印对话框。
     * 多 sheet 数据通过 CSS page-break 自动分页。
     *
     * @param request 账单打印请求体（与账单导出使用相同数据结构）
     * @return 账单打印 HTML 页面
     */
    public ResponseEntity<String> printTemplateBill(
            HospitalBillTemplateExportRequest request) {
        try {
            // 构建完整的账单打印 HTML
            String html = buildBillPrintHtml(request);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            log.error("生成打印账单 HTML 失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 【打印结款函 HTML】
     *
     * POST /api/hospital-reconciliations/print-template-settlement
     *
     * 生成可直接打印的结款函 HTML 页面。
     * 内容与 export-html-settlement 相同，但 Content-Disposition 不同
     * （不触发下载，直接在浏览器中渲染和打印）。
     *
     * @param request 结款函打印请求体（与结款函导出使用相同数据结构）
     * @return 结款函打印 HTML 页面
     */
    public ResponseEntity<String> printTemplateSettlement(
            HospitalSettlementTemplateExportRequest request) {
        try {
            // 服务端重新计算大写金额，不依赖前端传入的值
            if (request.getTotalAmount() != null) {
                request.setUppercaseTotal(amountToChineseUpper(request.getTotalAmount()));
            }
            // 构建完整的结款函打印 HTML
            String html = buildSettlementPrintHtml(request);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            log.error("生成打印结款函 HTML 失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    //  第七节：响应构建方法
    //  Section 7: Response Builder Methods
    // ========================================================================

    /**
     * 构建核对任务完整响应（含行数据）
     * 快捷重载方法，默认包含行数据
     *
     * @param job 核对任务实体
     * @return 包含完整信息（含行数据）的响应 DTO
     */
    private ReconciliationJobResponse buildJobResponse(HospitalReconciliationJob job) {
        return buildJobResponse(job, true);
    }

    /**
     * 构建核对任务响应（可选是否包含行数据）
     *
     * ===== 构建内容 =====
     * 1. 导出日志列表：从 exportLogRepository 查询该任务的所有导出记录
     * 2. 行数据：从 job.rowsJson JSON 字符串反序列化为 List<Map>
     *    - includeRows=true：反序列化并返回行数据
     *    - includeRows=false：返回空列表（列表查询优化，减小响应体积）
     *
     * ===== 性能考虑 =====
     * 列表查询时（listReconciliations）行数据可能非常大（数千行每行 20+ 字段），
     * 如果全部返回会导致响应体巨大。因此只在线获取单个任务详情时返回行数据，
     * 列表查询仅返回摘要统计信息。
     *
     * @param job         核对任务实体
     * @param includeRows 是否包含行数据（列表查询时为 false 以减小响应体积）
     * @return 任务响应 DTO
     */
    private ReconciliationJobResponse buildJobResponse(HospitalReconciliationJob job, boolean includeRows) {
        // ===== 1. 加载导出日志 =====
        // 列表查询不加载导出日志，减少 N+1 查询
        List<ReconciliationExportLogResponse> exportResponses = Collections.emptyList();

        // ===== 2. 加载行数据和 sheet 统计 =====
        List<Map<String, Object>> rows = Collections.emptyList();
        List<String> sheetNames;
        Map<String, Integer> sheetRowCounts;
        Map<String, Integer> sheetWarningCounts;

        if (includeRows) {
            // 详情查询：需要完整的行数据，从 rowsJson 反序列化
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allRows = JsonUtils.parseToList(job.getRowsJson(), (Class) Map.class);
            if (allRows == null) allRows = Collections.emptyList();
            rows = allRows;

            sheetNames = allRows.stream()
                    .map(r -> (String) r.get("sheetName"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            sheetRowCounts = new HashMap<>();
            sheetWarningCounts = new HashMap<>();
            for (Map<String, Object> row : allRows) {
                String sn = (String) row.get("sheetName");
                if (sn == null) continue;
                sheetRowCounts.merge(sn, 1, Integer::sum);
                if ("warning".equals(row.get("status"))) {
                    sheetWarningCounts.merge(sn, 1, Integer::sum);
                }
            }
        } else {
            // 列表查询：从实体预计算的列读取，避免解析巨大 rowsJson
            sheetNames = parseStringList(job.getSheetNames());
            sheetRowCounts = parseIntMap(JsonUtils.parseToMap(job.getSheetRowCounts()));
            sheetWarningCounts = parseIntMap(JsonUtils.parseToMap(job.getSheetWarningCounts()));
        }

        // ===== 3. 构建完整响应 DTO =====
        ReconciliationJobResponse response = new ReconciliationJobResponse(
                job.getId(),
                job.getHospitalName(),
                job.getSourceFileName(),
                job.getSourceFilePath(),
                job.getSourceFileSize(),
                job.getRuleId(),
                job.getRuleName(),
                job.getRuleVersion(),
                job.getVersionNo(),
                job.getTotalRows(),
                job.getCorrectedRows(),
                job.getUnchangedRows(),
                job.getWarningRows(),
                job.getSkippedRows(),
                job.getTotalDifference(),
                job.getReviewStatus(),
                job.getReviewComment(),
                job.getOperatorName(),
                job.getReviewerName(),
                job.getSourceDateRange(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                exportResponses,
                rows,
                sheetNames,
                sheetRowCounts,
                sheetWarningCounts,
                job.getLogisticsTripCount(),
                job.getLogisticsFee(),
                job.getOriginalTotalPrice(),
                job.getCorrectedTotalPrice());
        response.setPlanName(job.getPlanName());
        response.setLogisticsBreakdown(parseLogisticsBreakdown(job.getLogisticsBreakdown()));
        response.setSettlementAdjustment(job.getSettlementAdjustment());
        response.setMonthlyBreakdown(parseMonthlyBreakdown(job.getMonthlyBreakdown()));
        response.setUrgentBreakdown(parseMonthlyBreakdown(job.getUrgentBreakdown()));
        response.setDeductionBreakdown(parseMonthlyBreakdown(job.getDeductionBreakdown()));
        return response;
    }

    /**
     * 从行数据中预计算 sheet 统计信息，写入实体列。
     * 列表查询时直接读取这些列，避免每次反序列化巨大的 rowsJson。
     */
    /**
     * 一键修正：按规则单价重算修正总价与差额，并将有差异的行标记为 corrected。
     */
    private void applyBatchCorrection(Map<String, Object> rowMap, PricingEngine.ProcessedResult pr) {
        rowMap.put("expectedUnitPrice", pr.expectedUnitPrice);
        rowMap.put("pricingRule", pr.pricingRule);
        rowMap.put("notes", pr.notes);
        rowMap.put("matchedRuleId", pr.matchedRuleId);
        rowMap.put("matchedPriceOption", pr.matchedPriceOption);
        rowMap.put("billingNotes", pr.billingNotes);

        if (pr.expectedUnitPrice == null) {
            rowMap.put("correctedTotalPrice", pr.correctedTotalPrice);
            rowMap.put("difference", pr.difference);
            rowMap.put("status", "skipped");
            return;
        }

        int packCount = Math.max(safeGetInt(rowMap, "packCount", 1), 1);
        double correctedTotal = Math.round(pr.expectedUnitPrice * packCount * 100.0) / 100.0;
        rowMap.put("correctedTotalPrice", correctedTotal);

        Double totalPrice = safeGetDoubleObj(rowMap, "totalPrice");
        double originalTotal = totalPrice != null ? totalPrice : 0.0;
        double difference = Math.round((correctedTotal - originalTotal) * 100.0) / 100.0;
        rowMap.put("difference", difference);
        rowMap.put("status", Math.abs(difference) > 0.001 ? "corrected" : "unchanged");
    }

    private void applySummaryFromRows(HospitalReconciliationJob job, List<Map<String, Object>> rows) {
        int correctedRows = 0;
        int unchangedRows = 0;
        int warningRows = 0;
        int skippedRows = 0;
        double totalDifference = 0.0;
        double originalTotal = 0.0;
        double correctedTotal = 0.0;

        for (Map<String, Object> row : rows) {
            String status = valueToString(row.get("status"), "unchanged");
            switch (status) {
                case "corrected": correctedRows++; break;
                case "unchanged": unchangedRows++; break;
                case "warning": warningRows++; break;
                case "skipped": skippedRows++; break;
            }
            if ("warning".equals(status)) {
                Double diff = safeGetDoubleObj(row, "difference");
                if (diff != null) totalDifference += diff;
            }
            Double tp = safeGetDoubleObj(row, "totalPrice");
            if (tp != null) originalTotal += tp;
            Double ctp = safeGetDoubleObj(row, "correctedTotalPrice");
            if (ctp != null) correctedTotal += ctp;
        }

        job.setTotalRows(rows.size());
        job.setCorrectedRows(correctedRows);
        job.setUnchangedRows(unchangedRows);
        job.setWarningRows(warningRows);
        job.setSkippedRows(skippedRows);
        job.setTotalDifference(Math.round(totalDifference * 100.0) / 100.0);
        job.setOriginalTotalPrice(Math.round(originalTotal * 100.0) / 100.0);
        job.setCorrectedTotalPrice(Math.round(correctedTotal * 100.0) / 100.0);
    }

    private boolean rowsDataEquals(List<Map<String, Object>> existing, List<Map<String, Object>> updated) {
        if (existing.size() != updated.size()) {
            return false;
        }
        Map<String, Map<String, Object>> existingByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : existing) {
            existingByKey.put(rowIdentityKey(row), row);
        }
        for (Map<String, Object> row : updated) {
            Map<String, Object> oldRow = existingByKey.get(rowIdentityKey(row));
            if (oldRow == null || !rowCorrectionEquals(oldRow, row)) {
                return false;
            }
        }
        return true;
    }

    private String rowIdentityKey(Map<String, Object> row) {
        return valueToString(row.get("sheetName"), "") + "#" + safeGetInt(row, "rowNumber", 0);
    }

    private boolean rowCorrectionEquals(Map<String, Object> left, Map<String, Object> right) {
        if (!Objects.equals(valueToString(left.get("status"), ""), valueToString(right.get("status"), ""))) {
            return false;
        }
        if (!doublesEqual(safeGetDoubleObj(left, "correctedTotalPrice"), safeGetDoubleObj(right, "correctedTotalPrice"))) {
            return false;
        }
        if (!doublesEqual(safeGetDoubleObj(left, "difference"), safeGetDoubleObj(right, "difference"))) {
            return false;
        }
        if (!doublesEqual(safeGetDoubleObj(left, "expectedUnitPrice"), safeGetDoubleObj(right, "expectedUnitPrice"))) {
            return false;
        }
        return doublesEqual(safeGetDoubleObj(left, "totalPrice"), safeGetDoubleObj(right, "totalPrice"));
    }

    private boolean doublesEqual(Double left, Double right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        return Math.abs(left - right) < 0.005;
    }

    private int nextVersionNo(String hospitalName, String sourceFileName) {
        String normalizedFile = ReconciliationVersionGroup.normalizeSourceFileName(sourceFileName);
        Integer maxVersion = jobMapper.selectMaxVersionNoByHospitalNameAndSourceFileName(
                hospitalName, normalizedFile);
        return (maxVersion != null ? maxVersion : 0) + 1;
    }

    private HospitalReconciliationJob createNewVersionFromJob(
            HospitalReconciliationJob source,
            List<Map<String, Object>> updatedRows) {
        String hospitalName = source.getHospitalName();
        String sourceFileName = ReconciliationVersionGroup.normalizeSourceFileName(source.getSourceFileName());
        int versionNo = nextVersionNo(hospitalName, sourceFileName);

        HospitalReconciliationJob newJob = new HospitalReconciliationJob();
        newJob.setHospitalName(source.getHospitalName());
        newJob.setSourceFileName(sourceFileName);
        newJob.setSourceFilePath(source.getSourceFilePath());
        newJob.setSourceFileSize(source.getSourceFileSize());
        newJob.setRuleId(source.getRuleId());
        newJob.setRuleName(source.getRuleName());
        newJob.setRuleVersion(source.getRuleVersion());
        newJob.setPlanName(source.getPlanName());
        newJob.setVersionNo(versionNo);
        newJob.setReviewStatus("pending");
        newJob.setReviewComment(null);
        newJob.setReviewerName(null);
        newJob.setOperatorName(source.getOperatorName());
        newJob.setSourceDateRange(source.getSourceDateRange());
        newJob.setLogisticsTripCount(source.getLogisticsTripCount());
        newJob.setLogisticsFee(source.getLogisticsFee());
        newJob.setRowsJson(JsonUtils.toJson(updatedRows));
        applySummaryFromRows(newJob, updatedRows);
        computeSheetStats(newJob, updatedRows);
        if (source.getRuleId() != null) {
            HospitalPricingRule ruleEntity = pricingRuleMapper.selectById(source.getRuleId());
            if (ruleEntity != null) {
                try {
                    JsonNode rulesJson = JsonUtils.getObjectMapper().readTree(ruleEntity.getRulesJson());
                    recomputeJobPriceTotals(newJob, updatedRows);
                    applyLogisticsToJob(newJob, rulesJson, hospitalName, updatedRows);
                } catch (Exception e) {
                    log.warn("结算策略重算失败: {}", e.getMessage());
                }
            }
        }
        jobMapper.insert(newJob);
        saveReconciliationRows(newJob.getId(), updatedRows);
        return newJob;
    }

    private void computeSheetStats(HospitalReconciliationJob job, List<Map<String, Object>> rows) {
        List<String> names = new ArrayList<>();
        Map<String, Integer> rowCounts = new LinkedHashMap<>();
        Map<String, Integer> warningCounts = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String sn = (String) row.get("sheetName");
            if (sn == null) continue;
            if (!rowCounts.containsKey(sn)) names.add(sn);
            rowCounts.merge(sn, 1, Integer::sum);
            if ("warning".equals(row.get("status"))) {
                warningCounts.merge(sn, 1, Integer::sum);
            }
        }
        // 按原始顺序排序
        Collections.sort(names);

        job.setSheetNames(JsonUtils.toJson(names));
        job.setSheetRowCounts(JsonUtils.toJson(rowCounts));
        job.setSheetWarningCounts(JsonUtils.toJson(warningCounts));
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return JsonUtils.getObjectMapper().readValue(json,
                    JsonUtils.getObjectMapper().getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Integer> parseIntMap(Map<String, Object> raw) {
        if (raw == null) return Collections.emptyMap();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getValue() instanceof Number) {
                result.put(e.getKey(), ((Number) e.getValue()).intValue());
            }
        }
        return result;
    }

    // ========================================================================
    //  第八节：账单 Excel 生成（模板操作 —— Apache POI 核心逻辑）
    //  Section 8: Bill Excel Generation (Template Manipulation with POI)
    // ========================================================================
    //
    // 本节是系统中最复杂的 Excel 操作逻辑。核心挑战在于：
    // 基于预制的 xlsx 模板（包含样式、合并单元格、布局），动态写入数据行，
    // 并根据数据行数插入或删除行，保持模板的格式和样式完整。
    //
    // ===== 模板文件结构（1-indexed 行号） =====
    //   行 1-10: 标题/信息区域
    //     C1: 报表标题（如"发货单汇总表-显示包装材料"）
    //     B4: 日期范围（如"2024年1月1日 至 2024年1月31日"）
    //     D8: 医院计费规则名称
    //     D10: 工作表名称（科室名称）
    //     I10: 总包数汇总
    //     K10: 总金额汇总
    //   行 11: 数据起始行
    //   行 19: 模板预设的最后模板数据行
    //   列 (row 9 表头):
    //     D(4) - 发货日期  |  E(5) - 发货单号  |  F(6) - 类型
    //     G(7) - 包类别号  |  H(8) - 包名      |  I(9) - 包装材料
    //     J(10) - 包数     |  K(11) - 器械数   |  L(12) - 单价
    //     M(13) - 总价     |  N(14) - 差额
    //
    // ===== 关键技术操作 =====
    // - insertRows / deleteRows: 在模板中插入或删除行，保持样式
    // - unmergeCellRange / addMergedRegionSafe: 管理单元格合并
    // - cloneRowStyle: 从模板行复制格式到新插入的行
    // - setCellValue: 根据列字母引用（如 "C1"）或行列号设置值
    //
    // ========================================================================

    /**
     * 生成账单导出 Excel 字节数组（模板优先，降级简单导出）
     *
     * ===== 决策逻辑 =====
     * 1. 检查配置的 billTemplatePath 是否指向一个有效的 xlsx 文件
     * 2. 有效 → 加载模板 → createBillTemplateWorkbook() → 输出字节流
     * 3. 无效 → log.warn 降级 → generateSimpleExcel() 生成基础表格
     *
     * @param request 账单导出请求
     * @return Excel 文件字节数组
     * @throws IOException 文件读取或工作簿写入异常
     */
    @Override
    public byte[] generateBillExportBytes(HospitalBillTemplateExportRequest request) throws IOException {
        // ===== 自动加载：如前端未传 rows/metas，则从数据库按 jobId 查询 =====
        if (request.getTemplateId() != null) {
            Long jobId = Long.parseLong(request.getTemplateId());
            if (request.getRows() == null || request.getRows().isEmpty()) {
                request.setRows(loadBillRowsFromDb(jobId));
            }
            if (request.getSheetMetas() == null || request.getSheetMetas().isEmpty()) {
                request.setSheetMetas(buildSheetMetasFromJob(jobId, request.getHospitalName()));
            }
        }
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new IllegalArgumentException("该版本没有可导出的数据");
        }
        File templateFile = new File(billTemplatePath);

        // ===== 优先方式1：标准模板导出 =====
        if (request.getTemplateId() != null) {
            try {
                XSSFWorkbook workbook;
                if (templateFile.exists() && templateFile.isFile()) {
                    workbook = new XSSFWorkbook(new FileInputStream(templateFile));
                } else {
                    log.info("物理模板文件不存在，使用程序化生成的标准模板");
                    workbook = createProgrammaticBillTemplate();
                    copyLogoFromOriginalFile(request.getTemplateId(), workbook);
                }
                // 判断多工作表还是单表：有多于一个不同的 sheetName 时保留多表结构
                long distinctSheets = request.getRows() != null
                        ? request.getRows().stream()
                                .filter(r -> !"skipped".equals(r.getStatus()))
                                .map(BillRowItem::getSheetName)
                                .filter(n -> n != null && !n.isBlank())
                                .distinct()
                                .count()
                        : 1;
                if (request.getSheetMetas() != null && request.getSheetMetas().size() > 1) {
                    distinctSheets = Math.max(distinctSheets, request.getSheetMetas().size());
                }
                long exportRowCount = request.getRows() != null
                        ? request.getRows().stream().filter(r -> !"skipped".equals(r.getStatus())).count()
                        : 0;
                ExportLayoutSettings layoutSettings = resolveExportLayoutSettings(request);
                boolean deptSplit = billExportLayoutResolver.useDeptSplitWorkbook(
                        layoutSettings.billLayout(), distinctSheets, exportRowCount);
                if (billExportLayoutResolver.preferProgrammaticTemplate(layoutSettings.billLayout(), exportRowCount)) {
                    log.info("generateBillExportBytes: {} rows → programmatic template master (dept_split OOM guard)",
                            exportRowCount);
                    workbook.close();
                    workbook = createProgrammaticBillTemplate();
                    copyLogoFromOriginalFile(request.getTemplateId(), workbook);
                }
                if (deptSplit) {
                    log.info("generateBillExportBytes: {} rows / {} sheets → dept_split workbook",
                            exportRowCount, distinctSheets);
                    createBillTemplateWorkbook(workbook, request);
                } else {
                    log.info("generateBillExportBytes: {} rows / {} sheets → combined workbook",
                            exportRowCount, distinctSheets);
                    createCombinedBillWorkbook(workbook, request);
                }
                byte[] result = writeWorkbookToBytes(workbook);
                workbook.close();
                return result;
            } catch (Exception e) {
                log.warn("标准模板导出失败，尝试降级到原始文件: {}", e.getMessage());
            }
        }
        // ===== 降级方式1：使用上传的原始文件作为模板 =====
        if (request.getTemplateId() != null) {
            try {
                return generateBillFromOriginalFile(request);
            } catch (Exception e) {
                log.warn("使用原始文件导出失败: {}", e.getMessage());
            }
        }
        // ===== 降级方式2：生成简单 Excel =====
        log.warn("无可用的导出方式，使用简单导出");
        return generateSimpleExcel("账单", buildSimpleBillHeaderAndRows(request));
    }

    /**
     * 从零创建标准账单模板 xlsx（不依赖任何物理模板文件）。
     *
     * 布局（与用户期望的导出账单格式一致）：
     *   Row 1: C1 合并 — 标题"发货单汇总表-显示包装材料"
     *   Row 4: B4 合并 — 日期范围
     *   Row 7: B7-K7 空（带边框占位）
     *   Row 8: D8 合并 — 医院计费规则名称
     *   Row 9: D9-K9 — 表头（发货日期/发货单号/类型/包类别号/包名/包数/单价/总价）
     *   Row 10: D10 合并 — 科室名，I10 包数汇总，K10 总价汇总
     *   Row 11+: 数据行（含边框样式供克隆）
     */
    private XSSFWorkbook createProgrammaticBillTemplate() {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("结算单");
        sheet.setDisplayGridlines(false);

        // 列布局:
        // A(0)=窄分隔 B(1)=日期标签 C(2)=窄分隔
        // D(3)=发货日期 E(4)=发货单号 F(5)=类型
        // G(6)=包类别号 H(7)=包名 I(8)=包数 J(9)=单价 K(10)=总价
        sheet.setColumnWidth(0, (int) (2.0 * 256));
        sheet.setColumnWidth(1, (int) (13.0 * 256));
        sheet.setColumnWidth(2, (int) (2.0 * 256));
        sheet.setColumnWidth(3, (int) (15.5 * 256));
        sheet.setColumnWidth(4, (int) (15.5 * 256));
        sheet.setColumnWidth(5, (int) (12.0 * 256));   // F列 — 类型
        sheet.setColumnWidth(6, (int) (18.0 * 256));
        sheet.setColumnWidth(7, (int) (30.0 * 256));
        sheet.setColumnWidth(8, (int) (10.0 * 256));   // I列 — 包数
        sheet.setColumnWidth(9, (int) (12.0 * 256));   // J列 — 单价
        sheet.setColumnWidth(10, (int) (12.0 * 256));  // K列 — 总价

        // -- 字体 --
        XSSFFont font10 = wb.createFont();
        font10.setFontName("宋体");
        font10.setFontHeightInPoints((short) 10);

        XSSFFont font10b = wb.createFont();
        font10b.setFontName("宋体");
        font10b.setFontHeightInPoints((short) 10);
        font10b.setBold(true);

        XSSFFont font18 = wb.createFont();
        font18.setFontName("宋体");
        font18.setFontHeightInPoints((short) 18);

        // -- 样式 --
        XSSFCellStyle titleStyle = wb.createCellStyle();
        titleStyle.setFont(font18);
        titleStyle.setAlignment(HorizontalAlignment.LEFT);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        XSSFCellStyle dateStyle = wb.createCellStyle();
        dateStyle.setFont(font10);
        dateStyle.setAlignment(HorizontalAlignment.LEFT);
        dateStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // D8: 蓝灰底色（#6799AF）+ 白色字体
        XSSFFont font12w = wb.createFont();
        font12w.setFontName("宋体");
        font12w.setFontHeightInPoints((short) 12);
        font12w.setColor(IndexedColors.WHITE.getIndex());

        XSSFCellStyle d8Style = wb.createCellStyle();
        d8Style.setFont(font12w);
        d8Style.setAlignment(HorizontalAlignment.LEFT);
        d8Style.setVerticalAlignment(VerticalAlignment.TOP);
        d8Style.setWrapText(true);
        d8Style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x67, (byte) 0x99, (byte) 0xAF}, null));
        d8Style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        XSSFCellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(font10);
        headerStyle.setAlignment(HorizontalAlignment.LEFT);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        XSSFCellStyle summaryLabelStyle = wb.createCellStyle();
        summaryLabelStyle.setFont(font10);
        summaryLabelStyle.setAlignment(HorizontalAlignment.LEFT);
        summaryLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        summaryLabelStyle.setBorderTop(BorderStyle.THIN);
        summaryLabelStyle.setBorderBottom(BorderStyle.THIN);
        summaryLabelStyle.setBorderLeft(BorderStyle.THIN);
        summaryLabelStyle.setBorderRight(BorderStyle.THIN);

        XSSFCellStyle summaryValStyle = wb.createCellStyle();
        summaryValStyle.setFont(font10b);
        summaryValStyle.setAlignment(HorizontalAlignment.LEFT);
        summaryValStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        summaryValStyle.setBorderTop(BorderStyle.THIN);
        summaryValStyle.setBorderBottom(BorderStyle.THIN);
        summaryValStyle.setBorderLeft(BorderStyle.THIN);
        summaryValStyle.setBorderRight(BorderStyle.THIN);

        // 数据格 10pt 带细边框（D-K列）
        XSSFCellStyle dataStyle = wb.createCellStyle();
        dataStyle.setFont(font10);
        dataStyle.setAlignment(HorizontalAlignment.LEFT);
        dataStyle.setVerticalAlignment(VerticalAlignment.TOP);
        dataStyle.setWrapText(true);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);

        XSSFCellStyle dataStyleLeft = wb.createCellStyle();
        dataStyleLeft.cloneStyleFrom(dataStyle);
        dataStyleLeft.setAlignment(HorizontalAlignment.LEFT);

        // 空列占位样式（无边框）
        XSSFCellStyle emptyStyle = wb.createCellStyle();
        emptyStyle.setFont(font10);

        // 空占位列（I列）专用：带边框但无内容
        XSSFCellStyle emptyBorderedStyle = wb.createCellStyle();
        emptyBorderedStyle.setFont(font10);
        emptyBorderedStyle.setBorderTop(BorderStyle.THIN);
        emptyBorderedStyle.setBorderBottom(BorderStyle.THIN);
        emptyBorderedStyle.setBorderLeft(BorderStyle.THIN);
        emptyBorderedStyle.setBorderRight(BorderStyle.THIN);

        final int MAX_COL = 10; // A-K (0-10)

        int r = 0;

        // Row 0: 标题 C1，合并 C1:K2
        XSSFRow row1 = sheet.createRow(r++);
        row1.setHeightInPoints(28);
        sheet.addMergedRegion(new CellRangeAddress(0, 1, 2, MAX_COL));
        XSSFCell titleCell = row1.createCell(2);
        titleCell.setCellValue("发货单汇总表-显示包装材料");
        titleCell.setCellStyle(titleStyle);
        for (int c = 0; c <= MAX_COL; c++) {
            if (c != 2) { XSSFCell ec = row1.createCell(c); ec.setCellStyle(emptyStyle); }
        }

        // Row 1: 合并区域内
        XSSFRow row2 = sheet.createRow(r++);
        row2.setHeightInPoints(23);
        for (int c = 0; c <= MAX_COL; c++) { XSSFCell ec = row2.createCell(c); ec.setCellStyle(emptyStyle); }

        // Row 2: 空白
        XSSFRow row3 = sheet.createRow(r++);
        row3.setHeightInPoints(2);
        for (int c = 0; c <= MAX_COL; c++) { XSSFCell ec = row3.createCell(c); ec.setCellStyle(emptyStyle); }

        // Row 3: B4 日期，合并 B4:K5
        XSSFRow row4 = sheet.createRow(r++);
        row4.setHeightInPoints(15);
        sheet.addMergedRegion(new CellRangeAddress(3, 4, 1, MAX_COL));
        XSSFCell dateCell4 = row4.createCell(1);
        dateCell4.setCellValue("日期占位");
        dateCell4.setCellStyle(dateStyle);
        for (int c = 0; c <= MAX_COL; c++) {
            if (c != 1) { XSSFCell ec = row4.createCell(c); ec.setCellStyle(emptyStyle); }
        }

        // Row 4: 合并区域内
        XSSFRow row5 = sheet.createRow(r++);
        row5.setHeightInPoints(2);
        for (int c = 0; c <= MAX_COL; c++) { XSSFCell ec = row5.createCell(c); ec.setCellStyle(emptyStyle); }

        // Row 5: 空白
        XSSFRow row6 = sheet.createRow(r++);
        row6.setHeightInPoints(3);
        for (int c = 0; c <= MAX_COL; c++) { XSSFCell ec = row6.createCell(c); ec.setCellStyle(emptyStyle); }

        // Row 6: B7-K7 边框占位
        XSSFRow row7 = sheet.createRow(r++);
        row7.setHeightInPoints(4);
        { XSSFCell ec = row7.createCell(0); ec.setCellStyle(emptyStyle); }
        for (int c = 1; c <= MAX_COL; c++) {
            XSSFCell bc = row7.createCell(c);
            bc.setCellValue("");
            bc.setCellStyle(headerStyle);
        }

        // Row 7: D8 计费规则，合并 D8:K8
        XSSFRow row8 = sheet.createRow(r++);
        row8.setHeightInPoints(16);
        sheet.addMergedRegion(new CellRangeAddress(7, 7, 3, MAX_COL));
        XSSFCell d8Cell = row8.createCell(3);
        d8Cell.setCellValue("计费规则占位");
        d8Cell.setCellStyle(d8Style);
        for (int c = 0; c <= MAX_COL; c++) {
            if (c != 3) { XSSFCell ec = row8.createCell(c); ec.setCellStyle(emptyStyle); }
        }

        // Row 8: 表头 D9-K9
        XSSFRow row9 = sheet.createRow(r++);
        row9.setHeightInPoints(18);
        for (int c = 0; c < 3; c++) { XSSFCell ec = row9.createCell(c); ec.setCellStyle(emptyStyle); }
        String[] headers = {"发货日期", "发货单号", "类型", "包类别号", "包名", "包数", "单价", "总价"};
        for (int c = 0; c < headers.length; c++) {
            XSSFCell hc = row9.createCell(c + 3);
            hc.setCellValue(headers[c]);
            hc.setCellStyle(headerStyle);
        }

        // Row 9: D10 科室名（合并 D10:H10），I10 包数汇总，J10 空，K10 总价汇总
        XSSFRow row10 = sheet.createRow(r++);
        row10.setHeightInPoints(18);
        sheet.addMergedRegion(new CellRangeAddress(9, 9, 3, 7)); // D10:H10
        for (int c = 0; c < 3; c++) { XSSFCell ec = row10.createCell(c); ec.setCellStyle(emptyStyle); }
        XSSFCell d10Cell = row10.createCell(3);
        d10Cell.setCellValue("科室占位");
        d10Cell.setCellStyle(summaryLabelStyle);
        for (int c = 4; c <= 7; c++) { XSSFCell ec = row10.createCell(c); ec.setCellStyle(summaryLabelStyle); }
        { XSSFCell ec = row10.createCell(8); ec.setCellStyle(summaryValStyle); }     // I10 包数汇总
        { XSSFCell ec = row10.createCell(9); ec.setCellStyle(emptyStyle); }           // J10
        { XSSFCell ec = row10.createCell(10); ec.setCellStyle(summaryValStyle); }     // K10 总价汇总

        // Row 10 (1-indexed Row 11): 模板数据行（供 cloneRowStyle 克隆样式）
        XSSFRow dataRow = sheet.createRow(r++);
        dataRow.setHeightInPoints(18);
        for (int c = 0; c <= MAX_COL; c++) {
            XSSFCell dc = dataRow.createCell(c);
            if (c == 3) {
                dc.setCellStyle(dataStyleLeft);
            } else if (c >= 4 && c <= MAX_COL) {
                dc.setCellStyle(dataStyle);
            } else {
                dc.setCellStyle(emptyStyle);
            }
        }

        return wb;
    }

    /** 账单导出后处理：超过此行数时跳过 autoSizeColumn，防止大行数 OOM */
    static final int BILL_EXPORT_AUTO_SIZE_ROW_THRESHOLD = 2000;
    /** 超过此行数时仅写 D8 + K 列宽，跳过 autoSize / 行样式（D4 省医院香坊 ~3000 行） */
    static final int BILL_EXPORT_LIGHT_POST_PROCESS_ROW_THRESHOLD = 2500;
    /** 超过此行数且多 sheet 时走 combined 单表导出，避免 cloneSheet OOM（D6 哈工大 ~1152 行） */
    public static final int BILL_EXPORT_COMBINED_MODE_ROW_THRESHOLD = 1000;

    static boolean shouldAutoSizeBillExportColumns(int rowCount) {
        return rowCount <= BILL_EXPORT_AUTO_SIZE_ROW_THRESHOLD;
    }

    static boolean shouldApplyBillExportRowDecorations(int rowCount) {
        return rowCount <= BILL_EXPORT_LIGHT_POST_PROCESS_ROW_THRESHOLD;
    }

    static boolean shouldCloneRowStylesForExport(int rowCount) {
        return rowCount <= BILL_EXPORT_LIGHT_POST_PROCESS_ROW_THRESHOLD;
    }

    /**
     * 后处理：在最终输出的 byte[] 上直接修正 D8 和列宽。
     *
     * 不依赖内部导出流程，直接操作最终字节，确保：
     * 1. D8 优先使用原始导入文件第9行 D 列的值，若为空则回退到方案名称
     * 2. K 列（总价）收窄宽度
     */
    @Override
    public byte[] postProcessBillExport(byte[] content, String templateId) {
        if (templateId == null || templateId.isBlank()) return content;
        try {
            Long jobId = Long.parseLong(templateId);
            HospitalReconciliationJob job = jobMapper.selectById(jobId);
            if (job == null) {
                log.warn("postProcessBillExport: job not found, jobId={}", jobId);
                return content;
            }

            String displayName = resolveD8DisplayName(job, resolveExportLayoutSettingsForJob(jobId).d8DisplaySource());
            log.info("postProcessBillExport: resolved displayName='{}' for jobId={}", displayName, jobId);

            if (displayName.isBlank()) {
                log.warn("postProcessBillExport: displayName is blank, skip D8 override");
                return content;
            }

            try (ByteArrayInputStream bis = new ByteArrayInputStream(content);
                 XSSFWorkbook workbook = new XSSFWorkbook(bis)) {

                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    XSSFSheet sheet = workbook.getSheetAt(i);

                    // 1. 强制覆写 D8（先删后建，解决模板 inlineStr 类型导致的设值失效）
                    Row row8 = sheet.getRow(7);
                    if (row8 == null) row8 = sheet.createRow(7);
                    // 删除旧 D8 单元格，避免 inlineStr 类型残留
                    Cell oldD8 = row8.getCell(3);
                    CellStyle d8Style = oldD8 != null ? oldD8.getCellStyle() : null;
                    if (oldD8 != null) row8.removeCell(oldD8);
                    Cell cellD8 = row8.createCell(3);
                    if (d8Style != null) cellD8.setCellStyle(d8Style);
                    cellD8.setCellValue(displayName);
                    log.info("postProcessBillExport: sheet[{}] D8 set to '{}'", workbook.getSheetName(i), displayName);

                    // 2. 查找列索引：总价(K)
                    int headerRowIdx = findHeaderRowIndexInSheet(sheet, true);
                    if (headerRowIdx < 0) headerRowIdx = 8; // 默认 row 9 (0-indexed)
                    Row headerRow = sheet.getRow(headerRowIdx);
                    if (headerRow != null) {
                        Map<String, Integer> colMap = new LinkedHashMap<>();
                        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                            Cell cell = headerRow.getCell(c);
                            if (cell != null) {
                                String val = getCellStringValue(cell).trim();
                                if (!val.isEmpty()) colMap.put(val, c);
                            }
                        }

                        // K 列（总价）收窄宽度
                        Integer totalPriceCol = colMap.get("总价");
                        if (totalPriceCol != null && totalPriceCol >= 0) {
                            sheet.setColumnWidth(totalPriceCol, 3072); // ~12字符
                        }
                    }

                    // 3. 自动撑开列宽（A/B/C 隐藏作为左侧留白，数据从 D 列开始）
                    // 隐藏 A、B、C 列，使表格视觉上从 D 列开始
                    sheet.setColumnHidden(0, true); // A列
                    sheet.setColumnHidden(1, true); // B列
                    sheet.setColumnHidden(2, true); // C列
                    int rowCount = sheet.getLastRowNum() + 1;
                    if (shouldAutoSizeBillExportColumns(rowCount)) {
                        int lastCol = 0;
                        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                            Row row = sheet.getRow(r);
                            if (row != null && row.getLastCellNum() > lastCol) {
                                lastCol = row.getLastCellNum();
                            }
                        }
                        for (int c = 3; c < lastCol; c++) {
                            try {
                                sheet.autoSizeColumn(c);
                                int w = sheet.getColumnWidth(c);
                                sheet.setColumnWidth(c, Math.min(w + 1024, 65280));
                            } catch (Exception ignored) {
                            }
                        }
                    } else {
                        // 大行数账单跳过 autoSizeColumn，避免 POI 全表扫描导致 OOM（如市五院 ~5005 行）
                        log.info("postProcessBillExport: sheet[{}] skip autoSizeColumn ({} rows > threshold {})",
                                workbook.getSheetName(i), rowCount, BILL_EXPORT_AUTO_SIZE_ROW_THRESHOLD);
                    }

                    // 4. 第10行（首行数据）灰色底色（仅 D-K 列，不含 L 列及之后）
                    if (shouldApplyBillExportRowDecorations(rowCount)) {
                        Row row10 = sheet.getRow(9); // 0-indexed: row 10 = index 9
                        if (row10 != null) {
                            for (int c = 3; c <= 10; c++) { // D(3) ~ K(10)
                                Cell cell = row10.getCell(c);
                                if (cell != null) {
                                    CellStyle cs = workbook.createCellStyle();
                                    cs.cloneStyleFrom(cell.getCellStyle());
                                    cs.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                                    cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                    cell.setCellStyle(cs);
                                }
                            }
                        }

                        // 5. 仅第10行 I 列和 K 列加粗
                        if (row10 != null) {
                            Font boldFont = workbook.createFont();
                            boldFont.setBold(true);
                            // I 列 (index 8)
                            Cell cellI10 = row10.getCell(8);
                            if (cellI10 != null) {
                                CellStyle boldStyle = workbook.createCellStyle();
                                boldStyle.cloneStyleFrom(cellI10.getCellStyle());
                                boldStyle.setFont(boldFont);
                                cellI10.setCellStyle(boldStyle);
                            }
                            // K 列 (index 10)
                            Cell cellK10 = row10.getCell(10);
                            if (cellK10 != null) {
                                CellStyle boldStyle = workbook.createCellStyle();
                                boldStyle.cloneStyleFrom(cellK10.getCellStyle());
                                boldStyle.setFont(boldFont);
                                cellK10.setCellStyle(boldStyle);
                            }
                        }
                    } else {
                        log.info("postProcessBillExport: sheet[{}] skip row decorations ({} rows > threshold {})",
                                workbook.getSheetName(i), rowCount, BILL_EXPORT_LIGHT_POST_PROCESS_ROW_THRESHOLD);
                    }
                }

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                workbook.write(bos);
                log.info("postProcessBillExport: D8='{}', {} sheets processed", displayName, workbook.getNumberOfSheets());
                return bos.toByteArray();
            }
        } catch (Exception e) {
            log.warn("postProcessBillExport 失败: {}", e.getMessage(), e);
            return content; // 失败时返回原始内容，不阻断导出
        }
    }

    /**
     * 使用原始上传文件作为模板导出账单（保留原始格式、logo、行数列数完全一致）
     *
     * 从 HospitalReconciliationJob 中取得上传的原始 Excel 文件路径，
     * 直接修改其中的数据单元格（包数、单价、总价），其余一切不变。
     *
     * @param request 账单导出请求（需含 templateId = 任务 ID）
     * @return 修改后的 Excel 字节数组
     */
    private byte[] generateBillFromOriginalFile(HospitalBillTemplateExportRequest request) throws IOException {
        Long jobId = Long.parseLong(request.getTemplateId());
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("核对任务不存在: " + jobId);
        }

        String sourceFilePath = job.getSourceFilePath();
        if (sourceFilePath == null || sourceFilePath.isEmpty()) {
            throw new IllegalArgumentException("原始文件路径为空，任务: " + jobId);
        }

        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("原始文件不存在: " + sourceFilePath);
        }

        try (FileInputStream fis = new FileInputStream(sourceFile);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            // 按 sheet_name 分组
            Map<String, List<BillRowItem>> groupedRows = groupRowsBySheet(request.getRows());

            // 构建 sheet 元数据映射（标题、日期范围、医院显示名等）
            Map<String, BillSheetMeta> metaMap = new HashMap<>();
            if (request.getSheetMetas() != null) {
                for (BillSheetMeta meta : request.getSheetMetas()) {
                    if (meta.getSheetName() != null) {
                        metaMap.put(meta.getSheetName(), meta);
                    }
                }
            }
            // D8 使用计费规则名称
            resolveD8HospitalText(request.getTemplateId(), metaMap);

            // 处理每个 sheet
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                XSSFSheet sheet = workbook.getSheetAt(i);
                String sheetName = workbook.getSheetName(i);

                List<BillRowItem> sheetRows = groupedRows.get(sheetName);
                if (sheetRows == null || sheetRows.isEmpty()) continue;

                // 查找表头行
                int headerRowIdx = findHeaderRowIndexInSheet(sheet);
                if (headerRowIdx < 0) continue;

                // 构建列名 → 列索引映射（0-indexed）
                Row headerRow = sheet.getRow(headerRowIdx);
                Map<String, Integer> colMap = buildColumnIndexMap(headerRow);

                Integer packCountCol = colMap.get("包数");
                Integer unitPriceCol = colMap.get("单价");
                Integer totalPriceCol = colMap.get("总价");
                Integer packageMaterialCol = colMap.get("包装材料");
                Integer instrumentCountCol = colMap.get("器械数");

                // 覆写表头行为标准列名（对应修正后的导入账单）
                String[][] headerAliases = {
                    {"发货日期", "灭菌日期"},
                    {"发货单号", "灭菌锅次"},
                    {"包类别号", "病人ID"},
                    {"包名", "器械名称"},
                    {"包装材料"},
                    {"包数"},
                    {"单价"},
                    {"总价"},
                };
                for (String[] aliases : headerAliases) {
                    Integer colIdx = null;
                    for (String alias : aliases) {
                        colIdx = colMap.get(alias);
                        if (colIdx != null) break;
                    }
                    if (colIdx != null) {
                        Cell hc = headerRow.getCell(colIdx);
                        if (hc == null) hc = headerRow.createCell(colIdx);
                        hc.setCellValue(aliases[0]);
                    }
                }
                colMap = buildColumnIndexMap(headerRow);
                packCountCol = colMap.get("包数");
                unitPriceCol = colMap.get("单价");
                totalPriceCol = colMap.get("总价");
                packageMaterialCol = colMap.get("包装材料");
                instrumentCountCol = colMap.get("器械数");

                // 更新 B4 日期范围 / D8 计费规则名称
                BillSheetMeta meta = metaMap.get(sheetName);
                if (meta != null) {
                    String drText = cleanExcelText(meta.getDateRangeText());
                    if (drText != null && !drText.isBlank()) {
                        setCellValue(sheet, "B4", drText);
                    }
                    String displayName = cleanExcelText(meta.getHospitalDisplayName());
                    if (displayName != null && !displayName.isBlank()) {
                        // 先删后建，处理可能的 inlineStr 单元格类型
                        Row row8 = sheet.getRow(7);
                        if (row8 == null) row8 = sheet.createRow(7);
                        Cell oldD8 = row8.getCell(3);
                        CellStyle d8Style = oldD8 != null ? oldD8.getCellStyle() : null;
                        if (oldD8 != null) row8.removeCell(oldD8);
                        Cell cellD8 = row8.createCell(3);
                        if (d8Style != null) cellD8.setCellStyle(d8Style);
                        cellD8.setCellValue(displayName);
                    }
                }

                // 按关键字段构建查找索引
                Map<String, BillRowItem> lookup = new LinkedHashMap<>();
                for (BillRowItem r : sheetRows) {
                    lookup.put(buildRowMatchKey(r), r);
                }

                // 汇总统计
                int totalPackCount = 0;
                int totalInstrumentCount = 0;
                double totalAmount = 0.0;
                int lastDataRowIdx = headerRowIdx;

                // 遍历数据行，匹配并更新
                // 跳过 headerRowIdx+1（原第8行汇总）和 headerRowIdx+2（原第9行，保留为空）
                int summaryRow1Idx = headerRowIdx + 1; // row 8 (1-indexed) — 清除
                int summaryRow2Idx = headerRowIdx + 2; // row 9 (1-indexed) — 清除包数/单价/总价

                for (int r = summaryRow2Idx + 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    String key = buildRowMatchKeyFromRow(row, colMap);
                    if (key == null) continue;

                    BillRowItem matched = lookup.get(key);
                    if (matched == null) continue;

                    lastDataRowIdx = r;
                    // 汇总
                    if (matched.getPackCount() != null) totalPackCount += matched.getPackCount();
                    if (matched.getInstrumentCount() != null) totalInstrumentCount += matched.getInstrumentCount();
                    if (matched.getCorrectedTotalPrice() != null) {
                        totalAmount += matched.getCorrectedTotalPrice();
                    } else if (matched.getTotalPrice() != null) {
                        totalAmount += matched.getTotalPrice();
                    }

                    // 更新 包数列
                    if (matched.getPackCount() != null && packCountCol != null) {
                        setCellValue(sheet, r + 1, packCountCol + 1, matched.getPackCount());
                    }
                    // 更新 单价列（优先期望单价）
                    if (unitPriceCol != null) {
                        Double price = matched.getExpectedUnitPrice() != null
                                ? matched.getExpectedUnitPrice() : matched.getUnitPrice();
                        if (price != null) {
                            setCellValue(sheet, r + 1, unitPriceCol + 1, price);
                        }
                    }
                    // 更新 总价列（优先校正后总价）
                    if (totalPriceCol != null) {
                        Double total = matched.getCorrectedTotalPrice() != null
                                ? matched.getCorrectedTotalPrice() : matched.getTotalPrice();
                        if (total != null) {
                            setCellValue(sheet, r + 1, totalPriceCol + 1, total);
                        }
                    }
                }

                // 清除原第8行（汇总行）全部数据
                clearRowCells(sheet, summaryRow1Idx);

                // 清除原第9行的包数/单价/总价
                if (packCountCol != null) setCellValue(sheet, summaryRow2Idx + 1, packCountCol + 1, null);
                if (unitPriceCol != null) setCellValue(sheet, summaryRow2Idx + 1, unitPriceCol + 1, null);
                if (totalPriceCol != null) setCellValue(sheet, summaryRow2Idx + 1, totalPriceCol + 1, null);

                // 在数据末尾下方写入汇总行（仅包数和总价）
                int summaryRow = lastDataRowIdx + 2; // 1-indexed
                if (packCountCol != null) {
                    setCellValue(sheet, summaryRow, packCountCol + 1, totalPackCount);
                }
                if (totalPriceCol != null) {
                    setCellValue(sheet, summaryRow, totalPriceCol + 1, totalAmount);
                }
                // 给汇总行加粗
                Row sumRow = sheet.getRow(summaryRow - 1);
                if (sumRow == null) sumRow = sheet.createRow(summaryRow - 1);
                setRowBold(sumRow);

                // 补全总价列边框（横线+竖线），修复原文件边框缺失
                if (totalPriceCol != null) {
                    CellStyle borderStyle = workbook.createCellStyle();
                    borderStyle.setBorderLeft(BorderStyle.THIN);
                    borderStyle.setBorderRight(BorderStyle.THIN);
                    borderStyle.setBorderTop(BorderStyle.THIN);
                    borderStyle.setBorderBottom(BorderStyle.THIN);
                    for (int r = headerRowIdx; r <= summaryRow - 1; r++) {
                        Row rowObj = sheet.getRow(r);
                        if (rowObj == null) rowObj = sheet.createRow(r);
                        Cell cell = rowObj.getCell(totalPriceCol);
                        if (cell == null) cell = rowObj.createCell(totalPriceCol);
                        if (cell.getCellStyle() == null || cell.getCellStyle().getBorderLeft() == BorderStyle.NONE) {
                            cell.setCellStyle(borderStyle);
                        }
                    }
                }

                // 隐藏导出不需要的列：仅器械数，包装材料列保持可见
                if (instrumentCountCol != null && instrumentCountCol >= 0) {
                    sheet.setColumnHidden(instrumentCountCol, true);
                }
                // 总价列（K列）适当收窄
                if (totalPriceCol != null && totalPriceCol >= 0) {
                    sheet.setColumnWidth(totalPriceCol, 3072);
                }

                // 清空总价列之后的所有列（内容 + 边框）
                if (totalPriceCol != null) {
                    int sheetMaxCol = 0;
                    for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row != null) sheetMaxCol = Math.max(sheetMaxCol, row.getLastCellNum() - 1);
                    }
                    CellStyle noBorderStyle = workbook.createCellStyle();
                    noBorderStyle.setBorderTop(BorderStyle.NONE);
                    noBorderStyle.setBorderBottom(BorderStyle.NONE);
                    noBorderStyle.setBorderLeft(BorderStyle.NONE);
                    noBorderStyle.setBorderRight(BorderStyle.NONE);
                    for (int r = 1; r <= sheet.getLastRowNum() + 1; r++) {
                        for (int c = totalPriceCol + 1; c <= sheetMaxCol; c++) {
                            setCellValue(sheet, r, c + 1, null);
                            Row rowObj = sheet.getRow(r - 1);
                            if (rowObj != null) {
                                Cell cell = rowObj.getCell(c);
                                if (cell != null) cell.setCellStyle(noBorderStyle);
                            }
                        }
                    }
                }
            }

            return writeWorkbookToBytes(workbook);
        }
    }

    /**
     * 合并模式：将所有科室数据写入模板的单个 sheet，生成一份统一的账单。
     *
     * 与 {@link #createBillTemplateWorkbook} 不同，此方法不按科室拆分 sheet，
     * 而是将全部非 skipped 行合并写入标准模板的第一页，汇总行显示"合计"而非科室名。
     *
     * @param workbook 从标准模板文件加载的 XSSFWorkbook
     * @param request  账单导出请求（含所有行数据）
     */
    private void createCombinedBillWorkbook(XSSFWorkbook workbook, HospitalBillTemplateExportRequest request) {
        XSSFSheet templateSheet = workbook.getSheetAt(0);

        // 收集所有非 skipped 行（保留 DB 排序：sheetName → rowNumber）
        List<BillRowItem> allRows = request.getRows() != null
                ? request.getRows().stream()
                        .filter(r -> !"skipped".equals(r.getStatus()))
                        .collect(Collectors.toList())
                : new ArrayList<>();

        if (allRows.isEmpty()) return;

        // 构建合并模式的 meta（日期范围、医院名称取第一条即可，所有科室一致）
        Map<String, BillSheetMeta> combinedMetaMap = new HashMap<>();
        BillSheetMeta combinedMeta = new BillSheetMeta();
        // 取实际导入的 sheet 名：若所有行属于同一 sheet 则用该名，否则用"合计"
        String resolvedSheetName = allRows.stream()
                .map(BillRowItem::getSheetName)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .count() == 1
                ? allRows.stream().map(BillRowItem::getSheetName).filter(n -> n != null && !n.isBlank()).findFirst().orElse("合计")
                : "合计";
        combinedMeta.setSheetName(resolvedSheetName);
        combinedMeta.setTitleText("消毒供应中心结算表-显示包装材料");
        if (request.getSheetMetas() != null && !request.getSheetMetas().isEmpty()) {
            BillSheetMeta firstMeta = request.getSheetMetas().get(0);
            combinedMeta.setDateRangeText(firstMeta.getDateRangeText());
            combinedMeta.setHospitalDisplayName(firstMeta.getHospitalDisplayName());
        }
        combinedMetaMap.put(resolvedSheetName, combinedMeta);

        // 日期恢复：从原始上传文件读取 B4 日期文本
        if (request.getTemplateId() != null) {
            try {
                recoverDateRangeFromOriginalFile(request.getTemplateId(), combinedMetaMap);
            } catch (Exception e) {
                log.warn("从原始文件恢复日期范围失败: {}", e.getMessage());
            }
            // D8 解析独立调用，不受日期恢复失败影响
            resolveD8HospitalText(request.getTemplateId(), combinedMetaMap);
        }

        // 写入全部数据到模板第一页（合并模式）
        writeSheetFromTemplate(workbook, templateSheet, resolvedSheetName, allRows, combinedMetaMap, true);

        // 附加 logo 图片
        attachTemplateLogo(workbook, templateSheet);

        // 重命名 sheet 为实际 sheet 名
        try {
            workbook.setSheetName(0, safeSheetName(resolvedSheetName));
        } catch (Exception e) {
            log.warn("重命名 sheet 失败: {}", e.getMessage());
        }
    }

    /**
     * 从数据库加载账单行数据（按 jobId），用于导出时避免前端传输大量 JSON。
     */
    private List<BillRowItem> loadBillRowsFromDb(Long jobId) {
        List<HospitalReconciliationRow> entities = rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
        return entities.stream().map(r -> {
            BillRowItem item = new BillRowItem();
            item.setSheetName(r.getSheetName());
            item.setRowNumber(r.getRowNumber());
            item.setDeliveryDate(r.getDeliveryDate());
            item.setOrderNo(r.getOrderNo());
            item.setType(r.getType());
            item.setCategoryNo(r.getCategoryNo());
            item.setPackName(r.getPackName());
            item.setPackageMaterial(r.getPackageMaterial());
            item.setPackCount(r.getPackCount());
            item.setInstrumentCount(r.getInstrumentCount());
            item.setUnitPrice(r.getUnitPrice());
            item.setTotalPrice(r.getTotalPrice());
            item.setExpectedUnitPrice(r.getExpectedUnitPrice());
            item.setCorrectedTotalPrice(r.getCorrectedTotalPrice());
            Double exportUnit = BillExportPriceResolver.resolveUnitPrice(r);
            Double exportTotal = BillExportPriceResolver.resolveTotalPrice(r);
            if (exportUnit != null) {
                item.setExpectedUnitPrice(exportUnit);
                item.setUnitPrice(exportUnit);
            }
            if (exportTotal != null) {
                item.setCorrectedTotalPrice(exportTotal);
                item.setTotalPrice(exportTotal);
            }
            item.setDifference(r.getDifference());
            item.setStatus(r.getStatus());
            item.setPricingRule(r.getPricingRule());
            // 解析备注 JSON 数组
            if (r.getNotesJson() != null && !r.getNotesJson().isBlank()) {
                try {
                    item.setNotes(JsonUtils.getObjectMapper().readValue(r.getNotesJson(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
                } catch (Exception e) {
                    item.setNotes(Collections.emptyList());
                }
            }
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 从数据库构建 sheet 元数据（标题、日期范围等），用于导出。
     */
    private List<BillSheetMeta> buildSheetMetasFromJob(Long jobId, String hospitalName) {
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        List<String> sheetNames = parseStringList(job != null ? job.getSheetNames() : null);
        if (sheetNames.isEmpty()) {
            // 降级：从行表查询去重的 sheetName
            sheetNames = rowMapper.selectByJobIdOrderBySheetNameAscRowNumberAsc(jobId).stream()
                    .map(HospitalReconciliationRow::getSheetName)
                    .distinct().toList();
        }
        String dateRangeText = job != null ? job.getSourceDateRange() : "";
        final String finalDateRange = dateRangeText != null ? dateRangeText : "";
        // 优先用 job 的方案名称/规则名称，回退到参数传入的 hospitalName
        String displayName = hospitalName;
        if (job != null) {
            if (job.getPlanName() != null && !job.getPlanName().isBlank()) {
                displayName = job.getPlanName();
            } else if (job.getRuleName() != null && !job.getRuleName().isBlank()) {
                displayName = job.getRuleName();
            } else if (job.getHospitalName() != null && !job.getHospitalName().isBlank()) {
                displayName = job.getHospitalName();
            }
        }
        final String finalDisplayName = displayName;
        return sheetNames.stream().map(sn -> {
            BillSheetMeta meta = new BillSheetMeta();
            meta.setSheetName(sn);
            meta.setTitleText("发货单汇总表");
            meta.setDateRangeText(finalDateRange);
            meta.setHospitalDisplayName(finalDisplayName);
            return meta;
        }).collect(Collectors.toList());
    }

    /**
     * 在 POI Sheet 中查找表头行（包含"发货日期""包名""包装材料""器械数""单价""总价"的行）
     *
     * @return 0-indexed 行号，-1 表示未找到
     */
    private int findHeaderRowIndexInSheet(XSSFSheet sheet) {
        return findHeaderRowIndexInSheet(sheet, false);
    }

    /**
     * 在 sheet 中查找表头行（宽松模式可选）
     *
     * @param sheet   POI sheet 对象
     * @param relaxed true=仅匹配"发货日期"和"包名"（兼容无包装材料/器械数的模板）
     * @return 表头行的 0-indexed 行号，找不到返回 -1
     */
    private int findHeaderRowIndexInSheet(XSSFSheet sheet, boolean relaxed) {
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Set<String> cellTexts = new HashSet<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null) {
                    String val = getCellStringValue(cell).trim();
                    if (!val.isEmpty()) cellTexts.add(val);
                }
            }
            if (relaxed) {
                // 宽松模式：只需匹配"发货日期"和"包名"两个核心列
                if (cellTexts.contains("发货日期") && cellTexts.contains("包名")) {
                    return r;
                }
            } else {
                // 严格模式：需要匹配旧模板所有业务列
                if (cellTexts.contains("发货日期") && cellTexts.contains("包名")
                        && cellTexts.contains("包装材料") && cellTexts.contains("器械数")
                        && cellTexts.contains("单价") && cellTexts.contains("总价")) {
                    return r;
                }
            }
        }
        return -1;
    }

    /**
     * 按别名查找列索引，支持同一逻辑列在不同模板中使用不同表头名称。
     * 例如 deliveryDate 列在标准模板中叫"灭菌日期"，在旧模板中叫"发货日期"。
     */
    private Integer findColumnAlias(Map<String, Integer> colMap, String... candidateNames) {
        for (String name : candidateNames) {
            Integer col = colMap.get(name);
            if (col != null) return col;
        }
        return null;
    }

    /**
     * 从表头行构建列名 → 0-indexed 列号的映射
     */
    private Map<String, Integer> buildColumnIndexMap(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell != null) {
                String val = getCellStringValue(cell).trim();
                if (!val.isEmpty()) {
                    map.put(val, c);
                }
            }
        }
        return map;
    }

    /**
     * 为 BillRowItem 构建匹配 key（发货单号 + 类型 + 包名 + 包装材料）
     */
    private String buildRowMatchKey(BillRowItem item) {
        return joinFields(item.getOrderNo(), item.getType(), item.getPackName(), item.getPackageMaterial());
    }

    /**
     * 从 POI Row 构建匹配 key（与 buildRowMatchKey 对应）
     */
    private String buildRowMatchKeyFromRow(Row row, Map<String, Integer> colMap) {
        String orderNo = getCellStringAtCol(row, colMap, "发货单号");
        String type = getCellStringAtCol(row, colMap, "类型");
        String packName = getCellStringAtCol(row, colMap, "包名");
        String packageMaterial = getCellStringAtCol(row, colMap, "包装材料");
        if (orderNo.isEmpty() && type.isEmpty() && packName.isEmpty()) return null;
        return joinFields(orderNo, type, packName, packageMaterial);
    }

    private String joinFields(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String f : fields) {
            sb.append('|').append(f != null ? f.trim() : "");
        }
        return sb.toString();
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String getCellStringAtCol(Row row, Map<String, Integer> colMap, String colName) {
        Integer colIdx = colMap.get(colName);
        if (colIdx == null) return "";
        Cell cell = row.getCell(colIdx);
        return getCellStringValue(cell);
    }

    /**
     * 仅在模板存在指定列时写入值（避免向不存在的列写入数据破坏模板结构）
     */
    private void writeIfColExists(XSSFSheet sheet, int rowOneIdx, Map<String, Integer> colMap,
                                  String colName, Object value) {
        Integer colIdx = colMap.get(colName);
        if (colIdx != null && value != null) {
            setCellValue(sheet, rowOneIdx, colIdx + 1, value);
        }
    }

    /**
     * 创建账单模板工作簿（匹配 FastAPI _create_bill_template_workbook）
     *
     * ===== 模板操作完整步骤 =====
     *
     * 第一步：按 sheet_name 分组行数据
     *   - 每一组对应一个科室/部门，将生成一个独立的 sheet
     *   - 例如：{"手术室": [...], "供应室": [...], "消毒中心": [...]}
     *
     * 第二步：处理第一个 sheet（模板 sheet）
     *   - writeSheetFromTemplate()：写入数据、调整行合并、克隆样式
     *   - attachTemplateLogo()：从模板提取 logo 图片并粘贴到 sheet
     *
     * 第三步：复制模板 sheet 到后续分组
     *   - workbook.cloneSheet(0)：深拷贝第一个 sheet 的完整结构
     *   - 重命名 sheet：解决名称唯一性
     *   - 对每个克隆 sheet 执行相同的 writeSheetFromTemplate() + logo 操作
     *
     * ===== 设计思考 =====
     * 使用模板复制而不是创建全新 sheet，是为了保留模板中的：
     * - 预设的行高、列宽、字体、边框、颜色等样式
     * - 布局中的标题区域格式、公司 Logo 位置等
     * - 打印设置、页面边距等布局配置
     *
     * @param workbook 从模板文件加载的 XSSFWorkbook 对象（可写）
     * @param request  账单导出请求（含 rows, sheetMetas 等）
     */
    private void createBillTemplateWorkbook(XSSFWorkbook workbook, HospitalBillTemplateExportRequest request) {
        // 获取模板的第一个 sheet 作为基准模板
        XSSFSheet templateSheet = workbook.getSheetAt(0);

        // 按 sheet_name 字段将行数据分组（如按科室分组）
        Map<String, List<BillRowItem>> groupedRows = groupRowsBySheet(request.getRows());
        List<String> orderedSheetNames = new ArrayList<>(groupedRows.keySet());

        if (orderedSheetNames.isEmpty()) return;

        // 构建 sheet 元数据映射（标题、日期范围、医院显示名等）
        // key = sheetName，value = BillSheetMeta（含 titleText, dateRangeText 等）
        Map<String, BillSheetMeta> metaMap = new HashMap<>();
        if (request.getSheetMetas() != null) {
            for (BillSheetMeta meta : request.getSheetMetas()) {
                if (meta.getSheetName() != null) {
                    metaMap.put(meta.getSheetName(), meta);
                }
            }
        }

        // 日期恢复：始终从原始上传文件读取 B4 日期文本（这是日期的最权威来源）
        if (request.getTemplateId() != null) {
            try {
                recoverDateRangeFromOriginalFile(request.getTemplateId(), metaMap);
            } catch (Exception e) {
                log.warn("从原始文件恢复日期范围失败: {}", e.getMessage());
            }
            // D8 解析独立调用，不受日期恢复失败影响
            resolveD8HospitalText(request.getTemplateId(), metaMap);
        }

        // ===== 克隆一份干净的母版，用于后续 sheet 克隆 =====
        // writeSheetFromTemplate 会修改 sheet 0（取消合并/覆写表头等），
        // 如果后续 sheet 从已修改的 sheet 0 克隆，可能出现不一致。
        // 因此在处理第一个 sheet 前先克隆母版，后续统一从母版克隆。
        XSSFSheet masterTemplate = workbook.cloneSheet(0);
        int masterIdx = workbook.getSheetIndex(masterTemplate);

        // ===== 处理第一个 sheet =====
        String firstSheetName = orderedSheetNames.get(0);
        writeSheetFromTemplate(workbook, templateSheet, firstSheetName,
                groupedRows.get(firstSheetName), metaMap, false);
        workbook.setSheetName(0,
                resolveUniqueSheetTitle(workbook, firstSheetName, templateSheet.getSheetName()));
        attachTemplateLogo(workbook, templateSheet);

        // ===== 复制并处理后续 sheet（从母版克隆，而非已移位的 sheet 0） =====
        for (int i = 1; i < orderedSheetNames.size(); i++) {
            String sheetName = orderedSheetNames.get(i);
            XSSFSheet clonedSheet = workbook.cloneSheet(masterIdx);
            int clonedIdx = workbook.getSheetIndex(clonedSheet);
            workbook.setSheetName(clonedIdx,
                    resolveUniqueSheetTitle(workbook, sheetName, clonedSheet.getSheetName()));
            writeSheetFromTemplate(workbook, clonedSheet, sheetName,
                    groupedRows.get(sheetName), metaMap, false);
            attachTemplateLogo(workbook, clonedSheet);
        }

        // 移除母版 sheet
        workbook.removeSheetAt(masterIdx);
    }

    /**
     * 将行数据写入模板 sheet（匹配 FastAPI _write_sheet_from_template）
     *
     * 这是账单模板操作中最核心的方法，负责：
     * 1. 动态调整行数：根据数据行数在模板中插入或删除行
     * 2. 更新信息区域：标题、日期范围、医院名称、汇总数值
     * 3. 写入明细数据：遍历每行，写入日期、单号、类型、包信息、金额等
     * 4. 管理单元格合并：取消旧合并 → 写入数据 → 重新合并
     * 5. 设置自动筛选：方便 Excel 中对数据进行过滤
     *
     * ===== 模板行号参考（1-indexed） =====
     * templateLastRow = 19：模板预设的最后数据行
     * dataStartRow = 11：数据从第 11 行开始写入
     *
     * ===== 行列增量计算 =====
     * delta = dataEndRow - templateLastRow
     * delta > 0：数据比模板预设行多，需要在模板最后行下方插入行
     * delta < 0：数据比模板预设行少，需要删除多余的空行
     *
     * @param workbook  工作簿（用于从其他 sheet 克隆样式）
     * @param sheet     目标 sheet（第一个 sheet 或克隆后的 sheet）
     * @param sheetName 当前 sheet 的科室名称
     * @param sheetRows 该科室的行数据列表
     * @param metaMap   sheet 元数据映射（标题、日期范围等）
     */
    private void writeSheetFromTemplate(XSSFWorkbook workbook, XSSFSheet sheet,
                                        String sheetName, List<BillRowItem> sheetRows,
                                        Map<String, BillSheetMeta> metaMap,
                                        boolean combinedMode) {
        // ===== 第一步：过滤掉被标记为"skipped"的行 =====
        // skipped 行是对账引擎无法匹配规则的行，不参与导出
        List<BillRowItem> exportRows = sheetRows != null
                ? sheetRows.stream().filter(r -> !"skipped".equals(r.getStatus())).collect(Collectors.toList())
                : new ArrayList<>();

        // ===== 第二步：计算行数差 =====
        // 从模板动态检测最后数据行，而非硬编码
        int dataStartRow = 11;      // 数据起始行（1-indexed，模板第11行是第一条数据）
        int templateLastRow = Math.max(dataStartRow, sheet.getLastRowNum() + 1); // 模板实际最后行
        int dataEndRow = 10 + Math.max(exportRows.size(), 1); // 数据行结束位置（1-indexed）
        int delta = dataEndRow - templateLastRow;
        // delta > 0 表示需要插入行；delta < 0 表示需要删除行

        // ===== 第三步：取消所有合并单元格 =====
        // 先取消所有合并，待数据写入完毕后再重新合并
        // 这是为了在插入/删除行时合并区域不会出现偏移或错位
        // 同时避免新模板中存在旧模板未知的合并区域导致后续重新合并时冲突
        while (sheet.getNumMergedRegions() > 0) {
            sheet.removeMergedRegion(0);
        }

        // ===== 第四步：动态查找表头行（需在插入/删除行之前，供 cloneRowStyle 回退用） =====
        int headerRowIdx = findHeaderRowIndexInSheet(sheet, true);
        if (headerRowIdx < 0) {
            log.warn("Sheet[{}] 未找到表头行，默认 row 9", sheetName);
            headerRowIdx = 8; // 0-indexed，对应 1-indexed 的 row 9
        }
        int headerRowOneIdx = headerRowIdx + 1; // 转为 1-indexed
        int summaryRowIdx = headerRowOneIdx + 1; // 汇总行在表头行下一行

        // ===== 第六步：插入或删除行 =====
        if (delta > 0 && shouldCloneRowStylesForExport(exportRows.size())) {
            // 数据行数多于模板预设 → 在模板最后行下方插入 (delta) 行
            insertRows(sheet, templateLastRow + 1, delta);
            // 为新插入的行克隆模板最后行的样式（字体、边框、背景色等）
            // headerRowOneIdx 作为回退：当源行单元格为空时使用表头行对应列的样式
            for (int rowIdx = templateLastRow + 1; rowIdx <= dataEndRow; rowIdx++) {
                cloneRowStyle(sheet, templateLastRow, rowIdx, headerRowOneIdx);
            }
        } else if (delta > 0) {
            insertRows(sheet, templateLastRow + 1, delta);
            log.info("writeSheetFromTemplate: sheet[{}] skip cloneRowStyle ({} rows > threshold {})",
                    sheetName, exportRows.size(), BILL_EXPORT_LIGHT_POST_PROCESS_ROW_THRESHOLD);
        } else if (delta < 0) {
            // 数据行数少于模板预设 → 保留模板空行（含边框），仅清除旧数据，不删除行
        }
        Map<String, Integer> templateColMap = new LinkedHashMap<>();
        Row templateHeaderRow = sheet.getRow(headerRowIdx);
        if (templateHeaderRow != null) {
            for (int c = 0; c < templateHeaderRow.getLastCellNum(); c++) {
                Cell cell = templateHeaderRow.getCell(c);
                if (cell != null) {
                    String val = getCellStringValue(cell).trim();
                    if (!val.isEmpty()) templateColMap.put(val, c);
                }
            }
        }
        // ===== 第七步：覆写列表头为标准列名（对应修正后的导入账单） =====
        {
            String[][] headerAliases = {
                {"发货日期", "灭菌日期"},
                {"发货单号", "灭菌锅次"},
                {"类型"},
                {"包类别号", "病人ID"},
                {"包名", "器械名称"},
                {"包数"},
                {"单价"},
                {"总价"},
            };
            for (String[] aliases : headerAliases) {
                Integer colIdx = null;
                for (String alias : aliases) {
                    colIdx = templateColMap.get(alias);
                    if (colIdx != null) break;
                }
                if (colIdx != null) {
                    setCellValue(sheet, headerRowOneIdx, colIdx + 1, cleanExcelText(aliases[0]));
                }
            }
        }

        // ===== 第八步：写入标题/信息区域 =====
        setCellValue(sheet, "C1", "发货单汇总表-显示包装材料");
        BillSheetMeta meta = metaMap != null ? metaMap.get(sheetName) : null;
        if (meta != null) {
            // B4: 日期（仅在有有效文本时覆盖模板原始值）
            String drText = cleanExcelText(meta.getDateRangeText());
            if (drText != null && !drText.isBlank()) {
                setCellValue(sheet, "B4", drText);
            }
            // D8: 计费规则名称
            String displayName = cleanExcelText(meta.getHospitalDisplayName());
            if (displayName != null && !displayName.isBlank()) {
                // 先删后建，处理模板 inlineStr 单元格类型
                Row row8 = sheet.getRow(7);
                if (row8 == null) row8 = sheet.createRow(7);
                Cell oldD8 = row8.getCell(3);
                CellStyle d8Style = oldD8 != null ? oldD8.getCellStyle() : null;
                if (oldD8 != null) row8.removeCell(oldD8);
                Cell cellD8 = row8.createCell(3);
                if (d8Style != null) cellD8.setCellStyle(d8Style);
                cellD8.setCellValue(displayName);
            }
        }
        // 汇总行标签（合并模式为"合计"，否则为科室名称如"手术室"）
        setCellValue(sheet, "D" + summaryRowIdx, sheetName);

        // ===== 第九步：解析列映射（使用别名兼容不同模板表头命名） =====
        Integer dateCol = findColumnAlias(templateColMap, "灭菌日期", "发货日期");
        Integer orderNoCol = findColumnAlias(templateColMap, "灭菌锅次", "发货单号");
        Integer typeCol = findColumnAlias(templateColMap, "类型");
        Integer catNoCol = findColumnAlias(templateColMap, "病人ID", "包类别号");
        Integer packNameCol = findColumnAlias(templateColMap, "器械名称", "包名");
        Integer packCountCol = findColumnAlias(templateColMap, "包数");
        Integer instCountCol = findColumnAlias(templateColMap, "器械数");
        Integer unitPriceCol = findColumnAlias(templateColMap, "单价");
        Integer totalPriceCol = findColumnAlias(templateColMap, "总价");
        Integer diffCol = findColumnAlias(templateColMap, "差额");

        // ===== 第十步：写入汇总行（仅更新模板中存在的汇总列） =====
        int totalPackCount = exportRows.stream()
                .filter(r -> r.getPackCount() != null)
                .mapToInt(BillRowItem::getPackCount)
                .sum();
        int totalInstrumentCount = exportRows.stream()
                .filter(r -> r.getInstrumentCount() != null)
                .mapToInt(BillRowItem::getInstrumentCount)
                .sum();
        double totalAmount = exportRows.stream()
                .mapToDouble(r -> {
                    Double resolved = BillExportPriceResolver.resolveTotalPrice(r);
                    return resolved != null ? resolved : 0.0;
                })
                .sum();
        // 包数列汇总（如模板有该列）
        if (packCountCol != null) {
            setCellValue(sheet, summaryRowIdx, packCountCol + 1, totalPackCount);
        }
        // 器械数列汇总（如模板有该列）
        if (instCountCol != null) {
            setCellValue(sheet, summaryRowIdx, instCountCol + 1, totalInstrumentCount);
        }
        // 总价列汇总（如模板有该列）
        if (totalPriceCol != null) {
            setCellValue(sheet, summaryRowIdx, totalPriceCol + 1, totalAmount);
        }

        // 汇总行加粗
        Row summaryRow = sheet.getRow(summaryRowIdx - 1);
        if (summaryRow != null) {
            setRowBold(summaryRow);
        }

        // ===== 第十一步：逐行写入明细数据 =====

        for (int i = 0; i < exportRows.size(); i++) {
            BillRowItem row = exportRows.get(i);
            int r = dataStartRow + i;

            if (dateCol != null && row.getDeliveryDate() != null) {
                setCellValue(sheet, r, dateCol + 1, cleanExcelText(row.getDeliveryDate().replace("-", "/")));
            }
            if (orderNoCol != null) {
                setCellValue(sheet, r, orderNoCol + 1, cleanExcelText(formatIntegerString(row.getOrderNo())));
            }
            if (typeCol != null) {
                setCellValue(sheet, r, typeCol + 1, cleanExcelText(row.getType()));
            }
            if (catNoCol != null) {
                setCellValue(sheet, r, catNoCol + 1, cleanExcelText(formatIntegerString(row.getCategoryNo())));
            }
            if (packNameCol != null) {
                setCellValue(sheet, r, packNameCol + 1, cleanExcelText(row.getPackName()));
            }
            if (packCountCol != null) {
                setCellValue(sheet, r, packCountCol + 1, row.getPackCount());
            }
            if (instCountCol != null) {
                setCellValue(sheet, r, instCountCol + 1, row.getInstrumentCount());
            }
            if (unitPriceCol != null) {
                Double price = BillExportPriceResolver.resolveUnitPrice(row);
                if (price != null) setCellValue(sheet, r, unitPriceCol + 1, price);
            }
            if (totalPriceCol != null) {
                Double total = BillExportPriceResolver.resolveTotalPrice(row);
                if (total != null) setCellValue(sheet, r, totalPriceCol + 1, total);
            }
            if (diffCol != null) {
                setCellValue(sheet, r, diffCol + 1, row.getDifference());
            }
        }

        // ===== 第十二步：清空多余行的旧数据（内容 + 边框） =====
        // 防止之前模板行残留数据显示在导出文件中
        int clearStart = dataStartRow + exportRows.size();
        int clearEnd;
        if (delta < 0) {
            // 数据行数少于模板 → 清空多余模板行（含边框），使其变为空白
            clearEnd = templateLastRow;
        } else {
            clearEnd = Math.max(clearStart,
                    Math.min(dataEndRow + Math.max(1, 10 - exportRows.size()), sheet.getLastRowNum() + 1));
            // 清除范围不超出已有样式的行（模板末行或插入后 dataEndRow），
            // 避免 setCellValue 自动创建无边框的新行
            int maxStyledRow = Math.max(templateLastRow, dataEndRow);
            clearEnd = Math.min(clearEnd, maxStyledRow);
        }
        // 为清除行创建无边框样式
        CellStyle clearNoBorderStyle = null;
        if (clearStart <= clearEnd) {
            clearNoBorderStyle = workbook.createCellStyle();
            clearNoBorderStyle.setBorderTop(BorderStyle.NONE);
            clearNoBorderStyle.setBorderBottom(BorderStyle.NONE);
            clearNoBorderStyle.setBorderLeft(BorderStyle.NONE);
            clearNoBorderStyle.setBorderRight(BorderStyle.NONE);
        }
        for (int r = clearStart; r <= clearEnd; r++) {
            int rowZero = r - 1;
            Row rowObj = sheet.getRow(rowZero);
            for (int c = 3; c < 11; c++) {  // 列 D(4) 到 K(11)，0-indexed 为 3-10
                setCellValue(sheet, r, c + 1, null);
                if (rowObj != null) {
                    Cell cell = rowObj.getCell(c);
                    if (cell != null) cell.setCellStyle(clearNoBorderStyle);
                }
            }
        }

        // ===== 第十三步：清空总价列之后的所有列（内容 + 边框） =====
        if (totalPriceCol != null) {
            int sheetMaxCol = 0;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null) sheetMaxCol = Math.max(sheetMaxCol, row.getLastCellNum() - 1);
            }
            // 创建无边框样式（整个工作簿共享一份，避免超过 64000 样式上限）
            CellStyle noBorderStyle = workbook.createCellStyle();
            noBorderStyle.setBorderTop(BorderStyle.NONE);
            noBorderStyle.setBorderBottom(BorderStyle.NONE);
            noBorderStyle.setBorderLeft(BorderStyle.NONE);
            noBorderStyle.setBorderRight(BorderStyle.NONE);
            // 清除所有行（含表头区域）在总价列之后的内容和边框
            for (int r = 1; r <= sheet.getLastRowNum() + 1; r++) {
                for (int c = totalPriceCol + 1; c <= sheetMaxCol; c++) {
                    setCellValue(sheet, r, c + 1, null);
                    Row rowObj = sheet.getRow(r - 1);
                    if (rowObj != null) {
                        Cell cell = rowObj.getCell(c);
                        if (cell != null) cell.setCellStyle(noBorderStyle);
                    }
                }
            }
        }

        // ===== 第十四步：合并模式下隐藏器械数列，包装材料列保持可见 =====
        if (combinedMode) {
            if (instCountCol != null) sheet.setColumnHidden(instCountCol, true);
        }
        // 总价列（K列）适当收窄
        if (totalPriceCol != null) {
            sheet.setColumnWidth(totalPriceCol, 3072); // ~12个字符宽度
        }

        // ===== 第十四点五步：统一边框 — 仅有数据的行才加边框，空白行不加 =====
        // 模板中部分行可能缺少竖线，且 setCellValue 自动创建的行没有样式
        applyUniformBorders(workbook, sheet, summaryRowIdx, dataEndRow, 0, 10); // 列 A-K

        // ===== 第十五步：重新合并单元格 =====
        // 数据写入完毕，恢复合并区域（标题区域固定、数据区域动态）
        // 合并区域全部结束于 K 列（总价列），L 列及之后保持空白
        addMergedRegionSafe(sheet, "C1:K2");                // 标题跨列合并
        addMergedRegionSafe(sheet, "B4:K5");                // 日期范围合并
        addMergedRegionSafe(sheet, "D8:K8");                // 医院名称跨列合并
        addMergedRegionSafe(sheet, "A8:B" + dataEndRow);    // 左侧标签区纵向合并（动态行数）
        addMergedRegionSafe(sheet, "C" + headerRowOneIdx + ":C" + dataEndRow);    // 类别区纵向合并（动态行数）
        // 汇总标签合并：D 到 包数前一列（包数、总价需保持独立可见）
        int labelEndCol = packCountCol != null ? packCountCol - 1 : 7;
        addMergedRegionSafe(sheet, "D" + summaryRowIdx + ":" + CellReference.convertNumToColString(labelEndCol) + summaryRowIdx);

        // ===== 第十六步：设置自动筛选 =====
        // 对列标题行和数据行设置 AutoFilter
        try {
            sheet.setAutoFilter(CellRangeAddress.valueOf("A" + headerRowOneIdx + ":K" + dataEndRow));
        } catch (Exception e) {
            log.warn("设置自动筛选失败: {}", e.getMessage());
        }
    }

    // ========================================================================
    //  第九节：结款函 Excel 生成（模板操作）
    //  Section 9: Settlement Excel Generation (Template Manipulation)
    // ========================================================================
    //
    // 结款函是正式财务文件，模板结构比账单更简洁但格式要求更严格。
    // 固定布局（1-indexed）：
    //   D6: 标题（如"结款通知函"）
    //   D7: 收件人标签（"致："）
    //   D8: 医院名称（自动换行，固定高度 30pt）
    //   D9: 结算期间（如"2024年1月1日 至 2024年1月31日"）
    //   行 12+: 费用明细表格（序号 D, 条目 E, 费用 F, 备注 H）
    //   最后数据行+1: 合计（D列"合  计", F列合计金额）
    //   合计+1: 大写合计行
    //
    // ========================================================================

    /**
     * 生成结款函导出 Excel 字节数组
     *
     * 与账单导出相同的策略：优先使用模板，模板不存在则降级。
     *
     * @param request 结款函导出请求（含 feeRows, totalAmount, uppercaseTotal 等）
     * @return Excel 文件字节数组
     * @throws IOException 文件读写异常
     */
    @Override
    public byte[] generateSettlementExportBytes(HospitalSettlementTemplateExportRequest request) throws IOException {
        if (request.getTotalAmount() != null
                && (request.getUppercaseTotal() == null || request.getUppercaseTotal().isBlank())) {
            request.setUppercaseTotal(amountToChineseUpper(request.getTotalAmount()));
        }
        // 从任务中提取方案名称和结算月份（优先取文件名中的月份）
        String planName = null;
        int year = 0, month = 0;
        if (request.getTemplateId() != null && !request.getTemplateId().isBlank()) {
            try {
                Long jobId = Long.parseLong(request.getTemplateId());
                HospitalReconciliationJob job = jobMapper.selectById(jobId);
                if (job != null) {
                    // 方案名称：优先 planName，回退 ruleName → hospitalName
                    planName = job.getPlanName();
                    if (planName == null || planName.isBlank()) {
                        planName = job.getRuleName();
                    }
                    if (planName == null || planName.isBlank()) {
                        planName = job.getHospitalName();
                    }
                    log.info("结款函导出: jobId={}, planName={}, ruleName={}, hospitalName={}, sourceFileName={}",
                            jobId, planName, job.getRuleName(), job.getHospitalName(), job.getSourceFileName());

                    // 从文件名提取年月（如"2026年4月"、"2026-4月"、"202604月"等）
                    String srcFile = job.getSourceFileName();
                    log.info("结款函导出: sourceFileName='{}'", srcFile);
                    if (srcFile != null && !srcFile.isBlank()) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                                "(\\d{4})[年\\-\\\\/]?(\\d{1,2})\\s*月").matcher(srcFile);
                        if (m.find()) {
                            year = Integer.parseInt(m.group(1));
                            month = Integer.parseInt(m.group(2));
                            log.info("结款函导出: 从文件名提取 → year={}, month={}", year, month);
                        } else {
                            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
                                    "(\\d{1,2})\\s*月").matcher(srcFile);
                            if (m2.find()) {
                                month = Integer.parseInt(m2.group(1));
                                year = java.time.Year.now().getValue();
                                log.info("结款函导出: 从文件名(仅月份)提取 → year={}, month={}", year, month);
                            }
                        }
                    }
                    // 文件名未提取到 → 回退到 sourceDateRange（B4 单元格内容）
                    if (year == 0 || month == 0) {
                        String dateRange = job.getSourceDateRange();
                        log.info("结款函导出: sourceDateRange='{}'", dateRange);
                        if (dateRange != null && !dateRange.isBlank()) {
                            java.util.regex.Matcher m3 = java.util.regex.Pattern.compile(
                                    "(\\d{4})[/\\-](\\d{1,2})[/\\-]\\d{1,2}").matcher(dateRange);
                            if (m3.find()) {
                                year = Integer.parseInt(m3.group(1));
                                month = Integer.parseInt(m3.group(2));
                                log.info("结款函导出: 从sourceDateRange提取 → year={}, month={}", year, month);
                            }
                        }
                    }
                    // 最终回退：从请求中的 dateRangeText 解析
                    if (year == 0 || month == 0) {
                        String reqDate = request.getDateRangeText();
                        log.info("结款函导出: request.dateRangeText='{}'", reqDate);
                        if (reqDate != null && !reqDate.isBlank()) {
                            java.util.regex.Matcher m4 = java.util.regex.Pattern.compile(
                                    "(\\d{4})[/\\-年](\\d{1,2})[/\\-月]").matcher(reqDate);
                            if (m4.find()) {
                                year = Integer.parseInt(m4.group(1));
                                month = Integer.parseInt(m4.group(2));
                                log.info("结款函导出: 从dateRangeText提取 → year={}, month={}", year, month);
                            }
                        }
                    }
                    log.info("结款函导出: year={}, month={}, hasYearMonth={}",
                            year, month, (year > 0 && month > 0));
                } else {
                    log.warn("结款函导出: job not found for templateId={}", request.getTemplateId());
                }
            } catch (Exception e) {
                log.warn("提取结款函任务信息失败: {}", e.getMessage(), e);
            }
        } else {
            log.warn("结款函导出: templateId is null or blank");
        }

        // 回退：如果从任务中未提取到方案名称，从请求的 titleText 提取
        if (planName == null || planName.isBlank()) {
            String titleText = request.getTitleText();
            if (titleText != null && !titleText.isBlank()) {
                planName = titleText.replace("结款通知函", "").replace("结款函", "").trim();
            }
        }
        // 回退：如果年月仍未提取到，从 closingText 中解析日期
        if (year == 0 || month == 0) {
            String closing = request.getClosingText();
            if (closing != null && !closing.isBlank()) {
                java.util.regex.Matcher m5 = java.util.regex.Pattern.compile(
                        "(\\d{4})年(\\d{1,2})月(\\d{1,2})日").matcher(closing);
                if (m5.find()) {
                    year = Integer.parseInt(m5.group(1));
                    month = Integer.parseInt(m5.group(2));
                    log.info("结款函导出: 从closingText提取 → year={}, month={}", year, month);
                }
            }
        }

        File templateFile = new File(settlementTemplatePath);
        log.info("结款函导出: templatePath={}, exists={}", settlementTemplatePath, templateFile.exists());
        if (templateFile.exists() && templateFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(templateFile);
                 XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
                log.info("结款函导出: 模板加载成功, sheets={}", workbook.getNumberOfSheets());
                createSettlementTemplateWorkbook(workbook, request, planName, year, month);
                byte[] result = writeWorkbookToBytes(workbook);
                log.info("结款函导出: 输出字节数={}", result.length);
                return result;
            }
        } else {
            log.warn("结款函模板文件不存在，使用简单导出: {}", settlementTemplatePath);
            return generateSimpleSettlementExcel(request);
        }
    }

    /**
     * 创建结款函模板工作簿（匹配 FastAPI _create_settlement_template_workbook）
     *
     * 结款函只有一个 sheet，不需要复杂的 sheet 分组和克隆操作。
     * 直接获取模板的第一个 sheet 写入数据即可。
     *
     * @param workbook 结款函模板工作簿
     * @param request  结款函导出请求
     */
    private void createSettlementTemplateWorkbook(XSSFWorkbook workbook,
                                                  HospitalSettlementTemplateExportRequest request,
                                                  String planName, int year, int month) {
        XSSFSheet sheet = workbook.getSheetAt(0);
        writeSettlementTemplate(sheet, request, planName, year, month);
    }

    /**
     * 写入结款函模板数据（匹配 FastAPI _write_settlement_template）
     *
     * 结款函模板的布局比账单模板更简单，核心操作包括：
     *
     * ===== 行数动态调整 =====
     * 模板预设 2 行费用明细行（templateDetailRows = 2），如果实际费用条目
     * 多于或少于 2 行，需要在合计行之前插入或删除行以保持布局完整。
     *
     * ===== 写入内容 =====
     * 1. 信息区域：标题、收件人、医院名称、日期范围
     * 2. 费用明细行：遍历 feeRows 逐行写入序号/条目/费用/备注
     * 3. 合计行：汇总金额
     * 4. 大写合计行：中文大写金额
     * 5. 落款区域：公司名称与日期
     * 6. 合并单元格管理
     *
     * @param sheet   结款函模板 sheet
     * @param request 结款函导出请求
     */
    private void writeSettlementTemplate(XSSFSheet sheet, HospitalSettlementTemplateExportRequest request,
                                         String planName, int year, int month) {
        log.info("writeSettlementTemplate: planName={}, year={}, month={}, hospitalName={}, feeRows={}",
                planName, year, month,
                request.getHospitalDisplayName(),
                request.getFeeRows() != null ? request.getFeeRows().size() : 0);
        int detailStartRow = 12;      // 费用明细起始行（1-indexed）
        int templateDetailRows = 2;   // 模板预设的明细行数
        int totalRow = 14;            // 模板预设的合计行（1-indexed）
        int feeRowCount = request.getFeeRows() != null ? request.getFeeRows().size() : 0;
        int diff = feeRowCount - templateDetailRows;

        boolean hasYearMonth = year > 0 && month > 0;
        int lastDay = hasYearMonth ? java.time.YearMonth.of(year, month).lengthOfMonth() : 0;
        log.info("writeSettlementTemplate: hasYearMonth={}, lastDay={}, closingText={}",
                hasYearMonth, lastDay, request.getClosingText());

        // ===== 行数调整：如果费用条目数 ≠ 2，插入或删除行 =====
        if (diff > 0) {
            insertRows(sheet, totalRow, diff);
            for (int rowIdx = totalRow; rowIdx < totalRow + diff; rowIdx++) {
                cloneRowStyle(sheet, totalRow + diff, rowIdx, detailStartRow - 1);
            }
        } else if (diff < 0) {
            // 不物理删行：deleteRows/shiftRows 易破坏模板 merged regions（F13:G13 等）
            for (int i = 0; i < -diff; i++) {
                clearSettlementDetailRow(sheet, detailStartRow + feeRowCount + i);
            }
            // 清除模板预设合计/大写行的残留内容（feeRowCount=1 时 row15 仍留旧值）
            clearSettlementDetailRow(sheet, totalRow);
            clearSettlementDetailRow(sheet, totalRow + 1);
        }

        // ===== 重新计算行号 =====
        int newTotalRow = detailStartRow + feeRowCount;
        int uppercaseRow = newTotalRow + 1;
        int contentRow = uppercaseRow + 2;
        int saluteRow = contentRow + 1;
        int closingRow = saluteRow + 1;

        // ===== Row 6: 方案名称 + "结款通知函" =====
        String title = (planName != null && !planName.isBlank() ? planName : "") + "结款通知函";
        log.info("writeSettlementTemplate: D6 title={}", title);
        setCellValue(sheet, "D6", cleanExcelText(title));

        // ===== Row 7+8 合并为一行：致：XXX医院（冒号后加粗） =====
        String hospitalName = request.getHospitalDisplayName() != null
                ? request.getHospitalDisplayName() : "";
        String label = request.getRecipientLabel() != null
                ? request.getRecipientLabel() : "致：";
        String combinedText = label + hospitalName;
        log.info("writeSettlementTemplate: D7 combinedText={}", combinedText);

        setCellValue(sheet, "D7", cleanExcelText(combinedText));
        Row row7 = sheet.getRow(6);
        if (row7 != null) {
            row7.setHeightInPoints(Math.max(row7.getHeightInPoints(), 30));
        }

        // 清空 Row 8（现已与 Row 7 合并）
        setCellValue(sheet, "D8", null);
        Row row8 = sheet.getRow(7);
        if (row8 != null) {
            row8.setHeightInPoints((short) 2);
        }

        // ===== Row 9: 从:YYYY年M月1日  至: YYYY年M月DD日 灭菌费用总清单如下： =====
        if (hasYearMonth) {
            String dateRange = "从:" + year + "年" + month + "月1日  至: "
                    + year + "年" + month + "月" + lastDay + "日 灭菌费用总清单如下：";
            log.info("writeSettlementTemplate: D9 dateRange={}", dateRange);
            setCellValue(sheet, "D9", cleanExcelText(dateRange));
        } else {
            setCellValue(sheet, "D9", cleanExcelText(request.getDateRangeText()));
        }

        // ===== 逐行写入费用明细 =====
        if (request.getFeeRows() != null) {
            for (int i = 0; i < request.getFeeRows().size(); i++) {
                SettlementFeeRow feeRow = request.getFeeRows().get(i);
                int r = detailStartRow + i;
                setCellValue(sheet, r, 4, cleanExcelText(feeRow.getIndexLabel()));
                setCellValue(sheet, r, 5, cleanExcelText(feeRow.getItemLabel()));
                setCellValue(sheet, r, 6, cleanExcelNumber(feeRow.getAmount()));
                setCellValue(sheet, r, 8, cleanExcelText(feeRow.getRemark()));
            }
        }

        // ===== 合计行 =====
        setCellValue(sheet, newTotalRow, 4, "合　　计");
        setCellValue(sheet, newTotalRow, 6, cleanExcelNumber(request.getTotalAmount()));
        setCellValue(sheet, newTotalRow, 8, null);

        // ===== 大写合计行 =====
        setCellValue(sheet, uppercaseRow, 4, "合计大写");
        setCellValue(sheet, uppercaseRow, 6, cleanExcelText(request.getUppercaseTotal()));
        setCellValue(sheet, uppercaseRow, 8, null);

        // ===== 落款：日期修正为该月最后一天 =====
        if (request.getClosingText() != null && !request.getClosingText().isBlank()) {
            String closing = request.getClosingText();
            if (hasYearMonth) {
                closing = closing.replaceAll("(\\d{4})年(\\d{1,2})月(\\d{1,2})日",
                        year + "年" + month + "月" + lastDay + "日");
            }
            log.info("writeSettlementTemplate: closingRow={}, closing={}", closingRow, closing);
            setCellValue(sheet, closingRow, 4, cleanExcelText(closing));
        }

        // ===== 管理合并单元格 =====
        String[] rangesToUnmerge = {
                "D17:I17", "D18:I18", "D19:I19", "F8:G8", "H8:I8", "D8:I8"
        };
        for (String range : rangesToUnmerge) {
            unmergeCellRange(sheet, range);
        }

        // 重新合并：Row 7 合并 D7:I7（替代原来的 D8:I8）
        addMergedRegionSafe(sheet, "D7:I7");
        addMergedRegionSafe(sheet, "D" + contentRow + ":I" + contentRow);
        addMergedRegionSafe(sheet, "F" + saluteRow + ":G" + saluteRow);
        addMergedRegionSafe(sheet, "H" + saluteRow + ":I" + saluteRow);
        addMergedRegionSafe(sheet, "D" + closingRow + ":I" + closingRow);

        // ===== 重命名 sheet =====
        String newSheetName = safeSheetName(
                request.getSheetName() != null ? request.getSheetName() : sheet.getSheetName());
        try {
            XSSFWorkbook wb = sheet.getWorkbook();
            wb.setSheetName(wb.getSheetIndex(sheet), newSheetName);
        } catch (Exception e) {
            log.warn("重命名 sheet 失败: {}", e.getMessage());
        }
    }

    // ========================================================================
    //  第十节：账单 HTML 打印生成（匹配 FastAPI _build_bill_print_html）
    //  Section 10: Bill Print HTML Generation
    // ========================================================================
    //
    // 生成可直接在浏览器中打印的账单 HTML 页面。
    // 支持按科室分 sheet 展示，自动分页（page-break），内置打印样式。
    //
    // ===== HTML 布局结构 =====
    // <body>
    //   <section class="bill-sheet">          ← 每个科室一个 section
    //     <table class="bill-table">
    //       <thead>
    //         <tr> 标题行（25px 大字） </tr>
    //         <tr> 日期范围行 </tr>
    //         <tr> 分割线 </tr>
    //         <tr> 汇总行（医院名 + 总包数 + 总金额） </tr>
    //         <tr> 列标题行（发货日期/单号/类型/包类别/包名/包数/单价/总价） </tr>
    //       </thead>
    //       <tbody>
    //         <tr>... 逐行明细 ...</tr>
    //       </tbody>
    //     </table>
    //   </section>
    //   <script> 自动调用 window.print() </script>
    // </body>
    //
    // ===== 打印样式要点 =====
    // - A4 portrait, margin 8mm
    // - 字体：SimSun（宋体）
    // - thead 设置 display: table-header-group（每页重复表头）
    // - page-break-inside: avoid（行不分页）
    // - page-break-after: always（不同 sheet 强制分页）
    // - print-color-adjust: exact（保留背景色，确保汇总行蓝底显示）
    //
    // ========================================================================

    /**
     * 生成账单打印 HTML（匹配 FastAPI _build_bill_print_html）
     *
     * 核心逻辑与 writeSheetFromTemplate 对应，但输出为 HTML 而非 Excel。
     * 相同的数据分组逻辑（按科室 sheet）和优先级规则（优选 correctedTotalPrice）。
     *
     * @param request 账单导出/打印请求（含 rows, sheetMetas, hospitalName）
     * @return 完整带样式的账单打印 HTML 页面
     */
    private String buildBillPrintHtml(HospitalBillTemplateExportRequest request) {
        // ===== 第一步：按 sheet_name 分组行数据 =====
        Map<String, List<BillRowItem>> groupedRows = groupRowsBySheet(request.getRows());
        List<String> orderedSheetNames = new ArrayList<>(groupedRows.keySet());

        // 构建 sheet 元数据映射（标题、日期范围、医院显示名）
        Map<String, BillSheetMeta> metaMap = new HashMap<>();
        if (request.getSheetMetas() != null) {
            for (BillSheetMeta meta : request.getSheetMetas()) {
                if (meta.getSheetName() != null) {
                    metaMap.put(meta.getSheetName(), meta);
                }
            }
        }

        // D8 解析：使用 job 的方案名称覆盖 meta 的 hospitalDisplayName
        if (request.getTemplateId() != null) {
            resolveD8HospitalText(request.getTemplateId(), metaMap);
        }

        // ===== 第二步：遍历每个科室，生成 HTML 表格 =====
        StringBuilder sectionsHtml = new StringBuilder();

        for (int idx = 0; idx < orderedSheetNames.size(); idx++) {
            String sheetName = orderedSheetNames.get(idx);
            List<BillRowItem> sheetRows = groupedRows.get(sheetName);
            if (sheetRows == null) sheetRows = new ArrayList<>();

            // 过滤跳过行，只导出有效数据
            List<BillRowItem> exportRows = sheetRows.stream()
                    .filter(r -> !"skipped".equals(r.getStatus()))
                    .collect(Collectors.toList());

            // 获取 sheet 元数据（标题、日期范围、医院显示名）
            BillSheetMeta meta = metaMap.get(sheetName);
            String titleText = meta != null && meta.getTitleText() != null
                    ? htmlText(meta.getTitleText()) : "发货单汇总表-显示包装材料";
            String dateRangeText = meta != null && meta.getDateRangeText() != null
                    ? htmlText(meta.getDateRangeText()) : "";
            String hospitalDisplayName = meta != null && meta.getHospitalDisplayName() != null
                    ? htmlText(meta.getHospitalDisplayName())
                    : htmlText(request.getHospitalName() != null ? request.getHospitalName() : sheetName);

            // 计算汇总数据
            int totalPackCount = exportRows.stream()
                    .filter(r -> r.getPackCount() != null)
                    .mapToInt(BillRowItem::getPackCount)
                    .sum();
            double totalAmount = exportRows.stream()
                    .mapToDouble(r -> {
                        if (r.getCorrectedTotalPrice() != null) return r.getCorrectedTotalPrice();
                        if (r.getTotalPrice() != null) return r.getTotalPrice();
                        return 0.0;
                    })
                    .sum();

            // ===== 生成明细行 HTML =====
            StringBuilder tableRowsHtml = new StringBuilder();
            for (BillRowItem row : exportRows) {
                Double unitPrice = row.getExpectedUnitPrice() != null ? row.getExpectedUnitPrice() : row.getUnitPrice();
                Double price = row.getCorrectedTotalPrice() != null ? row.getCorrectedTotalPrice() : row.getTotalPrice();
                String deliveryDate = row.getDeliveryDate() != null
                        ? htmlText(row.getDeliveryDate().replace("-", "/")) : "";

                // 每行 7 列：发货日期 / 单号 / 包类别号 / 包名(左对齐) / 包数 / 单价 / 总价
                tableRowsHtml.append("<tr>")
                        .append("<td>").append(deliveryDate.isEmpty() ? "&nbsp;" : deliveryDate).append("</td>")
                        .append("<td>").append(htmlText(row.getOrderNo())).append("</td>")
                        .append("<td>").append(htmlText(row.getCategoryNo())).append("</td>")
                        .append("<td class=\"text-left\">").append(htmlText(row.getPackName())).append("</td>")
                        .append("<td>").append(row.getPackCount() != null ? row.getPackCount() : "").append("</td>")
                        .append("<td>").append(formatMoney(unitPrice)).append("</td>")
                        .append("<td>").append(formatMoney(price)).append("</td>")
                        .append("</tr>\n");
            }

            // ===== 组合一个科室的完整 section =====
            // 非最后一个 sheet 添加 page-break 类实现强制分页
            String pageBreakClass = idx < orderedSheetNames.size() - 1 ? " page-break" : "";
            sectionsHtml.append("<section class=\"bill-sheet").append(pageBreakClass).append("\">\n")
                    .append("<table class=\"bill-table\">\n")
                    // 列宽比例：日期16% / 单号12% / 类型16% / 类别12% / 包名24% / 包数8% / 单价12% / 总价12%
                    .append("<colgroup>\n")
                    .append("<col style=\"width:16%\"><col style=\"width:12%\">\n")
                    .append("<col style=\"width:16%\"><col style=\"width:12%\">\n")
                    .append("<col style=\"width:24%\"><col style=\"width:8%\">\n")
                    .append("<col style=\"width:12%\"><col style=\"width:12%\">\n")
                    .append("</colgroup>\n")
                    // thead（打印时每页重复表头）
                    .append("<thead>\n")
                    .append("<tr class=\"bill-doc-title\"><th colspan=\"8\">\n")
                    .append("<div class=\"bill-title-row\">\n")
                    .append("<div class=\"bill-title-text\">").append(titleText).append("</div>\n")
                    .append("</div>\n</th></tr>\n")
                    .append("<tr class=\"bill-doc-date\"><th colspan=\"8\">")
                    .append(dateRangeText.isEmpty() ? "&nbsp;" : dateRangeText)
                    .append("</th></tr>\n")
                    .append("<tr class=\"bill-doc-divider\"><th colspan=\"8\"><div class=\"bill-divider\">" +
                            "</div></th></tr>\n")
                    // 汇总信息行（蓝底白字：医院名 + 总包数 + 总金额）
                    .append("<tr class=\"bill-summary-row\">\n")
                    .append("<th colspan=\"5\" class=\"text-left\">").append(hospitalDisplayName).append("</th>\n")
                    .append("<th>").append(totalPackCount).append("</th>\n")
                    .append("<th>&nbsp;</th>\n")
                    .append("<th>").append(formatMoney(totalAmount)).append("</th>\n")
                    .append("</tr>\n")
                    // 列标题行（灰底）
                    .append("<tr class=\"bill-column-row\">\n")
                    .append("<th>发货日期</th><th>发货单号</th><th>包类别号</th>\n")
                    .append("<th>包名</th><th>包数</th><th>单价</th><th>总价</th>\n")
                    .append("</tr>\n</thead>\n<tbody>\n")
                    .append(tableRowsHtml.length() > 0 ? tableRowsHtml : "<tr><td colspan=\"7\">暂无明细</td></tr>")
                    .append("</tbody>\n</table>\n</section>\n");
        }

        // ===== 第三步：组合完整 HTML 页面 =====
        String hospitalName = htmlText(request.getHospitalName() != null ? request.getHospitalName() : "医院账单");
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\">\n<head>\n"
                + "<meta charset=\"UTF-8\" />\n"
                + "<title>" + hospitalName + "</title>\n"
                // ===== CSS 样式 =====
                + "<style>\n"
                + "@page { size: A4 portrait; margin: 8mm 8mm; }\n"
                + "* { box-sizing: border-box; }\n"
                + "body { margin: 0; font-family: \"SimSun\", \"宋体\", serif; color: #111; background: #fff; }\n"
                + ".bill-sheet.page-break { break-after: page; page-break-after: always; }\n"
                + ".bill-table { width: 100%; border-collapse: collapse; table-layout: fixed; }\n"
                + ".bill-table thead { display: table-header-group; }\n"   // 每页重复表头
                + ".bill-table tr { break-inside: avoid; page-break-inside: avoid; }\n"
                + ".bill-table th, .bill-table td { border: 1px solid #7f7f7f; padding: 5px 4px; "
                + "font-size: 12px; line-height: 1.35; text-align: center; vertical-align: middle; "
                + "word-break: break-all; }\n"
                + ".bill-table .text-left { text-align: left; }\n"
                + ".bill-doc-title th, .bill-doc-date th, .bill-doc-divider th { border: 0; "
                + "background: #fff; padding-left: 0; padding-right: 0; }\n"
                + ".bill-doc-title th { padding-top: 0; padding-bottom: 10px; }\n"
                + ".bill-doc-date th { padding-top: 0; padding-bottom: 14px; text-align: left; "
                + "font-weight: 400; color: #444; }\n"
                + ".bill-doc-divider th { padding-top: 0; padding-bottom: 16px; }\n"
                + ".bill-title-row { display: flex; align-items: flex-start; justify-content: space-between; "
                + "gap: 16px; min-height: 54px; }\n"
                + ".bill-title-text { font-size: 25px; line-height: 1.2; font-weight: 400; text-align: left; }\n"
                + ".bill-divider { width: 100%; border-top: 1px solid #222; height: 0; }\n"
                + ".bill-summary-row th { background: #7ea7bf; color: #fff; font-weight: 400; }\n"
                + ".bill-column-row th { background: #f6f6f6; font-weight: 400; }\n"
                + "@media print { html, body { margin: 0; padding: 0; } "
                + "body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } "
                + ".bill-sheet.page-break { break-after: page; page-break-after: always; } }\n"
                + "</style>\n</head>\n<body>\n"
                + sectionsHtml.toString()
                // 自动打印脚本（页面完全加载后延迟 200ms 弹出打印对话框）
                + "<script>window.addEventListener('load',function(){setTimeout(function(){window.print();},200);});</script>\n"
                + "</body>\n</html>";
    }

    // ========================================================================
    //  第十一节：结款函 HTML 打印生成（匹配 FastAPI _build_settlement_print_html）
    //  Section 11: Settlement Print HTML Generation
    // ========================================================================
    //
    // 结款函 HTML 是正式财务文档，包含以下内容区块：
    //
    // 1. 公司品牌区（占位，预留 Logo 位置）
    // 2. 分隔线
    // 3. 标题：结款通知函
    // 4. 收件人信息：医院名称
    // 5. 结算期间：日期范围
    // 6. 费用明细表（6 列：序号/条目/费用/费用/备注/备注）
    // 7. 合计行 + 大写合计行
    // 8. 正文内容（含付款说明、银行账户信息、免责条款）
    // 9. 此致敬礼
    // 10. 落款签名
    // 11. 自动打印脚本
    //
    // 备注：结款函中出现的"灭菌公司"是固定的，
    // 对应常量 COMPANY_NAME="黑龙江省铂康医疗灭菌有限公司"。
    //
    // ========================================================================

    /**
     * 生成结款函打印 HTML（匹配 FastAPI _build_settlement_print_html）
     *
     * @param request 结款函请求体（含 feeRows, totalAmount, uppercaseTotal, closingText 等）
     * @return 完整带样式的结款函 HTML 页面
     */
    private String buildSettlementPrintHtml(HospitalSettlementTemplateExportRequest request) {
        // ===== 提取公司信息和标题 =====
        // 公司名称：优先使用请求中的，否则使用系统常量
        String displayCompanyName = request.getCompanyName() != null ? htmlText(request.getCompanyName()) : companyName;
        String titleText = request.getTitleText() != null ? htmlText(request.getTitleText()) : "结款通知函";
        String recipientLabel = request.getRecipientLabel() != null ? htmlText(request.getRecipientLabel()) : "致：";
        String hospitalDisplayName = htmlText(request.getHospitalDisplayName());
        String dateRangeText = htmlText(request.getDateRangeText());

        // ===== 生成费用明细表格行 =====
        StringBuilder feeRowsHtml = new StringBuilder();
        if (request.getFeeRows() != null) {
            for (SettlementFeeRow feeRow : request.getFeeRows()) {
                feeRowsHtml.append("<tr>")
                        .append("<td>").append(htmlText(feeRow.getIndexLabel())).append("</td>")          // 序号
                        .append("<td>").append(htmlText(feeRow.getItemLabel())).append("</td>")           // 条目名称
                        .append("<td colspan=\"2\">").append(formatMoney(feeRow.getAmount())).append("</td>") // 费用（占2列）
                        .append("<td colspan=\"2\" class=\"text-left\">")
                        .append(feeRow.getRemark() != null ? htmlText(feeRow.getRemark()) : "&nbsp;")     // 备注（占2列）
                        .append("</td>")
                        .append("</tr>\n");
            }
        }

        // ===== 落款 HTML =====
        String closingHtml = "";
        if (request.getClosingText() != null && !request.getClosingText().isBlank()) {
            // 将换行符转为 <br /> 保持布局
            closingHtml = htmlText(request.getClosingText()).replace("\n", "<br />");
        }

        // ===== 构建完整 HTML =====
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\">\n<head>\n"
                + "<meta charset=\"UTF-8\" />\n"
                + "<title>" + titleText + "</title>\n"
                // ===== CSS 打印样式 =====
                + "<style>\n"
                + "@page { size: A4 portrait; margin: 10mm 12mm; }\n"
                + "* { box-sizing: border-box; }\n"
                + "body { margin: 0; font-family: \"SimSun\", \"宋体\", serif; color: #111; background: #fff; }\n"
                + ".settlement-page { width: 100%; }\n"
                + ".settlement-brand { display: flex; align-items: flex-start; "
                + "justify-content: space-between; gap: 16px; margin-bottom: 4px; }\n"
                + ".settlement-brand-left { display: flex; align-items: flex-start; "
                + "gap: 8px; min-height: 48px; }\n"
                + ".settlement-divider { border-top: 1px solid #222; margin: 0 0 18px; }\n"
                + ".settlement-title { text-align: center; font-size: 20px; "
                + "line-height: 1.2; margin: 0 0 10px; }\n"
                + ".settlement-to { font-size: 16px; line-height: 1.6; margin: 0 0 2px; }\n"
                + ".settlement-hospital { font-size: 16px; line-height: 1.6; margin: 0 0 2px; }\n"
                + ".settlement-date-range { font-size: 16px; line-height: 1.6; margin: 0 0 18px; }\n"
                + ".settlement-table { width: 84%; margin: 0 auto 20px; "
                + "border-collapse: collapse; table-layout: fixed; }\n"
                + ".settlement-table th, .settlement-table td { border: 1px solid #222; "
                + "padding: 6px 8px; font-size: 15px; line-height: 1.35; "
                + "text-align: center; vertical-align: middle; }\n"
                + ".settlement-table .text-left { text-align: left; }\n"
                + ".settlement-body { font-size: 16px; line-height: 1.65; "
                + "white-space: pre-line; margin-top: 4px; }\n"
                + ".settlement-salute { margin-top: 2px; text-align: left; }\n"
                + ".settlement-signature { margin-top: 8px; text-align: right; line-height: 1.7; }\n"
                + "@media print { body { -webkit-print-color-adjust: exact; "
                + "print-color-adjust: exact; } }\n"
                + "</style>\n</head>\n<body>\n"
                // ===== 内容主体 =====
                + "<section class=\"settlement-page\">\n"
                + "<div class=\"settlement-brand\">\n"
                + "<div class=\"settlement-brand-left\">\n"
                + "</div>\n</div>\n"
                + "<div class=\"settlement-divider\"></div>\n"
                + "<h1 class=\"settlement-title\">" + titleText + "</h1>\n"
                + "<p class=\"settlement-to\">" + recipientLabel + "</p>\n"
                + "<p class=\"settlement-hospital\">" + hospitalDisplayName + "</p>\n"
                + "<p class=\"settlement-date-range\">" + dateRangeText + "</p>\n"
                // 费用明细表（6 列）
                + "<table class=\"settlement-table\">\n"
                + "<colgroup>\n"
                + "<col style=\"width:9%\"><col style=\"width:20%\">\n"
                + "<col style=\"width:17%\"><col style=\"width:25%\">\n"
                + "<col style=\"width:28%\"><col style=\"width:1%\">\n"
                + "</colgroup>\n"
                + "<thead><tr>"
                + "<th>序号</th><th>条目</th><th colspan=\"2\">费用</th><th colspan=\"2\">备注</th>"
                + "</tr></thead>\n"
                + "<tbody>\n"
                + feeRowsHtml.toString()
                + "<tr><td colspan=\"2\">合　　计</td>"
                + "<td colspan=\"2\">" + formatMoney(request.getTotalAmount()) + "</td>"
                + "<td colspan=\"2\">&nbsp;</td></tr>\n"
                + "<tr><td colspan=\"2\">合计大写</td>"
                + "<td colspan=\"2\">" + htmlText(request.getUppercaseTotal()) + "</td>"
                + "<td colspan=\"2\">&nbsp;</td></tr>\n"
                + "</tbody>\n</table>\n"
                // 付款信息与免责条款
                + "<div class=\"settlement-body\">"
                + "贵医院各个科室消毒灭菌的器械详情见附件，请您仔细核对。<br />\n"
                + "我们将在您核对内容之后开具正规发票，请您在收到发票之后的3个工作日之内予以付款。<br />\n"
                + "我们的付款信息如下：<br />\n"
                + "公司名称：" + displayCompanyName + "<br />\n"
                + "账号：" + bankAccount + "<br />\n"
                + "开户银行：" + bankName + "<br />\n"
                + "感谢您的支持！<br />\n"
                + "＊截止本月末最后一天前不将反馈信息反馈到我公司，"
                + "视同默认金额，并以此账单为结算依据。"
                + "</div>\n"
                + "<div class=\"settlement-salute settlement-body\">此致<br />敬礼</div>\n"
                + "<div class=\"settlement-signature settlement-body\">" + closingHtml + "</div>\n"
                + "</section>\n"
                // 自动打印脚本
                + "<script>window.addEventListener('load',function(){"
                + "setTimeout(function(){window.print();},200);});</script>\n"
                + "</body>\n</html>";
    }

    /**
     * 生成结款函预览 HTML（含示例数据，匹配 FastAPI preview_settlement_template）
     *
     * 用于前端在模板选择页面展示模板样式。
     * 使用硬编码的示例数据（金额 1,234.56、物流费、大写金额等）。
     *
     * @param sampleFeeTable 示例费用表格 HTML
     * @param logoDataUri    公司 Logo 的 Data URI（当前未实现）
     * @return 用于预览的简洁 HTML 页面
     */
    private String buildSettlementPreviewHtml(String sampleFeeTable, String logoDataUri) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\">\n<head>\n"
                + "<meta charset=\"UTF-8\" />\n"
                + "<title>货款结算单 - 预览</title>\n"
                + "<style>\n"
                + "@page { size: A4 portrait; margin: 10mm 12mm; }\n"
                + "* { box-sizing: border-box; }\n"
                + "body { margin: 0; font-family: \"SimSun\", \"宋体\", serif; "
                + "color: #111; background: #fff; }\n"
                + ".settlement-title { text-align: center; font-size: 20px; "
                + "line-height: 1.2; margin: 0 0 10px; }\n"
                + ".settlement-divider { border-top: 1px solid #222; margin: 0 0 18px; }\n"
                + ".settlement-table { width: 84%; margin: 0 auto 20px; "
                + "border-collapse: collapse; }\n"
                + ".settlement-table th, .settlement-table td { border: 1px solid #222; "
                + "padding: 6px 8px; font-size: 15px; text-align: center; }\n"
                + ".settlement-table .amount-cell { text-align: right; }\n"
                + ".settlement-table .remark-cell { text-align: left; }\n"
                + ".settlement-table .total-label-cell { text-align: center; }\n"
                + "@media print { body { -webkit-print-color-adjust: exact; "
                + "print-color-adjust: exact; } }\n"
                + "</style>\n</head>\n<body>\n"
                + "<h1 class=\"settlement-title\">货款结算单</h1>\n"
                + "<p>医院名称：示例医院</p>\n"
                + "<p>结算期间：2026年1月1日 至 2026年1月31日</p>\n"
                + sampleFeeTable
                + "<div style=\"margin-top:20px;\">黑龙江省铂康医疗灭菌有限公司<br />2026年1月31日</div>\n"
                + "</body>\n</html>";
    }

    /**
     * 构建示例费用表格 HTML（用于模板预览）
     *
     * 生成 4 行 4 列的示例结算数据：
     * - 灭菌费用 1,234.56
     * - 物流费用 150.00（50元/次）
     * - 合计 1,384.56
     * - 大写合计
     *
     * @return 示例表格 HTML 片段
     */
    private String buildSampleFeeTableHtml() {
        return "<table class=\"settlement-table\">\n"
                + "<colgroup><col style=\"width:9%\"><col style=\"width:30%\">"
                + "<col style=\"width:25%\"><col style=\"width:36%\"></colgroup>\n"
                + "<thead><tr><th>序号</th><th>项  目</th><th>费  用</th><th>备  注</th></tr></thead>\n"
                + "<tbody>\n"
                + "<tr><td>一</td><td>灭菌费用</td>"
                + "<td class=\"amount-cell\">1,234.56</td><td class=\"remark-cell\"></td></tr>\n"
                + "<tr><td>二</td><td>物流费用</td>"
                + "<td class=\"amount-cell\">150.00</td><td class=\"remark-cell\">50元/次</td></tr>\n"
                + "<tr><td colspan=\"2\" class=\"total-label-cell\">合　計</td>"
                + "<td class=\"amount-cell\">1,384.56</td><td class=\"remark-cell\"></td></tr>\n"
                + "<tr><td colspan=\"2\" class=\"total-label-cell\">合计大写</td>"
                + "<td class=\"amount-cell\">壹仟叁佰捌拾肆元伍角陆分</td>"
                + "<td class=\"remark-cell\"></td></tr>\n"
                + "</tbody>\n</table>";
    }

    // ========================================================================
    //  第十二节：Excel POI 底层工具方法
    //  Section 12: Apache POI Low-level Utility Methods
    // ========================================================================
    //
    // 本节包含对 Apache POI 的封装工具方法，用于：
    // - 工作簿的读写
    // - 行的插入、删除、样式克隆（模板操作核心）
    // - 合并单元格的管理（取消合并/安全合并）
    // - 单元格值的设置（通过列字母引用或行列号）
    //
    // 这些方法实现了 openpyxl (Python) 中的常用操作，
    // 保持与 FastAPI 原型一致的行为。
    //
    // ========================================================================

    /**
     * 将 XSSFWorkbook 工作簿写入字节数组
     *
     * 使用 try-with-resources 确保工作簿在使用完毕后正确关闭，
     * 防止临时文件残留和内存泄漏。
     *
     * @param workbook 要写出的工作簿对象
     * @return Excel 文件的完整字节数组
     * @throws IOException 写入异常
     */
    private byte[] writeWorkbookToBytes(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);            // 将工作簿内容写出到字节流
            return out.toByteArray();       // 转为字节数组供 HTTP 响应
        } finally {
            workbook.close();               // 确保工作簿资源释放
        }
    }

    /**
     * 在 XSSFSheet 中插入行（匹配 openpyxl insert_rows 语义）
     *
     * ===== 实现原理 =====
     * 1. 使用 sheet.shiftRows() 将起始行之后的所有行向下移动 amount 行
     * 2. shiftRows 的参数 copyRowHeight=true, copyStyles=true
     *    保留移动行的行高和样式（但实际上移动后原位置变为空）
     * 3. 遍历新空出的行区域，如果行不存在则创建空行
     *
     * 注意：此方法仅在指定位置创建空行，数据的写入由调用方负责。
     * 数据写入后还需要调用 cloneRowStyle 来复制样式。
     *
     * @param sheet       POI sheet 对象
     * @param startRowIdx 起始行号（1-indexed），在此行之前插入新行
     * @param amount      要插入的行数
     */
    private void insertRows(XSSFSheet sheet, int startRowIdx, int amount) {
        int startZeroIdx = startRowIdx - 1;  // 转换为 0-indexed
        int lastRowNum = sheet.getLastRowNum();
        // 仅当插入位置在现有行范围内时才需要下移
        if (startZeroIdx <= lastRowNum) {
            sheet.shiftRows(startZeroIdx, lastRowNum, amount, true, true);
        }
        // 创建空行占位
        for (int i = startZeroIdx; i < startZeroIdx + amount; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                sheet.createRow(i);
            }
        }
    }

    private void clearSettlementDetailRow(XSSFSheet sheet, int rowOneIdx) {
        Row row = sheet.getRow(rowOneIdx - 1);
        if (row == null) {
            return;
        }
        for (int col = 3; col <= 8; col++) {
            Cell cell = row.getCell(col);
            if (cell != null) {
                cell.setBlank();
            }
        }
    }

    /**
     * 删除行（匹配 openpyxl delete_rows 语义）
     *
     * ===== 实现原理 =====
     * 1. 先逐行调用 removeRow() 移除单元格内容和样式
     * 2. 使用 shiftRows() 将删除区域之后的所有行上移 amount 行
     * 3. 注意这里不复制行高（false），因为我们是要删除行
     *
     * @param sheet       POI sheet 对象
     * @param startRowIdx 起始行号（1-indexed），从此行开始删除
     * @param amount      要删除的行数
     */
    private void deleteRows(XSSFSheet sheet, int startRowIdx, int amount) {
        int startZeroIdx = startRowIdx - 1;  // 转换为 0-indexed
        // 第一步：逐行移除内容
        for (int i = startZeroIdx; i < startZeroIdx + amount; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                sheet.removeRow(row);  // 移除该行的所有单元格数据
            }
        }
        // 第二步：将后续行上移填补空缺
        int lastRowNum = sheet.getLastRowNum();
        if (startZeroIdx + amount <= lastRowNum) {
            // shiftRows 不会复制行高（copyRowHeight=false），因为我们正在删除行
            sheet.shiftRows(startZeroIdx + amount, lastRowNum, -amount, true, false);
        }
    }

    /**
     * 克隆行样式（匹配 FastAPI _clone_template_row_style）
     *
     * 在模板操作中，插入新行后需要复制模板行的样式（字体、边框、背景色、对齐等）。
     * 这个方法逐列复制 CellStyle，确保新插入的行与模板行外观一致。
     *
     * ===== 克隆内容 =====
     * - 行高
     * - 每列的 CellStyle（完整样式对象，包含字体、边框、图案、对齐等）
     * - CellStyle 在 POI 中是对样式缓存的引用，不创建新的样式实例
     *   因此克隆不会增加工作簿的样式数量（不会达到 POI 的 64000 样式上限）
     *
     * @param sheet            POI sheet 对象
     * @param sourceRowOneIdx  源模板行（1-indexed，被克隆样式的行）
     * @param targetRowOneIdx  目标行（1-indexed，接收样式的行）
     * @param fallbackRowOneIdx 回退样式行（1-indexed），当源行单元格为空时从此行取样式
     */
    private void cloneRowStyle(XSSFSheet sheet, int sourceRowOneIdx, int targetRowOneIdx, int fallbackRowOneIdx) {
        int srcZeroIdx = sourceRowOneIdx - 1;  // 转换为 0-indexed
        int tgtZeroIdx = targetRowOneIdx - 1;
        int fbZeroIdx = fallbackRowOneIdx - 1;
        Row sourceRow = sheet.getRow(srcZeroIdx);
        Row fallbackRow = sheet.getRow(fbZeroIdx);
        Row targetRow = sheet.getRow(tgtZeroIdx);
        if (targetRow == null) {
            targetRow = sheet.createRow(tgtZeroIdx);
        }
        // 确定需要遍历的最大列数（取源行和回退行中的较大值）
        int maxCol = -1;
        if (sourceRow != null) maxCol = Math.max(maxCol, sourceRow.getLastCellNum());
        if (fallbackRow != null) maxCol = Math.max(maxCol, fallbackRow.getLastCellNum());
        if (maxCol < 0) return; // 无参考单元格，无法克隆样式
        if (sourceRow != null) {
            targetRow.setHeight(sourceRow.getHeight());
        }
        for (int col = 0; col <= maxCol; col++) {
            Cell sourceCell = sourceRow != null ? sourceRow.getCell(col) : null;
            Cell targetCell = targetRow.getCell(col);
            if (targetCell == null) {
                targetCell = targetRow.createCell(col);
            }
            if (sourceCell != null) {
                targetCell.setCellStyle(sourceCell.getCellStyle());
            } else if (fallbackRow != null) {
                Cell fbCell = fallbackRow.getCell(col);
                if (fbCell != null) {
                    targetCell.setCellStyle(fbCell.getCellStyle());
                }
            }
        }
    }

    /**
     * 取消合并指定范围的单元格
     *
     * 在模板操作中，需要在插入/删除行之前取消旧的合并区域，
     * 因为合并区域在行数变化后可能偏移或变得不适用。
     * 数据写入完毕后再重新创建正确的合并区域。
     *
     * @param sheet    POI sheet 对象
     * @param rangeStr 合并范围字符串，如 "A8:B19"（Excel 格式）
     */
    private void unmergeCellRange(XSSFSheet sheet, String rangeStr) {
        CellRangeAddress targetRange = CellRangeAddress.valueOf(rangeStr);
        // 逆序遍历合并区域列表，找到匹配的合并区域后移除
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.formatAsString().equals(rangeStr)) {
                sheet.removeMergedRegion(i);
                return;  // 找到即返回（每个范围最多只有一个合并区域）
            }
        }
    }

    /**
     * 安全地添加合并区域（防重复检查）
     *
     * 在添加合并区域前先检查是否已存在相同范围的合并，
     * 如果已存在则不重复添加，避免 POI 抛出 IllegalStateException。
     *
     * @param sheet    POI sheet 对象
     * @param rangeStr 合并范围字符串，如 "C1:H2"
     */
    private void addMergedRegionSafe(XSSFSheet sheet, String rangeStr) {
        CellRangeAddress range = CellRangeAddress.valueOf(rangeStr);
        int exactCount = 0;
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress existing = sheet.getMergedRegion(i);
            if (existing.formatAsString().equals(rangeStr)) {
                exactCount++;
                if (exactCount > 1) {
                    sheet.removeMergedRegion(i);
                }
                continue;
            }
            if (existing.intersects(range)) {
                sheet.removeMergedRegion(i);
            }
        }
        if (exactCount == 0) {
            try {
                sheet.addMergedRegion(range);
            } catch (IllegalStateException e) {
                log.warn("skip duplicate merged region {}: {}", rangeStr, e.getMessage());
            }
        }
    }

    /**
     * 获取 sheet 中所有合并区域的字符串表示集合
     *
     * 用于在取消合并操作前快速判断某个范围是否已被合并。
     *
     * @param sheet POI sheet 对象
     * @return 合并范围字符串集合，如 {"A8:B19", "C1:H2", ...}
     */
    private Set<String> getMergedRangesAsStrings(XSSFSheet sheet) {
        Set<String> ranges = new HashSet<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            ranges.add(sheet.getMergedRegion(i).formatAsString());
        }
        return ranges;
    }

    /**
     * 设置单元格值（通过 Excel 风格的字母列引用）
     *
     * 便捷方法，适用于在模板操作中按照模板坐标设置值。
     * 内部调用 setCellValue(sheet, rowNum, colNum, value) 实现。
     *
     * @param sheet   POI sheet 对象
     * @param cellRef 单元格引用，如 "C1"、"D10"、"K19"
     * @param value   要设置的值（String / Number / null 均可）
     */
    private void setCellValue(XSSFSheet sheet, String cellRef, Object value) {
        CellReference ref = new CellReference(cellRef);
        setCellValue(sheet, ref.getRow() + 1, ref.getCol() + 1, value);
    }

    /**
     * 设置单元格值（通过 1-indexed 的行列号）
     *
     * ===== 类型处理规则 =====
     * - value == null: 设置为空字符串（清除单元格内容）
     * - value instanceof Number: 设置为数值类型（Double），Excel 中可参与公式运算
     * - 其他类型: 调用 String.valueOf() 转为字符串设置
     *
     * 如果行或单元格不存在，自动创建（不丢失现有行的其他单元格）。
     *
     * @param sheet    POI sheet 对象
     * @param rowOneIdx 行号（1-indexed，如 1 表示第一行）
     * @param colOneIdx 列号（1-indexed，如 1 表示 A 列，4 表示 D 列）
     * @param value     要设置的值（支持 Number, String, null）
     */
    private void setCellValue(XSSFSheet sheet, int rowOneIdx, int colOneIdx, Object value) {
        int rowZero = rowOneIdx - 1;  // 转换为 0-indexed（POI 内部使用）
        int colZero = colOneIdx - 1;
        Row row = sheet.getRow(rowZero);
        if (row == null) {
            row = sheet.createRow(rowZero);  // 自动创建行
        }
        Cell cell = row.getCell(colZero);
        if (cell == null) {
            cell = row.createCell(colZero);  // 自动创建单元格
        }
        // 根据值类型设置不同的单元格类型
        if (value == null) {
            cell.setCellValue((String) null);  // 清空内容
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());  // 数值类型
            // Double/Float 类型的金额统一显示两位小数（如 8→8.00, 16.5→16.50）
            if (value instanceof Double || value instanceof Float) {
                applyNumericFormat(sheet, cell);
            }
        } else {
            cell.setCellValue(String.valueOf(value));  // 字符串类型
        }
    }

    /** 给单元格设置 0.00 数字格式（保留原有边框、字体等样式） */
    private void applyNumericFormat(XSSFSheet sheet, Cell cell) {
        XSSFWorkbook wb = sheet.getWorkbook();
        // 缓存 key 需包含 workbook 标识和字体索引，防止不同 workbook 间的样式串用
        CellStyle oldStyle = cell.getCellStyle();
        String cacheKey = System.identityHashCode(wb) + "_num_0.00_" + oldStyle.getDataFormat() + "_f" + oldStyle.getFontIndex();
        CellStyle cached = numericStyleCache.get(cacheKey);
        if (cached == null) {
            cached = wb.createCellStyle();
            cached.cloneStyleFrom(cell.getCellStyle());
            cached.setDataFormat(wb.createDataFormat().getFormat("0.00"));
            numericStyleCache.put(cacheKey, cached);
        }
        cell.setCellStyle(cached);
    }

    /** 强制覆写模板单元格（先删后建，解决 inlineStr 类型导致的设值失效） */
    private void forceCellValue(XSSFSheet sheet, int rowOneIdx, int colOneIdx, Object value) {
        int rowZero = rowOneIdx - 1;
        int colZero = colOneIdx - 1;
        Row row = sheet.getRow(rowZero);
        if (row == null) {
            row = sheet.createRow(rowZero);
        }
        Cell oldCell = row.getCell(colZero);
        CellStyle style = oldCell != null ? oldCell.getCellStyle() : null;
        if (oldCell != null) {
            row.removeCell(oldCell);
        }
        Cell cell = row.createCell(colZero);
        if (style != null) {
            cell.setCellStyle(style);
        }
        if (value == null) {
            cell.setCellValue((String) null);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
            if (value instanceof Double || value instanceof Float) {
                applyNumericFormat(sheet, cell);
            }
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    /** 清空指定行（0-indexed）所有有内容的单元格 */
    private void clearRowCells(XSSFSheet sheet, int rowIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) return;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null) cell.setCellValue((String) null);
        }
    }

    /** 设置行内所有单元格字体加粗（保留原有字体名称、字号、数字格式和边框） */
    private void setRowBold(Row row) {
        XSSFWorkbook wb = (XSSFWorkbook) row.getSheet().getWorkbook();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell == null) cell = row.createCell(c);
            CellStyle existingStyle = cell.getCellStyle();
            // 基于现有字体创建加粗版本
            Font existingFont = wb.getFontAt(existingStyle.getFontIndex());
            XSSFFont boldFont = wb.createFont();
            boldFont.setBold(true);
            if (existingFont != null) {
                boldFont.setFontName(existingFont.getFontName());
                boldFont.setFontHeight(existingFont.getFontHeight());
                boldFont.setColor(existingFont.getColor());
                boldFont.setItalic(existingFont.getItalic());
                boldFont.setUnderline(existingFont.getUnderline());
            }
            CellStyle style = wb.createCellStyle();
            style.cloneStyleFrom(existingStyle);
            style.setFont(boldFont);
            cell.setCellStyle(style);
        }
    }

    /**
     * 给指定行范围的列统一应用细线边框。
     * 用于修复模板中部分行缺少竖线及 setCellValue 自动创建行无样式的问题。
     * 已存在四边边框的单元格保留原样式不动，避免覆盖字体和数字格式。
     *
     * @param wb         工作簿
     * @param sheet      目标 sheet
     * @param startRow   起始行（1-indexed）
     * @param endRow     结束行（1-indexed，包含）
     * @param startCol   起始列（0-indexed，如 A=0）
     * @param endCol     结束列（0-indexed，包含，如 K=10）
     */
    private void applyUniformBorders(XSSFWorkbook wb, XSSFSheet sheet,
                                      int startRow, int endRow, int startCol, int endCol) {
        CellStyle borderStyle = null; // 延迟创建，避免不必要的工作簿样式增长
        for (int r = startRow; r <= endRow; r++) {
            int rowIdx = r - 1;
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                row = sheet.createRow(rowIdx);
            }
            for (int c = startCol; c <= endCol; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    cell = row.createCell(c);
                }
                CellStyle existing = cell.getCellStyle();
                // 仅当缺少任意一边边框时才覆盖样式
                if (existing == null
                        || existing.getBorderLeft() == BorderStyle.NONE
                        || existing.getBorderRight() == BorderStyle.NONE
                        || existing.getBorderTop() == BorderStyle.NONE
                        || existing.getBorderBottom() == BorderStyle.NONE) {
                    if (borderStyle == null) {
                        borderStyle = wb.createCellStyle();
                        borderStyle.setBorderLeft(BorderStyle.THIN);
                        borderStyle.setBorderRight(BorderStyle.THIN);
                        borderStyle.setBorderTop(BorderStyle.THIN);
                        borderStyle.setBorderBottom(BorderStyle.THIN);
                    }
                    cell.setCellStyle(borderStyle);
                }
            }
        }
    }

    // ========================================================================
    //  第十三节：降级方案 —— 简单 Excel 生成（模板不存在时的备用方案）
    //  Section 13: Fallback — Simple Excel Generation
    // ========================================================================
    //
    // 当预设的 xlsx 模板文件在服务器上不存在时，系统自动降级使用此方案。
    // 降级方案生成无格式的简单 Excel，只包含表头和数据行。
    // 虽然没有模板的精致样式，但可以确保在模板文件缺失时不中断导出流程。
    //
    // ========================================================================

    /**
     * 生成简单 Excel（模板文件不存在时的降级方案）
     *
     * 创建新的空工作簿，写入行数据。第一行自动设为粗体表头。
     * 所有单元格均为文本格式。写入后自动调整列宽适配内容。
     *
     * @param title sheet 名称
     * @param rows  行数据列表（第一行为表头）
     * @return Excel 文件字节数组
     * @throws IOException 工作簿写入异常
     */
    private byte[] generateSimpleExcel(String title, List<List<String>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(title);

            // 创建表头样式（粗体）
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 写入所有行
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i);
                List<String> rowData = rows.get(i);
                for (int j = 0; j < rowData.size(); j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(rowData.get(j));
                    if (i == 0) {
                        cell.setCellStyle(headerStyle);  // 表头应用粗体样式
                    }
                }
            }

            // 根据内容自适应列宽（兼容中文字符）
            for (int j = 0; j < rows.get(0).size(); j++) {
                int maxWidth = getMaxWidth(rows, j);
                sheet.setColumnWidth(j, (maxWidth + 4) * 256);
            }

            return writeWorkbookToBytes(workbook);
        }
    }

    private static int getMaxWidth(List<List<String>> rows, int j) {
        int maxWidth = 0;
        for (List<String> rowData : rows) {
            if (j < rowData.size()) {
                String val = rowData.get(j);
                if (val != null) {
                    int w = 0;
                    for (char ch : val.toCharArray()) {
                        w += (ch >= 0x4e00 && ch <= 0x9fff) || (ch >= 0x3400 && ch <= 0x4dbf) ? 2 : 1;
                    }
                    maxWidth = Math.max(maxWidth, w);
                }
            }
        }
        return maxWidth;
    }

    /**
     * 生成简单结款函 Excel（降级方案）
     *
     * 构建结款函的纯文本表格：
     * - 表头：序号 / 条目 / 费用 / 备注
     * - 明细行：从 request.feeRows 提取
     * - 合计行：含总计金额
     * - 大写合计行：中文大写金额
     *
     * @param request 结款函导出请求
     * @return 简单结款函 Excel 字节数组
     * @throws IOException 工作簿写入异常
     */
    private byte[] generateSimpleSettlementExcel(HospitalSettlementTemplateExportRequest request) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        // 表头
        rows.add(Arrays.asList("序号", "条目", "费用", "备注"));
        // 明细行
        if (request.getFeeRows() != null) {
            for (SettlementFeeRow feeRow : request.getFeeRows()) {
                rows.add(Arrays.asList(
                        feeRow.getIndexLabel() != null ? feeRow.getIndexLabel() : "",
                        feeRow.getItemLabel() != null ? feeRow.getItemLabel() : "",
                        feeRow.getAmount() != null ? String.format("%.2f", feeRow.getAmount()) : "",
                        feeRow.getRemark() != null ? feeRow.getRemark() : ""));
            }
        }
        // 合计行 + 大写合计行
        rows.add(Arrays.asList("合计", "", formatMoney(request.getTotalAmount()), ""));
        rows.add(Arrays.asList("合计大写", "",
                amountToChineseUpper(request.getTotalAmount() != null ? request.getTotalAmount() : 0.0), ""));
        return generateSimpleExcel("结款函", rows);
    }

    /**
     * 构建简单账单的表头和数据行（降级方案）
     *
     * @param request 账单导出请求
     * @return 二维字符串列表，第一行为表头，后续为数据行
     */
    private List<List<String>> buildSimpleBillHeaderAndRows(HospitalBillTemplateExportRequest request) {
        List<List<String>> result = new ArrayList<>();
        // 表头：与导入表单结构一致，末尾加差额列
        result.add(Arrays.asList("发货日期", "发货单号", "类型", "包类别号", "包名", "包数", "单价", "总价", "差额"));

        // 数据行（不区分科室，全部写入同一个 sheet）
        if (request.getRows() != null) {
            for (BillRowItem row : request.getRows()) {
                String unitPrice = row.getExpectedUnitPrice() != null
                        ? String.format("%.2f", row.getExpectedUnitPrice())
                        : (row.getUnitPrice() != null ? String.format("%.2f", row.getUnitPrice()) : "");
                String totalPrice = row.getCorrectedTotalPrice() != null
                        ? String.format("%.2f", row.getCorrectedTotalPrice())
                        : (row.getTotalPrice() != null ? String.format("%.2f", row.getTotalPrice()) : "");
                String diff = row.getDifference() != null
                        ? String.format("%.2f", row.getDifference())
                        : "";
                result.add(Arrays.asList(
                        row.getDeliveryDate() != null ? row.getDeliveryDate() : "",
                        formatIntegerString(row.getOrderNo()),
                        row.getType() != null ? row.getType() : "",
                        formatIntegerString(row.getCategoryNo()),
                        row.getPackName() != null ? row.getPackName() : "",
                        row.getPackCount() != null ? String.valueOf(row.getPackCount()) : "",
                        unitPrice,
                        totalPrice,
                        diff));
            }
        }
        return result;
    }

    // ========================================================================
    //  第十四节：文件处理与行数据持久化
    //  Section 14: File Handling & Row Data Persistence
    // ========================================================================
    //
    // 文件处理：接收前端上传的 Excel 原始文件，按医院/版本组织存储。
    // 行数据持久化：将前端提交的行级数据同时存储为 rowsJson（主表）和
    // 结构化行表（明细表），实现灵活查询和快速展示的兼顾。
    //
    // ========================================================================

    /**
     * 保存上传的 Excel 文件到服务器磁盘
     *
     * ===== 文件存储结构 =====
     * uploadDir/
     *   └── {hospitalName}/
     *       └── {versionNo}_{timestamp}_{originalFilename}
     *
     * 例如：./uploads/hospital-reconciliations/哈尔滨市第一医院/
     *       1_1714435200000_2024年3月灭菌账单.xlsx
     *
     * 文件名的三部分结构确保了：
     * - versionNo 前缀用于标识是该医院的第几次核对
     * - timestamp 时间戳确保文件名全局唯一
     * - originalFilename 保留原始文件名便于识别
     *
     * @param file         上传的 MultipartFile 对象
     * @param hospitalName 医院名称（用于子目录命名）
     * @param versionNo    版本号（用于文件名前缀）
     * @return 存储文件的绝对路径字符串，失败返回 null
     */
    private String saveUploadFile(MultipartFile file, String hospitalName, int versionNo) {
        try {
            // 医院名称为空时使用默认目录名，避免路径异常
            String safeHospital = sanitizeFileName(hospitalName);
            if (safeHospital.isEmpty()) {
                safeHospital = "unknown-hospital";
            }

            // 创建医院专属目录（如：uploads/hospital-reconciliations/哈尔滨市第一医院/）
            Path dirPath = Paths.get(uploadDir, safeHospital);
            Files.createDirectories(dirPath);

            // 构造存储文件名：版本号_时间戳_原始文件名（剥离路径防穿越）
            String originalName = file.getOriginalFilename();
            String safeFilename = (originalName != null)
                    ? java.nio.file.Paths.get(originalName).getFileName().toString()
                    : "unknown.xlsx";
            String storedName = versionNo + "_" + System.currentTimeMillis() + "_" + safeFilename;
            Path targetPath = dirPath.resolve(storedName);

            // 将上传文件内容写入磁盘
            file.transferTo(targetPath.toFile());
            return targetPath.toString();  // 返回完整路径

        } catch (IOException e) {
            log.error("保存上传文件失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 保存核对行数据到结构化表
     *
     * 除了在 Job 主表中以 JSON 形式存储 rowsJson 外，
     * 同时将行数据展开为 HospitalReconciliationRow 实体列表并持久化。
     *
     * ===== 设计考虑 =====
     * rowsJson（主表 JSON 存储）：
     *   - 优势：行数据以一次 I/O 读写，无需 JOIN 查询
     *   - 用途：任务详情展示、前端快速展示
     *
     * HospitalReconciliationRow（明细表结构化存储）：
     *   - 优势：支持 SQL 查询聚合，可按医院、日期等维度分析
     *   - 用途：后续的数据分析报表、按医院汇总差异金额等
     *
     * 当前以 rowsJson 为主，结构化存储为辅助。
     *
     * @param jobId   关联的核对任务 ID（外键）
     * @param rowsData 行数据列表（Map 形式，直接从前端 payload 解析）
     */
    private void saveReconciliationRows(Long jobId, List<Map<String, Object>> rowsData) {
        if (rowsData == null || rowsData.isEmpty()) {
            return;
        }

        List<HospitalReconciliationRow> entities = new ArrayList<>(rowsData.size());
        for (Map<String, Object> rowData : rowsData) {
            HospitalReconciliationRow row = new HospitalReconciliationRow();
            row.setJobId(jobId);
            row.setSheetName(valueToString(rowData.get("sheetName"), ""));
            row.setRowNumber(safeGetInt(rowData, "rowNumber", 0));
            row.setDeliveryDate(valueToString(rowData.get("deliveryDate"), ""));
            row.setOrderNo(valueToString(rowData.get("orderNo"), ""));
            row.setType(valueToString(rowData.get("type"), ""));
            row.setCategoryNo(valueToString(rowData.get("categoryNo"), ""));
            row.setPackName(valueToString(rowData.get("packName"), ""));
            row.setPackageMaterial(valueToString(rowData.get("packageMaterial"), ""));
            row.setPackCount(safeGetInt(rowData, "packCount", 0));
            row.setInstrumentCount(safeGetInt(rowData, "instrumentCount", 0));
            row.setUnitPrice(safeGetDoubleObj(rowData, "unitPrice"));
            row.setTotalPrice(safeGetDoubleObj(rowData, "totalPrice"));
            row.setExpectedUnitPrice(safeGetDoubleObj(rowData, "expectedUnitPrice"));
            row.setCorrectedTotalPrice(safeGetDoubleObj(rowData, "correctedTotalPrice"));
            row.setDifference(safeGetDoubleObj(rowData, "difference"));
            row.setStatus(valueToString(rowData.get("status"), "unchanged"));
            row.setPricingRule(valueToString(rowData.get("pricingRule"), ""));
            row.setMatchedProductId(longVal(rowData, "matchedProductId", "matched_product_id"));
            row.setMatchedVariantId(longVal(rowData, "matchedVariantId", "matched_variant_id"));
            row.setPricingPath(valueToString(rowData.get("pricingPath"), null));
            row.setNotesJson(JsonUtils.toJson(rowData.get("notes")));
            row.setMatchedRuleId(longVal(rowData, "matchedRuleId", "matched_rule_id"));
            Object matchedPriceOption = rowData.get("matchedPriceOption");
            if (matchedPriceOption == null) {
                matchedPriceOption = rowData.get("matched_price_option");
            }
            row.setMatchedPriceOption(matchedPriceOption instanceof Number
                    ? ((Number) matchedPriceOption).doubleValue() : null);
            Object billingNotes = rowData.get("billingNotes");
            if (billingNotes == null) {
                billingNotes = rowData.get("billing_notes");
            }
            row.setBillingNotes(billingNotes != null ? JsonUtils.toJson(billingNotes) : null);
            Object isUrgent = rowData.get("isUrgent");
            if (isUrgent == null) {
                isUrgent = rowData.get("is_urgent");
            }
            row.setIsUrgent(parseBooleanFlag(isUrgent));
            entities.add(row);
        }

        final int BATCH_SIZE = 500;
        for (int start = 0; start < entities.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, entities.size());
            rowMapper.batchInsert(entities.subList(start, end));
        }
    }

    // ========================================================================
    //  第十五节：Logo 图片处理
    //  Section 15: Logo Image Handling
    // ========================================================================
    //
    // 从模板 xlsx 文件中提取公司 Logo 图片，附加到生成的账单 sheet 中。
    // 当前实现仅支持从模板文件提取第一个图片。
    // 如果模板没有图片或提取失败，不阻塞导出流程（静默忽略）。
    //
    // ========================================================================

    /**
     * 从模板工作簿提取第一个图片作为 Logo（附加到指定 sheet 的 K2 位置）
     *
     * 在模板操作中，克隆 sheet 后需要将 Logo 图片复制到新的 sheet。
     * 此方法从工作簿的图片池中提取第一个图片数据，添加到目标的 sheet。
     *
     * ===== 放置位置 =====
     * 图片放在 K2 单元格区域（列 K=10, 行 2=1 在 0-indexed 中），
     * 对应模板设计中右上角的公司 Logo 位置。
     *
     * ===== 容错处理 =====
     * 如果 sheet 已有图片 → 跳过（防止重复添加）
     * 如果工作簿中无图片 → 跳过
     * 如果图片处理异常 → log.debug 记录，不阻塞导出流程
     *
     * @param workbook 工作簿对象（图片池来源）
     * @param sheet    目标 sheet（Logo 要附加到的 sheet）
     */
    @SuppressWarnings("unused")
    private void attachTemplateLogo(XSSFWorkbook workbook, XSSFSheet sheet) {
        try {
            // 从工作簿的全局图片池获取所有图片数据
            List<? extends PictureData> allPictures = workbook.getAllPictures();
            if (allPictures.isEmpty()) return;

            // ===== 移除旧 drawing（模板可能已含锚点错误的图片） =====
            if (sheet.getCTWorksheet().isSetDrawing()) {
                sheet.getCTWorksheet().unsetDrawing();
                // 清除 POI 内置的 drawing 引用缓存
                try {
                    java.lang.reflect.Field dmField = XSSFSheet.class.getDeclaredField("drawingManager");
                    dmField.setAccessible(true);
                    dmField.set(sheet, null);
                } catch (Exception ignored) {
                    // 反射清空失败不影响主流程
                }
            }

            // ===== 重新创建图片，锚点固定在第 K 列 =====
            PictureData picData = allPictures.get(0);
            int pictureIdx = workbook.addPicture(picData.getData(), picData.getPictureType());

            CreationHelper helper = workbook.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(10);  // K 列（0-indexed）
            anchor.setRow1(1);   // 第 2 行（0-indexed）
            anchor.setCol2(11);  // K+1 列（logo 仅占据 K 列范围）
            anchor.setRow2(4);   // 第 5 行（0-indexed），保持原图片高度

            Picture picture = drawing.createPicture(anchor, pictureIdx);
            picture.resize(1.0);
        } catch (Exception e) {
            log.debug("附加模板 Logo 失败（可忽略）: {}", e.getMessage());
        }
    }

    /**
     * 从原始上传文件中复制 Logo 图片到程序化生成的模板工作簿。
     * 打开原文件提取所有图片数据，重新写入目标 workbook 的图片池，
     * 之后 attachTemplateLogo 即可正常放置图片。
     */
    private void copyLogoFromOriginalFile(String templateId, XSSFWorkbook targetWb) {
        try {
            Long jobId = Long.parseLong(templateId);
            HospitalReconciliationJob job = jobMapper.selectById(jobId);
            if (job == null) return;

            String sourceFilePath = job.getSourceFilePath();
            if (sourceFilePath == null || sourceFilePath.isEmpty()) return;

            File sourceFile = new File(sourceFilePath);
            if (!sourceFile.exists()) return;

            try (FileInputStream fis = new FileInputStream(sourceFile);
                 XSSFWorkbook sourceWb = new XSSFWorkbook(fis)) {
                for (PictureData picData : sourceWb.getAllPictures()) {
                    targetWb.addPicture(picData.getData(), picData.getPictureType());
                }
            }
        } catch (Exception e) {
            log.debug("从原始文件复制 Logo 失败（可忽略）: {}", e.getMessage());
        }
    }

    /**
     * 从原始上传文件中恢复各 sheet 的 B4 日期范围文本
     *
     * 当导入时未正确捕获日期文本（sourceDateRange 为空）时的回退机制。
     * 直接读取原始 Excel 文件中每个 sheet 的 B4 单元格。
     *
     * @param templateId 核对任务 ID（即 jobId）
     * @param metaMap    sheet 元数据映射（会被原地修改，填充空白的 dateRangeText）
     */
    private void recoverDateRangeFromOriginalFile(String templateId,
                                                  Map<String, BillSheetMeta> metaMap) {
        try {
            Long jobId = Long.parseLong(templateId);
            HospitalReconciliationJob job = jobMapper.selectById(jobId);
            if (job == null) return;

            String sourceFilePath = job.getSourceFilePath();
            if (sourceFilePath == null || sourceFilePath.isEmpty()) return;

            File sourceFile = new File(sourceFilePath);
            if (!sourceFile.exists()) return;

            try (FileInputStream fis = new FileInputStream(sourceFile);
                 XSSFWorkbook sourceWorkbook = new XSSFWorkbook(fis)) {

                for (int i = 0; i < sourceWorkbook.getNumberOfSheets(); i++) {
                    XSSFSheet sourceSheet = sourceWorkbook.getSheetAt(i);
                    String sheetName = sourceWorkbook.getSheetName(i);

                    BillSheetMeta meta = metaMap.get(sheetName);
                    if (meta == null) continue;

                    // 读取原文件的 B4 单元格（row 3, col 1, 0-indexed）作为日期权威来源
                    Row b4Row = sourceSheet.getRow(3);
                    String b4Text = "";
                    if (b4Row != null) {
                        Cell b4Cell = b4Row.getCell(1);
                        if (b4Cell != null) b4Text = getCellStringValue(b4Cell).trim();
                    }
                    if (!b4Text.isBlank()) {
                        meta.setDateRangeText(cleanExcelText(b4Text));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从原始文件恢复日期范围失败: {}", e.getMessage());
        }
    }

    /**
     * 确定导出时 D8 显示内容（医院计费规则）。
     *
     * 优先读取原始导入文件第9行 D 列的值，若为空则回退：planName > ruleName > hospitalName。
     */
    private void resolveD8HospitalText(String templateId,
                                        Map<String, BillSheetMeta> metaMap) {
        try {
            Long jobId = Long.parseLong(templateId);
            HospitalReconciliationJob job = jobMapper.selectById(jobId);
            if (job == null) {
                log.warn("resolveD8HospitalText: job not found, jobId={}", jobId);
                return;
            }

            String displayName = resolveD8DisplayName(job, resolveExportLayoutSettingsForJob(jobId).d8DisplaySource());

            if (displayName.isBlank()) {
                log.warn("resolveD8HospitalText: displayName is blank, no suitable name found");
                return;
            }

            log.info("resolveD8HospitalText: displayName='{}', metaCount={}", displayName, metaMap.size());

            for (BillSheetMeta meta : metaMap.values()) {
                meta.setHospitalDisplayName(displayName);
            }
        } catch (Exception e) {
            log.warn("解析 D8 文本失败: {}", e.getMessage(), e);
        }
    }

    private record ExportLayoutSettings(String billLayout, String d8DisplaySource) {}

    private ExportLayoutSettings resolveExportLayoutSettings(HospitalBillTemplateExportRequest request) {
        String billLayout = request.getBillLayout();
        String d8DisplaySource = request.getD8DisplaySource();
        if ((billLayout == null || billLayout.isBlank() || d8DisplaySource == null || d8DisplaySource.isBlank())
                && request.getTemplateId() != null && !request.getTemplateId().isBlank()) {
            try {
                Long jobId = Long.parseLong(request.getTemplateId());
                ExportLayoutSettings fromJob = resolveExportLayoutSettingsForJob(jobId);
                if (billLayout == null || billLayout.isBlank()) {
                    billLayout = fromJob.billLayout();
                }
                if (d8DisplaySource == null || d8DisplaySource.isBlank()) {
                    d8DisplaySource = fromJob.d8DisplaySource();
                }
            } catch (NumberFormatException ignored) {
                // templateId not a job id
            }
        }
        return new ExportLayoutSettings(
                billExportLayoutResolver.normalizeBillLayout(billLayout),
                billExportLayoutResolver.normalizeD8DisplaySource(d8DisplaySource));
    }

    private ExportLayoutSettings resolveExportLayoutSettingsForJob(Long jobId) {
        if (jobId == null) {
            return new ExportLayoutSettings(BillExportLayoutResolver.LAYOUT_AUTO, BillExportLayoutResolver.D8_AUTO);
        }
        HospitalReconciliationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return new ExportLayoutSettings(BillExportLayoutResolver.LAYOUT_AUTO, BillExportLayoutResolver.D8_AUTO);
        }
        Long customerId = customerResolver.resolveByName(job.getHospitalName()).map(c -> c.getId()).orElse(null);
        ResolvedExportTemplate template = exportTemplateResolver.resolve(customerId, ExportType.BILL, null);
        ColumnMappingConfig mapping = template != null ? template.getColumnMapping() : null;
        return new ExportLayoutSettings(
                billExportLayoutResolver.resolveBillLayout(mapping),
                billExportLayoutResolver.resolveD8DisplaySource(mapping));
    }

    /**
     * 解析 D8 显示名称（医院计费规则行）。
     */
    private String resolveD8DisplayName(HospitalReconciliationJob job, String d8DisplaySource) {
        String resolved = d8DisplayNameResolver.resolve(
                job, d8DisplaySource, this::readOriginalFileD8);
        log.info("resolveD8DisplayName: source='{}' → '{}'", d8DisplaySource, resolved);
        return resolved != null ? resolved : "";
    }

    /**
     * 读取原始上传 Excel 文件的第9行 D 列单元格值。
     *
     * @param sourceFilePath 原始文件路径（job.getSourceFilePath()）
     * @return D9 的字符串值，文件不存在或读取失败返回 null
     */
    private String readOriginalFileD8(String sourceFilePath) {
        if (sourceFilePath == null || sourceFilePath.isBlank()) {
            return null;
        }
        java.io.File file = new java.io.File(sourceFilePath);
        if (!file.exists()) {
            log.info("readOriginalFileD8: 原始文件不存在, path={}", sourceFilePath);
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            Row row9 = sheet.getRow(8); // 第9行 (0-indexed: 8)
            if (row9 == null) return null;
            Cell cellD9 = row9.getCell(3); // column D = index 3
            if (cellD9 == null) return null;
            String value = getCellStringValue(cellD9);
            return (value != null && !value.isBlank()) ? value.trim() : null;
        } catch (Exception e) {
            log.warn("readOriginalFileD8: 读取失败, path={}, error={}", sourceFilePath, e.getMessage());
            return null;
        }
    }

    // ========================================================================
    //  第十六节：分组与 Sheet 标题工具方法
    //  Section 16: Grouping & Sheet Title Utilities
    // ========================================================================

    /**
     * 按 sheet_name 字段分组行数据（匹配 FastAPI _group_rows_by_sheet）
     *
     * 账单数据可能来自多个科室（sheet），每个科室的数据需要写入独立的 sheet。
     * 此方法将行数据按 sheetName 字段分组，保持插入顺序（LinkedHashMap）。
     *
     * 如果行没有指定 sheetName，默认归入"标准账单"分组。
     *
     * @param rows 所有行数据列表
     * @return 分组后的 Map（key=sheetName, value=行列表），保持有序
     */
    private Map<String, List<BillRowItem>> groupRowsBySheet(List<BillRowItem> rows) {
        Map<String, List<BillRowItem>> grouped = new LinkedHashMap<>();
        if (rows == null) return grouped;
        for (BillRowItem row : rows) {
            // sheetName 为空时使用默认名称"标准账单"
            String key = row.getSheetName() != null ? row.getSheetName() : "标准账单";
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /**
     * 生成唯一的工作表标题（匹配 FastAPI _resolve_unique_sheet_title）
     *
     * Excel 工作表名称限制：
     * - 最长 31 个字符
     * - 不能包含特殊字符：\/?*[]
     * - 在同一工作簿中必须唯一
     *
     * 此方法在 sheet 名称冲突时自动添加 "_2", "_3"... 后缀，
     * 并截断基础名称以保证总长度不超过 31 字符。
     *
     * @param workbook     工作簿（用于获取现有 sheet 名称）
     * @param sheetName    期望的 sheet 名称
     * @param currentTitle 当前 sheet 的旧名称（排除在冲突检查之外）
     * @return 唯一的工作表名称（长度 ≤ 31）
     */
    private String resolveUniqueSheetTitle(XSSFWorkbook workbook, String sheetName, String currentTitle) {
        // 先对名称做安全检查（去除非法字符、截断长度）
        String baseTitle = safeSheetName(sheetName);
        // 获取工作簿中所有现有的 sheet 名称
        Set<String> existingTitles = new HashSet<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            existingTitles.add(workbook.getSheetName(i));
        }
        // 排除当前正在重命名的 sheet 的旧名称
        existingTitles.remove(currentTitle);

        // 如果没有冲突，直接使用基础名称
        if (!existingTitles.contains(baseTitle)) {
            return baseTitle;
        }

        // 有冲突时添加 "_2", "_3"... 后缀
        int suffix = 2;
        while (true) {
            String suffixText = "_" + suffix;
            int maxLen = 31 - suffixText.length();  // 基础名称最大长度（留出后缀空间）
            String candidate = (baseTitle.length() > maxLen
                    ? baseTitle.substring(0, maxLen)   // 截断名称以容纳后缀
                    : baseTitle) + suffixText;
            if (!existingTitles.contains(candidate)) {
                return candidate;  // 找到唯一名称
            }
            suffix++;
        }
    }

    // ========================================================================
    //  第十七节：字符串与数值安全处理工具方法
    //  Section 17: String & Numeric Safety Utilities
    // ========================================================================
    //
    // 这些工具方法处理 Excel 导出和 HTML 生成中的数据安全问题：
    // - 文件名净化（防止路径穿越和非法文件名）
    // - Excel 文本净化（去除 XML 非法字符）
    // - HTML 转义（防止 XSS 攻击）
    // - Sheet 名称净化（符合 Excel 命名规范）
    // - 金额格式化（统一两位小数显示）
    //
    // ========================================================================

    /**
     * 安全导出用文件名（仅保留 ASCII 字母数字 + -_.）
     *
     * 匹配 FastAPI _ascii_download_name 的行为。
     * 用于 Content-Disposition 头中的 filename 字段，
     * 确保浏览器收到的文件名不包含非 ASCII 字符（兼容旧浏览器）。
     *
     * 所有非 ASCII 字符和中文字符都被替换为下划线。
     * 首尾的下划线也会被去除。
     *
     * @param value 原始文件名（可能包含中文）
     * @return 纯 ASCII 文件名
     */
    private String asciiDownloadName(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c < 128 && (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')) {
                sb.append(c);        // 保留安全的 ASCII 字符
            } else {
                sb.append('_');      // 非 ASCII / 特殊字符替换为下划线
            }
        }
        // 去除首尾下划线
        String result = sb.toString().replaceAll("^_+|_+$", "");
        return result.isEmpty() ? "hospital-template.xlsx" : result;
    }

    /**
     * 安全名称（仅保留字母数字和 -_）
     *
     * 匹配 FastAPI _safe_name 的行为。
     * 比 asciiDownloadName 更严格，只保留字母数字和 -_，
     * 不保留点号（因为此方法用于医院名称的文件目录名）。
     *
     * @param value 原始名称
     * @return 净化后的名称
     */
    private String safeName(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String result = sb.toString().replaceAll("^_+|_+$", "");
        return result.isEmpty() ? "hospital" : result;
    }

    @Override
    public ResponseEntity<byte[]> buildExcelDownloadResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(filename))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    /**
     * 构建 HTTP Content-Disposition 响应头
     *
     * 同时支持两种文件名编码方式：
     * 1. filename="ascii_name"：兼容旧的 HTTP 客户端（仅 ASCII）
     * 2. filename*=UTF-8''encoded_name：RFC 5987 标准编码，支持中文
     *
     * 匹配 FastAPI 的 Content-Disposition 构建行为。
     *
     * @param filename 原始文件名（可能包含中文）
     * @return 完整的 Content-Disposition 头值
     */
    private String buildContentDisposition(String filename) {
        String asciiName = asciiDownloadName(filename);                        // 降级 ASCII 名称
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");                                          // RFC 5987 编码
        return "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encodedName;
    }

    /**
     * 安全的 Excel sheet 名称
     *
     * Excel 对工作表名称的限制：
     * - 最大长度 31 个字符
     * - 不能包含字符：\ / ? * [ ] :
     * - 不能为空
     *
     * 匹配 FastAPI _safe_sheet_name 的行为。
     *
     * @param value 原始 sheet 名称
     * @return 符合 Excel 命名规范的 sheet 名称
     */
    private String safeSheetName(String value) {
        if (value == null) return "标准账单";                                // null 走默认
        String sanitized = value.replaceAll("[\\\\/?*\\[\\]:]", "_").trim(); // 替换非法字符
        if (sanitized.isEmpty()) return "标准账单";                          // 空字符串走默认
        if (sanitized.length() > 31) sanitized = sanitized.substring(0, 31); // 截断到 31 字符
        return sanitized;
    }

    /**
     * 将科学计数法或带 .0 后缀的字符串还原为整数字符串。
     * 用于兼容旧数据中因 String.valueOf(double) 导致的格式问题。
     */
    private String formatIntegerString(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.isEmpty()) return s;
        // 检测科学计数法 (如 2.0365495E7) 或 .0 后缀 (如 1591007.0)
        if (s.contains("E") || s.contains("e") || (s.contains(".") && !s.matches(".*\\d{4}[-/]\\d{2}[-/]\\d{2}.*"))) {
            try {
                double d = Double.parseDouble(s);
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.valueOf((long) d);
                }
            } catch (NumberFormatException ignored) {}
        }
        return s;
    }

    private String cleanExcelText(String value) {
        if (value == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            // 只保留允许的字符，非法字符替换为空格
            if ((c >= 0x09 && c <= 0x0A) || c == 0x0D || (c >= 0x20 && c <= 0xFFFF) || c == 0x0C) {
                sb.append(c);
            } else {
                sb.append(' ');  // 非法控制字符替换为空格
            }
        }
        String result = sb.toString();
        // 截断到 Excel 允许的最大长度
        return result.length() > 32767 ? result.substring(0, 32767) : result;
    }

    /**
     * 清理 Excel 数值（过滤无穷大和 NaN）
     *
     * Excel 不支持 Infinity 和 NaN 数值，写入会导致打开报错。
     * 此方法在这类无效数值出现时返回 null（对应 Excel 空单元格）。
     *
     * 匹配 FastAPI _clean_excel_number 的行为。
     *
     * @param value 原始数值
     * @return 清理后的数值，无效值返回 null
     */
    private Double cleanExcelNumber(Double value) {
        if (value == null) return null;
        if (Double.isInfinite(value) || Double.isNaN(value)) return null;  // 过滤无效数值
        return value;
    }

    /**
     * HTML 文本转义（防止 XSS 攻击）
     *
     * 将所有 HTML 特殊字符转义为实体：
     * & → &amp;   < → &lt;   > → &gt;
     * " → &quot;  ' → &#39;
     *
     * 匹配 FastAPI _html_text 的行为。
     * 在生成账单和结款函 HTML 时，所有用户输入的数据必须经过此方法处理，
     * 防止恶意脚本注入。
     *
     * @param value 原始值（可以是任意类型，会调用 toString）
     * @return HTML 转义后的安全字符串
     */
    private String htmlText(Object value) {
        if (value == null) return "";                                          // null 返回空字符串
        String s = String.valueOf(value);
        // 注意转义顺序：& 必须最先处理，否则 < 会被错误转义为 &lt;
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 金额格式化（两位小数，匹配 FastAPI _format_money）
     *
     * 格式化规则：
     * - null → "-"（占位符）
     * - 有效数值 → "1,234.56" 格式（两位小数）
     *
     * 注意：此处使用 String.format("%.2f", value)，
     * 不使用千分位分隔符（取决于具体实现）。
     *
     * @param value 金额数值
     * @return 格式化后的金额字符串
     */
    private String formatMoney(Double value) {
        if (value == null) return "-";                                          // 空值显示横线
        return String.format("%.2f", value);                                   // 保留两位小数
    }

    // ========================================================================
    //  第十八节：类型安全的 JSON 数据提取方法
    //  Section 18: Type-safe JSON Data Extraction
    // ========================================================================
    //
    // 这些方法用于从前端提交的 JSON payload Map 中安全地提取各种类型的数据。
    // 前端提交的 JSON 经过 Jackson 解析后，数值可能以 Integer/Long/Double
    // 等不同形态出现，这些方法统一处理类型转换，避免 ClassCastException。
    //
    // ========================================================================

    private void enrichProductMatch(Map<String, Object> row) {
        com.hospital.backend.dto.request.product.MatchPreviewRequest request =
                new com.hospital.backend.dto.request.product.MatchPreviewRequest();
        request.setType(valueToString(row.get("type"), ""));
        request.setPackName(valueToString(row.get("packName"), ""));
        request.setPackageMaterial(valueToString(row.get("packageMaterial"), ""));
        request.setCategoryNo(valueToString(row.get("categoryNo"), ""));
        request.setInstrumentCount(safeGetInt(row, "instrumentCount", 0));

        productMatchService.matchRow(request).ifPresent(match -> {
            row.put("matchedProductId", match.getProductId());
            row.put("matchedVariantId", match.getVariantId());
            row.put("pricingPath", match.getPricingPath());
            row.put("specFingerprint", match.getSpecFingerprint());
            if (valueToString(row.get("packageMaterial"), "").isBlank()
                    && match.getPackageMaterial() != null && !match.getPackageMaterial().isBlank()) {
                row.put("packageMaterial", match.getPackageMaterial());
            }
            if (safeGetInt(row, "instrumentCount", 0) <= 0
                    && match.getInstrumentCountHint() != null && match.getInstrumentCountHint() > 0) {
                row.put("instrumentCount", match.getInstrumentCountHint());
            }
        });
    }

    private Long longVal(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String str && !str.isBlank()) {
                try {
                    return Long.parseLong(str);
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    /**
     * 安全地将 Object 转为 String（带默认值）
     *
     * @param value        原始值
     * @param defaultValue 值为 null 时的默认值
     * @return 字符串值
     */
    private boolean parseBooleanFlag(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String str) {
            return "true".equalsIgnoreCase(str) || "1".equals(str);
        }
        return false;
    }

    private String valueToString(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        return String.valueOf(value);
    }

    /**
     * 安全地从 Map 中提取 int 值
     *
     * 处理以下情况：
     * - value 是 Number 类型（Integer/Long/Double）→ 转为 int
     * - value 是 String 类型 → 尝试解析为 int，失败返回默认值
     * - value 是其他类型或 null → 返回默认值
     *
     * @param map          JSON 数据 Map
     * @param key          字段名
     * @param defaultValue 解析失败时的默认值
     * @return 整数值
     */
    private int safeGetInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    /**
     * 安全地从 Map 中提取 double 值（基本类型）
     *
     * @param map          JSON 数据 Map
     * @param key          字段名
     * @param defaultValue 解析失败时的默认值
     * @return double 值
     */
    private double safeGetDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    /**
     * 安全地从 Map 中提取 Double 值（包装类型，可为 null）
     *
     * 与 safeGetDouble 的区别：返回值可以是 null，
     * 适用于数据库字段允许为空的场景（如 unitPrice）。
     *
     * @param map JSON 数据 Map
     * @param key 字段名
     * @return Double 值或 null
     */
    private Double safeGetDoubleObj(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /**
     * 安全地从 Map 中提取嵌套 Map
     *
     * 用于提取 summary 等嵌套对象。
     * 如果字段不存在或类型不匹配，返回空 Map 避免 NullPointerException。
     *
     * @param map 父级 Map
     * @param key 字段名（如 "summary"）
     * @return Map<String, Object>，不会返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> safeGetMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) return (Map<String, Object>) value;
        return new HashMap<>();
    }

    /**
     * 安全地从 Map 中提取 List<Map>
     *
     * 用于提取 rows 等数组字段。
     * 如果字段不存在或类型不匹配，返回空 List 避免 NullPointerException。
     *
     * @param map 父级 Map
     * @param key 字段名（如 "rows"）
     * @return List<Map<String, Object>>，不会返回 null
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeGetList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) return (List<Map<String, Object>>) value;
        return new ArrayList<>();
    }

    /**
     * 清理文件名字符（去除操作系统不允许的字符）
     *
     * Windows 不允许的字符：\ / : * ? " < > |
     * 这些字符被替换为下划线。
     *
     * @param name 原始文件名/目录名
     * @return 安全的文件名字符串
     */
    private String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("\\.{2,}", "_")
                   .trim();
    }

    private boolean isValidExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;
        String filename = file.getOriginalFilename();
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }
}
