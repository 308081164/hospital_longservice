# 黑龙江省第二医院（南岗 + 松北）Docker 本地测试记录

**测试时间：** 2026-07-17  
**工作目录：** `hospital-all-master`  
**测试人：** 自动化脚本 / Agent

## 1. 使用的 Compose 与命令

| 文件 | 用途 |
|------|------|
| `docker-compose.yml` | 本地完整栈（mysql + backend + frontend），本地 build |
| `docker-compose.dev.yml` | 可选：MySQL 3306 映射到宿主机 |
| `docker-compose.prod.yml` | 生产预构建镜像；默认 `HTTP_PORT=8854`、`BACKEND_PUBLISH_PORT=8853` |
| `.env` / `.env.example` | MySQL、JWT、`HTTP_PORT` 等 |

**启动命令（本次实际使用）：**

```powershell
cd D:\Hui_Files\MyProjects\guangsha_technology\hospital-all-master\hospital-all-master
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

修复启动阻塞后仅重建后端：

```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build backend frontend
```

**说明：** 根目录 `.env` 中 `HTTP_PORT=8080`（非 DEPLOY.md 默认 1001）。

## 2. 服务 URL

| 服务 | URL | 说明 |
|------|-----|------|
| 前端 | http://localhost:8080 | 浏览器登录入口 |
| 健康检查 | http://localhost:8080/api/v1/base/health | 经 Nginx 反代 backend |
| 后端 API 前缀 | http://localhost:8080/api/ | 本地 compose **未**映射 backend 8000 到宿主机 |
| Swagger | 容器内 http://127.0.0.1:8000/docs（HTTP 200） | 需 `docker exec hospital-backend curl ...` 或改用 prod compose 暴露 8853 |

## 3. 登录凭据（种子默认）

| 用户名 | 密码 | 来源 |
|--------|------|------|
| **admin** | **admin123** | `AdminUserInitializer` / `DataInitializer`（超级管理员，可编辑客户配置） |
| user1 | 123456 | README / `DataInitializer`（业务员视图，客户编辑只读） |
| user2 | 123456 | 同上 |

详见 [admin-account-and-billing-permissions.md](./admin-account-and-billing-permissions.md)。

登录 API：`POST /api/v1/base/access_token`，响应字段为 `data.access_token`（snake_case）。

## 4. 省二院种子数据核对

### 4.1 需求文档

- `docs/逐院需求登记表/黑龙江省第二医院（南岗院区）.md`
- `docs/逐院需求登记表/黑龙江省第二医院（松北院区）.md`

### 4.2 代码与 JSON 种子

| 来源 | 内容 |
|------|------|
| `MasterDataInitializer` | 创建 `ERYY-NG` / `ERYY-SB` 客户、别名、0.7 折扣 |
| `HardcodedRulesMigrationRunner` | 两院 fixedPrices + Phase1 hybrid / any_price 多报价 + 物流策略 |
| `BillingSeedMigrationRunner` | 加载 `billing-seeds/*.json`（通用批次，非省二专用） |
| `backend/src/test/resources/seed/ereryy-billing-profile.json` | 省二 Phase1 配置备份（测试资源） |

### 4.3 MySQL 客户与规则（库 `hospital`）

| ID | Code | 名称 | billing_enabled | billing_pricing_mode | product_rules 条数 |
|----|------|------|-----------------|----------------------|-------------------|
| 12 | ERYY-NG | 黑龙江省第二医院（南岗区） | 1 | hybrid | 12 |
| 13 | ERYY-SB | 黑龙江省第二医院（松北区） | 1 | hybrid | 12 |

两院均含：

- **折扣策略**（billing policy）
- **物流策略** `feePerTrip=80.5`
- **3 条 any_price 多报价规则**（小腔包 / 钉 / 3.6空心钉工具包）

示例（南岗 any_price）：

- 南岗小腔包多报价：`[49.7, 53.55]`
- 南岗钉多报价：`[140, 35]`（排除空心钉关键词）
- 南岗3.6空心钉工具包多报价：`[205.45, 190.05]`

## 5. API 功能测试结果

| 用例 | 结果 | 备注 |
|------|------|------|
| `GET /api/v1/base/health` | 通过 | `status: healthy` |
| `POST /api/v1/base/access_token` (user1) | 通过 | |
| `GET /api/v1/customers` | 通过 | 含 ERYY-NG / ERYY-SB |
| `GET /api/v1/customers/12` | 通过 | 含 12 条 product_rules、别名、折扣 |
| `GET /api/v1/customers/13` | 通过 | 同上 |
| `GET /api/v1/customers/{id}/billing-policies` | 通过 | 各 2 条（物流 + 折扣类） |
| `GET /api/v1/customers/{id}/product-rules` | 通过 | 各 12 条 |
| `POST /api/v1/billing-rules/simulate` | 部分通过 | 需 `ruleId=2`（标准灭菌计费规则）且 sampleRow 使用 camelCase 并含 `totalPrice` 等字段 |

### 5.1 规则试算示例（通过）

**南岗** `customerId=12`，`packName=小腔包/Z7526`，`unitPrice/totalPrice=53.55`：

- `code=200`，`status=unchanged`
- `matched_rule_id=42`（南岗小腔包多报价）
- `matched_price_option=53.55`，`expected_unit_price=49.7`
- `match_chain` 含 special_rule → pricing_engine → billing_policies

**松北** `customerId=13`，`3.6空心钉工具包`，`unitPrice=190.05`：HTTP 200，`status=unchanged`（多报价规则命中）。

**失败样例：** 仅传 `pack_name`/`unit_price` 且无 `ruleId` → 400「客户未绑定默认计价规则」；缺 `totalPrice` → 500 NPE。

## 6. 启动问题与修复

| 问题 | 原因 | 处理 |
|------|------|------|
| backend unhealthy，Spring 无法启动 | `HospitalReconciliationController` 与 `ExternalInstrumentController` 重复映射 `POST /api/hospital-reconciliations/{jobId}/external-instruments/import` | **已做最小修复：** 删除 `HospitalReconciliationController` 中 INT-04 重复方法，保留 `ExternalInstrumentController` 实现 |

## 7. 建议的手动 UI 测试步骤

1. 打开 http://localhost:8080 ，使用 user1 / 123456 登录。
2. **主数据 → 客户**：搜索「省二」或 code `ERYY-NG` / `ERYY-SB`，确认 hybrid 计费已开启。
3. 进入客户详情 **规则工具 / 试算**：用与小腔包、钉、3.6 工具包相关的样例行验证多报价 acceptance。
4. **医院对账**：上传省二院区样例 Excel（若有 `backend/测试表格` 或铂康样表），选对院区别名，跑计价与差异标记。
5. 若需直连 Swagger：在 `.env` 增加 backend 端口映射（参考 `docker-compose.prod.yml` 的 `BACKEND_PUBLISH_PORT`）或临时 `docker compose` override。

## 8. 容器状态（测试结束时）

```text
hospital-mysql     healthy   0.0.0.0:3306->3306
hospital-backend   healthy   8000/tcp（仅 Docker 网络）
hospital-frontend  healthy   0.0.0.0:8080->80
```