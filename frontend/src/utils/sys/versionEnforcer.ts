/**
 * 版本强制执行器（P0：新版本部署后旧版本必须立即不可用）。
 *
 * 两条检测通道汇总到这里：
 * - 前端：轮询 /version.json（构建时生成），与 bundle 内置的 __APP_VERSION__ 比对
 * - 后端：轮询 /api/v1/base/version 及 axios 响应头 X-App-Version，
 *   以首次观察到的 gitSha 为基线，变化即视为后端已重新部署
 *
 * 一旦判定失配：
 * 1. 清空 localStorage / sessionStorage / cookies（含登录态，刷新后需重新登录）
 * 2. 全屏阻断遮罩（不可关闭、拦截一切交互），倒计时后强制硬刷新
 * 3. 刷新带 cache-bust 参数且用 location.replace（历史记录不留旧版本入口）
 * 4. 防刷新死循环：60s 内已强制刷新过则不再自动刷新，遮罩改为引导手动刷新
 *
 * 本模块不依赖 Vue / Pinia / axios，可在应用挂载前（main.ts）安全调用。
 */

/** 防死循环 Cookie：记录最近一次强制刷新时间 */
const RELOAD_GUARD_COOKIE = 'app_ver_reload_ts'
/** 该时间窗口内不重复自动刷新（毫秒） */
const RELOAD_GUARD_WINDOW_MS = 60_000
/** 阻断遮罩自动刷新倒计时（秒） */
const COUNTDOWN_SECONDS = 3

let blocked = false
let enforcing = false
let backendShaBaseline: string | null = null
let overlayEl: HTMLElement | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null

/** 当前运行 bundle 的构建版本（本地 dev 未注入时为空串，跳过前端版本校验） */
export function getFrontendVersion(): string {
  return typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : ''
}

/** 是否已判定版本失配（axios 请求拦截器据此阻断一切新请求） */
export function isVersionBlocked(): boolean {
  return blocked
}

/** 服务器 /version.json 的版本与本地构建版本不一致 → 当前 bundle 已过期 */
export function reportServerFrontendVersion(serverVersion: string | null): void {
  const local = getFrontendVersion()
  if (!local || !serverVersion) return
  if (serverVersion !== local) {
    enforceVersionUpgrade('frontend-mismatch')
  }
}

/** 后端 gitSha 以首次观察为基线，运行期间发生变化 → 后端已重新部署 */
export function reportBackendSha(sha: string | null): void {
  if (!sha) return
  if (!backendShaBaseline) {
    backendShaBaseline = sha
    return
  }
  if (!sameSha(backendShaBaseline, sha)) {
    enforceVersionUpgrade('backend-mismatch')
  }
}

/** 完整 sha 与短 sha（前 8 位）视为同一版本，避免不同通道长度不一造成误判 */
function sameSha(a: string, b: string): boolean {
  return a === b || a.startsWith(b) || b.startsWith(a)
}

/**
 * 强制执行版本升级。immediate 用于应用启动阶段（跳过倒计时直接刷新）。
 * 重复调用幂等。
 */
export function enforceVersionUpgrade(reason: string, opts?: { immediate?: boolean }): void {
  if (enforcing) return
  enforcing = true
  blocked = true

  const loopSuspected = readReloadGuard()
  clearClientState()
  showBlockingOverlay(loopSuspected || !!opts?.immediate)

  if (loopSuspected) {
    // 刚强制刷新过仍失配：停止自动刷新避免死循环，遮罩引导手动刷新
    console.warn('[version] reload loop suspected, manual refresh required:', reason)
    return
  }
  writeReloadGuard()
  if (opts?.immediate) {
    hardReload()
    return
  }
  startCountdown()
}

/** 清空全部客户端状态（登录态随之失效，刷新后回到登录页） */
function clearClientState(): void {
  try {
    localStorage.clear()
  } catch {
    // ignore
  }
  try {
    sessionStorage.clear()
  } catch {
    // ignore
  }
  try {
    for (const entry of document.cookie.split(';')) {
      const name = entry.split('=')[0].trim()
      if (!name || name === RELOAD_GUARD_COOKIE) continue
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
    }
  } catch {
    // ignore
  }
}

