# Docker Compose 部署

Hospital 对账系统使用 **三容器栈**：`mysql` + `backend` + `frontend`。  
数据库名统一为 **`hospital`**（本地 IDE 直连 `application-dev.yml` 仍可能使用 `hospital_backend`，与 Docker 栈无关）。

## 1. 准备环境变量

```bash
cp .env.example .env
```

编辑 `.env`，至少设置：

```env
MYSQL_ROOT_PASSWORD=强随机 root 密码
MYSQL_PASSWORD=与应用用户 hospital 一致的密码
MYSQL_DATABASE=hospital
MYSQL_USER=hospital
APP_JWT_SECRET=Base64URL 随机密钥
HTTP_PORT=1001
```

生成 JWT 密钥：

```bash
openssl rand -base64 32 | tr '+/' '-_' | tr -d '='
```

`.env` 已加入 `.gitignore`，勿提交仓库。

## 2. 本地 / 开发部署（build）

```bash
docker compose up -d --build
```

- 访问：`http://服务器IP:${HTTP_PORT}`（默认 1001）
- 健康检查：`http://服务器IP:${HTTP_PORT}/api/v1/base/health`

**宿主机直连 MySQL**（DBeaver 等）：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

默认映射 `${MYSQL_PUBLISH_PORT:-3306}:3306`。

## 3. 生产部署（预构建镜像，CI 就绪）

服务器上准备 `.env`（含 `MYSQL_*`、`APP_JWT_SECRET`、`HTTP_PORT`），并设置镜像：

```bash
export IMAGE_BACKEND=ghcr.io/<owner>/hospital-backend:latest
export IMAGE_FRONTEND=ghcr.io/<owner>/hospital-frontend:latest
```

首次或全量：

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

推荐升级顺序（与 AutoAttend 一致，确保 entrypoint 跑迁移）：

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d mysql
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate frontend
```

生产 **不暴露 MySQL 3306**；需进库时：

```bash
docker exec -it hospital-mysql mysql -u root -p
```

## 4. 日常升级（源码 build 环境）

拉代码后仅重建应用容器：

```bash
git pull
docker compose up -d --build --no-deps backend frontend
# 或强制重建 backend（会重新跑 entrypoint 迁移）
docker compose up -d --build --no-deps --force-recreate backend
docker compose up -d --no-deps frontend
```

## 5. 日志与状态

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
```

## 6. 数据备份

**MySQL：**

```bash
docker exec hospital-mysql mysqldump -u root -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --routines --triggers hospital \
  > hospital-backup-$(date +%Y%m%d).sql
```

**上传与生成文件**（Docker volumes）：

- `mysql-data` — 数据库文件
- `backend-uploads` — 用户上传
- `backend-storage` — 后端生成文件

```bash
docker volume ls | grep hospital
```

## 7. 数据库迁移机制

| 层级 | 机制 | 说明 |
|------|------|------|
| 初始化 | `schema.sql` → `/docker-entrypoint-initdb.d/` | 仅 **空数据卷首次** 创建表 |
| SQL 清单 | `backend/src/main/resources/db/migrate_manifest.txt` + entrypoint | 容器每次启动按清单执行（幂等） |
| Java | `SchemaMigrationRunner` | 启动后补列，与 SQL 清单并存 |

新增增量 SQL 时：

1. 文件放在 `backend/src/main/resources/db/`，命名建议 `schema_*migration*.sql`
2. 按顺序写入 `migrate_manifest.txt`
3. 本地校验：`bash scripts/check-migrate-manifest.sh`
4. 重启 backend：`docker compose restart backend` 或 prod 下 `force-recreate backend`

调试跳过 SQL 清单：`SKIP_DB_MIGRATE=1`（一般勿在生产使用）。

## 8. 可选：宿主机 Nginx

若需 80/443 反代而非直接暴露 `HTTP_PORT`，参考 `deploy/nginx-hospital.conf` 复制到 Nginx vhost 并 `nginx -t && reload`。

## 9. 部署后 CLI 验证

统一入口 `./bin/hospital-cli`（或 `python3 scripts/hospital_cli.py`），替代浏览器逐页点检与分散 bash 脚本。

