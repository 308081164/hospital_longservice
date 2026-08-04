package com.hospital.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.backend.common.JsonUtils;
import com.hospital.backend.entity.*;
import com.hospital.backend.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 幂等加载 billing-seeds/*.json 客户策略/规则/客户组配置。
 */
@Slf4j
@Component
@Order(115)
@RequiredArgsConstructor
public class BillingSeedMigrationRunner implements CommandLineRunner {

    private static final String MARKER = "billing_seed_profiles_v1";
    private static final List<String> SEED_FILES = List.of(
            "billing-seeds/phase1-batch-a-extra.json",
            "billing-seeds/phase2-policies.json",
            "billing-seeds/phase5-batch-c.json",
            "billing-seeds/phase7-batch-d.json",
            "billing-seeds/phase7-batch-e.json",
            "billing-seeds/phase-missing-bokang-ref.json"
    );
    /** 可在已有库上增量导入的单院种子（每项独立 marker，backend 重启时幂等执行一次） */
    private static final List<IncrementalSeed> INCREMENTAL_SEEDS = List.of(
            new IncrementalSeed("billing_seed_zyy_d1_v1", "billing-seeds/phase-zyy-d1-fuyi.json"),
            new IncrementalSeed("billing_seed_batch_p0_v1", "billing-seeds/phase-batch-p0.json"),
            new IncrementalSeed("billing_seed_batch_p0_1_v1", "billing-seeds/phase-batch-p0.1.json"),
            new IncrementalSeed("billing_seed_batch_p0_2_v1", "billing-seeds/phase-batch-p0.2.json"),
            new IncrementalSeed("billing_seed_batch_p0_3_v1", "billing-seeds/phase-batch-p0.3.json"),
            new IncrementalSeed("billing_seed_batch_p0_4_v1", "billing-seeds/phase-batch-p0.4.json"),
            new IncrementalSeed("billing_seed_batch_p0_5_v1", "billing-seeds/phase-batch-p0.5.json"),
            new IncrementalSeed("billing_seed_batch_p0_5_1_v1", "billing-seeds/phase-batch-p0.5.1.json"),
            new IncrementalSeed("billing_seed_batch_p0_5_2_v1", "billing-seeds/phase-batch-p0.5.2.json"),
            new IncrementalSeed("billing_seed_batch_p0_6_v1", "billing-seeds/phase-batch-p0.6.json"),
            new IncrementalSeed("billing_seed_zyy_d1_p0_2_v1", "billing-seeds/phase-zyy-d1-p0-2-price-align.json"),
            new IncrementalSeed("billing_seed_bokang_20260722_v1", "billing-seeds/phase-bokang-20260722-hit-heu-clarify.json"),
            new IncrementalSeed("billing_seed_s3_pdf_20260722_v1", "billing-seeds/phase-s3-pdf-align-20260722.json"),
            new IncrementalSeed("billing_seed_s4_fix_20260722_v1", "billing-seeds/phase-s4-fix-20260722.json"),
            new IncrementalSeed("billing_seed_s4_fix_part2_20260722_v1", "billing-seeds/phase-s4-fix-part2-20260722.json"),
            new IncrementalSeed("billing_seed_s4_fix_part3_20260722_v1", "billing-seeds/phase-s4-fix-part3-20260722.json"),
            new IncrementalSeed("billing_seed_s4_fix_part4_20260722_v1", "billing-seeds/phase-s4-fix-part4-20260722.json"),
            new IncrementalSeed("billing_seed_s4_fix_part5_20260722_v1", "billing-seeds/phase-s4-fix-part5-20260722.json"),
            new IncrementalSeed("billing_seed_s4_close_20260722_v1", "billing-seeds/phase-s4-close-20260722.json"),
            new IncrementalSeed("billing_seed_hulan_heu_hit_20260722_v2", "billing-seeds/phase-hulan-heu-hit-20260722.json"),
            new IncrementalSeed("billing_seed_user_20260722_v1", "billing-seeds/phase-user-20260722-er-hulan.json"),
            new IncrementalSeed("billing_seed_zyy_d1_standard_pricing_20260723_v1",
                    "billing-seeds/phase-zyy-d1-standard-pricing-20260723.json"),
            new IncrementalSeed("billing_seed_hrb_hx_eye_20260723_v1",
                    "billing-seeds/phase-hrb-hx-eye-20260723.json"),
            new IncrementalSeed("billing_seed_ng_fuchan_gongqiangjing_20260723_v1",
                    "billing-seeds/phase-ng-fuchan-gongqiangjing-20260723.json"),
            new IncrementalSeed("billing_seed_ng_fuchan_fixed_price_20260723_v2",
                    "billing-seeds/phase-ng-fuchan-gongqiangjing-20260723.json"),
            new IncrementalSeed("billing_seed_ng_fuchan_pdf_ocr_20260723_v1",
                    "billing-seeds/phase-ng-fuchan-pdf-ocr-20260723.json"),
            new IncrementalSeed("billing_seed_ng_fuchan_kuobang_wanpan_20260723_v1",
                    "billing-seeds/phase-ng-fuchan-kuobang-wanpan-20260723.json"),
            new IncrementalSeed("billing_seed_hrb_bc_med_beauty_20260723_v1",
                    "billing-seeds/phase-hrb-bc-med-beauty-20260723.json"),
            new IncrementalSeed("billing_seed_s7_bokang_pdf_ocr_20260723_v1",
                    "billing-seeds/phase-s7-bokang-pdf-ocr-20260723.json"),
            new IncrementalSeed("billing_seed_s7_daowai_wailai_keywords_20260723_v1",
                    "billing-seeds/phase-s7-daowai-wailai-keywords-20260723.json"),
            new IncrementalSeed("billing_seed_s7_sanjing_hulan_wailai_keywords_20260723_v1",
                    "billing-seeds/phase-s7-sanjing-hulan-wailai-keywords-20260723.json"),
            new IncrementalSeed("billing_seed_hrb_cj_standard_billing_20260723_v1",
                    "billing-seeds/phase-hrb-cj-standard-billing-20260723.json"),
            new IncrementalSeed("billing_seed_hrb_mhm_xizhizhen_20260723_v1",
                    "billing-seeds/phase-hrb-mhm-xizhizhen-20260723.json"),
            new IncrementalSeed("billing_seed_hrb_sd_neau_kouqiang_fold_20260723_v1",
                    "billing-seeds/phase-hrb-sd-neau-kouqiang-fold-20260723.json"),
            new IncrementalSeed("billing_seed_export_rules_20260723_v1",
                    "billing-seeds/phase-export-rules-20260723.json"),
            new IncrementalSeed("billing_seed_daowai_path_override_20260723_v1",
                    "billing-seeds/phase-daowai-path-override-20260723.json"),
            new IncrementalSeed("billing_seed_hlfb_sf_chezhen_20260724_v1",
                    "billing-seeds/phase-hlfb-sf-chezhen-20260724.json"),
            new IncrementalSeed("billing_seed_hrb_bc_med_beauty_fix_20260724_v1",
                    "billing-seeds/phase-hrb-bc-med-beauty-fix-20260724.json"),
            new IncrementalSeed("billing_seed_hrb_bc_med_beauty_fix_20260724_v2",
                    "billing-seeds/phase-hrb-bc-med-beauty-fix-20260724-v2.json"),
            new IncrementalSeed("billing_seed_hrb_hx_eye_fix_20260724_v1",
                    "billing-seeds/phase-hrb-hx-eye-fix-20260724.json"),
            new IncrementalSeed("billing_seed_hrb_hx_eye_fix_20260724_v2",
                    "billing-seeds/phase-hrb-hx-eye-fix-20260724-v2.json"),
            new IncrementalSeed("billing_seed_ng_fuchan_renliubao_fix_20260724_v1",
                    "billing-seeds/phase-ng-fuchan-renliubao-fix-20260724.json"),
            new IncrementalSeed("billing_seed_hrb_cj_fix_20260724_v1",
                    "billing-seeds/phase-hrb-cj-fix-20260724.json"),
            new IncrementalSeed("billing_seed_hrb_cj_surgical_pack_fix_20260724_v1",
                    "billing-seeds/phase-hrb-cj-surgical-pack-fix-20260724.json"),
            new IncrementalSeed("billing_seed_wcsrm_yy_or_pricing_20260724_v1",
                    "billing-seeds/phase-wcsrm-yy-or-pricing-20260724.json"),
            new IncrementalSeed("billing_seed_wcsrm_yy_extra_bag_fix_20260724_v1",
                    "billing-seeds/phase-wcsrm-yy-extra-bag-fix-20260724.json"),
            new IncrementalSeed("billing_seed_wcsrm_yy_pack_price_fix_20260724_v1",
                    "billing-seeds/phase-wcsrm-yy-pack-price-fix-20260724.json"),
            new IncrementalSeed("billing_seed_wcsrm_yy_or_conditions_fix_20260724_v2",
                    "billing-seeds/phase-wcsrm-yy-or-conditions-fix-20260724-v2.json"),
            new IncrementalSeed("billing_seed_wcsrm_yy_lap_per_piece_revert_20260724_v1",
                    "billing-seeds/phase-wcsrm-yy-lap-per-piece-revert-20260724.json"),
            new IncrementalSeed("billing_seed_wcsrm_yy_or_consolidate_20260724_v1",
                    "billing-seeds/phase-wcsrm-yy-or-consolidate-20260724.json"),
            new IncrementalSeed("billing_seed_hrb_2nd_fix_20260724_v1",
                    "billing-seeds/phase-hrb-2nd-fix-20260724.json"),
            new IncrementalSeed("billing_seed_hrb_2nd_fix_20260724_v2",
                    "billing-seeds/phase-hrb-2nd-fix-20260724.json"),
            new IncrementalSeed("billing_seed_hrb_sd_neau_kouqiang_fold_fix_20260724_v2",
                    "billing-seeds/phase-hrb-sd-neau-kouqiang-fold-fix-20260724-v2.json"),
            new IncrementalSeed("billing_seed_hrb_sh_pricing_20260724_v1",
                    "billing-seeds/phase-hrb-sh-pricing-20260724.json"),
            new IncrementalSeed("billing_seed_hrb_ngjy_fix_20260724_v1",
                    "billing-seeds/phase-hrb-ngjy-fix-20260724.json"),
            new IncrementalSeed("billing_seed_zuyan_ng_export_pricing_20260724_v1",
                    "billing-seeds/phase-zuyan-ng-export-pricing-20260724.json"),
            new IncrementalSeed("billing_seed_sanjing_neilou_instrument_count_fix_20260727_v1",
                    "billing-seeds/phase-sanjing-neilou-instrument-count-fix-20260727.json"),
            new IncrementalSeed("billing_seed_sheng_yy_xf_dept_pricing_20260727_v1",
                    "billing-seeds/phase-sheng-yy-xf-dept-pricing-20260727.json"),
            new IncrementalSeed("billing_seed_sheng_yy_xf_shenwai_goudao_20260728_v1",
                    "billing-seeds/phase-sheng-yy-xf-shenwai-goudao-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_policies_20260724_v1",
                    "billing-seeds/phase-settlement-policies-20260724.json"),
            new IncrementalSeed("billing_seed_settlement_policies_20260725_v1",
                    "billing-seeds/phase-settlement-policies-20260725.json"),
            new IncrementalSeed("billing_seed_settlement_logistics_20260725_v1",
                    "billing-seeds/phase-settlement-logistics-20260725.json"),
            new IncrementalSeed("billing_seed_settlement_p0_20260728_v1",
                    "billing-seeds/phase-settlement-p0-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_logistics_batch_20260728_v1",
                    "billing-seeds/phase-settlement-logistics-batch-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_logistics_batch_20260728_v2",
                    "billing-seeds/phase-settlement-logistics-batch-20260728.json"),
            new IncrementalSeed("billing_seed_bill_s8_fix_20260728_v1",
                    "billing-seeds/phase-bill-s8-fix-20260728.json"),
            new IncrementalSeed("billing_seed_export_dept_split_20260728_v1",
                    "billing-seeds/phase-export-dept-split-20260728.json"),
            new IncrementalSeed("billing_seed_ng_fuchan_kuobang_bundle_24_fix_20260728_v1",
                    "billing-seeds/phase-ng-fuchan-kuobang-bundle-24-fix-20260728.json"),
            new IncrementalSeed("billing_seed_ng_fuchan_kuobang_bundle_24_fix_20260728_v2",
                    "billing-seeds/phase-ng-fuchan-kuobang-bundle-24-fix-20260728-v2.json"),
            new IncrementalSeed("billing_seed_zyy_d1_fold_ganlan_chongxi_fix_20260728_v1",
                    "billing-seeds/phase-zyy-d1-fold-ganlan-chongxi-fix-20260728.json"),
            new IncrementalSeed("billing_seed_zyy_d1_july_export_parity_20260804_v1",
                    "billing-seeds/phase-zyy-d1-july-export-parity-20260804.json"),
            new IncrementalSeed("billing_seed_zyy_d1_july_export_parity_v2_20260804_v1",
                    "billing-seeds/phase-zyy-d1-july-export-parity-v2-20260804.json"),
            new IncrementalSeed("billing_seed_zyy_d1_lap_3038_priority_20260804_v1",
                    "billing-seeds/phase-zyy-d1-lap-3038-priority-20260804.json"),
            new IncrementalSeed("billing_seed_zyy_d1_july_parity_reapply_20260804_v1",
                    "billing-seeds/phase-zyy-d1-july-parity-reapply-20260804.json"),
            new IncrementalSeed("billing_seed_zyy_d2_ng_special_pricing_fix_20260728_v1",
                    "billing-seeds/phase-zyy-d2-ng-special-pricing-fix-20260728.json"),
            new IncrementalSeed("billing_seed_zyy_d2_ng_guasha_tanzhen_per_piece_20260728_v1",
                    "billing-seeds/phase-zyy-d2-ng-guasha-tanzhen-per-piece-20260728.json"),
            new IncrementalSeed("billing_seed_wj_ngjy_sd_neau_zero_fold_20260728_v1",
                    "billing-seeds/phase-wj-ngjy-sd-neau-zero-fold-fix-20260728.json"),
            new IncrementalSeed("billing_seed_hlj_jyglj_weike_jiaqian_20260728_v1",
                    "billing-seeds/phase-hlj-jyglj-weike-jiaqian-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_logistics_batch_20260728_v3",
                    "billing-seeds/phase-settlement-logistics-batch-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_xinfa_20260728_v1",
                    "billing-seeds/phase-settlement-xinfa-20260728.json"),
            new IncrementalSeed("billing_seed_zyy_d2_ng_dayi_xiaodan_20260728_v1",
                    "billing-seeds/phase-zyy-d2-ng-dayi-xiaodan-20260728.json"),
            new IncrementalSeed("billing_seed_zyy_d2_ng_dayi_xiaodan_20260728_v2",
                    "billing-seeds/phase-zyy-d2-ng-dayi-xiaodan-20260728.json"),
            new IncrementalSeed("billing_seed_bill_wave3_fix_20260728_v1",
                    "billing-seeds/phase-bill-wave3-fix-20260728.json"),
            new IncrementalSeed("billing_seed_bill_wave3_fix_20260728_v2",
                    "billing-seeds/phase-bill-wave3-fix-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_wave3_20260728_v1",
                    "billing-seeds/phase-settlement-wave3-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_wave3_20260728_v2",
                    "billing-seeds/phase-settlement-wave3-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_wave3_20260728_v3",
                    "billing-seeds/phase-settlement-wave3-20260728.json"),
            new IncrementalSeed("billing_seed_bill_wave3_close_20260728_v1",
                    "billing-seeds/phase-bill-wave3-close-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_wave3_20260728_v4",
                    "billing-seeds/phase-settlement-wave3-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_wave3_20260728_v5",
                    "billing-seeds/phase-settlement-wave3-20260728.json"),
            new IncrementalSeed("billing_seed_settlement_wave4_20260728_v1",
                    "billing-seeds/phase-settlement-wave4-20260728.json"),
            new IncrementalSeed("billing_seed_bill_wave4_20260728_v1",
                    "billing-seeds/phase-bill-wave4-20260728.json"),
            new IncrementalSeed("billing_seed_wave4b_20260728_v1",
                    "billing-seeds/phase-wave4b-20260728.json"),
            new IncrementalSeed("billing_seed_jzsw_bio_yanhdao_20260729_v1",
                    "billing-seeds/phase-jzsw-bio-yanhdao-20260729.json"),
            new IncrementalSeed("billing_seed_bill_wave4c_close_20260729_v1",
                    "billing-seeds/phase-bill-wave4c-close-20260729.json"),
            new IncrementalSeed("billing_seed_bill_wave4c_close_v2_20260729_v1",
                    "billing-seeds/phase-bill-wave4c-close-v2-20260729.json"),
            new IncrementalSeed("billing_seed_wave5_heu_settlement_20260729_v1",
                    "billing-seeds/phase-wave5-heu-settlement-discount-20260729.json"),
            new IncrementalSeed("billing_seed_wave5_taiping_20260729_v1",
                    "billing-seeds/phase-wave5-taiping-20260729.json"),
            new IncrementalSeed("billing_seed_wave5_pricing_20260729_v1",
                    "billing-seeds/phase-wave5-pricing-20260729.json"),
            new IncrementalSeed("billing_seed_yuemei_yanbao_20260730_v1",
                    "billing-seeds/phase-yuemei-yanbao-20260730.json"),
            new IncrementalSeed("billing_seed_shkf_oral_box_pricing_20260730_v1",
                    "billing-seeds/phase-shkf-oral-box-pricing-20260730.json"),
            new IncrementalSeed("billing_seed_zyy_d1_waier_huanbao_20260730_v1",
                    "billing-seeds/phase-zyy-d1-waier-huanbao-20260730.json"),
            new IncrementalSeed("billing_seed_billing_mode_backfill_20260730_v1",
                    "billing-seeds/phase-billing-mode-backfill-20260730.json"),
            new IncrementalSeed("billing_seed_zyy_d1_gongqiangjing_jingtou_20260730_v1",
                    "billing-seeds/phase-zyy-d1-gongqiangjing-jingtou-20260730.json"),
            new IncrementalSeed("billing_seed_export_fuyi_11col_20260730_v1",
                    "billing-seeds/phase-export-fuyi-11col-20260730.json"),
            new IncrementalSeed("billing_seed_zyy_d1_prod_golden_closeout_20260731_v1",
                    "billing-seeds/phase-zyy-d1-prod-golden-closeout-20260731.json"),
            new IncrementalSeed("billing_seed_hrb_cj_pricing_fixed_20260731_v1",
                    "billing-seeds/phase-hrb-cj-pricing-fixed-20260731.json"),
            new IncrementalSeed("billing_seed_hrb_bc_med_beauty_huanzuan_20260731_v1",
                    "billing-seeds/phase-hrb-bc-med-beauty-huanzuan-20260731.json"),
            new IncrementalSeed("billing_seed_ng_fuchan_s4_extra_close_20260731_v2",
                    "billing-seeds/phase-ng-fuchan-s4-extra-close-20260731.json"),
            new IncrementalSeed("billing_seed_hrb_hit_pricing_fixed_20260731_v2",
                    "billing-seeds/phase-hrb-hit-pricing-fixed-20260731.json"),
            new IncrementalSeed("billing_seed_ng_fuchan_deactivate_export_kuobang_20260731_v1",
                    "billing-seeds/phase-ng-fuchan-deactivate-export-kuobang-20260731.json"),
            new IncrementalSeed("billing_seed_hrb_hit_deactivate_export_pricing_20260731_v1",
                    "billing-seeds/phase-hrb-hit-deactivate-export-pricing-20260731.json"),
            new IncrementalSeed("billing_seed_hrb_cj_default_rule_20260731_v1",
                    "billing-seeds/phase-hrb-cj-default-rule-20260731.json"),
            new IncrementalSeed("billing_seed_hrb_cj_dedup_customer_20260731_v1",
                    "billing-seeds/phase-hrb-cj-dedup-customer-20260731.json"),
            new IncrementalSeed("billing_seed_hrb_hit_pricing_fix_20260801_v1",
                    "billing-seeds/phase-hrb-hit-pricing-fix-20260801.json")
    );

