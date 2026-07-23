/**
 * 检测当前页面 JS 是否与服务器 index.html 引用的入口 chunk 一致。
 * Docker 重建后若用户未刷新，可能仍运行旧 bundle（/assets/ 为 immutable 长期缓存）。
 */
const ENTRY_SCRIPT_RE = /\/assets\/index-[A-Za-z0-9_-]+\.js/

function currentEntryScriptSrc(): string | null {
  for (const el of document.querySelectorAll('script[type="module"]')) {
    const src = (el as HTMLScriptElement).src
    if (src && ENTRY_SCRIPT_RE.test(src)) return src
  }
  return null
}

function entryScriptFromHtml(html: string): string | null {
  const match = html.match(/\/assets\/index-[A-Za-z0-9_-]+\.js/)
  return match ? match[0] : null
}

export async function checkDeployStaleAndReload(): Promise<void> {
  if (!import.meta.env.PROD) return

  const loaded = currentEntryScriptSrc()
  if (!loaded) return

  try {
    const res = await fetch(`/index.html?_deploy=${Date.now()}`, {
      cache: 'no-store',
      credentials: 'same-origin'
    })
    if (!res.ok) return
    const html = await res.text()
    const deployed = entryScriptFromHtml(html)
    if (!deployed) return

    const loadedPath = new URL(loaded, window.location.origin).pathname
    if (loadedPath !== deployed) {
      window.location.reload()
    }
  } catch {
    // 离线或 nginx 未就绪时忽略
  }
}
