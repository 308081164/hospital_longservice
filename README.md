# 医疗灭菌计费管理系统

基于 Spring Boot 3 + MyBatis + MySQL（后端）与 Vue 3 + Vite（前端）的医院灭菌计费管理系统。

本文档除项目使用说明外，重点沉淀两大运维与质量体系的**事故台账、根因分析与现行防护机制**：

- **第一部分：部署/发布流水线**——「本地验证通过但生产未更新/更新不完整」类事故的根因与防护
- **第二部分：特殊计价规则与 Excel 一致性**——「规则未按 Excel 落库 / 系统能力不足」类事故的根因与验收闸门

> 阅读建议：新成员先读第一、二部分的「根因分类」与「现行机制」，发生事故时直接查「故障排查指南」。

---

## 目录

- [技术栈与快速开始](#技术栈)
- [第一部分：部署/发布流水线事故与防护体系](#第一部分部署发布流水线事故与防护体系)
  - [1.1 当前流水线总览](#11-当前流水线总览)
  - [1.2 根因分类（五类）](#12-根因分类五类)
  - [1.3 事故台账（2026-09）](#13-事故台账2026-09)
  - [1.4 现行防护机制](#14-现行防护机制)
  - [1.5 如何验证部署成功](#15-如何验证部署成功)
  - [1.6 部署故障排查指南](#16-部署故障排查指南)
- [第二部分：特殊计价规则与 Excel 一致性](#第二部分特殊计价规则与-excel-一致性)
  - [2.1 规则同步流水线](#21-规则同步流水线)
  - [2.2 根因分类（R1-R5 + 引擎/解析器）](#22-根因分类)
  - [2.3 事故台账](#23-事故台账)
  - [2.4 现行验收闸门（G0-G5）](#24-现行验收闸门g0-g5)
  - [2.5 如何验证规则正确](#25-如何验证规则正确)
  - [2.6 如何安全新增/修改规则](#26-如何安全新增修改规则)
  - [2.7 测试清单与两条测试路径](#27-测试清单与两条测试路径)
- [项目结构](#项目结构)
- [核心业务模块](#核心业务模块)
- [关键 API 端点](#关键-api-端点)
- [生产部署与运维](#生产部署与运维)

---

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot | 3.2.5 |
| MyBatis | 3.0.3 |
| MySQL | 8.0+ |
| JWT | 0.12.5 (jjwt) |
| Apache POI | 5.2.5 |
| SpringDoc OpenAPI | 2.5.0 |
| 前端 | Vue 3 + Vite + Element Plus（`frontend/`） |

## 快速开始

### 环境要求

- JDK 17+、Maven 3.6+、MySQL 8.0+（后端）
- Node 20+、pnpm 9（前端）

### 数据库初始化

```sql
CREATE DATABASE IF NOT EXISTS hospital_backend
  DEFAULT CHARSET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -u root -p hospital_backend < create_tables.sql
```

### 配置与启动

修改 `backend/src/main/resources/application-dev.yml` 数据源配置后：

```bash
# 后端（默认 http://localhost:8000，Swagger 在 /docs）
cd backend && mvn spring-boot:run

# 前端
cd frontend && pnpm install && pnpm dev
```

### 默认用户

首次启动（空数据库）自动创建测试用户（密码均为 `123456`）：`user1` / `user2`（R_USER）。生产默认管理员 `admin` / `admin123`（以服务器 `.env` 为准）。

---

## 第一部分：部署/发布流水线事故与防护体系

> 本部分回答一个问题：**为什么过去反复出现「本地/开发环境验证通过，但生产没更新或只更新了一半」，以及现在靠什么机制保证它不再发生。**

### 1.1 当前流水线总览

推送 `main` 触发 `.github/workflows/deploy.yml`（Build and Deploy），关键阶段按序执行，**任一阶段失败即整体失败**：

```
push main
  → 校验迁移清单（check-migrate-manifest.sh）+ 计费 manifest 新鲜度（check-manifest-fresh.sh）
  → 后端单测（golden rows + pricing 共 11 个测试类）
  → 前端 sanity 构建
  → 构建并推送镜像（:latest + 不可变 :<SHA> 双标签）
  → SCP compose/脚本/manifest 到服务器
  → 服务器拉取 SHA 标签镜像
  → 启动 mysql（保持运行，避免空窗）→ 强制重建 backend
  → 等待 backend 健康（最长 600s）
  → 【硬闸门】断言生产 /version 的 gitSha == 本次提交，且容器镜像 == 期望 SHA 镜像
  → 此刻才替换 frontend（旧前端一直服务到本步）
  → manifest reconcile + MySQL/API 双校验
  → Post-deploy CLI smoke（run-prod-verify.sh smoke --expect-sha）
  → 独立 job：post-deploy-parity-gate（生产对版只读闸门）
```

另有 `prod-drift-watchdog.yml` 每 20 分钟定时巡检生产漂移（见 1.4）。

### 1.2 根因分类（五类）

| 类别 | 说明 | 典型事故 |
|------|------|---------|
| **构建** | 镜像未真正重建、构建时环境变量未注入 | 00d3bd4a 版本徽章显示「—」 |
| **部署** | 部署被阻断/取消/部分完成，且无告警 | 49201f9b 被 CI 阻断无人察觉；89bc9893 取消窗口部分部署 |
| **规则** | 代码已部署但规则种子/reconcile 未生效 | 6652c4be 种子路由缺分支静默跳过 |
| **数据** | 修复只对新数据生效，历史 Job 持久化了旧结果 | c25632b9 Job #800、37050295 Job #807 |
| **缓存** | 用户浏览器停留在旧 SPA bundle | 多次「功能已部署但用户看不到」误判 |

### 1.3 事故台账（2026-09）

| # | 日期 | 提交 | 现象 | 根因类别 | 现状 |
|---|------|------|------|---------|------|
| 1 | 09-03 | `00d3bd4a` | 侧栏版本徽章已部署，生产却显示「—」 | 构建 | 已修复+硬闸门防护 |
| 2 | 09-04 | `49201f9b` | 针盒 FOLD 修复已在 main，生产多日未更新 | 部署 | 已修复+watchdog 覆盖 |
| 3 | 09-04 | `c25632b9` | extraCount 修复已部署，Job #800 仍显示旧价 | 数据 | 已修复（需知：历史 Job 要重算） |
| 4 | 09-04 | `89bc9893` | deploy run 被取消，但生产版本号恰好一致，看似成功 | 部署 | 已修复（取消窗口已消除） |
| 5 | 09-03 | `37050295` | 定价修复已部署，市五院 Job #807 需人工重算为 Job #809 才生效 | 数据 | 已修复（同上） |
| 6 | 09-03 | `f433ca33` | 市五院 3 万+行导入超时修复在本地搁置未 push，生产长期无修复 | 流程 | 已推送；watchdog 可发现版本停滞 |
| 7 | 09-04 | `6652c4be` | extraCount 种子已注册，生产 `extra_count` 仍为 NULL | 规则 | 已修复+reconcile 状态监控 |
| 8 | 多次 | — | 用户停留在旧 SPA 页面，坚称「功能没修/数据造假」，硬刷新后恢复 | 缓存 | 已修复（强制失效机制） |

#### 事故 1：`00d3bd4a` 版本徽章显示「—」（构建）

- **经过**：侧栏版本徽章功能上线后，生产徽章一直显示「—」。`/api/v1/base/version` 不返回 `gitSha`/`buildTime`。
- **根因**：后端镜像未真正重建（部署使用 `:latest` 标签，服务器拉到的仍是旧镜像），且 `APP_GIT_SHA`/`APP_BUILD_TIME` 构建参数未注入镜像。
- **修复**：CI 构建显式传入 `--build-arg GIT_SHA / BUILD_TIME`；`2438a0c5` 让徽章对旧版 `/version` 响应降级展示。
- **现行防护**：不可变 SHA 镜像标签 + 部署后版本 parity 硬闸门（见 1.4），此类「部署了但没生效」会在流水线内直接失败。

#### 事故 2：`49201f9b` 在 main 但生产未更新（部署阻断无告警）

- **经过**：针盒 FOLD 修复合入 main 后，CI 单测（`ff8dd88d` 修复的期望值问题）失败，deploy job 被阻断。没有任何告警，团队数日未察觉生产仍跑旧代码。
- **根因**：部署依赖 CI 绿灯，但 CI 红时没有主动通知；也没有「生产版本 vs main」的对版巡检。
- **修复**：`ff8dd88d` 修复单测使流水线转绿。
- **现行防护**：`prod-drift-watchdog.yml` 每 20 分钟比对生产 `/version` 的 gitSha 与 main HEAD，落后即 Actions 失败并邮件通知仓库 watcher。

#### 事故 3/5：`c25632b9` Job #800、`37050295` 市五院 Job #807（历史数据未重算）

- **经过**：规则/引擎修复部署成功后，已处理的对账 Job 仍显示旧价格，被误认为「修复没上线」。
- **根因**：计价结果在 Job 处理时持久化。引擎修复只对**重算或新任务**生效，不会回溯改写历史 Job。
- **处理**：对受影响 Job 人工触发 reprice 并 persist（Job #800；市五院 Job #807 → 重算为 Job #809）。
- **现行认知**：**部署成功 ≠ 历史数据已更新**。规则类修复上线后，必须明确哪些历史 Job 需要重算，并用重算后的新 Job 号向客户交付。行级重算能力见 `439a902d`（`POST /api/hospital-reconciliations/{jobId}/rows/{rowId}/reprice`）。

#### 事故 4：`89bc9893` 部署取消造成部分部署（部署）

- **经过**：一次 deploy run 被新 push 触发 concurrency 取消，取消点恰好落在「frontend 已 stop/rm、尚未 recreate」窗口内，造成部分部署；而后端版本恰好与期望一致，表面看「部署成功」。
- **根因**：`cancel-in-progress: true` 允许部署在任意步骤被打断；frontend 先于 backend 校验被替换。
- **修复**（`219a65b9`）：`cancel-in-progress: false`（后续 push 排队等待）；frontend 延迟到 backend 通过版本 parity 硬闸门之后才替换；backend 失败不再连带停掉旧 frontend。

#### 事故 6：`f433ca33` 修复搁置未 push（流程）

- **经过**：市五院 3 万+行大账单导入超时误报「解析失败」的修复在本地完成后，一段时间未推送 main，生产持续无修复。
- **现行防护**：watchdog 会发现「生产版本长期停滞」；团队约定修复完成后立即 push，以 CI 为准而非本地为准。

#### 事故 7：`6652c4be` 种子注册但路由缺分支（规则/静默跳过）

- **经过**：`phase-special-charge-needle-box-extracount` 种子已注册进 `BillingSeedMigrationRunner.INCREMENTAL_SEEDS`，但 `applyBatchPatchSeedFile` 的 if-else 分发链没有对应分支，种子被**静默跳过**，生产 `extra_count` 仍为 NULL，`49201f9b` 的折算路径无法生效。随后 `c25632b9` 又发现 `BillingRulesManifestReconciler` 不同步 manifest 的 `extraCount` 字段。
- **根因**：种子生效需要「注册 + 分发分支 + reconcile 字段同步」三处同时正确，任何一处缺失都无声失败。
- **现行防护**：reconcile 状态落库 marker（`billing_rules_manifest_reconcile_status`），`/version` 暴露 `rulesReconcileStatus`；watchdog 与 `hospital-cli status` 均校验；`429771df` 增加针盒 extraCount 截图案例回归与 prod spot-check。

#### 事故 8：浏览器缓存「旧页面」误判（缓存）

- **经过**：多次发生「功能已部署、生产版本已一致，但用户端看不到」，被质疑修复造假。硬刷新（Ctrl/Cmd+Shift+R）后正常。根因是 SPA 长开标签页一直运行旧 bundle，且生产 nginx 的 SPA 回退缺少 no-cache 头导致 index.html 被缓存。
- **现行防护**（`51179c57` + `304ef03c`，详见 1.4「旧版本 SPA 强制失效」）：构建指纹轮询、失配即清空存储并强制刷新、nginx 禁缓存修复。用户侧此问题已不可能静默存在。

### 1.4 现行防护机制

以下机制全部已上线（主体来自 `219a65b9`、`51179c57`、`304ef03c`）：

| 机制 | 位置 | 作用 |
|------|------|------|
| **版本 parity 硬闸门** | `deploy.yml`「Assert backend version parity」 | 部署中断言生产 `/version` 的 gitSha == 本次提交、容器镜像 == 期望 SHA 镜像，不一致即部署失败 |
| **不可变 SHA 镜像标签** | `deploy.yml` 镜像构建 | 部署只认 `:<GITHUB_SHA>` 标签，杜绝 `:latest` 拉取漂移/缓存旧镜像 |
| **禁止取消部署** | `deploy.yml` `cancel-in-progress: false` | 消除「取消落在 frontend 替换窗口」的部分部署 |
| **frontend 延后替换** | `deploy.yml` 部署脚本 | 旧前端一直服务到 backend 通过硬闸门，消除额外停机/不一致窗口 |
| **reconcile 状态落库** | `BillingRulesManifestReconciler` | reconcile 结果写入 DB marker（`billing_rules_manifest_reconcile_status`），`/version` 返回 `rulesReconcileStatus`/`rulesManifestHash`/`rulesReconciledAt` |
| **一键体检 CLI** | `./bin/hospital-cli status` | L0 健康 → L1 版本/SHA 对版 → L6 规则 hash 对版 + reconcile 状态 → L8 billing_enabled 计数，一次跑完 |
| **生产漂移巡检** | `.github/workflows/prod-drift-watchdog.yml` | 每 20 分钟比对生产版本/manifest hash/reconcile 状态与 main，漂移即失败并邮件通知（30 分钟部署在途宽限） |
| **旧版本 SPA 强制失效** | `frontend/src/utils/sys/versionEnforcer.ts` 等（`51179c57`） | 构建生成 `dist/version.json` 指纹，运行时轮询比对；失配即清空 localStorage/sessionStorage/cookies，全屏阻断遮罩倒计时后 cache-bust 硬刷新；60s 防死循环窗口 |
| **X-App-Version 响应头** | `AppVersionHeaderFilter` + axios 拦截器 | 后端所有响应（含 401）携带版本头；前端逐请求核对，已阻断时拒绝一切新请求 |
| **nginx 禁缓存修复** | `deploy/nginx-frontend-shared-net.conf` | `/version.json` 禁缓存；补齐 SPA 回退 `no-cache, no-store, must-revalidate`（旧 HTML 缓存根因之一） |
| **部署版本监听提示** | `deployVersionWatch.ts`（`304ef03c`） | 每 60s 轮询 + 标签页可见时即查，发现新版本弹常驻「立即刷新」通知 |

### 1.5 如何验证部署成功

部署后按以下顺序确认（全部通过才算「真的上线了」）：

1. **Actions 全绿**：`Build and Deploy` 与 `post-deploy-parity-gate` 两个 job 均成功。只看「deploy 步骤绿」不够。
2. **一键体检**（本地执行，推荐）：

```bash
./bin/hospital-cli status            # 或指定 --expect-sha <本次提交SHA>
```

   关注：L1 gitSha 与本次提交一致；L6 规则 manifest hash 与仓库一致、`rulesReconcileStatus` 以 `OK` 开头；L8 billing_enabled 计数与 manifest 期望一致。

3. **手动对版**（服务器或本地均可）：

```bash
curl -s http://39.102.213.51:8853/api/v1/base/version
# data.gitSha 应等于本次 main 提交；data.rulesManifestHash 应等于
# backend/src/main/resources/billing-seeds/billing-rules-manifest.json 的 manifest_hash
```

4. **前端确认**：浏览器打开 `http://39.102.213.51:8854`，侧栏左下角徽章应显示本次短 SHA。若显示旧版本，等 60s 内版本监听弹窗或强制失效机制生效（正常用户不会再卡在旧版）。
5. **规则类变更额外确认**：生产跑 `rules compare` + 关键包名 simulate 抽查（见 2.5）。
6. **涉及历史 Job 的修复**：列出受影响 Job，逐个重算（reprice+persist）并记录新 Job 号。**不要**用旧 Job 截图向客户证明修复效果。

### 1.6 部署故障排查指南

| 症状 | 最可能原因 | 处理 |
|------|-----------|------|
| Actions deploy 红，生产还是旧版 | CI 单测失败/镜像构建失败/健康检查超时 | 看失败 step 日志；修复后 **Re-run all jobs** 或重新 push（不要只 Re-run failed jobs，SCP 步骤不会重放，见 `deploy/README.md` 2.2） |
| deploy 绿但 `/version` gitSha 是旧值 | 理论上不可能（硬闸门会拦）；若见到说明闸门被绕行 | 立即检查 deploy.yml 是否被改动；手动 `bash deploy/reapply-billing-manifest-on-server.sh` 并重建 backend |
| 生产功能「没有」，但版本号正确 | 用户在旧 SPA 页面（51179c57 后应自愈） | 让用户硬刷新；若复现，查 nginx conf 是否被覆盖、`/version.json` 是否可访问 |
| 修复上线但旧 Job 数据不对 | 历史 Job 未重算（事故 3/5） | 对受影响 Job 触发 reprice+persist，交付新 Job 号 |
| 规则改了但生产算价不变 | 种子未注册/缺 dispatch 分支/reconcile 失败（事故 7） | `./bin/hospital-cli status` 看 L6；查 `BillingSeedMigrationRunner` 注册与分支；跑 `bash deploy/reapply-billing-manifest-on-server.sh` |
| watchdog 报警「生产落后 main N 个提交」 | 最近一次 deploy 失败或在途 | 查最近 Actions run；若在途等其完成，若失败按第一行处理 |
| 徽章/版本接口显示「—」 | 旧镜像未注入 GIT_SHA（事故 1 形态） | 确认 CI build-arg 存在；重建镜像部署 |

---

## 第二部分：特殊计价规则与 Excel 一致性

> 本部分回答：**为什么过去反复出现「规则与客户 Excel 不一致 / 规则漏迁 / 系统算不出 Excel 的价」，以及现在靠什么流程与闸门保证规则完整、正确落库。**
>
> 强制规范全文见 `docs/计费规则迁移与验收规范.md`；测试路径约定见 `.cursor/rules/billing-test-paths.mdc`。本部分是这两份文件的索引与事故背景补充。

### 2.1 规则同步流水线

规则从客户 Excel 到生产生效的完整链路（任一环节断裂都会静默出错，历史上每一环都出过事故）：

```
客户 Excel（docs/source/ 仓库副本须逐行一致）
  → G0 版本确认
  → 逐条建模（关键词/匹配语义/价格公式/分段/温度域/包材约定）
  → 增量种子 backend/src/main/resources/billing-seeds/phase-*.json
  → 注册 BillingSeedMigrationRunner.INCREMENTAL_SEEDS + dispatch 分支   ← 事故7断点
  → python3 scripts/billing_rules_manifest.py --write 生成 manifest     ← R3断点（已加G1硬闸门）
  → billing-rules-manifest.json（CI check-manifest-fresh 保证新鲜）
  → 部署 → backend 启动时 BillingRulesManifestReconciler reconcile 落库  ← c25632b9断点
  → DB marker（manifest hash + reconcile 状态）
  → /api/v1/base/version 暴露 → watchdog / hospital-cli status 校验
```

**铁律**：禁止在生产手工建/改规则。reconcile 以 manifest 为准，手工规则会被覆盖清除并造成回归（R4 事故）。

### 2.2 根因分类

规则侧五类根因（R1-R5，出自 `07d692f0` 与验收规范），外加引擎/解析器两类实现缺陷：

| # | 根因 | 历史事故 | 对应闸门 |
|---|------|---------|---------|
| R1 | Excel「包名称带X」（包含语义）被建模为 exact_token 词边界匹配，CJK 邻接变体词中失配 | 胶帽组件-25件、加长根管锉-6 | G2 关键词失配复扫 + G4 Excel 对账 |
| R2 | Excel 段落/条目迁移时漏迁（整院缺失或单条缺失） | 总工会 14 条固定价、人口垫片 | G4 Excel 对账（逐条） |
| R3 | 种子 ruleUpdates 补丁被 manifest 生成器静默丢弃（目标规则由字母序靠后文件/硬编码创建） | 水管膜片（12-sync 补丁丢失 15 天） | G1 生成器丢弃硬闸门 |
| R4 | 生产手工建规则绕行 manifest，配置违背惯例（FIXED_PRICE 未 skipPackaging） | 总工会固定价多加 8 元包材 | G3 rules compare + 惯例检查 |
| R5 | 用户侧 Excel 已更新但仓库副本未同步，按旧版迁移 | 人口段新旧版本差异 | G0 Excel 版本确认 |
| E1 | 计价引擎路径/分支缺陷（规则正确但算价路径错） | 纸塑袋误告警、车针折算、FNN 含包材 FOLD | 单测 + 路径 A 严格对账 + prod spot-check |
| E2 | 种子路由/reconcile 字段缺陷（规则已建但未生效） | 6652c4be 缺 dispatch 分支、c25632b9 extraCount 未同步 | reconcile 状态监控 + spot-check |
| P1 | 包名计数解析器缺陷（数量解析错导致核对跳过/误判） | 括号内连字符、紧凑复合盒双重计数、多数字包名 | 解析器单测（Python/Java 双镜像） |

### 2.3 事故台账

| # | 日期 | 提交 | 症状 | 根因 | 修复与验证 |
|---|------|------|------|------|-----------|
| 1 | 09-02 | `04f94264` `07d692f0` | 胶帽组件-25件、加长根管锉-6 等词中失配，规则不命中 | R1 | 10 院 21 个「包名称带X」关键词改词级 `@contains`；真实账单复扫零行为变化；G2 复扫 |
| 2 | 09-02 | `04f94264` | 总工会 14 条固定价漏迁，且手工补建后多加 8 元包材 | R2+R4 | 种子补齐；FIXED_PRICE 强制 `skipPackaging+skipDiscount` 惯例；G3 漂移检查 |
| 3 | 09-02 | `adc924cb` | 人口垫片规则缺失、全冠套装 46.5 口径 | R2+R5 | 确认 Excel 版本后补齐；人口 7 月基线无新增差异 |
| 4 | 09-02 | `07d692f0` | 水管膜片补丁被 manifest 生成器静默丢弃 15 天 | R3 | 生成器硬编码规则插入移到二遍补丁之前（对齐 Java @Order 110<115）；基线外新丢弃 FATAL 硬失败；37/37 补丁落库 |
| 5 | 09-04 | `49201f9b` 链 | 针盒针5合1误走高温纸塑阶梯：免包材重复计袋费、含包材袋规价偏差；extraCount 折算缺失 | E1 | 免包材/extraCount 规则改按折算件数×5.5（含包材+标准2.5袋费），折算前扣除盒数；人口/平房回归测试 |
| 6 | 09-04 | `6652c4be`→`c25632b9` | 种子已注册但生产 `extra_count` 仍 NULL，修复不生效 | E2 | 补 dispatch 分支；reconciler 同步 extraCount；`429771df` 截图案例回归 + prod spot-check |
| 7 | 09-04 | `ff8dd88d` | 49201f9b 波及带 unitPrice 的含包材 FOLD（电机厂指针/通用小件/祖研排针），少计 2.5 元袋费 | E1（修复引入回归） | 恢复 forcedPrice 分支袋费叠加，保留针盒专用路径；CI 期望 unchanged |
| 8 | 09-03 | `a31323b6` | 标准纸塑袋阶梯已含袋费仍走 packaging 模块，diff=0 也标 warning（误告警） | E1 | 标准纸塑路径跳过 packaging 模块 |
| 9 | 09-03 | `a31323b6` | 高温纸塑单包≥3 件命中车针等小件词时，全局针折叠误按实件×5.5（27.5），应为 5 合 1 折算（8.0） | E1 | 小件关键词优先走折算；保留实件路径给其他情形 |
| 10 | 09-03 | `37050295` | a31323b6 跳过 packaging 时未排除方南南等 `skipPackaging=false` 的院级 FOLD，含包材小批与免包材同价 13.5 | E1（修复引入回归） | 保留院级 FOLD 含包材规则的 packaging 模块；市五院 Job #807 重算为 #809 验证 |
| 11 | 09-04 | `5cabd96a` | 全冠套装（针-8盒-1）计数返回 null 跳过核对（应为 9）；针7（盒1）双重计数为 9（应为 8） | P1 | 括号内 -N 参与计数；紧凑复合不再叠加括号容器；新增 11 条单测（含市五院/平房区/妇幼人口生产样本） |
| 12 | 09-03 | `1d3098ca` | 多数字包名计数丢失（针拆分块双缺陷） | P1 | 解析修复并随 `00d3bd4a` 上线 |

**规律**：E1 类事故（8/9/10）显示引擎路径修复极易引入相邻路径回归——每次引擎改动必须跑全量 pricing 单测 + 受影响医院路径 A 严格对账。

### 2.4 现行验收闸门（G0-G5）

任何计费规则增删改（种子、关键词、匹配模式、价格参数）必须通过全部适用闸门（定义见 `docs/计费规则迁移与验收规范.md`）：

| 闸门 | 命令/手段 | 通过标准 | 何时必跑 |
|------|----------|---------|---------|
| G0 Excel 版本确认 | 与用户确认 Excel 路径与版本；`docs/source/` 副本逐行一致 | 用户确认 | 每次迁移前 |
| G1 生成器丢弃闸门 | `python3 scripts/billing_rules_manifest.py --write` | 零 FATAL；WARN 仅允许已知基线内条目 | 任何规则增删改 |
| G2 关键词失配复扫 | `python3 scripts/tmp_keyword_gap_scan.py`（复刻后端 exact_token 语义 × 真实账单包名） | 零词中失配（或逐项确认系故意排除） | 涉及 FOLD 关键词/匹配模式 |
| G3 清单对账 | `./bin/hospital-cli rules compare --all` | 0 漂移（含 extra 规则硬失败） | 任何规则增删改 |
| G4 Excel↔manifest 逐条对账 | openpyxl 解析合并单元格 → 逐院逐条比对（方法见 `测试用例/excel-manifest-parity-audit-20260902.md`） | 29 家逐条 PASS 或每项差异有用户书面确认 | 每次 Excel 版本更新后 |
| G5 基线回归 | 路径 A 严格对账（受影响医院账期） | 无新增差异 | 引擎/规则行为变更 |

**关键词匹配模式判定**（严格按 Excel 措辞，禁止自行发挥）：「包名称带X」→ 词级 `@contains`；完整包名 → exact_token；「针多少盒1」模式词 → exact_token + 模式词（不得改 contains，0831 报告⑦过匹配先例）；非 FOLD 规则缺省 contains。**FIXED_PRICE 强制 `skipPackaging=true` + `skipDiscount=true`**。

### 2.5 如何验证规则正确

按粒度从快到慢：

```bash
# 1. 本地清单对账（秒级）——种子/manifest/DB 期望态零漂移
./bin/hospital-cli rules compare --all

# 2. 生产一键体检（含规则 hash 与 reconcile 状态）
./bin/hospital-cli status

# 3. 生产关键包名 simulate 抽查（如 针多少盒1、指针-10/z7537=13.5、克氏针-12/Z7530=16.5）
#    见 deploy/verify-guoyao2-*.sh 与 scripts/hospital_cli.py simulate 子命令

# 4. 路径 A 严格 Excel 对账（算价正确性的最终裁判，逐家逐行）
./bin/hospital-cli audit strict-v8    # 即 scripts/special_v8_strict_excel_audit.py
```

### 2.6 如何安全新增/修改规则

严格按 `docs/计费规则迁移与验收规范.md` 六步，缺一不可：

1. **G0**：确认 Excel 版本，仓库副本与用户手中逐行一致；用户发新 Excel 先替换仓库副本再迁移。
2. **逐条建模**：关键词、匹配语义（查判定表）、价格公式、分段阈值、温度域、包材约定；合并单元格按区间向下填充后解读。
3. **种子落库**：新增 `billing-seeds/phase-*.json`，注册 `INCREMENTAL_SEEDS` **并加 dispatch 分支**（事故 7 教训：两处缺一不可）。禁止生产手工建规则。
4. **G1**：`python3 scripts/billing_rules_manifest.py --write` 零 FATAL；补丁目标不存在时把种子加入生成器延后名单，禁止改规则名绕行。
5. **G2/G4**（按需）+ **G3** 零漂移后方可提交。
6. **部署后生产验证**：rules compare + 关键包名 simulate 抽查与本地一致；涉及历史 Job 的安排重算。

### 2.7 测试清单与两条测试路径

项目有两套**完全不同**的验收口径，禁止混用（强制约定见 `.cursor/rules/billing-test-paths.mdc`）：

| | 路径 A：特殊计价医院逐家严格测试 | 路径 B：规则/billing-seed 同步检查 |
|---|---|---|
| 何时用 | 「N 家特殊计价医院严格测试/逐家严格对账/升级前后能力对比」 | 「规则是否完全同步/种子落库核对/与 manifest 对比」 |
| 医院清单 | `BillingSeedMigrationRunner.STRICT_KEEP_CODES`（26 家） | `scripts/verify-billing-seed.sh` EXPECTED 26 + 铂康参考 42 |
| 材料/账期 | 2026-08-27 基线报告相同 raw+proc 账期，锁定基线月份 | 不适用 |
| 入口 | `scripts/special_v8_strict_excel_audit.py`（`audit strict-v8`） | `verify-billing-seed.sh`、`rules compare`、simulate spot-check |
| 报告命名 | `测试用例/特殊计价严格对账报告-YYYYMMDD*` | `billing-seed-*`、`billing_rules_parity_*` |

**变更后测试清单**：

- [ ] G1 生成器零 FATAL
- [ ]（涉及关键词/匹配模式）G2 复扫零失配
- [ ] G3 `rules compare --all` 零漂移
- [ ] CI 单测全绿（golden rows + pricing 11 个测试类）
- [ ]（引擎行为变更）G5 路径 A 受影响医院基线无新增差异
- [ ] 部署后 `hospital-cli status` 全过 + 生产 simulate 抽查
- [ ] 涉及历史 Job 的，列出清单并完成重算

---

## 项目结构

```
backend/src/main/java/com/hospital/backend/
├── BackendApplication.java          # 应用入口
├── common/                           # 公共工具（Result/全局异常/JSON）
├── config/
│   ├── SecurityConfig.java           # Spring Security 配置
│   ├── DataInitializer.java          # 启动数据初始化
│   ├── BillingSeedMigrationRunner.java      # 计费种子迁移（注册+dispatch）
│   ├── BillingRulesManifestReconciler.java  # 启动时 manifest reconcile + 状态落库
│   └── AppVersionHeaderFilter.java          # X-App-Version 响应头
├── controller/                       # Auth/User/Menu/计费规则/对账
├── dto/                              # 请求/响应 DTO
├── entity/  mapper/  security/       # 实体 / MyBatis / JWT
└── service/
    ├── PricingEngine.java            # 灭菌计费规则引擎
    └── SystemVersionInfoService.java # /version 信息（gitSha/规则hash/reconcile状态）

backend/src/main/resources/
├── application*.yml                  # 配置
└── billing-seeds/                    # 计费规则种子（phase-*.json）+ billing-rules-manifest.json

frontend/src/utils/sys/               # versionEnforcer.ts / deployVersionWatch.ts（旧版SPA强制失效）
scripts/                              # billing_rules_manifest.py、hospital_cli.py、审计与验收脚本
deploy/                               # 生产部署/校验脚本与 nginx 配置
docs/计费规则迁移与验收规范.md           # 规则变更强制规范（G0-G5）
.github/workflows/                    # deploy.yml、prod-drift-watchdog.yml 等
```

## 核心业务模块

### 医院对账流程

1. **上传 Excel**：前端上传医院发货汇总表（支持多文件、多 Sheet）
2. **解析校验**：自动识别表头和明细行，解析为结构化数据
3. **规则配对**：根据激活的计费规则重新计算单价和总价
4. **差异标记**：对比原始金额与规则计算结果，标记异常行
5. **人工修正**：查看异常详情，确认后一键修正并保存（支持行级重算 `439a902d`）
6. **导出报表**：支持导出账单、结款函、分科室价格汇总等

### 灭菌计费规则引擎（`PricingEngine.java`）

- **高温/低温判定**：根据灭菌类型（低温/ETO）自动分流
- **袋型检测**：自动识别纸塑袋尺寸（10/15/20/25cm）
- **阶梯定价**：低温多件阶梯套价（5件/10件/20件套）
- **小物件折算**：边匹配识别小物件关键词，按比例折算计费（5 合 1 等）
- **FOLD 规则**：院级/全局折叠规则，支持 extraCount 折算、skipPackaging/skipDiscount
- **"双"袋规则**：自动检测"双"字并追加额外袋费
- **包装耗材收费**：包装材料额外收费叠加

## 安全说明

- 无状态 JWT 认证，双令牌机制（access_token 240 分钟 + refresh_token 7 天）
- 密码 BCrypt 加密存储；禁用 CSRF，允许跨域
- 所有响应携带 `X-App-Version` 头（CORS exposedHeaders 已放行）

## 关键 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/base/access_token` | 登录获取令牌 |
| POST | `/api/v1/base/refresh_token` | 刷新令牌 |
| GET | `/api/v1/base/health` | 健康检查 |
| GET | `/api/v1/base/version` | 系统版本（gitSha/buildTime/规则 manifest hash/reconcile 状态） |
| GET | `/api/v1/menu/menus` | 获取菜单树 |
| GET | `/api/v1/user/list` | 用户列表 |
| POST | `/api/v1/hospital/pricing-rules/save` | 保存计费规则 |
| GET | `/api/v1/hospital/pricing-rules/active` | 获取激活规则 |
| POST | `/api/v1/hospital/reconciliation/upload` | 上传对账文件 |
| POST | `/api/v1/hospital/reconciliation/process` | 执行计价处理 |
| GET | `/api/v1/hospital/reconciliation/rows` | 分页查询对账行 |
| POST | `/api/hospital-reconciliations/{jobId}/rows/{rowId}/reprice` | 行级保存并重算 |
| POST | `/api/v1/hospital/reconciliation/export` | 导出报表 |

## 生产部署与运维

- 服务器：`39.102.213.51` · 目录 `/mnt/newdisk/app/Hospital` · 前端 **8854** · API **8853**
- 推送 `main` 自动构建部署（流水线与防护见[第一部分](#第一部分部署发布流水线事故与防护体系)）
- 运维经验、P0.6 特色账单开关、MySQL/API 双校验、CI 重跑注意事项：**[deploy/README.md](./deploy/README.md)**
- 相关：`deploy/PRODUCTION-RECOVERY.md`（网关/连库故障）、`deploy/MIGRATION.md`（Secrets 与首次部署）、`docs/计费规则迁移与验收规范.md`（规则变更强制规范）
