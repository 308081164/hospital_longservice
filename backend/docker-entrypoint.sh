#!/bin/sh
# 启动 Spring Boot 前：等待 MySQL 就绪；若存在 /app/db/migrate_manifest.txt 则按清单执行 SQL（幂等/可重复）。
# 生产/CI：推送代码后由部署流水线 force-recreate backend，本脚本自动跑完清单。
# Java SchemaMigrationRunner 仍会在应用启动后执行列级迁移。
set -e

if [ "${SKIP_DB_MIGRATE:-}" = "1" ]; then
  echo "[entrypoint] SKIP_DB_MIGRATE=1，跳过 SQL 清单迁移"
  exec java -jar /app/app.jar --spring.profiles.active=docker
fi

MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-hospital}"
MYSQL_USER="${MYSQL_USER:-hospital}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"

echo "[entrypoint] 等待 MySQL: ${MYSQL_HOST}:${MYSQL_PORT} ..."
i=0
while [ "$i" -lt 90 ]; do
  if mysqladmin ping -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --silent 2>/dev/null; then
    echo "[entrypoint] MySQL 已就绪"
    break
  fi
  i=$((i + 1))
  sleep 2
done

if ! mysqladmin ping -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --silent 2>/dev/null; then
  echo "[entrypoint] 错误: 无法在约 3 分钟内连接 MySQL，请检查 MYSQL_* 与网络"
  exit 1
fi

if [ -f /app/db/migrate_manifest.txt ]; then
  echo "[entrypoint] 按清单执行数据库迁移..."
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in \#*|"") continue ;; esac
    f="/app/db/$line"
    if [ -f "$f" ]; then
      echo "[entrypoint] -> $line"
      mysql --force -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < "$f" \
        || echo "[entrypoint] !! $line 执行有报错（若列/表已存在可忽略）"
    else
      echo "[entrypoint] !! 缺少文件: $f"
    fi
  done < /app/db/migrate_manifest.txt
  echo "[entrypoint] 迁移步骤结束，启动应用"
else
  echo "[entrypoint] 未找到 /app/db/migrate_manifest.txt，跳过 SQL 清单迁移"
fi

exec java -jar /app/app.jar --spring.profiles.active=docker
