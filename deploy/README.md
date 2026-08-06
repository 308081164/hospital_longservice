# 生产部署与运维（经验汇总）

> 服务器：`39.102.213.51` · 目录：`/mnt/newdisk/app/Hospital` · 前端 **8854** · API **8853**  
> 默认管理员：`admin` / `admin123`（以服务器 `.env` 为准）

本文档汇总 **GitHub Actions 部署**、**P0.6 特色账单开关**、**MySQL 双库/连库不一致** 等已踩坑与标准做法。更细的网关绕行见 [PRODUCTION-RECOVERY.md](./PRODUCTION-RECOVERY.md)。

---

## 1. 架构要点（当前 prod）

- `docker-compose.prod.yml`：**backend / frontend 与 mysql 共享网络栈**（`network_mode: service:mysql`）。
- backend **必须**连 `127.0.0.1:3306`（compose 内已写死，**不要**再让 `.env` 的 `MYSQL_HOST=172.20.0.1` 覆盖，否则 API 与 SQL 脚本可能连不同库）。
- 对外：`8853` → 容器内 backend `8000`；`8854` → nginx 静态页 + 反代 `/api/`。

---

## 2. 自动化流水线（main push）

| 工作流 | 作用 |
|--------|------|
| **Build and Deploy** | 构建镜像 → SCP 配置与 `deploy/` 脚本 → 拉镜像 → 启 mysql → **P0.6 SQL** → backend → 健康检查 → frontend → **reapply + 校验** |
| **Apply Billing P0.6 on Production** | 仅同步 billing 脚本 + 执行 `reapply-billing-p0-6-on-server.sh`（可手动 dispatch） |

### 2.1 部署后必须通过的校验

脚本 `deploy/verify-billing-on-server.sh` + `deploy/verify-billing-api-on-server.sh`：

- MySQL（与 backend **同一连库目标**）：`billing_enabled=1` 为 **24**，P0.6 marker `billing_seed_batch_p0_6_v1` 存在。
- API：`GET /api/v1/customers` 统计 `billing_enabled=1` 为 **24**，且 **与 MySQL 计数一致**（UI 客户管理读此接口）。

### 2.2 GitHub Actions 重跑失败 job 的注意点

- **只「Re-run failed jobs」** 时，**不会**再执行前面已成功的步骤（例如首次 SCP）。
- 若服务器上缺少新脚本（如 `apply-p0-6-billing-sql.sh`），会出现 `No such file or directory`。
- **现行做法**：在 reapply 前增加 **Sync billing deploy scripts**；`reapply-billing-p0-6-on-server.sh` 内对 P0.6 导入做了 **多级 fallback**（见下文脚本清单）。
- 建议：部署异常时优先 **Re-run all jobs** 或重新 push，不要只重跑最后一步。

---

## 3. P0.6 特色账单开关（24 院）

| 文件 | 说明 |
|------|------|
| `deploy/sql/p0-6-billing-toggle.sql` | 幂等 SQL：24 院 `billing_enabled=1`，其余 `0`，写入 marker |
| `deploy/mysql-hospital-cli.sh` | 按 **运行中 backend 的 MYSQL_* ** 或 `.env` 执行 SQL/查询（避免 docker exec 打错库） |
| `deploy/apply-p0-6-billing-sql.sh` | 仅导入 P0.6 SQL 并检查 24 |
| `deploy/reapply-billing-p0-6-on-server.sh` | 导入 SQL → 重启 backend → MySQL + API 校验 |
| `deploy/verify-billing-on-server.sh` | MySQL 统计 + 调用 API 校验 |

**手动在服务器执行**（需已 SCP 或 git pull 同步 `deploy/`）：

```bash
cd /mnt/newdisk/app/Hospital
bash deploy/reapply-billing-p0-6-on-server.sh
```

P0.6 **只改客户开关**，**不会**同步全部 `customer_product_rule`；规则与本地一致需另走 [FULL-BILLING-SEED.md](./FULL-BILLING-SEED.md) / `sync-billing-to-prod.sh`。

---

## 4. 故障现象 → 原因 → 处理

### 4.1 CI 显示 MySQL 24 院启用，浏览器仍约 10 院

| 原因 | 处理 |
|------|------|
| P0.6 SQL 用 `docker exec hospital-mysql` 写入 **Docker 卷**，backend 经 `.env` 连 **宿主机 3307/外库** | 使用 `mysql-hospital-cli.sh`；compose 固定 backend `127.0.0.1:3306`；部署后看 API 校验 |
| 看错列：「档案状态」≠「特色账单」 | 客户管理看 **特色账单** 列或「已启用 X / 总数」 |
| 前端旧缓存 | 硬刷新；确认 Deploy 已更新 frontend 镜像 |

### 4.2 `apply-p0-6-billing-sql.sh: No such file or directory`

| 原因 | 处理 |
|------|------|
| 服务器未 SCP 新脚本 / 只重跑了失败 step | 完整重跑 Deploy 或跑 **Apply Billing P0.6**；依赖 `reapply` 内 fallback |

### 4.3 `manifest unknown` / 拉取 `please-set` 镜像

`.env` 中 **必须**取消注释 `IMAGE_BACKEND` / `IMAGE_FRONTEND` 为 ghcr 真实地址。

### 4.4 backend 连不上 mysql（No route to host）

见 [PRODUCTION-RECOVERY.md](./PRODUCTION-RECOVERY.md) 网关绕行；**若已用共享网络栈**，应优先 `127.0.0.1:3306`，而非 172.20.0.1:3307。

---

## 5. 快速自检命令（SSH 上）

```bash
cd /mnt/newdisk/app/Hospital
curl -sf http://127.0.0.1:8853/api/v1/base/health && echo OK
bash deploy/mysql-hospital-cli.sh --print-target
docker inspect hospital-backend --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_'
bash deploy/verify-billing-on-server.sh
```

浏览器：`http://39.102.213.51:8854` → 客户管理 → **特色账单已启用 24 / …**。

---

## 6. 相关文档

- [MIGRATION.md](./MIGRATION.md) — CI Secrets、首次部署
- [PRODUCTION-RECOVERY.md](./PRODUCTION-RECOVERY.md) — 登录超时、网关绕行
- [FULL-BILLING-SEED.md](./FULL-BILLING-SEED.md) — 规则库全量同步

---

## 7. 变更记录（运维向）

| 日期 | 摘要 |
|------|------|
| 2026-07-22 | 铂康缺失包/特殊价格单导入测试用例；HRB-HEU 与工业/工程大学澄清；见 `测试用例/铂康材料与医院对照.md` |
| 2026-07-22 | 固定 backend 连 `127.0.0.1:3306`；P0.6 与 API 双校验；reapply 前强制 SCP + 导入 fallback；客户列表展示特色账单计数 |
