import { h } from 'vue'
import { ElButton, ElNotification } from 'element-plus'

/**
 * 部署版本监听：轮询 /api/v1/base/version，发现后端部署了新版本时
 * 弹出常驻通知引导用户刷新，避免 SPA 长开标签页一直运行旧 bundle
 * （历史上曾因旧缓存导致「新功能已部署但用户看不到」的误判）。
 */

const POLL_INTERVAL_MS = 60_000
const VERSION_URL = '/api/v1/base/version'

let currentSha: string | null = null
let notifiedSha: string | null = null
let timer: ReturnType<typeof setInterval> | null = null
let started = false

async function fetchDeploySha(): Promise<string | null> {
  try {
    const resp = await fetch(VERSION_URL, { cache: 'no-store' })
    if (!resp.ok) return null
    const body = await resp.json()
    const sha = body?.data?.gitShaShort ?? body?.data?.gitSha ?? null
    return typeof sha === 'string' && sha ? sha : null
  } catch {
    return null
  }
}

function notifyNewVersion(sha: string) {
  if (notifiedSha === sha) return
  notifiedSha = sha
  ElNotification({
    title: '系统已更新',
    message: h('div', null, [
      h('p', { style: 'margin: 0 0 8px' }, `检测到新版本（${sha}）已部署，当前页面为旧版本。`),
      h(
        ElButton,
        {
          type: 'primary',
          size: 'small',
          onClick: () => window.location.reload()
        },
        () => '立即刷新'
      )
    ]),
    type: 'warning',
    duration: 0,
    position: 'bottom-right'
  })
}

async function checkDeployVersion() {
  const sha = await fetchDeploySha()
  if (!sha) return
  if (!currentSha) {
    currentSha = sha
    return
  }
  if (sha !== currentSha) {
    notifyNewVersion(sha)
  }
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
