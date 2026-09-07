package com.hospital.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.config.BaselineRuleIndex;
import com.hospital.backend.service.impl.RulesVerificationServiceImpl;
import com.hospital.backend.entity.SysSetting;
import com.hospital.backend.mapper.SysSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统版本与计价规则版本信息（供 UI 左下角展示、生产环境快速对版）。
 *
 * <p>gitSha / buildTime 由 Docker 构建参数 APP_GIT_SHA / APP_BUILD_TIME 注入；
 * 规则 hash 来自 classpath billing-rules/index.json baseline_hash。
 */
@Service
@RequiredArgsConstructor
public class SystemVersionInfoService {

    public static final String MANIFEST_HASH_KEY = "billing_rules_manifest_hash";
    public static final String BASELINE_HASH_KEY = "billing_rules_baseline_hash";
    public static final String MANIFEST_GENERATED_AT_KEY = "billing_rules_manifest_generated_at";
    public static final String MANIFEST_RECONCILED_AT_KEY = "billing_rules_manifest_reconciled_at";
    public static final String MANIFEST_RECONCILE_STATUS_KEY = "billing_rules_manifest_reconcile_status";
    public static final String QUARANTINE_COUNT_KEY = "billing_rules_quarantine_count";

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(SHANGHAI);

    private final SysSettingMapper sysSettingMapper;
    private final BaselineRuleIndex baselineRuleIndex;

    @Value("${APP_GIT_SHA:local}")
    private String gitSha;

    @Value("${APP_BUILD_TIME:}")
    private String buildTime;

    public Map<String, Object> current() {
        String sha = normalizeSha(gitSha);
        String builtAt = normalizeBuildTime(buildTime);
        String rulesHash = setting(MANIFEST_HASH_KEY);
        String baselineHash = baselineRuleIndex.baselineHash();
        if (isBlank(baselineHash)) {
            baselineHash = setting(BASELINE_HASH_KEY);
        }
        String lastVerify = setting(RulesVerificationServiceImpl.LAST_VERIFY_KEY);
        String rulesGeneratedAt = setting(MANIFEST_GENERATED_AT_KEY);
        String rulesReconciledAt = setting(MANIFEST_RECONCILED_AT_KEY);
        String rulesReconcileStatus = setting(MANIFEST_RECONCILE_STATUS_KEY);

        // 启动后首次部署若 DB 尚未写入 generated_at，回退读 classpath manifest
        if (isBlank(rulesGeneratedAt) || isBlank(rulesHash)) {
            ManifestMeta meta = readClasspathManifest();
            if (isBlank(rulesHash) && meta.hash != null) {
                rulesHash = meta.hash;
            }
            if (isBlank(rulesGeneratedAt) && meta.generatedAt != null) {
                rulesGeneratedAt = meta.generatedAt;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gitSha", sha);
        payload.put("gitShaShort", shortSha(sha));
        payload.put("buildTime", builtAt);
        payload.put("buildTimeDisplay", displayTime(builtAt));
        payload.put("rulesManifestHash", rulesHash == null ? "" : rulesHash);
        payload.put("rulesManifestHashShort", shortSha(rulesHash == null ? "" : rulesHash));
        payload.put("rulesBaselineHash", baselineHash == null ? "" : baselineHash);
        payload.put("rulesBaselineHashShort", shortSha(baselineHash == null ? "" : baselineHash));
        payload.put("rulesVerifyStatus", parseVerifyOk(lastVerify));
        payload.put("rulesGeneratedAt", rulesGeneratedAt == null ? "" : rulesGeneratedAt);
        payload.put("rulesGeneratedAtDisplay", displayTime(rulesGeneratedAt));
        payload.put("rulesReconciledAt", rulesReconciledAt == null ? "" : rulesReconciledAt);
        payload.put("rulesReconciledAtDisplay", displayTime(rulesReconciledAt));
        payload.put("rulesReconcileStatus", rulesReconcileStatus == null ? "" : rulesReconcileStatus);
        payload.put("rulesReconcileOk", rulesReconcileStatus != null && rulesReconcileStatus.startsWith("OK"));
        String quarantineCount = setting(QUARANTINE_COUNT_KEY);
        long quarantineCountValue = 0L;
        if (quarantineCount != null && !quarantineCount.isBlank()) {
            try {
                quarantineCountValue = Long.parseLong(quarantineCount.trim());
            } catch (NumberFormatException ignored) {
                quarantineCountValue = 0L;
            }
        }
        payload.put("rulesQuarantineCount", quarantineCountValue);
        payload.put("version", shortSha(sha));
        payload.put("app_title", "Hospital Backend");
        payload.put("project_name", "hospital-backend");
        return payload;
    }

    private String setting(String key) {
        SysSetting row = sysSettingMapper.selectByKey(key);
        return row == null ? null : row.getSettingValue();
    }

    private static String normalizeSha(String raw) {
        if (isBlank(raw) || "unknown".equalsIgnoreCase(raw.trim())) {
            return "local";
        }
        return raw.trim();
    }

    private static String normalizeBuildTime(String raw) {
        if (isBlank(raw) || "unknown".equalsIgnoreCase(raw.trim())) {
            return Instant.now().toString();
        }
        return raw.trim();
    }

    private static String shortSha(String sha) {
        if (isBlank(sha)) {
            return "";
        }
        return sha.length() <= 8 ? sha : sha.substring(0, 8);
    }

    private static String displayTime(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        try {
            Instant instant = Instant.parse(raw);
            return DISPLAY.format(instant);
        } catch (Exception ignored) {
            // 非 ISO 瞬时时间则原样截断展示
            return raw.length() > 16 ? raw.substring(0, 16).replace('T', ' ') : raw;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean parseVerifyOk(String json) {
        if (isBlank(json)) {
            return true;
        }
        try {
            JsonNode node = JsonUtils.getObjectMapper().readTree(json);
            return !node.has("ok") || node.get("ok").asBoolean(true);
        } catch (Exception e) {
            return true;
        }
    }

    private static ManifestMeta readClasspathManifest() {
        try {
            ClassPathResource resource = new ClassPathResource("billing-seeds/billing-rules-manifest.json");
            if (!resource.exists()) {
                return ManifestMeta.empty();
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            String hash = root.hasNonNull("manifest_hash") ? root.get("manifest_hash").asText() : null;
            String generatedAt = root.hasNonNull("generated_at") ? root.get("generated_at").asText() : null;
            return new ManifestMeta(hash, generatedAt);
        } catch (Exception ignored) {
            return ManifestMeta.empty();
        }
    }

    private record ManifestMeta(String hash, String generatedAt) {
        static ManifestMeta empty() {
            return new ManifestMeta(null, null);
        }
    }
}
