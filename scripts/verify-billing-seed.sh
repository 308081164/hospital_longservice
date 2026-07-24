#!/usr/bin/env bash
# 验证测试/本地库的医院与特色账单种子是否就绪
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"
CONTAINER="${MYSQL_CONTAINER:-hospital-mysql}"

if [ ! -f "$ENV_FILE" ]; then
  echo "错误: 未找到 $ENV_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a && source "$ENV_FILE" && set +a

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "错误: MySQL 容器 $CONTAINER 未运行" >&2
  exit 1
fi

MYSQL=(docker exec "$CONTAINER" mysql -uroot "-p${MYSQL_ROOT_PASSWORD}" -N -B hospital)

echo "=== sys_setting 种子标记 ==="
"${MYSQL[@]}" -e "
SELECT setting_key, setting_value
FROM sys_setting
WHERE setting_key IN (
  'billing_seed_profiles_v1',
  'hardcoded_rules_migrated_v1',
  'bokang_data_import_v1',
  'ereryy_phase1_seeded_v1',
  'billing_seed_zyy_d1_standard_pricing_20260723_v1',
  'billing_seed_hrb_hx_eye_20260723_v1',
  'billing_seed_hrb_cj_standard_billing_20260723_v1',
  'billing_seed_hlfb_sf_chezhen_20260724_v1',
  'billing_seed_hrb_mhm_xizhizhen_20260723_v1',
  'billing_seed_hrb_sd_neau_kouqiang_fold_20260723_v1',
  'billing_seed_ng_fuchan_gongqiangjing_20260723_v1',
  'billing_seed_ng_fuchan_fixed_price_20260723_v2',
  'billing_seed_ng_fuchan_pdf_ocr_20260723_v1',
  'billing_seed_ng_fuchan_kuobang_wanpan_20260723_v1',
  'billing_seed_s7_bokang_pdf_ocr_20260723_v1',
  'billing_seed_s7_daowai_wailai_keywords_20260723_v1',
  'billing_seed_daowai_path_override_20260723_v1',
  'billing_seed_s7_sanjing_hulan_wailai_keywords_20260723_v1',
  'billing_seed_export_rules_20260723_v1',
  'billing_seed_fix_p0_mojibake_dup_20260723_v1',
  'billing_seed_hrb_bc_med_beauty_20260723_v1',
  'billing_seed_zyy_d1_p0_v2',
  'billing_seed_zyy_d1_p0_1_v3'
)
ORDER BY setting_key;
"

echo
echo "=== 客户统计 ==="
"${MYSQL[@]}" -e "
SELECT
  COUNT(*) AS total_customers,
  SUM(billing_enabled = 1) AS billing_enabled,
  SUM(billing_enabled = 0 OR billing_enabled IS NULL) AS billing_disabled
FROM customer;
"

echo
echo "=== 特色账单关联表 ==="
"${MYSQL[@]}" -e "
SELECT 'customer_billing_policy' AS tbl, COUNT(*) AS cnt FROM customer_billing_policy
UNION ALL SELECT 'customer_discount', COUNT(*) FROM customer_discount
UNION ALL SELECT 'customer_product_rule', COUNT(*) FROM customer_product_rule
UNION ALL SELECT 'customer_group', COUNT(*) FROM customer_group
UNION ALL SELECT 'hospital_pricing_rule', COUNT(*) FROM hospital_pricing_rule;
"

echo
echo "=== 已启用特色账单的客户（前 40 条）==="
"${MYSQL[@]}" -e "
SELECT code, canonical_name, billing_enabled, billing_pricing_mode
FROM customer
WHERE billing_enabled = 1
ORDER BY code
LIMIT 40;
"

echo
echo "=== billing-seeds 期望的 26 个 code 覆盖情况 ==="
EXPECTED=(
  BINGCHENG-YM GUOYAO-2 GUOYAO-3 GUOYAO-MAIN HRB-2ND HRB-HSZ HRB-WY HRB-WY-EM
  HULAN-HSZ HULAN-RM HULAN-TCM JIUZHOU-FK RENSHENG SHENG-YY-NG SHENG-YY-XF
  TAIPING-RM VICTORIA XINFA-HSZ YUANDONG-XN YUEMEI-FH ZUYAN-NG ZUYAN-SF ZUYAN-XA
  ZY3-DIANLI ZYY-D2-HN ZYY-D2-NG
)
missing=0
for code in "${EXPECTED[@]}"; do
  row=$("${MYSQL[@]}" -e "SELECT CONCAT(code,'|',IFNULL(billing_enabled,0)) FROM customer WHERE code='$code' LIMIT 1;" || true)
  if [ -z "$row" ]; then
    echo "  MISSING customer: $code"
    missing=$((missing + 1))
  elif [[ "$row" != *"|1" ]]; then
    echo "  NO billing_enabled: $row"
    missing=$((missing + 1))
  fi
done
if [ "$missing" -eq 0 ]; then
  echo "  OK: 26 个 billing-seed 客户均已存在且 billing_enabled=1"
else
  echo "  WARN: $missing 个 billing-seed 客户未就绪"
fi

BOKANG_MARKER=$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM sys_setting WHERE setting_key='bokang_data_import_v1';" || echo 0)
if [ "${BOKANG_MARKER:-0}" = "0" ]; then
  echo
  echo "提示: 未检测到 bokang_data_import_v1。若需导入铂康全量医院，请将 SQL 放入 铂康/建表语句/ 并设 IMPORT_BOKANG_DATA=1 后重建 backend。"
