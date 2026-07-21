# 附一（ZYY-D1）特色账单规则 — 生产环境导入说明

## 内容

- 种子文件：`backend/src/main/resources/billing-seeds/phase-zyy-d1-fuyi.json`
- 增量 marker：`billing_seed_zyy_d1_v1`（首导）、`billing_seed_zyy_d1_p0_v2`（P0 校对修正）
- 客户确认清单：`测试用例/黑龙江中医药大学附属第一医院/特色账单功能确认.txt`

## P0 校对修正（2026-07-21）

重启 backend 后自动执行 `billing_seed_zyy_d1_p0_v2`（若 marker 不存在）：

1. **停用** `无纺布按把4.4`、`纸塑袋3件最低把价`（宽泛关键词导致 175 条误报）
2. **更新** `低温袋10cm`（+保温杯）、`低温袋15cm`（+膀胱取石钳）关键词
3. **新增** 球内注药、辅料/孔巾/腔镜包、冲洗头/橄榄头固定价、特器 w12050 等 10 条精确规则

## P0.1 关键词收窄（2026-07-21）

marker：`billing_seed_zyy_d1_p0_1_v3`

- 腔镜包整包价：`excludeKeywords: ["腹腔镜"]`（避免误匹配「腹腔镜包」）
- 王树人特器：keyword 收窄为 `王树人特器-26`
- 保温杯：独立规则 `保温杯-1Z2044`；从低温袋10cm 移除宽泛「保温杯」

期望：6 月账单 warning ≈ **45**。

## 导入方式（推荐）

部署含本 JSON 的新 backend 镜像后 **重启 backend**，`BillingSeedMigrationRunner` 会自动：

1. 检测 `billing_seed_zyy_d1_v1` 是否存在
2. 若不存在：创建/更新客户 ZYY-D1、`billing_enabled=1`、别名、策略、29 条特殊计价规则
3. 写入 marker，避免重复导入

```bash
# 生产服务器示例
cd /mnt/newdisk/app/Hospital
docker compose -f docker-compose.prod.yml pull backend
docker compose -f docker-compose.prod.yml up -d --no-deps backend
```

## 验证 SQL

```sql
SELECT setting_key, setting_value FROM sys_setting
WHERE setting_key IN ('billing_seed_zyy_d1_v1', 'billing_seed_zyy_d1_p0_v2');

SELECT code, canonical_name, billing_enabled, billing_pricing_mode
FROM customer WHERE code = 'ZYY-D1';

SELECT COUNT(*) rule_count FROM customer_product_rule r
JOIN customer c ON c.id = r.customer_id WHERE c.code = 'ZYY-D1';

SELECT policy_type, name FROM customer_billing_policy p
JOIN customer c ON c.id = p.customer_id WHERE c.code = 'ZYY-D1';
```

期望：

- marker = true
- billing_enabled = 1
- billing_pricing_mode = special_only
- product_rule **active** = 37（含 P0 新增，停用 2 条宽泛规则）
- policies：LOGISTICS + URGENT

## 重新导入（慎用）

```sql
DELETE FROM sys_setting WHERE setting_key = 'billing_seed_zyy_d1_v1';
-- 可选：删除旧规则后重启
DELETE FROM customer_product_rule WHERE customer_id = (SELECT id FROM customer WHERE code = 'ZYY-D1');
DELETE FROM customer_billing_policy WHERE customer_id = (SELECT id FROM customer WHERE code = 'ZYY-D1');
```

然后 `force-recreate backend`。

## UI 抽查

1. 主数据 → 客户 → 黑龙江中医药大学附属第一医院
2. 确认「启用特色账单」已开
3. 特殊计价规则 Tab：29 条
4. 策略 Tab：物流 45 元/趟、加急 125% 无减免
