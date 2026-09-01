<template>
  <div class="reconciliation-entry-sticky-header">
    <!-- Row 1: file strip -->
    <div class="sticky-row sticky-row--file">
      <div class="file-strip__info">
        <span class="file-strip__name" :title="fileName">{{ fileName }}</span>
        <slot name="status-badge" />
        <ElTooltip
          v-if="ruleLabel"
          :content="ruleTooltip"
          placement="top"
        >
          <span class="file-strip__chip">{{ ruleLabel }}</span>
        </ElTooltip>
        <span class="file-strip__meta">
          <slot name="file-meta" />
        </span>
      </div>
      <ElButton
        size="small"
        type="danger"
        text
        :disabled="removeDisabled"
        @click="emit('remove')"
      >
        {{ t('reconciliation.upload.remove') }}
      </ElButton>
    </div>

    <!-- Row 2: sheet segments + save -->
    <div v-if="showToolbar" class="sticky-row sticky-row--sheets">
      <div class="sheet-segmented" role="tablist">
        <button
          v-if="entry.savedJobId"
          type="button"
          role="tab"
          class="sheet-segment"
          :class="{ 'is-active': !entry.selectedSheetFilter }"
          @click="emit('select-sheet', null)"
        >
          {{ t('reconciliation.upload.allSheets') }}
        </button>
        <button
          v-for="sheet in entry.workbook?.previews ?? []"
          :key="sheet.name"
          type="button"
          role="tab"
          class="sheet-segment"
          :class="{ 'is-active': entry.selectedSheetFilter === sheet.name }"
          :disabled="!entry.savedJobId"
          @click="entry.savedJobId && emit('select-sheet', sheet.name)"
        >
          <span class="sheet-segment__name">{{ sheet.name }}</span>
          <span class="sheet-segment__meta">{{ sheet.dataRows }}</span>
          <span
            v-if="entry.savedSheetWarningCounts?.[sheet.name]"
            class="sheet-segment__warn"
          >
            {{ entry.savedSheetWarningCounts[sheet.name] }}
          </span>
        </button>
      </div>
      <div class="sticky-row__trailing">
        <span v-if="entry.sheetFilterLoading" class="toolbar-hint toolbar-hint--loading">
          {{ t('reconciliation.preview.sheetFilterLoading') }}
        </span>
        <ElButton
          type="primary"
          size="small"
          :disabled="entry.status !== 'parsed' || !activeRule || isRuleLoading"
          :loading="entry.status === 'processing'"
          @click="emit('process')"
        >
          {{
            entry.status === 'processing'
              ? '处理中…'
              : entry.savedJobId
                ? savedVersionLabel || '已保存'
                : '校对并保存'
          }}
        </ElButton>
      </div>
    </div>

    <!-- Row 3: stats + actions -->
    <div v-if="showToolbar" class="sticky-row sticky-row--actions">
      <div class="stat-chips">
        <span class="stat-chip">
          总行 <strong>{{ summary.total }}</strong>
        </span>
        <span class="stat-chip stat-chip--primary">
          修正 <strong>{{ summary.corrected }}</strong>
        </span>
        <span class="stat-chip stat-chip--warn">
          待复核 <strong>{{ summary.warning }}</strong>
        </span>
        <span class="stat-chip stat-chip--divider" aria-hidden="true" />
        <span class="stat-chip">
          原总价 <strong>{{ formatNumber(summary.originalTotalPrice) }}</strong>
        </span>
        <span class="stat-chip">
          修正价 <strong>{{ formatNumber(summary.correctedTotalPrice) }}</strong>
        </span>
        <span class="stat-chip">
          差额
          <strong :class="summary.totalDifference >= 0 ? 'text-green-600' : 'text-red-600'">
            {{ formatSignedNumber(summary.totalDifference) }}
          </strong>
        </span>
      </div>
      <div class="toolbar-actions">
        <ElCheckbox
          :model-value="entry.onlyShowAbnormal"
          :disabled="entry.anomalyLoading"
          size="small"
          @change="emit('toggle-anomaly')"
        >
          <span class="anomaly-checkbox-label">
            仅异常
            <ElIcon v-if="entry.anomalyLoading" class="is-loading"><Loading /></ElIcon>
          </span>
        </ElCheckbox>
        <ElButton
          v-if="entry.savedJobId && canEdit"
          size="small"
          type="primary"
          plain
          :disabled="!hasDirty"
          :loading="isSaving"
          @click="emit('save-changes')"
        >
          {{ t('reconciliation.inlineEdit.saveChanges') }}
        </ElButton>
        <ElButton
          v-if="entry.savedJobId && canEdit"
          size="small"
          plain
          :loading="isRepricing"
          @click="emit('reprice')"
        >
          {{ t('reconciliation.inlineEdit.reprice') }}
        </ElButton>
        <ElButton
          v-if="entry.savedJobId"
          size="small"
          plain
          @click="emit('open-unmatched')"
        >
          待建档 {{ entry.unmatchedCount ?? '…' }}
        </ElButton>
        <ElButton
          v-if="entry.savedJobId"
          size="small"
          type="danger"
          plain
          :disabled="summary.warning === 0 && summary.corrected === 0"
          @click="emit('export-anomaly')"
        >
          导出异常
        </ElButton>
        <ElTooltip v-if="showFieldConsistencyLegend" placement="top" effect="light">
          <template #content>
            <div class="legend-tooltip">
              <span class="field-consistency-legend field-consistency-legend--red">{{
                t('reconciliation.detail.fieldConsistencyLegendRed')
              }}</span>
              <span class="field-consistency-legend field-consistency-legend--amber">{{
                t('reconciliation.detail.fieldConsistencyLegendAmber')
              }}</span>
              <span class="field-consistency-legend field-consistency-legend--blocked">{{
                t('reconciliation.detail.pricingBlockedLegend')
              }}</span>
            </div>
          </template>
          <button type="button" class="legend-trigger" aria-label="行高亮说明">
            <ElIcon><QuestionFilled /></ElIcon>
          </button>
        </ElTooltip>
        <span
          v-if="hasDirty"
          class="toolbar-hint toolbar-hint--warn"
        >
          {{ t('reconciliation.inlineEdit.dirtyHint') }}
        </span>
        <span
          v-else-if="entry.onlyShowAbnormal && !entry.anomalyLoading"
          class="toolbar-hint toolbar-hint--warn"
        >
          {{
            t('reconciliation.preview.anomalyFilterActive', {
              context: entry.selectedSheetFilter ? `${entry.selectedSheetFilter} · ` : '',
              total: entry.displayTotal
            })
          }}
        </span>
      </div>
    </div>

    <!-- Row 4: version ops (compact) -->
    <div
      v-if="entry.savedJobId && versionItem"
      class="sticky-row sticky-row--version"
      :class="{ 'is-highlighted': versionHighlighted }"
    >
      <ElTag :type="reviewTagType(versionItem.reviewStatus)" size="small" effect="plain">
        {{ reviewLabelMap[versionItem.reviewStatus] ?? versionItem.reviewStatus }}
      </ElTag>
      <ElSelect
        v-if="versionGroup && versionGroup.versions.length > 1"
        :model-value="versionItem.id"
        size="small"
        class="version-select"
        @change="(id: number) => versionGroup && emit('version-change', versionGroup.key, id)"
      >
        <ElOption
          v-for="version in versionGroup.versions"
          :key="version.id"
          :value="version.id"
          :label="formatVersionLabel(version)"
        />
      </ElSelect>
      <span v-else class="text-xs text-gray-500">V{{ versionItem.versionNo }}</span>
      <span class="text-xs text-gray-400">
        {{ t('reconciliation.history.stats.warningRows') }} {{ versionItem.warningRows }}
      </span>
      <div class="version-actions">
        <ElButton size="small" @click="actions?.openDetail(versionItem)">
          {{ t('reconciliation.history.actions.detail') }}
        </ElButton>
        <ElButton
          size="small"
          :disabled="!canReview(versionItem)"
          @click="actions?.openReview(versionItem)"
        >
          {{ t('reconciliation.history.actions.review') }}
        </ElButton>
        <ElDropdown
          trigger="click"
          :disabled="!canExportPerm"
          @command="(cmd: string) => versionItem && actions?.requestExport(versionItem, cmd)"
        >
          <ElButton size="small" :disabled="!canExportPerm">
            {{ t('reconciliation.history.actions.export') }}
            <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
          </ElButton>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem
                v-for="exportType in resolveJobExportTypes(versionItem)"
                :key="exportType"
                :command="exportType"
              >
                {{ t(exportTypeI18nKey(exportType)) }}
              </ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
        <RouterLink
          to="/settings/version-management"
          class="text-xs text-primary hover:underline"
        >
          {{ t('reconciliation.history.scoped.viewAllHistory') }} →
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, inject } from 'vue'
  import { ArrowDown, Loading, QuestionFilled } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import { reconciliationJobActionsKey } from '@/composables/reconciliationJobActionsKey'
  import type { ReconciliationHistoryGroup } from '@/composables/useReconciliationHistory'
  import type {
    ReconciliationEntryPanelEntry,
    ReconciliationEntrySummary
  } from '@/components/business/reconciliation/ReconciliationEntryPanel.vue'
  import { useReconciliationTableColumns } from '@/composables/useReconciliationTableColumns'
  import { useBillingPermission } from '@/composables/useBillingPermission'
  import { exportTypeI18nKey, resolveJobExportTypes } from '@/utils/hospitalExportCapabilities'

  const props = defineProps<{
    fileName: string
    ruleLabel?: string
    ruleTooltip?: string
    removeDisabled?: boolean
    entry: ReconciliationEntryPanelEntry
    summary: ReconciliationEntrySummary
    activeRule: unknown
    isRuleLoading: boolean
    savedVersionLabel: string
    showFieldConsistencyLegend: boolean
    showToolbar: boolean
    versionGroup?: ReconciliationHistoryGroup | null
    versionItem?: Api.Hospital.ReconciliationJob | null
    versionHighlighted?: boolean
    formatVersionLabel: (version: Api.Hospital.ReconciliationJob) => string
    canEdit?: boolean
    hasDirty?: boolean
    isSaving?: boolean
    isRepricing?: boolean
  }>()

  const emit = defineEmits<{
    remove: []
    'select-sheet': [sheetName: string | null]
    process: []
    'toggle-anomaly': []
    'save-changes': []
    reprice: []
    'open-unmatched': []
    'export-anomaly': []
    'version-change': [groupKey: string, jobId: number]
  }>()

  const { t } = useI18n()
  const { formatNumber, formatSignedNumber } = useReconciliationTableColumns()
  const { canReviewReconciliation, canExport } = useBillingPermission()
  const actions = inject(reconciliationJobActionsKey, null)

  const canExportPerm = computed(() => canExport.value)

  const reviewLabelMap: Record<string, string> = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已驳回'
  }

  function reviewTagType(status: string): 'warning' | 'success' | 'danger' {
    switch (status) {
      case 'pending':
        return 'warning'
      case 'approved':
        return 'success'
      case 'rejected':
        return 'danger'
      default:
        return 'warning'
    }
  }

  function canReview(job: Api.Hospital.ReconciliationJob) {
    return canReviewReconciliation.value && job.reviewStatus === 'pending'
  }
