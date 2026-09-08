#!/usr/bin/env bash
# 部署前归档当前生产版本（镜像标签 + 版本 API + 计费 marker）。
# 由 GitHub Actions 在 pull 新镜像之前调用；也可在服务器手动执行。
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
ARCHIVE_ROOT="${DEPLOY_PATH}/releases/archive"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARCHIVE_DIR="${ARCHIVE_ROOT}/${STAMP}"

mkdir -p "${ARCHIVE_DIR}"

cd "${DEPLOY_PATH}"

echo "==> 归档生产版本 → ${ARCHIVE_DIR}"

if curl -sS --connect-timeout 5 "http://127.0.0.1:8853/api/v1/base/version" > "${ARCHIVE_DIR}/version-api.json" 2>/dev/null; then
  echo "    version API 已保存"
else
  echo '{}' > "${ARCHIVE_DIR}/version-api.json"
  echo "    WARN: version API 不可达，写入空 JSON"
fi

BE_IMAGE="$(docker inspect hospital-backend --format '{{.Config.Image}}' 2>/dev/null || echo unknown)"
FE_IMAGE="$(docker inspect hospital-frontend --format '{{.Config.Image}}' 2>/dev/null || echo unknown)"
BE_ID="$(docker inspect hospital-backend --format '{{.Image}}' 2>/dev/null || echo unknown)"
FE_ID="$(docker inspect hospital-frontend --format '{{.Image}}' 2>/dev/null || echo unknown)"

{
  echo "ARCHIVE_STAMP=${STAMP}"
  echo "IMAGE_BACKEND=${BE_IMAGE}"
  echo "IMAGE_FRONTEND=${FE_IMAGE}"
  echo "BACKEND_IMAGE_ID=${BE_ID}"
  echo "FRONTEND_IMAGE_ID=${FE_ID}"
} > "${ARCHIVE_DIR}/images.env"

if [ -f .env ]; then
  cp .env "${ARCHIVE_DIR}/dotenv.snapshot"
fi

if [ -f docker-compose.prod.yml ]; then
  cp docker-compose.prod.yml "${ARCHIVE_DIR}/docker-compose.prod.yml.snapshot"
fi

if [ -x deploy/mysql-hospital-cli.sh ]; then
  bash deploy/mysql-hospital-cli.sh --exec-root -N -e \
    "SELECT setting_key, setting_value FROM sys_setting WHERE setting_key LIKE 'billing_%' OR setting_key LIKE 'rules_%' OR setting_key LIKE '%baseline%' OR setting_key LIKE '%manifest%'" \
    > "${ARCHIVE_DIR}/billing-markers.tsv" 2>/dev/null || true
fi

# 本地镜像留档标签（不推送 registry，仅供同机回退）
if [ "${BE_IMAGE}" != "unknown" ]; then
  docker tag "${BE_IMAGE}" "hospital-backend:archive-${STAMP}" 2>/dev/null || true
fi
if [ "${FE_IMAGE}" != "unknown" ]; then
  docker tag "${FE_IMAGE}" "hospital-frontend:archive-${STAMP}" 2>/dev/null || true
fi

GIT_SHA="$(python3 -c 'import json;print(json.load(open("'"${ARCHIVE_DIR}/version-api.json"'")).get("data",{}).get("gitSha",""))' 2>/dev/null || true)"

cat > "${ARCHIVE_DIR}/README.txt" <<EOF
生产版本留档
=============
时间戳: ${STAMP}
gitSha: ${GIT_SHA:-未知}
backend 镜像: ${BE_IMAGE}
frontend 镜像: ${FE_IMAGE}

回退（须运维负责人明确指令后执行）:
  cd ${DEPLOY_PATH}
  bash deploy/rollback-prod-to-archive.sh ${STAMP}

严禁在无明确授权时自动回退。
EOF

ln -sfn "${ARCHIVE_DIR}" "${ARCHIVE_ROOT}/latest"
echo "${STAMP}" > "${ARCHIVE_ROOT}/latest-stamp.txt"

# 保留最近 10 份留档目录
if [ -d "${ARCHIVE_ROOT}" ]; then
  ls -1dt "${ARCHIVE_ROOT}"/20* 2>/dev/null | tail -n +11 | xargs -r rm -rf
fi

echo "ARCHIVE_DIR=${ARCHIVE_DIR}"
echo "ARCHIVE_STAMP=${STAMP}"
echo "归档完成"
