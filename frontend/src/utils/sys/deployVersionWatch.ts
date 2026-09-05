/**
 * 部署版本监听（P0 强制版）：轮询前端 /version.json 与后端 /api/v1/base/version，
 * 任一通道发现新版本部署即交给 versionEnforcer 强制升级（清缓存 + 阻断 + 硬刷新），
 * 不再给用户「留在旧版本」的选择。
 */

import { reportBackendSha, reportServerFrontendVersion } from './versionEnforcer'

const POLL_INTERVAL_MS = 30_000
const BACKEND_VERSION_URL = '/api/v1/base/version'
const FRONTEND_VERSION_URL = '/version.json'

let timer: ReturnType<typeof setInterval> | null = null
let started = false

async function fetchBackendSha(): Promise<string | null> {
  try {
    const resp = await fetch(BACKEND_VERSION_URL, { cache: 'no-store' })
    if (!resp.ok) return null
    const body = await resp.json()
    // 优先完整 gitSha（与 X-App-Version 响应头一致），短 sha 仅作兜底
    const sha = body?.data?.gitSha ?? body?.data?.gitShaShort ?? null
    return typeof sha === 'string' && sha ? sha : null
  } catch {
    return null
  }
}

async function fetchServerFrontendVersion(): Promise<string | null> {
  try {
    const resp = await fetch(`${FRONTEND_VERSION_URL}?_t=${Date.now()}`, { cache: 'no-store' })
    if (!resp.ok) return null
    const body = await resp.json()
    const version = body?.version ?? null
    return typeof version === 'string' && version ? version : null
  } catch {
    // dev 环境无 version.json（vite 回退返回 HTML，json 解析失败）→ 跳过
    return null
  }
}

async function checkDeployVersion() {
  const [frontendVersion, backendSha] = await Promise.all([
    fetchServerFrontendVersion(),
    fetchBackendSha()
  ])
  reportServerFrontendVersion(frontendVersion)
  reportBackendSha(backendSha)
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void checkDeployVersion()
  }
}

export function startDeployVersionWatch(): void {
  if (started) return
  started = true
  void checkDeployVersion()
  timer = setInterval(() => {
    void checkDeployVersion()
  }, POLL_INTERVAL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
}

export function stopDeployVersionWatch(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  document.removeEventListener('visibilitychange', onVisibilityChange)
  started = false
}