function readReloadGuard(): boolean {
  const match = document.cookie.match(new RegExp(`(?:^|; )${RELOAD_GUARD_COOKIE}=(\\d+)`))
  if (!match) return false
  const ts = Number(match[1])
  return Number.isFinite(ts) && Date.now() - ts < RELOAD_GUARD_WINDOW_MS
}

function writeReloadGuard(): void {
  try {
    document.cookie = `${RELOAD_GUARD_COOKIE}=${Date.now()}; path=/; max-age=300`
  } catch {
    // ignore
  }
}

/** cache-bust + replace：确保拿到最新 index.html，且历史记录不留旧版本 */
function hardReload(): void {
  const url = new URL(window.location.href)
  url.searchParams.set('_v', String(Date.now()))
  window.location.replace(url.toString())
}

function startCountdown(): void {
  let remaining = COUNTDOWN_SECONDS
  updateCountdownText(remaining)
  countdownTimer = setInterval(() => {
    remaining -= 1
    if (remaining <= 0) {
      if (countdownTimer) clearInterval(countdownTimer)
      countdownTimer = null
      hardReload()
      return
    }
    updateCountdownText(remaining)
  }, 1000)
}

function updateCountdownText(seconds: number): void {
  const el = overlayEl?.querySelector('[data-countdown]')
  if (el) el.textContent = `${seconds} 秒后自动刷新`
}

/**
 * 全屏阻断遮罩：最高 z-index + 拦截全部指针事件，不可关闭。
 * manualOnly 时隐藏倒计时，仅提供手动刷新按钮（防死循环分支）。
 */
function showBlockingOverlay(manualOnly: boolean): void {
  if (overlayEl || !document.body) return
  const el = document.createElement('div')
  el.id = 'app-version-upgrade-overlay'
  el.setAttribute(
    'style',
    [
      'position:fixed',
      'inset:0',
      'z-index:2147483647',
      'background:rgba(15,23,42,0.94)',
      'display:flex',
      'align-items:center',
      'justify-content:center',
      'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif'
    ].join(';')
  )

  const box = document.createElement('div')
  box.setAttribute(
    'style',
    'max-width:420px;padding:32px 36px;text-align:center;color:#f8fafc;user-select:none'
  )

  const title = document.createElement('div')
  title.setAttribute('style', 'font-size:20px;font-weight:600;margin-bottom:12px')
  title.textContent = '系统已更新'

  const desc = document.createElement('div')
  desc.setAttribute('style', 'font-size:14px;line-height:1.7;color:#cbd5e1;margin-bottom:20px')
  desc.textContent = manualOnly
    ? '新版本已部署，旧版本已停用。本地缓存与登录状态已清除，请手动刷新后重新登录。'
    : '新版本已部署，旧版本已停用。正在清除本地缓存与登录状态，即将自动刷新并跳转到登录页。'

  const countdown = document.createElement('div')
  countdown.setAttribute('data-countdown', '1')
  countdown.setAttribute(
    'style',
    `font-size:13px;color:#93c5fd;margin-bottom:20px;${manualOnly ? 'display:none' : ''}`
  )

  const btn = document.createElement('button')
  btn.type = 'button'
  btn.textContent = '立即刷新'
  btn.setAttribute(
    'style',
    [
      'padding:8px 28px',
      'font-size:14px',
      'color:#fff',
      'background:#2563eb',
      'border:none',
      'border-radius:6px',
      'cursor:pointer'
    ].join(';')
  )
  btn.addEventListener('click', () => {
    writeReloadGuard()
    hardReload()
  })

  box.appendChild(title)
  box.appendChild(desc)
  box.appendChild(countdown)
  box.appendChild(btn)
  el.appendChild(box)
  document.body.appendChild(el)
  overlayEl = el
}
