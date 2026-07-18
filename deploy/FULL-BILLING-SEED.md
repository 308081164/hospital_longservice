# 测试库 / 生产库：医院与特色账单全量配置指南

本文说明如何在**可全量重部署**的前提下，先配齐测试库，再将同一套数据同步到生产。

---

## 一、数据从哪里来（三层）

| 层级 | 机制 | 内容 | 何时执行 |
|------|------|------|----------|
| 1 | `schema.sql` + `db/migrate_manifest.txt` | 表结构、增量 DDL | MySQL 首次建卷 / entrypoint |
| 2 | Java `CommandLineRunner` | 菜单、产品、~17 家基础客户、省二院硬编码规则 | 每次 backend 启动（幂等） |
| 3a | `BillingSeedMigrationRunner` | **26 家**特色账单（策略/折扣/商品规则/客户组） | **仅空库首次**（`billing_seed_profiles_v1` 不存在） |
| 3b | `BokangDataImportRunner` | 铂康 SQL：pricing rules、**全量医院名**、Top 产品样本 | 需 `IMPORT_BOKANG_DATA=1` 且挂载 `铂康/建表语句/` |

**重要：**

- `billing-seeds/*.json` 覆盖 [逐院需求登记表](../docs/逐院需求登记表/) 中 **26 家**已建模医院，不是全部 42 家文档。
- `phase-missing-bokang-ref.json` 补齐参考文件夹中 **另外 13 家**（仅创建客户，`billing_enabled=0`）。
- `MasterDataInitializer` 另保留 **13 家**非参考内置客户（胸科、东北农大、松电门诊等），状态仍为 `active`，未做停用。
- 其余医院需通过 **铂康 `hospital_reconciliation_job.sql`** 批量创建客户，或在 UI 手工补录。
- 铂康 SQL **不在 Git 仓库**（`.gitignore`），需从备份/U 盘/旧环境拷贝到 `铂康/建表语句/`。

必需文件（启用 `--bokang` 时）：

```
铂康/建表语句/hospital_pricing_rule.sql
铂康/建表语句/hospital_reconciliation_job.sql
铂康/建表语句/hospital_reconciliation_row.sql   # 可选，用于产品样本
```

---

## 二、测试库（本地 Docker）全量重置

### 步骤 1：准备铂康 SQL（推荐，否则只有 26 院特色配置）

将上述 SQL 放入项目根目录 `铂康/建表语句/`。

### 步骤 2：执行重置脚本

```bash
cd /path/to/hospital_longservice

# 仅 billing-seeds + 内置 master（无铂康时）
bash scripts/full-billing-seed-reset.sh

# 含铂康全量医院
bash scripts/full-billing-seed-reset.sh --bokang
```

脚本会：`docker compose down -v` → 重建 → 等待健康 → 跑验证。

### 步骤 3：验证

```bash
bash scripts/verify-billing-seed.sh
```

期望结果（无铂康时最低标准）：

- `sys_setting.billing_seed_profiles_v1 = true`
- `sys_setting.hardcoded_rules_migrated_v1 = true`
- 26 个 billing-seed code 均 `billing_enabled = 1`
- `customer_billing_policy` / `customer_discount` / `customer_product_rule` 有数据

有铂康时 additionally：

- `sys_setting.bokang_data_import_v1 = true`
- `customer` 总数显著增加（job 去重后的医院数）

### 步骤 4：UI 抽查

浏览器打开 `http://localhost:1001`（或 `.env` 中 `HTTP_PORT`）：

1. **主数据 → 客户**：筛选「已启用特色账单」
2. 抽 2～3 家（如省医院南岗、祖研南岗、国药主院区）确认策略、折扣范围、商品规则
3. 对账试算一单，确认计价与文档一致

### 步骤 5：导出 dump（供生产）

```bash
bash deploy/export-local-mysql.sh
# 生成 deploy/hospital-migration-YYYYMMDD.sql
```

---

## 三、生产环境更新（两种方案）

生产服务器参考：`39.102.213.51`，目录 `/mnt/newdisk/app/Hospital`，端口 **8853/8854**。

### 方案 A：导入测试库 dump（推荐）

与测试库完全一致，适合「可以覆盖生产 master 数据」的场景。

**A1. 备份生产**

```bash
cd /mnt/newdisk/app/Hospital
docker exec hospital-mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --routines --triggers --databases hospital \
  > "backup-before-billing-$(date +%Y%m%d).sql"
```

**A2. 上传测试 dump**

将本地 `deploy/hospital-migration-YYYYMMDD.sql` 上传到 `/mnt/newdisk/app/Hospital/`。

**A3. 导入**

```bash
cd /mnt/newdisk/app/Hospital
bash deploy/import-on-server.sh hospital-migration-YYYYMMDD.sql
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend
```

**A4. 验证**

```bash
curl -s http://127.0.0.1:8853/api/v1/base/health
docker exec hospital-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" hospital -e \
  "SELECT COUNT(*) customers, SUM(billing_enabled=1) billing_on FROM customer;"
```

浏览器访问 `http://39.102.213.51:8854` 做 UI 抽查。

---

### 方案 B：生产空卷 + 容器内种子（不依赖 dump）

适合测试库未导出、但生产可接受**清空 MySQL 卷**的情况。

**B1. 备份**（同 A1）

**B2. 上传铂康 SQL 到服务器**