</script>

<style scoped>
  .reconciliation-entry-sticky-header {
    position: sticky;
    top: 0;
    z-index: 20;
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding-bottom: 8px;
    margin-bottom: 8px;
    background: #fff;
    box-shadow: 0 1px 0 var(--el-border-color-lighter, #ebeef5);
  }

  .sticky-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
  }

  .sticky-row--file {
    padding: 6px 10px;
    background: var(--el-fill-color-lighter, #f5f7fa);
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 6px;
  }

  .file-strip__info {
    display: flex;
    flex: 1 1 auto;
    flex-wrap: wrap;
    gap: 6px 10px;
    align-items: center;
    min-width: 0;
  }

  .file-strip__name {
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
    white-space: nowrap;
  }

  .file-strip__chip {
    padding: 1px 8px;
    font-size: 11px;
    color: var(--el-text-color-secondary, #909399);
    background: #fff;
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 4px;
  }

  .file-strip__meta {
    font-size: 11px;
    color: var(--el-text-color-secondary, #909399);
  }

  .sticky-row--sheets {
    justify-content: space-between;
  }

  .sticky-row__trailing {
    display: flex;
    flex-shrink: 0;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    margin-left: auto;
  }

  .sticky-row--actions {
    padding: 6px 10px;
    background: var(--el-fill-color-lighter, #f5f7fa);
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 6px;
  }

  .sticky-row--version {
    padding: 6px 10px;
    background: #fafafa;
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 6px;
  }

  .sticky-row--version.is-highlighted {
    background: var(--el-color-primary-light-9, #ecf5ff);
    border-color: var(--el-color-primary-light-7, #c6e2ff);
  }

  .version-select {
    min-width: 140px;
  }

  .version-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
    margin-left: auto;
  }

  .sheet-segmented {
    display: inline-flex;
    flex-wrap: wrap;
    overflow: hidden;
    border: 1px solid var(--el-border-color, #dcdfe6);
    border-radius: 6px;
  }

  .sheet-segment {
    display: inline-flex;
    gap: 4px;
    align-items: center;
    padding: 4px 10px;
    font-size: 12px;
    cursor: pointer;
    background: #fff;
    border: none;
    border-right: 1px solid var(--el-border-color-lighter, #ebeef5);
  }

  .sheet-segment:last-child {
    border-right: none;
  }

  .sheet-segment:disabled {
    cursor: default;
    opacity: 0.65;
  }

  .sheet-segment.is-active {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9, #ecf5ff);
  }

  .sheet-segment__name {
    font-weight: 500;
  }

  .sheet-segment__meta {
    color: var(--el-text-color-secondary, #909399);
  }

  .sheet-segment__warn {
    min-width: 16px;
    padding: 0 4px;
    font-size: 10px;
    font-weight: 600;
    color: #b88230;
    background: #fdf6ec;
    border-radius: 8px;
  }

  .stat-chips {
    display: flex;
    flex: 1 1 auto;
    flex-wrap: wrap;
    gap: 4px 10px;
    align-items: center;
    min-width: 0;
    font-size: 12px;
    color: var(--el-text-color-secondary, #909399);
  }

  .stat-chip strong {
    margin-left: 2px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  .stat-chip--primary strong {
    color: var(--el-color-primary);
  }

  .stat-chip--warn strong {
    color: var(--el-color-warning);
  }

  .stat-chip--divider {
    width: 1px;
    height: 14px;
    padding: 0;
    background: var(--el-border-color, #dcdfe6);
  }

  .toolbar-actions {
    display: flex;
    flex-shrink: 0;
    flex-wrap: wrap;
    gap: 6px 8px;
    align-items: center;
    margin-left: auto;
  }

  .toolbar-hint {
    font-size: 11px;
    color: var(--el-text-color-secondary, #909399);
  }

  .toolbar-hint--loading {
    color: var(--el-color-primary);
  }

  .toolbar-hint--warn {
    color: var(--el-color-warning);
  }

  .anomaly-checkbox-label {
    display: inline-flex;
    gap: 4px;
    align-items: center;
    font-size: 12px;
  }

  .legend-trigger {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    padding: 0;
    color: var(--el-text-color-secondary, #909399);
    cursor: help;
    background: transparent;
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 4px;
  }

  .legend-tooltip {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-width: 280px;
  }

  .field-consistency-legend {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    font-size: 12px;
    font-weight: 600;
    border-radius: 4px;
  }

  .field-consistency-legend--red {
    color: #c45656;
    background: #fef0f0;
    box-shadow: inset 0 0 0 1px #fab6b6;
  }

  .field-consistency-legend--amber {
    color: #b88230;
    background: #fdf6ec;
    box-shadow: inset 0 0 0 1px #f5dab1;
  }

  .field-consistency-legend--blocked {
    color: #f56c6c;
    background: #fef0f0;
    box-shadow: inset 0 0 0 1px #fab6b6;
  }
</style>
