# 生产环境恢复手册（登录超时 / MySQL 连不通）

> **适用服务器**：`39.102.213.51`  
> **部署目录**：`/mnt/newdisk/app/Hospital`  
> **对外端口**：前端 `8854`，后端 API `8853`  
> **默认登录**：`admin` / `admin123`

---

## 一、问题现象

| 现象 | 说明 |
|------|------|
| 浏览器登录无响应 / 超时 | `POST /api/v1/base/access_token` 30s 无返回 |
| backend 日志 | `HikariPool Connection timed out` 或 `[entrypoint] 无法在约 3 分钟内连接 MySQL` |
| 容器内测连通 | `docker exec hospital-backend ... /dev/tcp/mysql/3306` → `No route to host` |
| 宿主机测 MySQL | `timeout 3 bash -c "echo >/dev/tcp/172.20.0.x/3306"` → **OK** |
| `docker compose up` 失败 | `manifest unknown`（镜像名变成 `ghcr.io/please-set/...`） |

---

## 二、根因说明

### 2.1 容器间网络不通（主要）

- backend 与 mysql 在同一 Docker 网络、DNS 解析正确，但 **容器 → 容器 TCP 3306 被拦**（errno 113）。
- 宿主机 → MySQL 容器 IP 正常，说明 MySQL 本身健康。
- `firewalld` 未运行时可排除防火墙；需用 **网关端口绕行** 恢复服务。

### 2.2 镜像变量未生效（次要）

- `.env` 中 `IMAGE_BACKEND` / `IMAGE_FRONTEND` 若被注释，compose 会拉取占位镜像 `ghcr.io/please-set/...`，导致 `manifest unknown`。
- 本地已有镜像时可用 `--pull never` 启动。

### 2.3 compose 写死连库地址（配置问题）

- 旧版 `docker-compose.prod.yml` 中 `MYSQL_HOST: mysql`、`MYSQL_PORT: "3306"` 为硬编码。
- **仅改 `.env` 无效**，必须同步修改 compose（或使用 gateway overlay）。

---

## 三、修复思路（网关绕行）

```
backend 容器 ──→ 172.20.0.1:3307（Docker 网桥网关）
                      │
                      ▼
              宿主机端口映射 3307 → mysql 容器 3306
```

1. MySQL 容器映射 `3307:3306` 到宿主机。
2. backend 通过 `.env` 连接 `172.20.0.1:3307`（网关 IP，重建网络后需重新确认）。
3. `IMAGE_*` 必须在 `.env` 中取消注释。

---

## 四、完整 `.env`（上传到服务器）

**路径**：`/mnt/newdisk/app/Hospital/.env`  
**权限**：`chmod 600 .env`  
**注意**：含密钥，勿提交 Git。

```env
# ============================================================
# 服务器生产环境 .env
# 使用方法：上传到 /mnt/newdisk/app/Hospital/.env
# 上传后：chmod 600 /mnt/newdisk/app/Hospital/.env
# ============================================================

# ---------- MySQL（网关绕行：backend 经 Docker 网桥访问映射端口）----------
# 网关 IP 需在服务器执行下面命令确认：
#   docker network inspect hospital-reconciliation_hospital-net -f '{{range .IPAM.Config}}{{.Gateway}}{{end}}'
MYSQL_HOST=172.20.0.1
MYSQL_PORT=3307
MYSQL_PUBLISH_PORT=3307
MYSQL_DATABASE=hospital
MYSQL_USER=hospital
MYSQL_PASSWORD=<填写实际密码>
MYSQL_ROOT_PASSWORD=<填写实际 root 密码>

# ---------- 应用 ----------
APP_JWT_SECRET=<填写 Base64URL 密钥>
HTTP_PORT=8854
BACKEND_PUBLISH_PORT=8853
APP_COMPANY_BANK_ACCOUNT=
APP_COMPANY_BANK_NAME=

# ---------- 生产镜像（必须取消注释，否则 up 会拉 please-set 占位镜像）----------
IMAGE_BACKEND=ghcr.io/308081164/hospital-backend:latest
IMAGE_FRONTEND=ghcr.io/308081164/hospital-frontend:latest

# 跳过 entrypoint SQL 迁移（一般勿设）
# SKIP_DB_MIGRATE=1
```

---

## 五、完整 `docker-compose.prod.yml`（关键修改）

在服务器 `/mnt/newdisk/app/Hospital/docker-compose.prod.yml` 中做 **两处修改**。

### 5.1 `mysql` 服务：增加端口映射