| 场景 | 命令 |
|------|------|
| 本地 Docker（默认） | `./bin/hospital-cli smoke` |
| 本地 Docker + billing 计数 | `./bin/hospital-cli deploy-check` |
| 生产 SSH 到部署机 | `bash deploy/run-prod-verify.sh smoke` |
| 生产 SSH 全链路（smoke + deploy-check） | `bash deploy/run-prod-verify.sh full` |
| 本机直连生产 8853 | `./bin/hospital-cli smoke --mode direct --api http://HOST:8853` |
| 单院 S8（生产 Job map） | `./bin/hospital-cli s8 -H 太平人民医院 --job-map 测试用例/job_baseline_prod.json --mode direct --api http://HOST:8853` |
| 定点 S4 pricing | `./bin/hospital-cli s4 -H "黑龙江中医药大学附属第二医院（南岗）"` |
| 编排套件 | `./bin/hospital-cli verify --profile prod --level full --hospitals 太平,工程大学 --allow-import` |

**三种传输模式**：

- `docker`（默认）：`docker exec hospital-backend curl` → 容器内 `127.0.0.1:8000`
- `direct`：宿主机 HTTP → 生产 `8853` 或本地映射端口
- 环境变量：`API_BASE`（direct 模式）、`API_MODE`、`SMOKE_USER`/`SMOKE_PASS`（或 `ADMIN_PASSWORD`）

**Post-deploy smoke 端口**：CI/SSH 上 `bash deploy/run-prod-verify.sh smoke` 在容器内探测 **8000**（JSON 字段 `api_base`）；宿主机映射 **8853** 仅记录在 `api_base_host`。勿将 `API_BASE=8853` 传入 `prod-smoke-docker.sh`。

**生产 Job 映射**：`测试用例/job_baseline_prod.json` 自 stable 复制，部署后需校准：

```bash
./bin/hospital-cli jobs list -H "太平人民医院" --mode direct --api http://127.0.0.1:8853
```

与现有脚本关系：`deploy/verify-billing-api-on-server.sh` 仍保留；`deploy-check` 为其 Python 化 superset。详见 [`docs/CLI验证手册.md`](docs/CLI验证手册.md)。

---

## Future CI/CD（尚未实现，清单供后续添加 `.github/workflows/deploy.yml`）

参考 [AutoAttend deploy.yml](https://github.com/308081164/AutoAttend/blob/main/.github/workflows/deploy.yml)。

### GitHub Secrets（仓库 Settings → Secrets）

| Secret | 用途 |
|--------|------|
| `SSH_HOST` | 部署服务器 IP/域名 |
| `SSH_USER` | SSH 用户 |
| `SSH_PRIVATE_KEY` | 部署私钥 |
| `SSH_PORT` | 可选，默认 22 |
| `DEPLOY_PATH` | 服务器项目目录，如 `/opt/hospital` |
| `GHCR_USERNAME` | 可选，拉私有镜像 |
| `GHCR_READ_TOKEN` | 可选，PAT `read:packages` |

`GITHUB_TOKEN` 用于 push 镜像到 `ghcr.io/<owner>/hospital-backend|frontend`。

### CI Job 概要（push `main` / `master`）

1. **Checkout**
2. **校验迁移清单**：`bash scripts/check-migrate-manifest.sh`
3. **构建前端 sanity**（可选）：`cd frontend && pnpm install && pnpm build`
4. **docker buildx push** → `ghcr.io/<owner>/hospital-backend:latest`、`hospital-frontend:latest`
5. **SCP 到服务器**：`docker-compose.prod.yml`、`backend/`（含 `src/main/resources/db`）、`deploy/nginx-hospital.conf`
6. **SSH pull**：
   ```bash
   export IMAGE_BACKEND=ghcr.io/<owner>/hospital-backend:latest
   export IMAGE_FRONTEND=ghcr.io/<owner>/hospital-frontend:latest
   docker compose -f docker-compose.prod.yml pull
   ```
7. **SSH deploy**：
   ```bash
   docker compose -f docker-compose.prod.yml up -d mysql
   docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend
   # 轮询 backend :8000/api/v1/base/health
   docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate frontend
   ```
8. **（可选）** 同步 `deploy/nginx-hospital.conf` 并 reload Nginx

### 服务器首次准备

```bash
mkdir -p /opt/hospital && cd /opt/hospital
# 放置 .env（含 MYSQL_ROOT_PASSWORD、MYSQL_PASSWORD、APP_JWT_SECRET、HTTP_PORT）
# CI 后续 scp compose + backend/db 目录
```

### 镜像占位符（未设置时 pull 会失败，属预期）

- `IMAGE_BACKEND` → `ghcr.io/please-set/hospital-backend:latest`
- `IMAGE_FRONTEND` → `ghcr.io/please-set/hospital-frontend:latest`
