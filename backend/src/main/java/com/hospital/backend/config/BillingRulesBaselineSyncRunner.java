package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.SysSetting;
import com.hospital.backend.mapper.SysSettingMapper;
import com.hospital.backend.service.BaselineRuleSyncService;
import com.hospital.backend.service.RulesVerificationService;
import com.hospital.backend.service.SystemVersionInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * 每次启动：classpath baseline_hash 与 DB 比对，变化则全量 import + verify。
 * 替代一次性 {@link BillingRulesBaselineBootstrapRunner}。
 */
@Slf4j
@Component
@Order(117)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "billing.baseline.sync-enabled", havingValue = "true", matchIfMissing = true)
public class BillingRulesBaselineSyncRunner implements CommandLineRunner {

    private static final String MANIFEST_FILE = "billing-seeds/billing-rules-manifest.json";

    private final SysSettingMapper sysSettingMapper;
    private final BaselineRuleIndex baselineRuleIndex;
    private final BaselineRuleSyncService baselineRuleSyncService;
    private final RulesVerificationService rulesVerificationService;
    private final BillingRulesBaselineSyncHealth syncHealth;

    @Value("${billing.baseline.fail-on-verify-error:false}")
    private boolean failOnVerifyError;

    @Override
    public void run(String... args) {
        String classpathHash = baselineRuleIndex.baselineHash();
        if (classpathHash == null || classpathHash.isBlank()) {
            log.warn("billing-rules/index.json 缺少 baseline_hash，跳过 baseline sync");
            return;
        }

        String dbHash = readSetting(SystemVersionInfoService.BASELINE_HASH_KEY);
        boolean hashChanged = !classpathHash.equals(dbHash);
        boolean imported = false;

        if (hashChanged) {
            log.info("Baseline hash 变化（db={} classpath={}），开始全量 sync",
                    shortHash(dbHash), shortHash(classpathHash));
            imported = importBaselines(classpathHash);
            if (!imported) {
                return;
            }
        } else {
            log.debug("Baseline hash 未变，跳过 import");
        }

        Map<String, Object> verify = rulesVerificationService.verifyAll();
        if (!Boolean.TRUE.equals(verify.get("ok")) && !imported) {
            log.warn("Baseline hash 未变但 verify 失败（{}），强制全量 re-import 自愈", verify);
            imported = importBaselines(classpathHash);
            if (imported) {
                verify = rulesVerificationService.verifyAll();
            }
        }
        if (!Boolean.TRUE.equals(verify.get("ok"))) {
            upsertSetting(SystemVersionInfoService.MANIFEST_RECONCILE_STATUS_KEY,
                    "FAILED verify " + Instant.now(),
                    "Last billing rules baseline sync status");
            syncHealth.markUnhealthy(verify);
            log.error("Billing baseline verify 失败: {}", verify);
            if (failOnVerifyError) {
                throw new IllegalStateException("Billing baseline verify failed: " + verify);
            }
            return;
        }

        upsertSetting(SystemVersionInfoService.MANIFEST_RECONCILE_STATUS_KEY,
                "OK baseline-sync " + Instant.now(),
                "Last billing rules baseline sync status");
        upsertSetting(SystemVersionInfoService.MANIFEST_RECONCILED_AT_KEY, Instant.now().toString(),
                "Last billing rules baseline sync / verify time");
        syncHealth.markHealthy();
        if (imported) {
            log.info("Baseline verify OK（hash={}）", shortHash(classpathHash));
        } else {
            log.debug("Baseline verify OK（hash 未变）");
        }
    }

    private boolean importBaselines(String classpathHash) {
        try {
            int imported = baselineRuleSyncService.importAllBaselines(false);
            upsertSetting(SystemVersionInfoService.BASELINE_HASH_KEY, classpathHash,
                    "Classpath billing-rules/index.json baseline_hash");
            updateManifestMarkersFromClasspath();
            log.info("Baseline sync 完成：导入/更新 {} 条规则", imported);
            return true;
        } catch (Exception e) {
            log.error("Baseline sync 失败: {}", e.getMessage(), e);
            try {
                upsertSetting(SystemVersionInfoService.MANIFEST_RECONCILE_STATUS_KEY,
                        truncateSettingValue("FAILED baseline-sync " + Instant.now() + " " + e.getMessage()),
                        "Last billing rules baseline sync status");
            } catch (Exception persistEx) {
                log.warn("无法写入 baseline sync 失败状态: {}", persistEx.getMessage());
            }
            syncHealth.markUnhealthy(Map.of("error", e.getMessage()));
            if (failOnVerifyError) {
                throw new IllegalStateException("Billing baseline sync failed", e);
            }
            return false;
        }
    }

    private void updateManifestMarkersFromClasspath() {
        try {
            ClassPathResource resource = new ClassPathResource(MANIFEST_FILE);
            if (!resource.exists()) {
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            if (root.hasNonNull("manifest_hash")) {
                upsertSetting(SystemVersionInfoService.MANIFEST_HASH_KEY,
                        root.get("manifest_hash").asText(),
                        "SHA256 of billing-rules-manifest.json customers payload");
            }
            if (root.hasNonNull("generated_at")) {
                upsertSetting(SystemVersionInfoService.MANIFEST_GENERATED_AT_KEY,
                        root.get("generated_at").asText(),
                        "billing-rules-manifest.json generated_at");
            }
        } catch (Exception e) {
            log.warn("无法从 classpath manifest 更新 marker: {}", e.getMessage());
        }
    }

    private String readSetting(String key) {
        SysSetting row = sysSettingMapper.selectByKey(key);
        return row == null ? null : row.getSettingValue();
    }

    private void upsertSetting(String key, String value, String description) {
        SysSetting existing = sysSettingMapper.selectByKey(key);
        if (existing == null) {
            SysSetting row = new SysSetting();
            row.setSettingKey(key);
            row.setSettingValue(value);
            row.setDescription(description);
            sysSettingMapper.insert(row);
        } else {
            existing.setSettingValue(value);
            if (description != null && !description.isBlank()) {
                existing.setDescription(description);
            }
            sysSettingMapper.updateByKey(existing);
        }
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "(none)";
        }
        return hash.length() <= 12 ? hash : hash.substring(0, 12) + "…";
    }

    /** sys_setting.setting_value 在部分环境为 VARCHAR，避免写入过长状态文本导致启动失败。 */
    private static String truncateSettingValue(String value) {
        if (value == null) {
            return "";
        }
        int maxLen = 480;
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "…";
    }
}
