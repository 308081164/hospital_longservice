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
      <span class="value">{{ display.buildTime }}</span>
    </div>
    <div v-if="menuOpen" class="line">
      <span class="label">规则</span>
      <span class="value mono">{{ display.rulesHash }}</span>
      <span v-if="display.rulesTime" class="value muted">· {{ display.rulesTime }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { fetchSystemVersion, type SystemVersionInfo } from '@/api/system/versionApi'
  import { useSettingStore } from '@/store/modules/setting'

  defineOptions({ name: 'SystemVersionBadge' })

  const settingStore = useSettingStore()
  const { menuOpen } = storeToRefs(settingStore)

  const info = ref<SystemVersionInfo | null>(null)
  const frontendSha = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : ''

  const display = computed(() => {
    const gitSha = info.value?.gitShaShort || short(frontendSha) || 'local'
    const buildTime = info.value?.buildTimeDisplay || '—'
    const rulesHash = info.value?.rulesManifestHashShort || '—'
    const rulesTime =
      info.value?.rulesReconciledAtDisplay || info.value?.rulesGeneratedAtDisplay || ''
    return { gitSha, buildTime, rulesHash, rulesTime }
  })

  const tooltipText = computed(() => {
    const i = info.value
    const lines = [
      `系统 ${i?.gitSha || short(frontendSha) || 'local'}`,
      `更新 ${i?.buildTimeDisplay || i?.buildTime || '—'}`,
      `规则 ${i?.rulesManifestHashShort || '—'} · ${i?.rulesReconciledAtDisplay || i?.rulesGeneratedAtDisplay || '—'}`,
      frontendSha && frontendSha !== i?.gitSha ? `前端构建 ${short(frontendSha)}` : ''
    ].filter(Boolean)
    return lines.join('\n')
  })

  function short(sha: string): string {
    if (!sha) return ''
    return sha.length <= 8 ? sha : sha.slice(0, 8)
  }

  onMounted(async () => {
    try {
      info.value = await fetchSystemVersion()
    } catch {
      // 未登录/后端未就绪时仍展示前端构建号，避免空白
      info.value = null
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
  }
</style>
