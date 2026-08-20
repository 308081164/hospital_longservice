#!/usr/bin/env bash
# Deprecated wrapper: 请使用 reapply-billing-manifest-on-server.sh
set -euo pipefail
DEPLOY_PATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"
echo "警告: reapply-billing-p0-6-on-server.sh 已废弃，转调 manifest reconcile" >&2
exec bash "${DEPLOY_PATH}/deploy/reapply-billing-manifest-on-server.sh"
