# CLI 验证手册

Hospital 部署与回归的统一命令行入口：`./bin/hospital-cli`。

## 子命令

| 子命令 | 说明 |
|--------|------|
| `smoke` | L0–L5：health → version → login → userinfo → GET job → export-v2(bill) |
| `deploy-check` | L7–L8：`billing_enabled` API 计数 vs 期望（默认 36）及 MySQL |
| `jobs list` | 按 `--hospital` 过滤 reconciliation Job |
| `s8` | 透传 `batch_s8_export_compare.py` |
| `s4` | 透传 `batch_june_system_test.py`（会 import，有副作用） |
| `verify` | 顺序执行 smoke + deploy-check；`--level full` 追加 S8/S4 |

## 通用参数

```text
--mode docker|direct     传输模式（默认 docker）
--api / --api-base URL   API 根地址
--profile local|prod     Job map 默认选择（prod → job_baseline_prod.json）
--username / --password  登录凭证（默认读 env）
--json                   机器可读报告
```

## 凭证来源

| 变量 | 用途 |
|------|------|
| `SMOKE_USER` / `SMOKE_PASS` | smoke 登录 |
| `ADMIN_PASSWORD` / `APP_ADMIN_PASSWORD` | 生产 admin 密码（`.env`） |
| `EXPECTED_BILLING_ENABLED` | deploy-check 期望计数（默认 36） |
| `DEPLOY_PATH` | 部署目录，用于加载 `.env` 与 `mysql-hospital-cli.sh` |

**禁止**在仓库写死生产密码。

## Exit code

| 码 | 含义 |
|----|------|
| 0 | 全部步骤通过 |
| 1 | 任一步骤失败（API 错误或断言失败） |
| 2 | 参数错误 |

## `--json` 报告 schema

```json
{
  "command": "smoke",
  "profile": "local",
  "mode": "docker",
  "api_base": "http://127.0.0.1:8000",
  "started_at": 1730000000.0,
  "finished_at": 1730000005.0,
  "duration_sec": 5.0,
  "ok": true,
  "steps": [
    {
      "name": "L0_health",
      "level": "L0",
      "ok": true,
      "detail": "ok",
      "data": {}
    }
  ]
}
```

## 生产一键脚本

在部署机（已 `cd $DEPLOY_PATH` 且存在 `.env`）：

```bash
bash deploy/run-prod-verify.sh smoke
bash deploy/run-prod-verify.sh full          # smoke + deploy-check
bash deploy/run-prod-verify.sh verify-full   # 含 S8/S4（慎用，S4 会写库）
```

Post-deploy smoke 在 `hospital-backend` 容器内 curl **8000**（非宿主机 8853）；JSON 含 `api_base`（容器）与 `api_base_host`（宿主机映射）。

## Job map 维护

1. **双轨基线**：本地 `job_baseline_stable.json` · 生产 `job_baseline_prod.json`（勿混用）
2. 生产校准：`python3 scripts/calibrate_prod_job_map.py --api http://<prod>:8853 --mode direct`
3. SSH 到生产，对每个医院执行 `jobs list -H "医院名"` 可人工核对

> **⚠️ 禁止用 stable Job ID 跑 prod S8/S4**（例：stable 691 哈工大 ≠ prod 77）。误用会导致大量 fail 误报。

S8/S4 在生产上 Job ID 不一致会导致误报 fail，务必先 `calibrate_prod_job_map.py`。
