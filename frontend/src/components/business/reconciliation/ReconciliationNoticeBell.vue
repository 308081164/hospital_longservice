<template>
  <ElPopover
    placement="bottom-end"
    :width="320"
    trigger="click"
    @show="markAsRead"
  >
    <template #reference>
      <ElBadge :value="unreadCount" :hidden="unreadCount === 0" :max="9">
        <ElButton size="small" circle plain aria-label="规则变更通知">
          <ElIcon><Bell /></ElIcon>
        </ElButton>
      </ElBadge>
    </template>
    <div class="notice-popover">
      <div class="notice-popover__header">
        <span class="notice-popover__title">{{ t('reconciliation.notice.title') }}</span>
        <RouterLink
          to="/settings/changelog"
          class="notice-popover__link"
          @click="markAsRead"
        >
          {{ t('reconciliation.notice.viewChangelog') }}
        </RouterLink>
      </div>
      <div v-if="notices.length === 0" class="notice-popover__empty">
        {{ t('reconciliation.notice.empty') }}
      </div>
      <ul v-else class="notice-popover__list">
        <li v-for="(notice, index) in notices" :key="index" class="notice-popover__item">
          <div class="notice-popover__item-title">{{ notice.ruleChangedTitle }}</div>
          <div class="notice-popover__item-meta">
            {{ notice.ruleName }} · {{ notice.version }}
          </div>
          <div class="notice-popover__item-time">{{ formatDateTime(notice.updatedAt) }}</div>
          <p v-if="notice.summary" class="notice-popover__item-summary">{{ notice.summary }}</p>
        </li>
      </ul>
    </div>
  </ElPopover>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue'
  import { Bell } from '@element-plus/icons-vue'
  import { ElNotification } from 'element-plus'
  import { useI18n } from 'vue-i18n'

  const LAST_SEEN_KEY = 'reconciliation:lastSeenRuleVersion'

  interface RuleNotice {
    ruleName: string
    version: string
    updatedAt: string
    ruleChangedTitle: string
    summary?: string
  }

  const props = defineProps<{
    activeRule: Api.Hospital.PricingRuleRecord | null
  }>()

  const { t } = useI18n()
  const unreadCount = ref(0)
  const notices = ref<RuleNotice[]>([])
  let notifiedKey = ''

  function buildRuleKey(rule: Api.Hospital.PricingRuleRecord): string {
    return `${rule.id}:${rule.version}:${rule.updatedAt}`
  }

  function formatDateTime(value: string): string {
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) return value
    return parsed.toLocaleString('zh-CN', { hour12: false })
  }

  function markAsRead() {
    if (!props.activeRule) return
    localStorage.setItem(LAST_SEEN_KEY, buildRuleKey(props.activeRule))
    unreadCount.value = 0
  }

  watch(
    () => props.activeRule,
    (rule) => {
      if (!rule) {
        notices.value = []
        unreadCount.value = 0
        return
      }

      const currentKey = buildRuleKey(rule)
      const lastSeen = localStorage.getItem(LAST_SEEN_KEY)
      const notice: RuleNotice = {
        ruleName: rule.name,
        version: rule.version,
        updatedAt: rule.updatedAt,
        ruleChangedTitle: t('reconciliation.notice.ruleChangedTitle'),
        summary: rule.description
      }
      notices.value = [notice]

      if (lastSeen === currentKey) {
        unreadCount.value = 0
        return
      }

      unreadCount.value = 1
      if (notifiedKey !== currentKey) {
        notifiedKey = currentKey
        ElNotification({
          title: t('reconciliation.notice.ruleChangedTitle'),
          message: `${rule.name} · ${rule.version}`,
          type: 'warning',
          duration: 6000,
          position: 'top-right'
        })
      }
    },
    { immediate: true }
  )
</script>

<style scoped>
  .notice-popover__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  .notice-popover__title {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  .notice-popover__link {
    font-size: 12px;
    color: var(--el-color-primary);
    text-decoration: none;
  }

  .notice-popover__link:hover {
    text-decoration: underline;
  }

  .notice-popover__empty {
    padding: 12px 0;
    font-size: 12px;
    color: var(--el-text-color-secondary, #909399);
    text-align: center;
  }

  .notice-popover__list {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .notice-popover__item {
    padding: 8px 0;
    border-top: 1px solid var(--el-border-color-extra-light, #f2f6fc);
  }

  .notice-popover__item:first-child {
    border-top: none;
  }

  .notice-popover__item-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  .notice-popover__item-meta {
    margin-top: 2px;
    font-size: 12px;
    color: var(--el-text-color-regular, #606266);
  }

  .notice-popover__item-time {
    margin-top: 2px;
    font-size: 11px;
    color: var(--el-text-color-secondary, #909399);
  }

  .notice-popover__item-summary {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 1.4;
    color: var(--el-text-color-secondary, #909399);
  }
</style>
