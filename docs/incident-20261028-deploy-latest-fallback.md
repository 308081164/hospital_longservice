# 事故报告：部署回退到 :latest（2026-10-28）

## 摘要

- **发生**：2026-10-28 14:20–15:12（北京时间）
- **影响**：生产 backend 被换成 `:latest` 里的 2026-09-04 旧镜像（gitSha `429771df`），10.27/10.28 的规则改动在生产**失效**约 50 分钟；前端仍为 `333c2dfa`。
- **发现**：部署卡在「Transfer service images」时，`/api/v1/base/version` 返回 `429771df`（9 月构建）。
- **恢复**：回滚到留档 `20261028-133550`（`7a2a7f2f`）→ 前向部署到 `bdf9a40d`。
- **最终状态**：`gitSha=bdf9a40d`、`baseline=4e82862f`、`verify=true`、`quarantine=0`、`rules compare` 45/45 零漂移。

## 时间线

| 时间 | 事件 |
|---|---|
| 14:18 | 推送 `af7b9e38`，部署 40643190967 开始 |
| 14:2x | 镜像传输步骤卡住（>25 分钟） |
| 14:47 | 发现生产 `/version` = `429771df`（9 月构建），公网 8853 间歇不可达 |
| 14:59 | 取消卡住的 40643190967 |
| 15:02 | 诊断：backend 容器镜像为 `hospital-backend:latest`，磁盘 48% 正常，MySQL 正常 |
| 15:0x | 回滚到 `20261028-133550` → 生产恢复 `7a2a7f2f` |
| 15:12 | 另一未含护栏的部署（`6264071f`）完成 |
| 15:18 | 含护栏的 `bdf9a40d` 部署完成，最终态确认 |

## 根因

1. `docker save | ssh docker load` 传输步骤在本次异常缓慢/卡住；
2. 后续「Deploy stack on server」在此状态下仍执行 `docker compose up -d --force-recreate backend`；
3. 该步骤 `CI_IMAGE_BACKEND` 为空时，`source .env` 里的 `IMAGE_BACKEND=...:latest` 生效，compose 使用服务器本地旧 `:latest` 镜像；
4. 旧镜像（9 月）与当前 DB/规则不匹配，但仍以 healthy 状态运行，`gitSha` 变更无人拦截（parity 硬闸门在被取消的步骤之后）。

## 已实施的修复

| 修复 | 位置 |
|---|---|
| backend/frontend 重建前，CI 镜像标签为空即中止 | `.github/workflows/deploy.yml` |
| `docker load` 后校验服务器上两个镜像确实存在，缺失则中止（不再进入 compose） | `deploy/transfer-images-via-ssh.sh` |
| 新增手动恢复 workflow（诊断 / 回滚到留档） | `.github/workflows/prod-recover.yml` |
| 纯文档变更不再触发部署（降低无谓重启风险） | `.github/workflows/deploy.yml` 的 `paths-ignore` |

## 未解决 / 待观察

- 传输步骤缓慢的**根本原因未定位**（网络或 GHCR 拉取）；本次以护栏把「失败」变为「安全失败」，但慢的问题仍在。
- 建议后续：给传输加超时与重试上限、或在服务器侧直接从 GHCR pull（已验证 `docker-login-ghcr.sh` 存在）。

## 教训

> 部署脚本中**任何** `:latest` 兜底都是定时炸弹：镜像未就绪时必须失败，而不是用旧镜像顶替。