在 `command:` 行之后、`volumes:` 之前插入：

```yaml
    ports:
      - "${MYSQL_PUBLISH_PORT:-3307}:3306"
```

### 5.2 `backend` 服务：连库地址读 `.env`

将：

```yaml
      MYSQL_HOST: mysql
      MYSQL_PORT: "3306"
```

改为：

```yaml
      MYSQL_HOST: ${MYSQL_HOST:-mysql}
      MYSQL_PORT: ${MYSQL_PORT:-3306}
```

### 5.3 修改后的完整文件（可直接覆盖）

```yaml
# 生产环境：仅拉取预构建镜像，不在服务器上 build。
# 使用前在服务器 .env 或 export 中设置：
#   IMAGE_BACKEND=ghcr.io/<owner>/hospital-backend:latest
#   IMAGE_FRONTEND=ghcr.io/<owner>/hospital-frontend:latest
#
# 网关绕行：.env 中设 MYSQL_HOST=172.20.0.1 MYSQL_PORT=3307 MYSQL_PUBLISH_PORT=3307
# 网络正常后改回 MYSQL_HOST=mysql MYSQL_PORT=3306 并移除 mysql ports 映射。

name: hospital-reconciliation

x-logging: &default-logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"

services:
  mysql:
    image: mysql:8.0
    container_name: hospital-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE:-hospital}
      MYSQL_USER: ${MYSQL_USER:-hospital}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      TZ: Asia/Shanghai
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    ports:
      - "${MYSQL_PUBLISH_PORT:-3307}:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./backend/src/main/resources/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 30s
    networks:
      - hospital-net
    logging: *default-logging

  backend:
    image: ${IMAGE_BACKEND:-ghcr.io/please-set/hospital-backend:latest}
    container_name: hospital-backend
    restart: unless-stopped
    stop_grace_period: 30s
    ports:
      - "${BACKEND_PUBLISH_PORT:-8853}:8000"
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      MYSQL_HOST: ${MYSQL_HOST:-mysql}
      MYSQL_PORT: ${MYSQL_PORT:-3306}
      MYSQL_DATABASE: ${MYSQL_DATABASE:-hospital}
      MYSQL_USER: ${MYSQL_USER:-hospital}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      APP_JWT_SECRET: ${APP_JWT_SECRET}
      APP_COMPANY_BANK_ACCOUNT: ${APP_COMPANY_BANK_ACCOUNT:-}
      APP_COMPANY_BANK_NAME: ${APP_COMPANY_BANK_NAME:-}
      TZ: Asia/Shanghai
    volumes:
      - backend-uploads:/app/uploads
      - backend-storage:/app/storage
      - ./backend/src/main/resources/db:/app/db:ro
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1:8000/api/v1/base/health >/dev/null || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 40
      start_period: 120s
    networks:
      - hospital-net
    logging: *default-logging

  frontend:
    image: ${IMAGE_FRONTEND:-ghcr.io/please-set/hospital-frontend:latest}
    container_name: hospital-frontend
    restart: unless-stopped
    ports:
      - "${HTTP_PORT:-8854}:80"
    depends_on:
      backend:
        condition: service_started
    networks:
      - hospital-net
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1/ >/dev/null || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 10s
    logging: *default-logging

volumes:
  mysql-data:
  backend-uploads:
  backend-storage:

networks:
  hospital-net:
    driver: bridge
```

---

## 六、部署步骤

### 6.1 本地上传（在开发机执行）

```bash
# 上传 .env（勿提交 Git）
scp deploy/server.env root@39.102.213.51:/mnt/newdisk/app/Hospital/.env

# 上传 compose（若已在服务器手改可跳过）
scp docker-compose.prod.yml root@39.102.213.51:/mnt/newdisk/app/Hospital/

ssh root@39.102.213.51 'chmod 600 /mnt/newdisk/app/Hospital/.env'
```

### 6.2 服务器确认网关 IP

```bash
cd /mnt/newdisk/app/Hospital
docker network inspect hospital-reconciliation_hospital-net -f '{{range .IPAM.Config}}{{.Gateway}}{{end}}'
```

若输出不是 `172.20.0.1`，修改 `.env` 中 `MYSQL_HOST=` 为实际值。

### 6.3 启动服务

```bash
cd /mnt/newdisk/app/Hospital
set -a && source .env && set +a

# 本地已有镜像时加 --pull never，避免 manifest unknown
docker compose -f docker-compose.prod.yml up -d --pull never --force-recreate mysql
sleep 30
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate backend
sleep 90
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate frontend
```

