#!/usr/bin/env bash
# 一键：GitHub CLI 浏览器登录 + 触发「Build and Deploy」生产部署
# 用法：
#   bash scripts/gh-auth-and-trigger-deploy.sh           # 登录（如需）并触发部署
#   bash scripts/gh-auth-and-trigger-deploy.sh --login-only
#   bash scripts/gh-auth-and-trigger-deploy.sh --watch # 触发后跟踪本次运行
#   WORKFLOW="Apply Billing P0.6 on Production" bash scripts/gh-auth-and-trigger-deploy.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

WORKFLOW="${WORKFLOW:-Build and Deploy}"
BRANCH="${BRANCH:-main}"
REPO="${GITHUB_REPO:-308081164/hospital_longservice}"
LOGIN_ONLY=0
WATCH_RUN=0
GIT_PROTOCOL="${GIT_PROTOCOL:-}"

for arg in "$@"; do
  case "$arg" in
    --login-only) LOGIN_ONLY=1 ;;
    --watch) WATCH_RUN=1 ;;
    -h|--help)
      sed -n '2,8p' "$0"
      exit 0
      ;;
    *)
      echo "未知参数: $arg（可用 --login-only / --watch / --help）" >&2
      exit 1
      ;;
  esac
done

proxy_reachable() {
  curl -sS --connect-timeout 2 --proxy http://127.0.0.1:7890 https://api.github.com/zen >/dev/null 2>&1
}

configure_network() {
  if proxy_reachable; then
    echo "==> 检测到本地代理 127.0.0.1:7890 可用，保留代理环境变量"
    return
  fi
  if [ -n "${http_proxy:-}${https_proxy:-}${all_proxy:-}" ]; then
    echo "==> 本地代理 7890 不可用，临时取消 http(s)_proxy / all_proxy"
    unset http_proxy https_proxy all_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY
  fi
}

ensure_gh() {
  if command -v gh >/dev/null 2>&1; then
    echo "==> GitHub CLI: $(gh --version | head -1)"
    return
  fi
  echo "未找到 gh 命令。" >&2
  if command -v brew >/dev/null 2>&1; then
    echo "正在通过 Homebrew 安装 gh …"
    brew install gh
  else
    echo "请先安装 GitHub CLI: https://cli.github.com/" >&2
    exit 1
  fi
}

detect_git_protocol() {
  if [ -n "$GIT_PROTOCOL" ]; then
    return
  fi
  local url
  url="$(git remote get-url origin 2>/dev/null || true)"
  if [[ "$url" == git@* ]]; then
    GIT_PROTOCOL=ssh
  else
    GIT_PROTOCOL=https
  fi
}

gh_logged_in() {
  gh auth status --hostname github.com >/dev/null 2>&1
}

ensure_gh_login() {
  detect_git_protocol
  if gh_logged_in; then
    echo "==> 已登录 GitHub CLI"
    gh auth status --hostname github.com 2>&1 | sed 's/^/    /'
    # 触发 workflow 需要 workflow scope；缺失时走浏览器补授权
    if ! gh auth status --hostname github.com 2>&1 | grep -q 'Token scopes:.*workflow'; then
      echo "==> 补申请 workflow 权限（请在浏览器完成授权）"
      gh auth refresh --hostname github.com -s workflow -s repo -w
    fi
    return
  fi

  echo ""
  echo "=========================================="
  echo "  请在浏览器完成 GitHub 授权"
  echo "  1. 终端会显示一次性验证码"
  echo "  2. 按 Enter 打开浏览器（或访问 https://github.com/login/device）"
  echo "  3. 粘贴验证码并确认授权"
  echo "=========================================="
  echo ""

  gh auth login \
    --hostname github.com \
    --git-protocol "$GIT_PROTOCOL" \
    --web \
    --scopes workflow,repo

  echo ""
  echo "==> 登录完成"
  gh auth status --hostname github.com 2>&1 | sed 's/^/    /'
}

verify_repo_access() {
  echo "==> 验证仓库访问: $REPO"
  gh repo view "$REPO" --json name,url -q '"    仓库: \(.name)  \(.url)"'
}

trigger_deploy() {
  echo "==> 触发工作流: 「${WORKFLOW}」  分支: ${BRANCH}"
  gh workflow run "$WORKFLOW" --repo "$REPO" --ref "$BRANCH"

  echo "==> 等待 Actions 创建运行记录 …"
  local run_id=""
  for _ in $(seq 1 12); do
    sleep 3
    run_id="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --branch "$BRANCH" --limit 1 --json databaseId,status -q '.[0].databaseId' 2>/dev/null || true)"
    if [ -n "$run_id" ] && [ "$run_id" != "null" ]; then
      break
    fi
  done

  echo ""
  echo "==> 最近运行（${WORKFLOW} / ${BRANCH}）"
  gh run list --repo "$REPO" --workflow "$WORKFLOW" --branch "$BRANCH" --limit 3

  if [ -z "$run_id" ] || [ "$run_id" = "null" ]; then
    echo ""
    echo "工作流已触发，但未及时查到 run id。请手动查看："
    echo "  https://github.com/${REPO}/actions"
    return
  fi

  local run_url
  run_url="$(gh run view "$run_id" --repo "$REPO" --json url -q .url)"
  echo ""
  echo "本次运行: $run_url"

  if [ "$WATCH_RUN" -eq 1 ]; then
    echo "==> 跟踪运行进度（Ctrl+C 可退出，不影响 CI）"
    gh run watch "$run_id" --repo "$REPO" --exit-status || true
  else
    echo ""
    echo "实时跟踪: gh run watch $run_id --repo $REPO"
    echo "或重新执行: bash scripts/gh-auth-and-trigger-deploy.sh --watch"
  fi
}

main() {
  configure_network
  ensure_gh
  ensure_gh_login
  verify_repo_access

  if [ "$LOGIN_ONLY" -eq 1 ]; then
    echo "==> 仅登录模式，跳过触发部署"
    exit 0
  fi

  trigger_deploy
  echo ""
  echo "完成。部署结束后可在生产服务器验证 billing marker："
  echo "  ssh <user>@39.102.213.51 'cd /mnt/newdisk/app/Hospital && bash deploy/verify-billing-on-server.sh'"
}

main "$@"
