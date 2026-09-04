<template>
  <div class="reconciliation-entry-panel">
    <ReconciliationEntryStickyHeader
      :file-name="fileName"
      :rule-label="ruleLabel"
      :rule-tooltip="ruleTooltip"
      :remove-disabled="removeDisabled"
      :entry="entry"
      :summary="summary"
      :active-rule="activeRule"
      :is-rule-loading="isRuleLoading"
      :saved-version-label="savedVersionLabel"
      :show-field-consistency-legend="showFieldConsistencyLegend"
      :show-toolbar="entry.processedRows.length > 0"
      :version-group="versionGroup ?? null"
      :version-item="versionItem ?? null"
      :version-highlighted="versionHighlighted"
      :format-version-label="formatVersionLabel ?? defaultFormatVersion"
      :can-edit="canEdit"
      :has-dirty="hasDirty"
      :is-saving="isSaving"
      :is-repricing="isRepricing"
      @remove="emit('remove')"
      @select-sheet="(sheet) => emit('select-sheet', sheet)"
      @process="emit('process')"
      @toggle-anomaly="emit('toggle-anomaly')"
      @save-changes="emit('save-changes')"
      @reprice="emit('reprice')"
      @open-unmatched="emit('open-unmatched')"
      @export-anomaly="emit('export-anomaly')"
      @version-change="(key, id) => emit('version-change', key, id)"
    >
      <template #status-badge>
        <slot name="status-badge" />
      </template>
      <template #file-meta>
        <slot name="file-meta" />
      </template>
    </ReconciliationEntryStickyHeader>

    <template v-if="entry.processedRows.length > 0">
      <div class="entry-panel-table-shell">
        <div
          v-if="entry.anomalyLoading"
          class="flex items-center justify-center py-12 text-sm text-blue-500"
        >
          {{ t('reconciliation.preview.anomalyLoading') }}
        </div>
        <template v-else>
          <ReconciliationDataTable
            :rows="tableRows"
            mode="preview"
            :show-sheet-column="!entry.selectedSheetFilter"
            :max-height="tableMaxHeight"
            :row-class-name="rowClassName"
            :editable="canEdit"
            :editable-source-fields="canEdit"
            @open-pricing-flow="(row) => emit('open-pricing-flow', row)"
            @row-field-change="(row, field, value) => emit('row-field-change', row, field, value)"
          />
          <div class="entry-panel-footer">
            <span class="text-xs text-gray-400">
              {{
                entry.onlyShowAbnormal
                  ? t('reconciliation.preview.anomalyMode')
                  : t('reconciliation.preview.currentDisplay')
              }}
              {{
                entry.onlyShowAbnormal
                  ? t('reconciliation.preview.rowCount', { count: displayRowCount })
                  : t('reconciliation.preview.rowCountWithTotal', {
                      current: displayRowCount,
                      total: entry.displayTotal || summary.total
                    })
              }}
            </span>
            <ElPagination
              v-if="!entry.onlyShowAbnormal && entry.displayTotal > entry.displayPageSize"
              :current-page="entry.displayPage"
              :page-size="entry.displayPageSize"
              :total="entry.displayTotal"
              layout="prev, pager, next"
              size="small"
              background
              @current-change="(p: number) => emit('page-change', p)"
            />
          </div>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import ReconciliationDataTable from '@/components/business/reconciliation/ReconciliationDataTable.vue'
  import ReconciliationEntryStickyHeader from '@/components/business/reconciliation/ReconciliationEntryStickyHeader.vue'
  import type { ReconciliationHistoryGroup } from '@/composables/useReconciliationHistory'

  export interface ReconciliationEntryPanelEntry {
    status: string
    savedJobId: number | null
    selectedSheetFilter: string | null
    /** 识别出的医院全称（Excel 内容优先），用于文件条头部展示 */
    hospitalName?: string
    processedRows: Record<string, unknown>[]
    onlyShowAbnormal: boolean
    anomalyLoading: boolean
    sheetFilterLoading: boolean
    displayPage: number
    displayPageSize: number
    displayTotal: number
    unmatchedCount?: number | null
    savedSheetWarningCounts?: Record<string, number> | null
    workbook: {
      previews: Array<{ name: string; dataRows: number; headerRowIndex: number }>
    } | null
  }

  export interface ReconciliationEntrySummary {
    total: number
    corrected: number
    warning: number
    totalDifference: number
    originalTotalPrice: number
    correctedTotalPrice: number
  }

  const props = defineProps<{
    fileName: string
    ruleLabel?: string
    ruleTooltip?: string
    removeDisabled?: boolean
    entry: ReconciliationEntryPanelEntry
    summary: ReconciliationEntrySummary
    displayRows: Record<string, unknown>[]
    activeRule: unknown
    isRuleLoading: boolean
    savedVersionLabel: string
    showFieldConsistencyLegend: boolean
    rowClassName: (ctx: { row: Record<string, unknown> }) => string
    versionGroup?: ReconciliationHistoryGroup | null
    versionItem?: Api.Hospital.ReconciliationJob | null
    versionHighlighted?: boolean
    formatVersionLabel?: (version: Api.Hospital.ReconciliationJob) => string
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
    'page-change': [page: number]
    'open-pricing-flow': [row: Record<string, unknown>]
    'row-field-change': [row: Record<string, unknown>, field: string, value: unknown]
    'version-change': [groupKey: string, jobId: number]
  }>()

  const { t } = useI18n()

  const tableRows = computed(() => props.displayRows)
  const displayRowCount = computed(() => props.displayRows.length)

  const tableMaxHeight = computed(() =>
    props.entry.onlyShowAbnormal ? 'calc(100vh - 280px)' : 'calc(100vh - 320px)'
  )

  function defaultFormatVersion(version: Api.Hospital.ReconciliationJob) {
    return `V${version.versionNo}`
  }
</script>

<style scoped>
  .entry-panel-table-shell {
    overflow: hidden;
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 6px;
  }

  .entry-panel-footer {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px 6px;
    background: #fff;
    border-top: 1px solid var(--el-border-color-extra-light, #f2f6fc);
  }
</style>