### 6.4 若需从 GHCR 拉新镜像

```bash
echo "<GitHub PAT read:packages>" | docker login ghcr.io -u 308081164 --password-stdin
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

---

## 七、验证清单

```bash
cd /mnt/newdisk/app/Hospital
set -a && source .env && set +a

# 1. 容器状态
docker compose -f docker-compose.prod.yml ps

# 2. 网关端口（应 GW_OK）
docker exec hospital-backend sh -c \
  'timeout 3 bash -c "echo >/dev/tcp/172.20.0.1/3307" && echo GW_OK || echo GW_FAIL'

# 3. MySQL ping（entrypoint 同款）
docker exec hospital-backend sh -c \
  'mysqladmin ping -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" 2>&1'

# 4. backend 日志（应出现 MySQL 已就绪、Started BackendApplication）
docker logs hospital-backend --tail 20

# 5. 健康检查
curl -s --max-time 10 http://127.0.0.1:8853/api/v1/base/health

# 6. 登录（应返回 code 200 和 access_token）
curl -s --max-time 10 -X POST http://127.0.0.1:8853/api/v1/base/access_token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

**浏览器**：http://39.102.213.51:8854 → `admin` / `admin123`

---

## 八、常见错误处理

### 8.1 `manifest unknown`

| 原因 | 处理 |
|------|------|
| `IMAGE_*` 未设置 | `.env` 取消注释或 `export IMAGE_BACKEND=...` |
| GHCR 未登录 | `docker login ghcr.io` |
| 本地有旧镜像 | `docker compose ... up -d --pull never` |

### 8.2 `down` 后容器全没了

- 未加 `-v` 时 **数据卷仍在**（`mysql-data` 等）。
- 重新 `up` 时务必设置 `IMAGE_*` 并 `--pull never`（若本地有镜像）。

### 8.3 backend 仍等待 MySQL

1. 确认 compose 已改 `${MYSQL_HOST:-mysql}` 且 `.env` 为 `172.20.0.1:3307`。
2. 确认 mysql 已映射 `3307:3306`：`ss -tlnp | grep 3307`。
3. 确认网关 IP 与 `MYSQL_HOST` 一致。
4. 查看日志：`docker logs hospital-backend --tail 30`。

### 8.4 容器间网络修好后恢复标准配置

网络恢复正常后，建议改回内网直连（不暴露 MySQL 端口）：

**.env：**

```env
MYSQL_HOST=mysql
MYSQL_PORT=3306
# 删除或注释 MYSQL_PUBLISH_PORT
```

**compose：** 删除 `mysql` 下 `ports:` 段。

然后：

```bash
docker compose -f docker-compose.prod.yml up -d --force-recreate mysql backend
```

---

## 九、可选：gateway overlay（仓库已提供）

若主 compose 不想长期保留 `ports`，可使用 overlay：

```bash
# .env 仍设 MYSQL_HOST / MYSQL_PORT / MYSQL_PUBLISH_PORT
docker compose -f docker-compose.prod.yml -f deploy/docker-compose.prod.gateway.yml up -d --pull never
```

---

## 十、检查清单（改前 / 改后）

| 检查项 | 改前 | 改后 |
|--------|------|------|
| `.env` `IMAGE_BACKEND/FRONTEND` | 可能注释 | ✅ 已设置 |
| `.env` `MYSQL_HOST` | `mysql` 或未生效 | ✅ `172.20.0.1` |
| `.env` `MYSQL_PORT` | `3306` 或未生效 | ✅ `3307` |
| `.env` `MYSQL_PUBLISH_PORT` | 无 | ✅ `3307` |
| compose mysql `ports` | 无 | ✅ `3307:3306` |
| compose backend `MYSQL_HOST` | 硬编码 `mysql` | ✅ `${MYSQL_HOST:-mysql}` |
| 登录 API | 超时 | ✅ 1 秒内返回 JSON |

---

## 十一、相关文件

| 文件 | 说明 |
|------|------|
| `docker-compose.prod.yml` | 生产 compose |
| `deploy/docker-compose.prod.gateway.yml` | 网关绕行 overlay |
| `deploy/server.env` | 服务器 `.env` 模板（本地，勿提交） |
| `.env.example` | 变量说明（可提交 Git） |
| `deploy/MIGRATION.md` | 数据迁移说明 |
| `backend/docker-entrypoint.sh` | 等待 MySQL + SQL 迁移 |
