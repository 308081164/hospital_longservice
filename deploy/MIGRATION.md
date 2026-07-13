# Hospital 首次部署与数据迁移指南

部署目录：`/mnt/newdisk/app/Hospital`  
访问地址：`http://39.102.213.51:8854`（前端）、`http://39.102.213.51:8853/api/v1/base/health`（后端健康检查）

---

## 阶段 A：本地准备（你当前步骤）

### A1. 导出本地 MySQL 数据

在 Windows 项目根目录执行：

```powershell
cd D:\Hui_Files\MyProjects\guangsha_technology\hospital-all-master\hospital-all-master
powershell -ExecutionPolicy Bypass -File deploy\export-local-mysql.ps1
```

会在 `deploy/` 下生成 `hospital-migration-YYYYMMDD.sql`。

### A2. 上传文件到服务器

将以下文件上传到 `/mnt/newdisk/app/Hospital/`：

| 本地文件 | 服务器路径 |
|----------|------------|
| `deploy/server.env` | `/mnt/newdisk/app/Hospital/.env` |
| `deploy/hospital-migration-*.sql` | `/mnt/newdisk/app/Hospital/hospital-migration-YYYYMMDD.sql` |
| `deploy/import-on-server.sh` | `/mnt/newdisk/app/Hospital/deploy/import-on-server.sh` |

服务器执行：

```bash
mkdir -p /mnt/newdisk/app/Hospital/deploy
chmod 600 /mnt/newdisk/app/Hospital/.env
chmod +x /mnt/newdisk/app/Hospital/deploy/import-on-server.sh
```

---

## 阶段 B：配置 GitHub Secrets（下一步引导）

仓库：https://github.com/308081164/hospital_longservice（private）

在 **Settings → Secrets and variables → Actions** 添加：

| Secret | 值 |
|--------|-----|
| `SSH_HOST` | `39.102.213.51` |
| `SSH_USER` | `root`（或你的 SSH 用户） |
| `SSH_PRIVATE_KEY` | 部署私钥全文 |
| `DEPLOY_PATH` | `/mnt/newdisk/app/Hospital` |

可选（镜像私有时需要）：

| Secret | 值 |
|--------|-----|
| `GHCR_USERNAME` | `308081164` |
| `GHCR_READ_TOKEN` | PAT，勾选 `read:packages` |

---

## 阶段 C：首次数据迁移（在触发 CI 之前手动执行）

> 首次部署建议先手动导入数据，再触发 CI，避免空库初始化。

```bash
cd /mnt/newdisk/app/Hospital
bash deploy/import-on-server.sh
docker exec -it hospital-mysql mysql -uroot -p -e "USE hospital; SHOW TABLES;"
```

---

## 阶段 D：触发 CI 自动部署

Secrets 配置完成且数据已导入后，push 到 `main` 或手动 Run workflow。

验证：

```bash
curl http://127.0.0.1:8853/api/v1/base/health
curl -I http://127.0.0.1:8854/
```

浏览器访问：http://39.102.213.51:8854