fi

echo
echo "=== 客户状态统计 ==="
"${MYSQL[@]}" -e "
SELECT status, COUNT(*) AS cnt
FROM customer
GROUP BY status
ORDER BY status;
"

echo
echo "=== 铂康参考文件夹 42 院覆盖情况 ==="
# ref_name|expected_code（避免 shell 传中文进 MySQL LIKE 的编码问题）
REF_EXPECTED=(
  "三精肾病医院|SANJING-SB"
  "南岗区先锋路社区卫生服务中心|NG-XFX-SQ"
  "南岗区妇产医院|NG-FUCHAN"
  "呼兰中医院|HULAN-TCM"
  "呼兰区红十字医院|HULAN-HSZ"
  "哈尔滨工业大学医院|HRB-HIT"
  "哈尔滨工程大学医院|HRB-HEU"
  "哈尔滨仁胜医院|RENSHENG"
  "哈尔滨冰城医疗美容医院|BINGCHENG-YM"
  "哈尔滨华夏眼科医院|HRB-HX-EYE"
  "哈尔滨市南岗区人民医院（九院）|HRB-NGJY"
  "哈尔滨市呼兰区第一人民医院|HULAN-RM"
  "哈尔滨市第二医院|HRB-2ND"
  "哈尔滨市第五医院|HRB-WY"
  "哈尔滨市第五医院（二门诊）|HRB-WY-EM"
  "哈尔滨市红十字妇产医院|HRB-HSZ"
  "哈尔滨市骨伤科医院|HRB-GUSHANG"
  "国药总医院主院区|GUOYAO-MAIN"
  "国药总医院第三院区|GUOYAO-3"
  "国药总医院第二院区|GUOYAO-2"
  "太平人民医院|TAIPING-RM"
  "奥兰医院|AL-YY"
  "悦美芳华医疗门诊医院|YUEMEI-FH"
  "新发红十字医院|XINFA-HSZ"
  "武警黑龙江省总队医院|WJ-HLJ-ZD"
  "祖研-黑龙江省中医医院（三辅院区）|ZUYAN-SF"
  "祖研-黑龙江省中医医院（南岗院区）|ZUYAN-NG"
  "祖研-黑龙江省中医医院（香安院区）|ZUYAN-XA"
  "道外区人民医院|DAOWAI-RM"
  "香坊中医院|XF-ZYY"
  "黑龙江东大肛肠|DD-DC"
  "黑龙江中医药大学附属第一医院|ZYY-D1"
  "黑龙江中医药大学附属第二医院（南岗）|ZYY-D2-NG"
  "黑龙江中医药大学附属第二医院（哈南分院）|ZYY-D2-HN"
  "黑龙江九洲妇科医院|JIUZHOU-FK"
  "黑龙江省中医药大学附属第三医院（电力）|ZY3-DIANLI"
  "黑龙江省医院（南岗院区）|SHENG-YY-NG"
  "黑龙江省医院（香坊院区）|SHENG-YY-XF"
  "黑龙江省社会康复医院|SHKF-YY"
  "黑龙江省第二医院（南岗院区）|ERYY-NG"
  "黑龙江省第二医院（松北院区）|ERYY-SB"
  "黑龙江省远东心脑血管医院|YUANDONG-XN"
  "黑龙江维多利亚妇产医院|VICTORIA"
)
ref_missing=0
ref_matched=0
for entry in "${REF_EXPECTED[@]}"; do
  ref_name="${entry%%|*}"
  code="${entry##*|}"
  row=$("${MYSQL[@]}" -e "SELECT code FROM customer WHERE code='$code' LIMIT 1;" || true)
  if [ -z "$row" ]; then
    echo "  MISSING ref: $ref_name (expected $code)"
    ref_missing=$((ref_missing + 1))
  else
    ref_matched=$((ref_matched + 1))
  fi
done
echo "  matched=$ref_matched missing=$ref_missing (expect 42, 0 missing)"

echo
echo "=== 内置额外客户（非 42 院参考，期望 status=inactive）==="
EXTRA_CODES=(
  HRB-XK NEAU-YY HRB-SD-MB HRB-AM HRB-ASM HRB-BY HRB-CY HRB-BNXS HRB-CJ
  WCSRMYY YMYXZX HY-HYY ZYY-DSFY
  HL-ZGH HLFB-SF HRB-DLFB HRB-HTFH HRB-MHM ZXYSJT
)
extra_ok=0
extra_bad=0
for code in "${EXTRA_CODES[@]}"; do
  row=$("${MYSQL[@]}" -e "SELECT CONCAT(code,'|',canonical_name,'|',status) FROM customer WHERE code='$code' LIMIT 1;" || true)
  if [ -z "$row" ]; then
    echo "  MISSING extra: $code"
    extra_bad=$((extra_bad + 1))
  elif [[ "$row" != *"|inactive" ]]; then
    echo "  NOT inactive: $row"
    extra_bad=$((extra_bad + 1))
  else
    extra_ok=$((extra_ok + 1))
  fi
done
if [ "$extra_bad" -eq 0 ]; then
  echo "  OK: 19 个非参考额外客户均已存在且 status=inactive (matched=$extra_ok)"
else
  echo "  WARN: $extra_bad 个非参考额外客户未就绪 (inactive=$extra_ok)"
fi
