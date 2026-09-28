#!/usr/bin/env bash
# CI：通过 tar 流将 backend/ 同步到生产机（比 scp -r 更快、更稳）。
set -euo pipefail

: "${SSH_HOST:?SSH_HOST required}"
: "${SSH_USER:?SSH_USER required}"
: "${SSH_KEY_FILE:?SSH_KEY_FILE required}"

SSH_PORT="${SSH_PORT:-22}"
DPATH="${DEPLOY_PATH:-/mnt/newdisk/app/Hospital}"

SSH_BASE=(
  ssh -i "$SSH_KEY_FILE"
  -o StrictHostKeyChecking=no
  -o ServerAliveInterval=30
  -o ServerAliveCountMax=120
  -o ConnectTimeout=30
  -p "$SSH_PORT"
)

echo "==> prepare remote deploy dir: ${DPATH}"
"${SSH_BASE[@]}" "${SSH_USER}@${SSH_HOST}" \
  "mkdir -p '${DPATH}/deploy' && \
   if [ -d '${DPATH}/deploy/nginx-frontend-shared-net.conf' ]; then \
     rm -rf '${DPATH}/deploy/nginx-frontend-shared-net.conf'; \
   fi"

echo "==> sync backend/ via tar (exclude target/)"
tar czf - \
  --exclude='backend/target' \
  --exclude='backend/.idea' \
  --exclude='backend/*.iml' \
  backend | \
  "${SSH_BASE[@]}" "${SSH_USER}@${SSH_HOST}" \
  "cd '${DPATH}' && tar xzf -"

echo "==> backend sync OK"