    private static final String ZYY_D1_P0_MARKER = "billing_seed_zyy_d1_p0_v2";
    private static final String ZYY_D1_P0_1_MARKER = "billing_seed_zyy_d1_p0_1_v3";
    /** apply_batch_p0_to_db.py 未用 utf8mb4 产生的乱码规则副本（与 Java P0 种子重复） */
    private static final String P0_MOJIBAKE_DUP_MARKER = "billing_seed_fix_p0_mojibake_dup_20260723_v1";
    /** 五常并行 seed 产生的无科室条件重复规则清理 */
    private static final String WCSRMYY_OR_DEDUP_MARKER = "billing_seed_wcsrm_yy_or_dedup_20260724_v1";

    private record IncrementalSeed(String markerKey, String classpathFile) {}

    private final SysSettingMapper sysSettingMapper;
    private final CustomerMapper customerMapper;
    private final CustomerAliasMapper customerAliasMapper;
    private final CustomerDiscountMapper customerDiscountMapper;
    private final CustomerProductRuleMapper customerProductRuleMapper;
    private final CustomerBillingPolicyMapper billingPolicyMapper;
    private final CustomerGroupMapper customerGroupMapper;
    private final CustomerGroupMemberMapper customerGroupMemberMapper;
    private final ExportTemplateMapper exportTemplateMapper;
    private final LogisticsCardMapper logisticsCardMapper;
    private final ExternalInstrumentMapper externalInstrumentMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        if (sysSettingMapper.countByKey(MARKER) == 0) {
            for (String file : SEED_FILES) {
                loadSeedClasspathFile(file);
            }
            insertMarker(MARKER, "BillingSeedMigrationRunner v1 完成标记");
            log.info("Billing seed migration complete.");
        }
        for (IncrementalSeed incremental : INCREMENTAL_SEEDS) {
            if (sysSettingMapper.countByKey(incremental.markerKey()) > 0) {
                continue;
            }
            boolean applied = true;
            if ("billing-seeds/phase-batch-p0.1.json".equals(incremental.classpathFile())) {
                applyBatchP0_1SeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-batch-p0.4.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-batch-p0.5.json".equals(incremental.classpathFile())) {
                applyBatchP0_4SeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-batch-p0.6.json".equals(incremental.classpathFile())) {
                applyBatchP0_6SeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-bokang-20260722-hit-heu-clarify.json".equals(incremental.classpathFile())) {
                applyBokang20260722SeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-zyy-d1-standard-pricing-20260723.json".equals(incremental.classpathFile())) {
                applied = applyCustomerStandardPricingSeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-export-rules-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-export-dept-split-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-export-fuyi-11col-20260730.json".equals(incremental.classpathFile())) {
                applied = applyExportRulesSeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-batch-p0.2.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-batch-p0.3.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-batch-p0.5.1.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-batch-p0.5.2.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-s4-fix-20260722.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-s4-fix-part2-20260722.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-s4-fix-part3-20260722.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-s4-fix-part4-20260722.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-s4-fix-part5-20260722.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-s4-close-20260722.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-user-20260722-er-hulan.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-hx-eye-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-ng-fuchan-gongqiangjing-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-ng-fuchan-pdf-ocr-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-ng-fuchan-kuobang-wanpan-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-bc-med-beauty-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-mhm-xizhizhen-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-sd-neau-kouqiang-fold-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hlfb-sf-chezhen-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-bc-med-beauty-fix-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-bc-med-beauty-fix-20260724-v2.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-hx-eye-fix-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-hx-eye-fix-20260724-v2.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-ng-fuchan-renliubao-fix-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-cj-fix-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-cj-surgical-pack-fix-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wcsrm-yy-or-pricing-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wcsrm-yy-extra-bag-fix-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wcsrm-yy-pack-price-fix-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wcsrm-yy-or-conditions-fix-20260724-v2.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wcsrm-yy-lap-per-piece-revert-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wcsrm-yy-or-consolidate-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-2nd-fix-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-sd-neau-kouqiang-fold-fix-20260724-v2.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-sh-pricing-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-ngjy-fix-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zuyan-ng-export-pricing-20260724.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-s7-bokang-pdf-ocr-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-s7-daowai-wailai-keywords-20260723.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-sanjing-neilou-instrument-count-fix-20260727.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-sheng-yy-xf-dept-pricing-20260727.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-sheng-yy-xf-shenwai-goudao-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-ng-fuchan-kuobang-bundle-24-fix-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-ng-fuchan-kuobang-bundle-24-fix-20260728-v2.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d1-fold-ganlan-chongxi-fix-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d2-ng-special-pricing-fix-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d2-ng-guasha-tanzhen-per-piece-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wj-ngjy-sd-neau-zero-fold-fix-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hlj-jyglj-weike-jiaqian-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-bill-wave3-close-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wave4b-20260728.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-jzsw-bio-yanhdao-20260729.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-bill-wave4c-close-20260729.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-bill-wave4c-close-v2-20260729.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wave5-heu-settlement-discount-20260729.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wave5-taiping-20260729.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-wave5-pricing-20260729.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-yuemei-yanbao-20260730.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-shkf-oral-box-pricing-20260730.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d1-waier-huanbao-20260730.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d1-gongqiangjing-jingtou-20260730.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d1-prod-golden-closeout-20260731.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-ng-fuchan-deactivate-export-kuobang-20260731.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-hit-deactivate-export-pricing-20260731.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-cj-default-rule-20260731.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-cj-dedup-customer-20260731.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-hrb-hit-pricing-fix-20260801.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d1-p0-2-price-align.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d1-july-export-parity-20260804.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d1-july-export-parity-v2-20260804.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d1-lap-3038-priority-20260804.json".equals(incremental.classpathFile())
                    || "billing-seeds/phase-zyy-d1-july-parity-reapply-20260804.json".equals(incremental.classpathFile())) {
                applyBatchPatchSeedFile(incremental.classpathFile());
            } else if ("billing-seeds/phase-billing-mode-backfill-20260730.json".equals(incremental.classpathFile())) {
                applyBillingModeBackfillSeedFile(incremental.classpathFile());
            } else {
                applied = loadSeedClasspathFile(incremental.classpathFile());
            }
            if (!applied) {
                log.warn("Incremental billing seed NOT marked (apply failed): {}", incremental.classpathFile());
                continue;
            }
            insertMarker(incremental.markerKey(), "Incremental billing seed: " + incremental.classpathFile());
            log.info("Incremental billing seed applied: {}", incremental.classpathFile());
        }
        if (sysSettingMapper.countByKey(P0_MOJIBAKE_DUP_MARKER) == 0) {
            int deleted = deleteP0ScriptMojibakeDuplicateRules();
            insertMarker(P0_MOJIBAKE_DUP_MARKER,
                    "Remove duplicate customer_product_rule rows from apply_batch_p0_to_db.py latin1 import");
            log.info("P0 mojibake duplicate cleanup: deleted {} rules", deleted);
        }
        if (sysSettingMapper.countByKey(WCSRMYY_OR_DEDUP_MARKER) == 0) {
            int deactivated = deactivateWcsrmYyDuplicateOrRules();
            insertMarker(WCSRMYY_OR_DEDUP_MARKER,
                    "Deactivate WCSRMYY OR rules duplicated without department conditions");
            log.info("WCSRMYY OR duplicate cleanup: deactivated {} rules", deactivated);
        }
        if (sysSettingMapper.countByKey(ZYY_D1_P0_MARKER) == 0) {
            applyZyyD1P0RuleFixes();
            insertMarker(ZYY_D1_P0_MARKER, "ZYY-D1 P0 校对规则修正（停用宽泛无纺布、补精确产品规则）");
            log.info("ZYY-D1 P0 rule fixes applied.");
        }
        if (sysSettingMapper.countByKey(ZYY_D1_P0_1_MARKER) == 0) {
            applyZyyD1P0_1RuleFixes();
            insertMarker(ZYY_D1_P0_1_MARKER, "ZYY-D1 P0.1 收窄腔镜包/王树人/保温杯关键词");
            log.info("ZYY-D1 P0.1 rule fixes applied.");
        }
    }

    private boolean loadSeedClasspathFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("Billing seed file missing: {}", file);
                return false;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            reactivateBillingPolicies(root.path("reactivateBillingPolicies"));
            seedProfiles(root.path("profiles"));
            seedCustomerGroups(root.path("customerGroups"));
            seedLogisticsCards(root.path("logisticsCards"));
            log.info("Loaded billing seed: {}", file);
            return true;
        } catch (Exception e) {
            log.error("Failed to load billing seed {}: {}", file, e.getMessage(), e);
            return false;
        }
    }

    private void reactivateBillingPolicies(JsonNode codes) {
        if (!codes.isArray()) {
            return;
        }
        for (JsonNode codeNode : codes) {
            String code = codeNode.asText();
            if (code == null || code.isBlank()) {
                continue;
            }
            Customer customer = customerMapper.selectByCode(code);
            if (customer == null) {
                continue;
            }
            billingPolicyMapper.selectByCustomerId(customer.getId()).forEach(p -> {
                if (!Boolean.TRUE.equals(p.getIsActive())) {
                    p.setIsActive(true);
                    billingPolicyMapper.updateById(p);
                }
            });
            log.info("Reactivated billing policies for {}", code);
        }
    }

    /** P0.1 补丁种子：更新 billing 模式 / 规则 keywords / 关闭零差异院 billing */
    private void applyBatchP0_1SeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("P0.1 seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode upd : root.path("customerUpdates")) {
                String code = text(upd, "code");
                if (code == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("P0.1 customer update skipped: {} not found", code);
                    continue;
                }
                if (upd.has("billingPricingMode")) {
                    customer.setBillingPricingMode(text(upd, "billingPricingMode"));
                }
                if (upd.has("billingEnabled")) {
                    customer.setBillingEnabled(bool(upd, "billingEnabled", false));
                }
                if (upd.has("defaultRuleId")) {
                    customer.setDefaultRuleId(upd.get("defaultRuleId").asLong());
                }
                if (upd.has("setStatus")) {
                    customer.setStatus(text(upd, "setStatus"));
                }
                if (upd.has("setCanonicalName")) {
                    customer.setCanonicalName(text(upd, "setCanonicalName"));
                }
                customerMapper.updateById(customer);
                log.info("P0.1 updated customer {}: mode={} enabled={}",
                        code, customer.getBillingPricingMode(), customer.getBillingEnabled());
            }
            for (JsonNode patch : root.path("ruleUpdates")) {
                String code = text(patch, "code");
                String ruleName = text(patch, "ruleName");
                if (code == null || ruleName == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                CustomerProductRule rule = findProductRuleByName(customer.getId(), ruleName);
                if (rule == null) {
                    log.warn("P0.1 rule patch skipped: {}/{}", code, ruleName);
                    continue;
                }
                List<String> keywords = parseStringList(rule.getKeywords());
                List<String> exclude = parseStringList(rule.getExcludeKeywords());
                for (JsonNode rm : patch.path("removeKeywords")) {
                    keywords.remove(rm.asText());
                }
                for (JsonNode add : patch.path("addKeywords")) {
                    String kw = add.asText();
                    if (!keywords.contains(kw)) {
                        keywords.add(kw);
                    }
                }
                for (JsonNode addEx : patch.path("addExcludeKeywords")) {
                    String ex = addEx.asText();
                    if (!exclude.contains(ex)) {
                        exclude.add(ex);
                    }
                }
                rule.setKeywords(JsonUtils.toJson(keywords));
                rule.setExcludeKeywords(exclude.isEmpty() ? null : JsonUtils.toJson(exclude));
                customerProductRuleMapper.updateById(rule);
                log.info("P0.1 patched rule {}/{} keywords={} exclude={}", code, ruleName, keywords, exclude);
            }
            for (JsonNode codeNode : root.path("deactivateBillingPolicies")) {
                String code = codeNode.asText();
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                billingPolicyMapper.selectByCustomerId(customer.getId()).forEach(p -> {
                    p.setIsActive(false);
                    billingPolicyMapper.updateById(p);
                });
                customerDiscountMapper.deleteByCustomerId(customer.getId());
                log.info("P0.1 deactivated policies/discounts for {}", code);
            }
            log.info("Applied P0.1 seed: {}", file);
        } catch (Exception e) {
            log.error("Failed to apply P0.1 seed {}: {}", file, e.getMessage(), e);
        }
    }

    /** P0.4：L9-L61 补充院 customer 收窄 + 工程大学口腔规则 */
    private void applyBatchP0_4SeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("P0.4 seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode upd : root.path("customerUpdates")) {
                String code = text(upd, "code");
                if (code == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("P0.4 customer update skipped: {} not found", code);
                    continue;
                }
                if (upd.has("billingPricingMode")) {
                    customer.setBillingPricingMode(text(upd, "billingPricingMode"));
                }
                if (upd.has("billingEnabled")) {
                    customer.setBillingEnabled(bool(upd, "billingEnabled", false));
                }
                if (upd.has("defaultRuleId")) {
                    customer.setDefaultRuleId(upd.get("defaultRuleId").asLong());
                }
                if (upd.has("setStatus")) {
                    customer.setStatus(text(upd, "setStatus"));
                }
                if (upd.has("setCanonicalName")) {
                    customer.setCanonicalName(text(upd, "setCanonicalName"));
                }
                customerMapper.updateById(customer);
                log.info("P0.4 updated customer {}: mode={} enabled={}",
                        code, customer.getBillingPricingMode(), customer.getBillingEnabled());
            }
            for (JsonNode codeNode : root.path("deactivateBillingPolicies")) {
                String code = codeNode.asText();
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                billingPolicyMapper.selectByCustomerId(customer.getId()).forEach(p -> {
                    p.setIsActive(false);
                    billingPolicyMapper.updateById(p);
                });
                customerDiscountMapper.deleteByCustomerId(customer.getId());
                log.info("P0.4 deactivated policies/discounts for {}", code);
            }
            for (JsonNode aliasNode : root.path("customerAliases")) {
                String code = text(aliasNode, "code");
                String alias = text(aliasNode, "alias");
                if (code == null || alias == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                ensureCustomerAliasExact(customer.getId(), alias,
                        text(aliasNode, "matchType", "exact"), "p0.4_seed", 10);
                log.info("P0.4 alias {} → {}", alias, code);
            }
            for (JsonNode ruleNode : root.path("newRules")) {
                String code = text(ruleNode, "code");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                String name = text(ruleNode, "name");
                if (customerProductRuleMapper.countByCustomerIdAndName(customer.getId(), name) > 0) {
                    continue;
                }
                seedProductRules(customer.getId(), JsonUtils.getObjectMapper().createArrayNode().add(ruleNode));
                log.info("P0.4 inserted rule {}/{}", code, name);
            }
            log.info("Applied P0.4 seed: {}", file);
        } catch (Exception e) {
            log.error("Failed to apply P0.4 seed {}: {}", file, e.getMessage(), e);
        }
    }

    /** P0.6：验收通过院启用 billing，其余停用 */
    private void applyBatchP0_6SeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("P0.6 seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            List<String> enableCodes = new ArrayList<>();
            for (JsonNode codeNode : root.path("enableBilling")) {
                enableCodes.add(codeNode.asText());
            }
            if (enableCodes.isEmpty()) {
                log.warn("P0.6 enableBilling list is empty, skipped");
                return;
            }
            for (String code : enableCodes) {
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("P0.6 enable skipped: {} not found", code);
                    continue;
                }
                customer.setBillingEnabled(true);
                customerMapper.updateById(customer);
                log.info("P0.6 enabled billing for {}", code);
            }
            if (bool(root, "disableAllOthers", false)) {
                List<Customer> all = customerMapper.selectAll();
                if (all != null) {
                    for (Customer customer : all) {
                        if (customer.getCode() == null || enableCodes.contains(customer.getCode())) {
                            continue;
                        }
                        if (!Boolean.TRUE.equals(customer.getBillingEnabled())) {
                            continue;
                        }
                        customer.setBillingEnabled(false);
                        customerMapper.updateById(customer);
                        log.info("P0.6 disabled billing for {}", customer.getCode());
                    }
                }
            }
            log.info("Applied P0.6 seed: {} ({} enabled)", file, enableCodes.size());
        } catch (Exception e) {
            log.error("Failed to apply P0.6 seed {}: {}", file, e.getMessage(), e);
        }
    }

    /** 铂康 2026-07-22：工程大学客户、结款折扣与客户备注 */
    private void applyBokang20260722SeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("Bokang clarify seed missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            seedProfiles(root.path("profiles"));
            for (JsonNode upd : root.path("customerUpdates")) {
                String code = text(upd, "code");
                if (code == null || !upd.hasNonNull("notes")) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("Bokang notes skipped: {} not found", code);
                    continue;
                }
                customer.setNotes(text(upd, "notes"));
                customerMapper.updateById(customer);
                log.info("Bokang notes updated for {}", code);
            }
            log.info("Applied Bokang clarify seed: {}", file);
        } catch (Exception e) {
            log.error("Failed to apply Bokang clarify seed {}: {}", file, e.getMessage(), e);
        }
    }

    /** S7：客户级 export_template 绑定 + exportCatalog 文档化条目 */
    private boolean applyExportRulesSeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("Export rules seed file missing: {}", file);
                return false;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            seedProfiles(root.path("profiles"));
            int bound = 0;
            for (JsonNode overrideNode : root.path("exportTemplateOverrides")) {
                String code = text(overrideNode, "code");
                if (code == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("Export template override skipped: {} not found", code);
                    continue;
                }
                JsonNode templates = overrideNode.path("templates");
                if (!templates.isArray()) {
                    continue;
                }
                for (JsonNode tpl : templates) {
                    if (ensureCustomerExportTemplateBinding(customer, tpl)) {
                        bound++;
                    }
                }
            }
            log.info("Applied export rules seed: {} ({} template bindings)", file, bound);
            return true;
        } catch (Exception e) {
            log.error("Failed to apply export rules seed {}: {}", file, e.getMessage(), e);
            return false;
        }
    }

    private boolean ensureCustomerExportTemplateBinding(Customer customer, JsonNode tpl) {
        String type = text(tpl, "type");
        String strategyKey = text(tpl, "strategyKey");
        if (type == null || strategyKey == null) {
            return false;
        }
        String name = text(tpl, "name");
        if (name == null || name.isBlank()) {
            name = customer.getCode() + "-" + type + "-S7";
        }
        String columnMapping = tpl.has("columnMapping") ? tpl.get("columnMapping").toString() : "{}";
        String sheetConfig = String.format("{\"strategyKey\":\"%s\"}", strategyKey);

        List<ExportTemplate> existing =
                exportTemplateMapper.selectByCustomerAndType(customer.getId(), type);
        ExportTemplate target = null;
        if (existing != null) {
            for (ExportTemplate row : existing) {
                if (name.equals(row.getName())) {
                    target = row;
                    break;
                }
            }
        }
        if (target == null) {
            ExportTemplate created = new ExportTemplate();
            created.setCustomerId(customer.getId());
            created.setTemplateType(type);
            created.setName(name);
            created.setStoragePath("");
            created.setColumnMapping(columnMapping);
            created.setSheetConfig(sheetConfig);
            created.setIsActive(true);
            exportTemplateMapper.insert(created);
            log.info("Export template binding created: {} → {} ({})", customer.getCode(), name, type);
            return true;
        }
        boolean changed = false;
        if (!sheetConfig.equals(target.getSheetConfig())) {
            target.setSheetConfig(sheetConfig);
            changed = true;
        }
        if (!columnMapping.equals(target.getColumnMapping())) {
            target.setColumnMapping(columnMapping);
            changed = true;
        }
        if (changed) {
            exportTemplateMapper.updateById(target);
            log.info("Export template binding updated: {} → {}", customer.getCode(), name);
        }
        return true;
    }

    /** 客户标准灭菌价覆盖 + 计价模式（如附一 hybrid + standardPricingOverride） */
    private boolean applyCustomerStandardPricingSeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("Standard pricing seed file missing: {}", file);
                return false;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            int updated = 0;
            for (JsonNode upd : root.path("customerUpdates")) {
                String code = text(upd, "code");
                if (code == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("Standard pricing seed skipped: {} not found", code);
                    continue;
                }
                if (upd.has("billingPricingMode")) {
                    customer.setBillingPricingMode(text(upd, "billingPricingMode"));
                }
                if (upd.has("standardPricingOverride")) {
                    customer.setStandardPricingOverride(upd.get("standardPricingOverride").toString());
                }
                customerMapper.updateById(customer);
                updated++;
                log.info("Standard pricing seed updated customer {}: mode={} hasOverride={}",
                        code, customer.getBillingPricingMode(),
                        customer.getStandardPricingOverride() != null);
            }
            if (updated == 0) {
                log.warn("Standard pricing seed applied no rows: {}", file);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to apply standard pricing seed {}: {}", file, e.getMessage(), e);
            return false;
        }
    }

    /** P0.2+ 补丁种子：规则更新 / 新增 / 停用 */
    private void applyBatchPatchSeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("Batch patch seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            seedProfiles(root.path("profiles"));
            for (JsonNode upd : root.path("customerUpdates")) {
                String code = text(upd, "code");
                if (code == null) {
                    continue;
                }
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("Batch patch customer update skipped: {} not found", code);
                    continue;
                }
                if (upd.has("billingPricingMode")) {
                    customer.setBillingPricingMode(text(upd, "billingPricingMode"));
                }
                if (upd.has("billingEnabled")) {
                    customer.setBillingEnabled(bool(upd, "billingEnabled", false));
                }
                if (upd.has("defaultRuleId")) {
                    customer.setDefaultRuleId(upd.get("defaultRuleId").asLong());
                }
                if (upd.has("setStatus")) {
                    customer.setStatus(text(upd, "setStatus"));
                }
                if (upd.has("setCanonicalName")) {
                    customer.setCanonicalName(text(upd, "setCanonicalName"));
                }
                customerMapper.updateById(customer);
                log.info("Batch patch updated customer {}: mode={} enabled={}",
                        code, customer.getBillingPricingMode(), customer.getBillingEnabled());
            }
            for (JsonNode patch : root.path("ruleUpdates")) {
                String code = text(patch, "code");
                String ruleName = text(patch, "ruleName");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                CustomerProductRule rule = findProductRuleByName(customer.getId(), ruleName);
                if (rule == null) {
                    log.warn("Batch patch rule skipped: {}/{}", code, ruleName);
                    continue;
                }
                boolean changed = false;
                if (patch.has("conditionsJson")) {
                    rule.setConditionsJson(patch.get("conditionsJson").asText());
                    changed = true;
                }
                if (patch.has("setKeywords")) {
                    rule.setKeywords(toJsonArray(patch.get("setKeywords")));
                    changed = true;
                } else {
                    List<String> keywords = parseStringList(rule.getKeywords());
                    List<String> exclude = parseStringList(rule.getExcludeKeywords());
                    for (JsonNode rm : patch.path("removeKeywords")) {
                        keywords.remove(rm.asText());
                    }
                    for (JsonNode add : patch.path("addKeywords")) {
                        String kw = add.asText();
                        if (!keywords.contains(kw)) {
                            keywords.add(kw);
                        }
                    }
                    for (JsonNode addEx : patch.path("addExcludeKeywords")) {
                        String ex = addEx.asText();
                        if (!exclude.contains(ex)) {
                            exclude.add(ex);
                        }
                    }
                    if (patch.has("removeKeywords") || patch.has("addKeywords")) {
                        rule.setKeywords(JsonUtils.toJson(keywords));
                        changed = true;
                    }
                    if (patch.has("addExcludeKeywords")) {
                        rule.setExcludeKeywords(JsonUtils.toJson(exclude));
                        changed = true;
                    }
                }
                if (patch.has("setExcludeKeywords")) {
                    rule.setExcludeKeywords(toJsonArray(patch.get("setExcludeKeywords")));
                    changed = true;
                }
                if (patch.has("setMatchMode")) {
                    rule.setMatchMode(text(patch, "setMatchMode", "first"));
                    changed = true;
                }
                if (patch.has("setAcceptedPrices")) {
                    rule.setAcceptedPrices(patch.get("setAcceptedPrices").toString());
                    changed = true;
                }
                if (patch.has("setPrice")) {
                    rule.setPrice(new BigDecimal(patch.get("setPrice").asText()));
                    changed = true;
                }
                if (patch.has("setName")) {
                    rule.setName(text(patch, "setName"));
                    changed = true;
                }
                if (patch.has("setSkipDiscount")) {
                    rule.setSkipDiscount(bool(patch, "setSkipDiscount", false));
                    changed = true;
                }
                if (patch.has("setRuleType")) {
                    rule.setRuleType(text(patch, "setRuleType"));
                    changed = true;
                }
                if (patch.has("setSkipPackaging")) {
                    rule.setSkipPackaging(bool(patch, "setSkipPackaging", false));
                    changed = true;
                }
                if (patch.has("setMinInstrumentCount")) {
                    if (patch.get("setMinInstrumentCount").isNull()) {
                        rule.setMinInstrumentCount(null);
                    } else {
                        rule.setMinInstrumentCount(intVal(patch, "setMinInstrumentCount", null));
                    }
                    changed = true;
                }
                if (patch.has("setMaxInstrumentCount")) {
                    if (patch.get("setMaxInstrumentCount").isNull()) {
                        rule.setMaxInstrumentCount(null);
                    } else {
                        rule.setMaxInstrumentCount(intVal(patch, "setMaxInstrumentCount", null));
                    }
                    changed = true;
                }
                if (patch.has("setThreshold")) {
                    rule.setThreshold(intVal(patch, "setThreshold", 5));
                    changed = true;
                }
                if (patch.has("setFoldRatio")) {
                    rule.setFoldRatio(decimal(patch, "setFoldRatio"));
                    changed = true;
                }
                if (patch.has("setBillingMode")) {
                    rule.setBillingMode(text(patch, "setBillingMode"));
                    changed = true;
                }
                if (patch.has("setPieceCountSource")) {
                    rule.setPieceCountSource(text(patch, "setPieceCountSource"));
                    changed = true;
                }
                if (changed) {
                    customerProductRuleMapper.updateById(rule);
                    log.info("Batch patch updated rule {}/{}", code, ruleName);
                }
            }
            for (JsonNode ruleNode : root.path("newRules")) {
                String code = text(ruleNode, "code");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                String name = text(ruleNode, "name");
                if (customerProductRuleMapper.countByCustomerIdAndName(customer.getId(), name) > 0) {
                    continue;
                }
                seedProductRules(customer.getId(), JsonUtils.getObjectMapper().createArrayNode().add(ruleNode));
                log.info("Batch patch inserted rule {}/{}", code, name);
            }
            for (JsonNode deact : root.path("deactivateRules")) {
                String code = text(deact, "code");
                String ruleName = text(deact, "ruleName");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    continue;
                }
                deactivateProductRule(customer.getId(), ruleName);
            }
            seedExternalInstruments(root.path("externalInstruments"));
            for (JsonNode discNode : root.path("discountUpdates")) {
                String code = text(discNode, "code");
                String name = text(discNode, "name");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null || name == null) {
                    continue;
                }
                CustomerDiscount existing = customerDiscountMapper.selectByCustomerId(customer.getId()).stream()
                        .filter(d -> name.equals(d.getName()))
                        .findFirst()
                        .orElse(null);
                if (existing == null && bool(discNode, "insertIfMissing", false)) {
                    CustomerDiscount discount = new CustomerDiscount();
                    discount.setCustomerId(customer.getId());
                    discount.setName(name);
                    discount.setDiscountRate(decimal(discNode, "rate"));
                    discount.setApplyStage(text(discNode, "applyStage", "after_base"));
                    discount.setSkipWhenFixedPrice(bool(discNode, "skipWhenFixedPrice", true));
                    discount.setPriority(intVal(discNode, "priority", 100));
                    discount.setIsActive(true);
                    customerDiscountMapper.insert(discount);
                    log.info("Batch patch inserted discount {}/{}", code, name);
                    continue;
                }
                if (existing == null) {
                    log.warn("Batch patch discount skipped (missing): {}/{}", code, name);
                    continue;
                }
                boolean changed = false;
                if (discNode.has("rate")) {
                    existing.setDiscountRate(decimal(discNode, "rate"));
                    changed = true;
                }
                if (discNode.has("skipWhenFixedPrice")) {
                    existing.setSkipWhenFixedPrice(bool(discNode, "skipWhenFixedPrice", true));
                    changed = true;
                }
                if (discNode.has("applyStage")) {
                    existing.setApplyStage(text(discNode, "applyStage", existing.getApplyStage()));
                    changed = true;
                }
                if (changed) {
                    customerDiscountMapper.updateById(existing);
                    log.info("Batch patch updated discount {}/{}", code, name);
                }
            }
            log.info("Applied batch patch seed: {}", file);
        } catch (Exception e) {
            log.error("Failed to apply batch patch seed {}: {}", file, e.getMessage(), e);
        }
    }

    private void seedExternalInstruments(JsonNode rows) {
        if (rows == null || !rows.isArray()) {
            return;
        }
        for (JsonNode row : rows) {
            Long jobId = row.has("jobId") ? row.get("jobId").asLong() : null;
            String code = text(row, "code");
            String packName = text(row, "packName");
            String categoryNo = text(row, "categoryNo");
            if (jobId == null || code == null || packName == null) {
                continue;
            }
            Customer customer = customerMapper.selectByCode(code);
            if (customer == null) {
                log.warn("External instrument seed skipped: customer {} not found", code);
                continue;
            }
            List<ExternalInstrument> existing = externalInstrumentMapper.selectByJobId(jobId);
            boolean duplicate = existing != null && existing.stream().anyMatch(item ->
                    packName.equals(item.getPackName())
                            && (categoryNo == null || categoryNo.equals(item.getCategoryNo())));
            if (duplicate) {
                continue;
            }
            ExternalInstrument instrument = new ExternalInstrument();
            instrument.setCustomerId(customer.getId());
            instrument.setReconciliationJobId(jobId);
            instrument.setCategoryNo(categoryNo != null ? categoryNo : "");
            instrument.setPackName(packName);
            instrument.setDepartment("外来器械");
            instrument.setPackCount(1);
            instrument.setInstrumentCount(0);
            double unit = row.path("unitPrice").asDouble(0);
            double total = row.has("totalAmount") ? row.path("totalAmount").asDouble(unit) : unit;
            instrument.setUnitPrice(BigDecimal.valueOf(unit));
            instrument.setTotalAmount(BigDecimal.valueOf(total));
            instrument.setIsActive(true);
            externalInstrumentMapper.insert(instrument);
            log.info("Seeded external instrument job {} pack {}", jobId, packName);
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = JsonUtils.getObjectMapper().readTree(json);
            List<String> out = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    out.add(item.asText());
                }
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void insertMarker(String key, String description) {
        SysSetting marker = new SysSetting();
        marker.setSettingKey(key);
        marker.setSettingValue("true");
        marker.setDescription(description);
        sysSettingMapper.insert(marker);
    }

    private void seedProfiles(JsonNode profiles) {
        if (!profiles.isArray()) {
            return;
        }
        for (JsonNode profile : profiles) {
            String code = text(profile, "code");
            if (code == null) {
                continue;
            }
            Customer customer = ensureCustomer(profile);
            applyCustomerFields(customer, profile);
            seedAliases(customer.getId(), profile.path("aliases"));
            seedDiscounts(customer.getId(), profile.path("discounts"));
            seedPolicies(customer.getId(), profile.path("policies"));
            seedProductRules(customer.getId(), profile.path("productRules"));
        }
    }

    private Customer ensureCustomer(JsonNode profile) {
        String code = text(profile, "code");
        Customer existing = customerMapper.selectByCode(code);
        if (existing != null) {
            return existing;
        }
        Customer customer = new Customer();
        customer.setCode(code);
        customer.setCanonicalName(text(profile, "name"));
        customer.setStatus("active");
        customer.setBillingEnabled(bool(profile, "billingEnabled", false));
        customer.setBillingPricingMode(text(profile, "billingPricingMode", "standard"));
        customer.setNotes(text(profile, "notes"));
        if (profile.hasNonNull("exportNameMapping")) {
            customer.setExportNameMapping(profile.get("exportNameMapping").toString());
        }
        customerMapper.insert(customer);
        log.info("Seeded customer: {}", code);
        return customer;
    }

    private void applyCustomerFields(Customer customer, JsonNode profile) {
        boolean changed = false;
        if (profile.has("billingEnabled")) {
            Boolean enabled = bool(profile, "billingEnabled", false);
            if (!enabled.equals(customer.getBillingEnabled())) {
                customer.setBillingEnabled(enabled);
                changed = true;
            }
        }
        if (profile.has("billingPricingMode")) {
            String mode = text(profile, "billingPricingMode");
            if (mode != null && !mode.equals(customer.getBillingPricingMode())) {
                customer.setBillingPricingMode(mode);
                changed = true;
            }
        }
        if (profile.hasNonNull("exportNameMapping") && customer.getExportNameMapping() == null) {
            customer.setExportNameMapping(profile.get("exportNameMapping").toString());
            changed = true;
        }
        if (profile.hasNonNull("notes") && (customer.getNotes() == null || customer.getNotes().isBlank())) {
            customer.setNotes(text(profile, "notes"));
            changed = true;
        }
        if (profile.hasNonNull("pathOverride")) {
            String json = profile.get("pathOverride").toString();
            if (!json.equals(customer.getPathOverride())) {
                customer.setPathOverride(json);
                changed = true;
            }
        }
        if (changed) {
            customerMapper.updateById(customer);
        }
    }

    private void seedAliases(Long customerId, JsonNode aliases) {
        if (!aliases.isArray()) {
            return;
        }
        List<String> existingAliases = customerAliasMapper.selectByCustomerId(customerId).stream()
                .map(CustomerAlias::getAlias)
                .toList();
        for (JsonNode aliasNode : aliases) {
            String alias = aliasNode.asText();
            if (alias == null || alias.isBlank() || existingAliases.contains(alias)) {
                continue;
            }
            if (isAliasOwnedElsewhere(alias, customerId)) {
                log.debug("Skip alias seed (already bound): {} → customer {}", alias, customerId);
                continue;
            }
            CustomerAlias entity = new CustomerAlias();
            entity.setCustomerId(customerId);
            entity.setAlias(alias);
            entity.setMatchType("contains");
            entity.setSource("seed");
            entity.setPriority(100);
            entity.setIsActive(true);
            customerAliasMapper.insert(entity);
        }
    }

    /** uk_customer_alias 全局唯一：含 inactive 行，插入前需查全表 */
    private boolean isAliasOwnedElsewhere(String alias, Long customerId) {
        CustomerAlias row = customerAliasMapper.selectByAlias(alias);
        return row != null && !row.getCustomerId().equals(customerId);
    }

    private void ensureCustomerAliasExact(Long customerId, String alias, String matchType,
                                          String source, int priority) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        boolean exists = customerAliasMapper.selectByCustomerId(customerId).stream()
                .anyMatch(a -> alias.equals(a.getAlias()));
        if (exists) {
            return;
        }
        if (isAliasOwnedElsewhere(alias, customerId)) {
            log.debug("Skip exact alias (already bound elsewhere): {}", alias);
            return;
        }
        CustomerAlias entity = new CustomerAlias();
        entity.setCustomerId(customerId);
        entity.setAlias(alias);
        entity.setMatchType(matchType != null && !matchType.isBlank() ? matchType : "exact");
        entity.setSource(source);
        entity.setPriority(priority);
        entity.setIsActive(true);
        customerAliasMapper.insert(entity);
    }

    private void seedDiscounts(Long customerId, JsonNode discounts) {
        if (!discounts.isArray()) {
            return;
        }
        for (JsonNode discountNode : discounts) {
            String name = text(discountNode, "name");
            if (name == null || hasDiscountNamed(customerId, name)) {
                continue;
            }
            CustomerDiscount discount = new CustomerDiscount();
            discount.setCustomerId(customerId);
            discount.setName(name);
            discount.setDiscountRate(decimal(discountNode, "rate"));
            discount.setApplyStage(text(discountNode, "applyStage", "after_base"));
            discount.setSkipWhenFixedPrice(bool(discountNode, "skipWhenFixedPrice", true));
            discount.setPriority(intVal(discountNode, "priority", 100));
            discount.setIsActive(true);
            customerDiscountMapper.insert(discount);
        }
    }

    private void seedPolicies(Long customerId, JsonNode policies) {
        if (!policies.isArray()) {
            return;
        }
        for (JsonNode policyNode : policies) {
            String name = text(policyNode, "name");
            String type = text(policyNode, "policyType");
            if (name == null || type == null) {
                continue;
            }
            List<CustomerBillingPolicy> existing = billingPolicyMapper.selectByCustomerIdAndType(customerId, type);
            if (existing != null && existing.stream().anyMatch(p -> name.equals(p.getName()))) {
                existing.stream()
                        .filter(p -> name.equals(p.getName()))
                        .forEach(p -> {
                            if (policyNode.has("scope")) {
                                p.setScope(policyNode.get("scope").toString());
                            }
                            if (policyNode.has("params")) {
                                p.setParams(policyNode.get("params").toString());
                            }
                            p.setPriority(intVal(policyNode, "priority", p.getPriority() != null ? p.getPriority() : 100));
                            p.setIsActive(true);
                            billingPolicyMapper.updateById(p);
                        });
                continue;
            }
            CustomerBillingPolicy policy = new CustomerBillingPolicy();
            policy.setCustomerId(customerId);
            policy.setPolicyType(type);
            policy.setName(name);
            if (policyNode.has("scope")) {
                policy.setScope(policyNode.get("scope").toString());
            }
            if (policyNode.has("params")) {
                policy.setParams(policyNode.get("params").toString());
            }
            policy.setPriority(intVal(policyNode, "priority", 100));
            policy.setIsActive(true);
            billingPolicyMapper.insert(policy);
        }
    }

    /**
     * 附一 P0.1：收窄关键词，消除腹腔镜包/小王树人/非Z2044保温杯误报。
     */
    private void applyZyyD1P0_1RuleFixes() {
        Customer customer = customerMapper.selectByCode("ZYY-D1");
        if (customer == null) {
            log.warn("ZYY-D1 P0.1 fixes skipped: customer not found");
            return;
        }
        Long customerId = customer.getId();
        updateRuleKeywords(customerId, "王树人特器w12050", List.of("王树人特器-26"));
        updateRuleKeywords(customerId, "低温袋10cm", List.of("低温灭菌 10cm"));
        updateRuleKeywordsAndExclude(customerId, "腔镜包整包价",
                List.of("腔镜包"), List.of("腹腔镜"));
        try {
            ClassPathResource resource = new ClassPathResource("billing-seeds/phase-zyy-d1-fuyi.json");
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode profile : root.path("profiles")) {
                if (!"ZYY-D1".equals(text(profile, "code"))) {
                    continue;
                }
                for (JsonNode ruleNode : profile.path("productRules")) {
                    if ("保温杯-1Z2044".equals(text(ruleNode, "name"))) {
                        seedProductRules(customerId, JsonUtils.getObjectMapper().createArrayNode().add(ruleNode));
                        break;
                    }
                }
                break;
            }
        } catch (Exception e) {
            log.error("ZYY-D1 P0.1 seed insert failed: {}", e.getMessage(), e);
        }
    }

    private void updateRuleKeywordsAndExclude(Long customerId, String ruleName,
                                              List<String> keywords, List<String> excludeKeywords) {
        CustomerProductRule rule = findProductRuleByName(customerId, ruleName);
        if (rule == null) {
            return;
        }
        rule.setKeywords(JsonUtils.toJson(keywords));
        rule.setExcludeKeywords(JsonUtils.toJson(excludeKeywords));
        customerProductRuleMapper.updateById(rule);
        log.info("Updated keywords/exclude for rule {} (customerId={})", ruleName, customerId);
    }

    /**
     * 附一 6 月校对 P0：停用误报规则、更新关键词、补精确产品固定价。
     */
    private void applyZyyD1P0RuleFixes() {
        Customer customer = customerMapper.selectByCode("ZYY-D1");
        if (customer == null) {
            log.warn("ZYY-D1 P0 fixes skipped: customer not found");
            return;
        }
        Long customerId = customer.getId();
        deactivateProductRule(customerId, "无纺布按把4.4");
        deactivateProductRule(customerId, "纸塑袋3件最低把价");
        updateRuleKeywords(customerId, "低温袋10cm", List.of("低温灭菌 10cm", "保温杯"));
        updateRuleKeywords(customerId, "低温袋15cm", List.of("低温灭菌 15cm", "膀胱取石钳"));
        try {
            ClassPathResource resource = new ClassPathResource("billing-seeds/phase-zyy-d1-fuyi.json");
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode profile : root.path("profiles")) {
                if (!"ZYY-D1".equals(text(profile, "code"))) {
                    continue;
                }
                seedProductRules(customerId, profile.path("productRules"));
                break;
            }
        } catch (Exception e) {
            log.error("ZYY-D1 P0 seedProductRules failed: {}", e.getMessage(), e);
        }
    }

    private void deactivateProductRule(Long customerId, String ruleName) {
        CustomerProductRule rule = findProductRuleByName(customerId, ruleName);
        if (rule == null || !Boolean.TRUE.equals(rule.getIsActive())) {
            return;
        }
        rule.setIsActive(false);
        customerProductRuleMapper.updateById(rule);
        log.info("Deactivated customer product rule: {} (customerId={})", ruleName, customerId);
    }

    private void updateRuleKeywords(Long customerId, String ruleName, List<String> keywords) {
        CustomerProductRule rule = findProductRuleByName(customerId, ruleName);
        if (rule == null) {
            return;
        }
        rule.setKeywords(JsonUtils.toJson(keywords));
        customerProductRuleMapper.updateById(rule);
        log.info("Updated keywords for rule {} (customerId={})", ruleName, customerId);
    }

    private CustomerProductRule findProductRuleByName(Long customerId, String ruleName) {
        return customerProductRuleMapper.selectByCustomerId(customerId).stream()
                .filter(r -> ruleName.equals(r.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * {@code scripts/apply_batch_p0_to_db.py} 早期未指定 {@code --default-character-set=utf8mb4}，
     * 中文被双重 UTF-8 编码（规则名以 {@code æ} 开头）；随后 Java {@code billing_seed_batch_p0_v1} 插入了正确副本。
     */
    private int deleteP0ScriptMojibakeDuplicateRules() {
        return jdbcTemplate.update("""
                DELETE r FROM customer_product_rule r
                INNER JOIN customer_product_rule g
                  ON g.customer_id = r.customer_id
                  AND g.priority = r.priority
                  AND g.rule_type = r.rule_type
                  AND (g.price <=> r.price)
                  AND g.id <> r.id
                  AND g.name NOT LIKE 'æ%'
                WHERE r.name LIKE 'æ%'
                """);
    }

    /** 并行 seed 可能插入同名但无 conditions_json 的副本；保留带科室条件的那条。 */
    private int deactivateWcsrmYyDuplicateOrRules() {
        int deactivated = jdbcTemplate.update("""
                UPDATE customer_product_rule r
                INNER JOIN customer c ON c.id = r.customer_id AND c.code = 'WCSRMYY'
                SET r.is_active = 0
                WHERE r.is_active = 1
                  AND r.name IN ('手术室操作器22元/件', '手术室腹腔镜器械包187元/包')
                  AND (r.conditions_json IS NULL OR TRIM(r.conditions_json) = '' OR TRIM(r.conditions_json) = '[]')
                  AND EXISTS (
                    SELECT 1 FROM (
                      SELECT r2.customer_id, r2.name
                      FROM customer_product_rule r2
                      WHERE r2.conditions_json IS NOT NULL
                        AND TRIM(r2.conditions_json) <> ''
                        AND TRIM(r2.conditions_json) <> '[]'
                    ) conditioned
                    WHERE conditioned.customer_id = r.customer_id
                      AND conditioned.name = r.name
                  )
                """);
        int reactivated = jdbcTemplate.update("""
                UPDATE customer_product_rule r
                INNER JOIN customer c ON c.id = r.customer_id AND c.code = 'WCSRMYY'
                SET r.is_active = 1
                WHERE r.is_active = 0
                  AND r.name IN ('手术室操作器22元/件', '手术室腹腔镜器械包187元/包')
                  AND r.conditions_json IS NOT NULL
                  AND TRIM(r.conditions_json) <> ''
                  AND TRIM(r.conditions_json) <> '[]'
                  AND NOT EXISTS (
                    SELECT 1 FROM (
                      SELECT r2.customer_id, r2.name
                      FROM customer_product_rule r2
                      WHERE r2.is_active = 1
                        AND r2.conditions_json IS NOT NULL
                        AND TRIM(r2.conditions_json) <> ''
                        AND TRIM(r2.conditions_json) <> '[]'
                    ) active_conditioned
                    WHERE active_conditioned.customer_id = r.customer_id
                      AND active_conditioned.name = r.name
                  )
                """);
        return deactivated + reactivated;
    }

    /** billing_mode 回填：显式写入 PER_PACK / PER_INSTRUMENT / PACK_NAME_SUFFIX */
    private void applyBillingModeBackfillSeedFile(String file) {
        try {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                log.warn("Billing mode backfill seed file missing: {}", file);
                return;
            }
            JsonNode root = JsonUtils.getObjectMapper().readTree(resource.getInputStream());
            for (JsonNode upd : root.path("updateRules")) {
                String code = text(upd, "code");
                String ruleName = text(upd, "ruleName");
                Customer customer = customerMapper.selectByCode(code);
                if (customer == null) {
                    log.warn("Billing mode update skipped: customer {} not found", code);
                    continue;
                }
                CustomerProductRule rule = findProductRuleByName(customer.getId(), ruleName);
                if (rule == null) {
                    log.warn("Billing mode update skipped: rule {}/{} not found", code, ruleName);
                    continue;
                }
                boolean force = bool(upd, "force", false);
                if (!force && rule.getBillingMode() != null && !rule.getBillingMode().isBlank()) {
                    continue;
                }
                if (upd.has("billingMode")) {
                    rule.setBillingMode(text(upd, "billingMode"));
                }
                if (upd.has("pieceCountSource")) {
                    rule.setPieceCountSource(text(upd, "pieceCountSource"));
                }
                customerProductRuleMapper.updateById(rule);
                log.info("Billing mode updated rule {}/{} -> mode={} source={}",
                        code, ruleName, rule.getBillingMode(), rule.getPieceCountSource());
            }
            for (JsonNode bulk : root.path("bulkUpdates")) {
                String ruleType = text(bulk, "ruleType");
                String setBillingMode = text(bulk, "setBillingMode");
                if (ruleType == null || setBillingMode == null) {
                    continue;
                }
                List<String> excludeKeywords = new ArrayList<>();
                if (bulk.has("excludeKeywords")) {
                    bulk.path("excludeKeywords").forEach(node -> excludeKeywords.add(node.asText()));
                }
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT id, keywords FROM customer_product_rule "
                                + "WHERE rule_type = ? AND (billing_mode IS NULL OR billing_mode = '')",
                        ruleType);
                int updated = 0;
                for (Map<String, Object> row : rows) {
                    Object keywordsObj = row.get("keywords");
                    if (!excludeKeywords.isEmpty() && keywordsObj != null) {
                        List<String> keywords = parseStringList(String.valueOf(keywordsObj));
                        if (keywords.stream().anyMatch(excludeKeywords::contains)) {
                            continue;
                        }
                    }
                    Long ruleId = ((Number) row.get("id")).longValue();
                    updated += jdbcTemplate.update(
                            "UPDATE customer_product_rule SET billing_mode = ?, updated_at = NOW() WHERE id = ?",
                            setBillingMode, ruleId);
                }
                log.info("Billing mode bulk updated ruleType={} -> {} ({} rows)", ruleType, setBillingMode, updated);
            }
            log.info("Applied billing mode backfill seed: {}", file);
        } catch (Exception e) {
            log.error("Failed to apply billing mode backfill seed {}: {}", file, e.getMessage(), e);
        }
    }

    private void seedProductRules(Long customerId, JsonNode rules) {
        if (!rules.isArray()) {
            return;
        }
        for (JsonNode ruleNode : rules) {
            String name = text(ruleNode, "name");
            if (name == null || customerProductRuleMapper.countByCustomerIdAndName(customerId, name) > 0) {
                continue;
            }
            CustomerProductRule rule = new CustomerProductRule();
            rule.setCustomerId(customerId);
            rule.setRuleType(text(ruleNode, "ruleType", "FIXED_PRICE"));
            if (ruleNode.hasNonNull("billingMode")) {
                rule.setBillingMode(text(ruleNode, "billingMode"));
            }
            if (ruleNode.hasNonNull("pieceCountSource")) {
                rule.setPieceCountSource(text(ruleNode, "pieceCountSource"));
            }
            rule.setName(name);
            rule.setPriority(intVal(ruleNode, "priority", 100));
            if (ruleNode.hasNonNull("price")) {
                rule.setPrice(decimal(ruleNode, "price"));
            }
            if (ruleNode.hasNonNull("foldRatio")) {
                rule.setFoldRatio(decimal(ruleNode, "foldRatio"));
            }
            if (ruleNode.hasNonNull("threshold")) {
                rule.setThreshold(intVal(ruleNode, "threshold", null));
            }
            if (ruleNode.has("keywords")) {
                rule.setKeywords(toJsonArray(ruleNode.get("keywords")));
            }
            if (ruleNode.has("excludeKeywords")) {
                rule.setExcludeKeywords(toJsonArray(ruleNode.get("excludeKeywords")));
            }
            if (ruleNode.hasNonNull("temperature")) {
                rule.setTemperature(text(ruleNode, "temperature"));
            }
            if (ruleNode.hasNonNull("bagSizeEquals")) {
                rule.setBagSizeEquals(intVal(ruleNode, "bagSizeEquals", null));
            }
            if (ruleNode.hasNonNull("minInstrumentCount")) {
                rule.setMinInstrumentCount(intVal(ruleNode, "minInstrumentCount", null));
            }
            if (ruleNode.hasNonNull("maxInstrumentCount")) {
                rule.setMaxInstrumentCount(intVal(ruleNode, "maxInstrumentCount", null));
            }
            rule.setSkipPackaging(bool(ruleNode, "skipPackaging", false));
            rule.setSkipDiscount(bool(ruleNode, "skipDiscount", false));
            rule.setMatchMode(text(ruleNode, "matchMode", "first"));
            if (ruleNode.has("acceptedPrices")) {
                rule.setAcceptedPrices(ruleNode.get("acceptedPrices").toString());
            }
            if (ruleNode.has("conditionsJson")) {
                rule.setConditionsJson(ruleNode.get("conditionsJson").asText());
            }
            rule.setIsActive(true);
            customerProductRuleMapper.insert(rule);
        }
    }

    private void seedCustomerGroups(JsonNode groups) {
        if (!groups.isArray()) {
            return;
        }
        for (JsonNode groupNode : groups) {
            String name = text(groupNode, "name");
            if (name == null) {
                continue;
            }
            CustomerGroup group = findGroupByName(name);
            if (group == null) {
                group = new CustomerGroup();
                group.setName(name);
                group.setGroupType(text(groupNode, "groupType", "settlement_merge"));
                if (groupNode.has("config")) {
                    group.setConfig(groupNode.get("config").toString());
                }
                group.setIsActive(true);
                customerGroupMapper.insert(group);
            }
            seedGroupMembers(group.getId(), groupNode.path("memberCodes"));
        }
    }

    private void seedLogisticsCards(JsonNode cards) {
        if (!cards.isArray()) {
            return;
        }
        for (JsonNode cardNode : cards) {
            String customerCode = text(cardNode, "customerCode");
            if (customerCode == null) {
                continue;
            }
            Customer customer = customerMapper.selectByCode(customerCode);
            if (customer == null) {
                log.warn("Logistics card seed skipped: customer {} not found", customerCode);
                continue;
            }
            LogisticsCard existing = logisticsCardMapper.selectActiveByCustomerId(customer.getId());
            if (existing != null) {
                continue;
            }
            LogisticsCard card = new LogisticsCard();
            card.setCustomerId(customer.getId());
            card.setName(text(cardNode, "name", customer.getCanonicalName() + "物流卡"));
            double balance = cardNode.path("balance").asDouble(0);
            double initial = cardNode.has("initialBalance")
                    ? cardNode.path("initialBalance").asDouble(balance)
                    : balance;
            card.setBalance(balance);
            card.setInitialBalance(initial);
            card.setIsActive(true);
            logisticsCardMapper.insert(card);
            log.info("Seeded logistics card for {} balance={}", customerCode, balance);
        }
    }

    private CustomerGroup findGroupByName(String name) {
        List<CustomerGroup> all = customerGroupMapper.selectAll(null);
        if (all == null) {
            return null;
        }
        return all.stream().filter(g -> name.equals(g.getName())).findFirst().orElse(null);
    }

    private void seedGroupMembers(Long groupId, JsonNode memberCodes) {
        if (!memberCodes.isArray()) {
            return;
        }
        for (JsonNode codeNode : memberCodes) {
            String code = codeNode.asText();
            Customer customer = customerMapper.selectByCode(code);
            if (customer == null) {
                log.warn("Customer group member code not found: {}", code);
                continue;
            }
            if (customerGroupMemberMapper.selectByGroupAndCustomer(groupId, customer.getId()) != null) {
                continue;
            }
            CustomerGroupMember member = new CustomerGroupMember();
            member.setGroupId(groupId);
            member.setCustomerId(customer.getId());
            member.setShareRatio(1.0);
            customerGroupMemberMapper.insert(member);
        }
    }

    private static String toJsonArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return JsonUtils.toJson(values);
    }

    private static String text(JsonNode node, String field) {
        return text(node, field, null);
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText();
    }

    private static boolean bool(JsonNode node, String field, boolean defaultValue) {
        if (node == null || !node.has(field)) {
            return defaultValue;
        }
        return node.get(field).asBoolean(defaultValue);
    }

    private static int intVal(JsonNode node, String field, Integer defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue != null ? defaultValue : 0;
        }
        return node.get(field).asInt();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        return new BigDecimal(node.get(field).asText());
    }

    private boolean hasDiscountNamed(Long customerId, String name) {
        List<CustomerDiscount> discounts = customerDiscountMapper.selectByCustomerId(customerId);
        if (discounts == null) {
            return false;
        }
        return discounts.stream().anyMatch(d -> name.equals(d.getName()));
    }
}
