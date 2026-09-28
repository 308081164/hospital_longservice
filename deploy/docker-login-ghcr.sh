#!/usr/bin/env bash
# 生产机 / CI SSH 远程执行：解析 GHCR 凭证并 docker login。
# 优先级：显式 GHCR_LOGIN_* > GitHub Secrets 映射 (GHCR_USERNAME/GHCR_READ_TOKEN) >
#         服务器 .env 同名变量 > GITHUB_TOKEN + 仓库 owner。
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
if [ -f "${DEPLOY_PATH}/.env" ]; then
  # shellcheck disable=SC1091
  set -a && source "${DEPLOY_PATH}/.env" && set +a
fi

OWNER_LC="${GHCR_OWNER:-${GITHUB_REPOSITORY_OWNER:-}}"
if [ -z "$OWNER_LC" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
  OWNER_LC="${GITHUB_REPOSITORY%%/*}"
fi
OWNER_LC="$(echo "${OWNER_LC:-${GITHUB_ACTOR:-}}" | tr '[:upper:]' '[:lower:]')"

LOGIN_USER="${GHCR_LOGIN_USER:-${GHCR_USERNAME:-${GHCR_USER:-${OWNER_LC}}}}"
LOGIN_TOKEN="${GHCR_LOGIN_TOKEN:-${GHCR_READ_TOKEN:-${GHCR_TOKEN:-${GITHUB_TOKEN:-}}}}"

if [ -z "$LOGIN_TOKEN" ] || [ -z "$LOGIN_USER" ]; then
  echo "错误: 无法解析 GHCR 登录凭证。" >&2
  echo "  请配置仓库 Secrets: GHCR_USERNAME + GHCR_READ_TOKEN" >&2
  echo "  或在服务器 ${DEPLOY_PATH}/.env 中设置 GHCR_USERNAME / GHCR_READ_TOKEN" >&2
  echo "  或确保 CI 传入 GITHUB_TOKEN（Actions 内置，用于拉取同仓库 packages）" >&2
  exit 1
fi

echo "==> docker login ghcr.io (user=${LOGIN_USER})"
echo "$LOGIN_TOKEN" | docker login ghcr.io -u "$LOGIN_USER" --password-stdin
