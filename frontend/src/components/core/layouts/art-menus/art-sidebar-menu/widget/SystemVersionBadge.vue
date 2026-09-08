<!-- 侧栏左下角：系统版本 / 更新时间 / 计价规则版本（生产快速对版） -->
<template>
  <div
    class="system-version-badge"
    :class="{ collapsed: !menuOpen }"
    :title="tooltipText"
  >
    <div class="line">
      <span class="label">系统</span>
      <span class="value mono">{{ display.gitSha }}</span>
    </div>
    <div v-if="menuOpen" class="line">
      <span class="label">更新</span>
      <span class="value">{{ display.updatedAt }}</span>
    </div>
    <div v-if="menuOpen" class="line">
      <span class="label">规则</span>
      <span class="value mono">{{ display.rulesHash }}</span>
      <span v-if="display.rulesTime" class="value muted">· {{ display.rulesTime }}</span>
    </div>
    <div v-if="menuOpen" class="line">
      <span class="label">基线</span>
      <span class="value mono">{{ display.baselineHash }}</span>
      <span v-if="display.baselineFromManifest" class="value muted">· manifest</span>
      <span v-else-if="!display.verifyOk" class="value warn">· 漂移</span>
    </div>
  </div>
</template>

<script setup lang="ts">
  import {
    fetchSystemVersion,
    isSystemVersionInfoComplete,
    type SystemVersionInfo
  } from '@/api/system/versionApi'
  import { useSettingStore } from '@/store/modules/setting'

  defineOptions({ name: 'SystemVersionBadge' })

  const settingStore = useSettingStore()
  const { menuOpen } = storeToRefs(settingStore)

  const info = ref<SystemVersionInfo | null>(null)
  /** API 失败或旧版后端（无 gitShaShort 等字段） */
  const backendUnavailable = ref(false)
  const frontendSha = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : ''
  const frontendBuildTimeDisplay = ref('')

  const display = computed(() => {
    const i = info.value
    const gitSha = i?.gitShaShort || short(frontendSha) || 'local'
    const fallback = backendUnavailable.value ? '后端未就绪' : '—'
    const updatedAt = pickUpdatedAtDisplay(i, frontendBuildTimeDisplay.value) || fallback
    const rulesHash = i?.rulesManifestHashShort || fallback
    const rulesTime =
      i?.rulesReconciledAtDisplay || i?.rulesGeneratedAtDisplay || ''
    const baselineHash =
      i?.rulesBaselineHashShort || i?.rulesManifestHashShort || fallback
    const baselineFromManifest =
      !i?.rulesBaselineHashShort && !!i?.rulesManifestHashShort
    const verifyOk = i?.rulesVerifyStatus !== false
    const shaMismatch =
      !!frontendSha && !!i?.gitSha && frontendSha !== i.gitSha && i.gitSha !== 'local'
    return {
      gitSha: shaMismatch ? `${gitSha}!` : gitSha,
      updatedAt,
      rulesHash,
      rulesTime,
      baselineHash,
      baselineFromManifest,
      verifyOk
    }
  })

  const tooltipText = computed(() => {
    const i = info.value
    const fallback = backendUnavailable.value ? '后端未就绪' : '—'
    const lines = [
      `系统 ${i?.gitSha || short(frontendSha) || 'local'}`,
      `更新 ${pickUpdatedAtDisplay(i, frontendBuildTimeDisplay.value) || i?.buildTimeDisplay || fallback}`,
      i?.buildTimeDisplay && i.buildTimeDisplay !== pickUpdatedAtDisplay(i, frontendBuildTimeDisplay.value)
        ? `镜像构建 ${i.buildTimeDisplay}`
        : '',
      `规则 manifest ${i?.rulesManifestHashShort || fallback}`,
      i?.rulesBaselineHashShort ? `基线 ${i.rulesBaselineHashShort}` : '',
      i?.rulesVerifyStatus === false ? 'baseline 校验未通过' : '',
      backendUnavailable.value && frontendSha ? `前端构建 ${short(frontendSha)}` : '',
      !backendUnavailable.value && frontendSha && frontendSha !== i?.gitSha
        ? `前端构建 ${short(frontendSha)}（与后端不一致）`
        : '',
      frontendBuildTimeDisplay.value ? `前端更新 ${frontendBuildTimeDisplay.value}` : ''
    ].filter(Boolean)
    return lines.join('\n')
  })

  function pickUpdatedAtDisplay(
    i: SystemVersionInfo | null,
    frontendTime: string
  ): string {
    const candidates = [
      i?.updatedAtDisplay,
      i?.runtimeStartedAtDisplay,
      i?.rulesReconciledAtDisplay,
      i?.rulesGeneratedAtDisplay,
      i?.buildTimeDisplay,
      frontendTime
    ].filter((v): v is string => !!v && v !== '—')
    if (candidates.length === 0) return ''
    return candidates.sort().at(-1) || ''
  }

  function formatFrontendBuildTime(raw: string): string {
    if (!raw) return ''
    const parsed = Date.parse(raw)
    if (Number.isNaN(parsed)) return raw.slice(0, 16).replace('T', ' ')
    const d = new Date(parsed)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }

  function short(sha: string): string {
    if (!sha) return ''
    return sha.length <= 8 ? sha : sha.slice(0, 8)
  }

  onMounted(async () => {
    try {
      const versionRes = await fetch('/version.json', { cache: 'no-store' })
      if (versionRes.ok) {
        const payload = (await versionRes.json()) as { buildTime?: string }
        if (payload.buildTime) {
          frontendBuildTimeDisplay.value = formatFrontendBuildTime(payload.buildTime)
        }
      }
    } catch {
      // 忽略：旧部署或无 version.json 时仍展示后端时间
    }

    try {
      const data = await fetchSystemVersion()
      if (isSystemVersionInfoComplete(data)) {
        info.value = data
      } else {
        backendUnavailable.value = true
      }
    } catch {
      backendUnavailable.value = true
    }
  })
</script>

<style scoped lang="scss">
  .system-version-badge {
    flex-shrink: 0;
    padding: 8px 12px 12px;
    border-top: 1px solid color-mix(in srgb, var(--art-card-border) 80%, transparent);
    font-size: 11px;
    line-height: 1.45;
    color: var(--el-text-color-secondary, #909399);
    user-select: text;
    cursor: default;

    &.collapsed {
      padding: 6px 4px 10px;
      text-align: center;

      .line {
        justify-content: center;
      }

      .label {
        display: none;
      }
    }

    .line {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      align-items: baseline;
    }

    .label {
      flex: 0 0 auto;
      min-width: 2em;
      color: var(--el-text-color-placeholder, #a8abb2);
    }

    .value {
      word-break: break-all;
    }

    .mono {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      letter-spacing: 0.02em;
    }

    .muted {
      opacity: 0.85;
    }

    .line.quarantine .warn,
    .warn {
      color: var(--el-color-warning);
      font-weight: 600;
    }
  }
</style>