```bash
mkdir -p /mnt/newdisk/app/Hospital/bokang-data
# 上传 hospital_pricing_rule.sql、hospital_reconciliation_job.sql 等
```

**B3. 修改生产 compose / .env**

在 `docker-compose.prod.yml` 的 backend 中增加（若尚未配置）：

```yaml
environment:
  IMPORT_BOKANG_DATA: "1"
  BOKANG_DATA_DIR: /app/bokang-data
volumes:
  - ./bokang-data:/app/bokang-data:ro
```

**B4. 清空并重跑**

```bash
cd /mnt/newdisk/app/Hospital
docker compose -f docker-compose.prod.yml down -v
docker compose -f docker-compose.prod.yml up -d
# 等待 healthy 后跑与测试库相同的 SQL 验证
```

**B5. 导入完成后** 可将 `IMPORT_BOKANG_DATA` 改回 `0`，避免每次重启重复扫描大文件。

---

### 方案 C：GitHub Actions 空卷重建（推荐自动化）

与本地 `full-billing-seed-reset.sh` 等价：**不依赖 dump 上传**，用仓库内 billing-seeds + Java Runner 重建生产库。

**前提：**

1. 代码已 merge 到 `main`（含最新 `billing-seeds/`、`migrate_manifest.txt`、镜像内种子逻辑）
2. 仓库 **Settings → Secrets and variables → Actions** 已配置：

| Secret | 用途 |
|--------|------|
| `SSH_HOST` | `39.102.213.51` |
| `SSH_USER` | SSH 用户 |
| `SSH_PRIVATE_KEY` | 部署私钥全文 |
| `DEPLOY_PATH` | `/mnt/newdisk/app/Hospital` |
| `SSH_PORT` | 可选，默认 22 |
| `GHCR_USERNAME` | 可选，拉私有镜像 |
| `GHCR_READ_TOKEN` | 可选，PAT `read:packages` |

3. 服务器 `$DEPLOY_PATH/.env` 已存在（含 `MYSQL_ROOT_PASSWORD` 等），**勿提交仓库**

**触发步骤：**

1. GitHub → **Actions** → **Reset Production Database**
2. **Run workflow**，分支选 `main`
3. `confirm` 输入框必须输入 **`RESET`**（大小写敏感）
4. `skip_build`：若刚跑过 **Build and Deploy** 且镜像已最新，可勾选跳过构建以节省时间
5. 等待 Job 完成（约 15～40 分钟，含镜像构建）

**Workflow 自动执行：**

1. 校验 `migrate_manifest.txt`
2. （默认）构建并 push `ghcr.io/<owner>/hospital-*:latest`
3. SCP `docker-compose.prod.yml`、`backend/`（含 SQL 清单）、`deploy/reset-database-on-server.sh`
4. 服务器：**mysqldump 备份** → `docker compose down -v` → `pull` → `up -d`
5. 等待 8853 健康检查 → SQL 验证（customer ≥ 61、billing_enabled ≥ 26、`billing_seed_profiles_v1`）

**备份位置：** `/mnt/newdisk/app/Hospital/backups/hospital-backup-YYYYMMDD-HHMMSS.sql`

**手动等价命令（SSH 到生产）：**

```bash
cd /mnt/newdisk/app/Hospital
export IMAGE_BACKEND=ghcr.io/<owner>/hospital-backend:latest
export IMAGE_FRONTEND=ghcr.io/<owner>/hospital-frontend:latest
bash deploy/reset-database-on-server.sh
```

> ⚠️ **`down -v` 会删除 `mysql-data`、`backend-uploads`、`backend-storage` 全部卷**；仅 `backups/` 目录下的 SQL 可恢复 MySQL。用户上传文件与生成文件无法自动恢复。

日常 **仅 schema 增量**（不清库、不重跑 billing-seeds）仍用 push `main` 触发的 **Build and Deploy**，勿用本 workflow。

---

## 四、生产已有数据、仅补种子（不清库）

若**不能**清卷，且 `billing_seed_profiles_v1` 已存在，`BillingSeedMigrationRunner` **不会重跑**。

可选操作（需 DBA 确认）：

```sql
-- 仅当确认要重新导入 billing-seeds 时
DELETE FROM sys_setting WHERE setting_key = 'billing_seed_profiles_v1';
-- 然后 force-recreate backend（会再次执行 seeds，可能与手工改动冲突）
```

更安全的做法仍是：**方案 A 整库导入** 或先在测试库验证后再覆盖生产。

---

## 五、检查清单

| 项 | 测试库 | 生产 |
|----|--------|------|
| billing_seed_profiles_v1 | ☐ | ☐ |
| hardcoded_rules_migrated_v1 | ☐ | ☐ |
| bokang_data_import_v1（若用铂康） | ☐ | ☐ |
| 26 院 billing_enabled | ☐ | ☐ |
| 生产备份已做 | — | ☐ |
| 健康检查 8853/8854 | ☐ | ☐ |
| UI 抽查 2～3 家医院 | ☐ | ☐ |

---

## 六、相关文件

- 种子 JSON：`backend/src/main/resources/billing-seeds/`
- 重置脚本：`scripts/full-billing-seed-reset.sh`
- 验证脚本：`scripts/verify-billing-seed.sh`
- 导出：`deploy/export-local-mysql.sh` / `deploy/export-local-mysql.ps1`
- 生产导入：`deploy/import-on-server.sh`
- 铂康说明：`system_docs/bokang-import-report.md`
