#!/usr/bin/env bash
# CI：在 runner 上 pull GHCR 镜像（已 login），再 docker save/load 传到生产机。
# 避免生产机缺少 GHCR 凭证时 pull denied。
set -euo pipefail

: "${SSH_HOST:?SSH_HOST required}"
: "${SSH_USER:?SSH_USER required}"
: "${SSH_KEY_FILE:?SSH_KEY_FILE required}"
: "${IMAGE_BACKEND:?IMAGE_BACKEND required}"
: "${IMAGE_FRONTEND:?IMAGE_FRONTEND required}"

SSH_PORT="${SSH_PORT:-22}"

SSH_BASE=(
  ssh -i "$SSH_KEY_FILE"
  -o StrictHostKeyChecking=no
  -o ServerAliveInterval=30
  -o ServerAliveCountMax=360
  -o ConnectTimeout=30
  -p "$SSH_PORT"
)

echo "==> pull images on CI runner"
docker pull "$IMAGE_BACKEND"
docker pull "$IMAGE_FRONTEND"

echo "==> transfer images to ${SSH_USER}@${SSH_HOST} via docker save/load"
docker save "$IMAGE_BACKEND" "$IMAGE_FRONTEND" | \
  "${SSH_BASE[@]}" "${SSH_USER}@${SSH_HOST}" docker load

echo "==> image transfer OK"
