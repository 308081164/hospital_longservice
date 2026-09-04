<template>
  <div class="reconciliation-data-table">
    <ElTable
      :data="rows"
      border
      stripe
      size="small"
      style="width: 100%"
      :max-height="maxHeight"
      :default-sort="defaultSort"
      :row-class-name="rowClassName"
      @selection-change="onSelectionChange"
    >
      <ElTableColumn
        v-if="mode === 'detail'"
        type="selection"
        width="42"
        fixed="left"
        :selectable="rowSelectable"
      />
      <ElTableColumn
        prop="rowNumber"
        :label="columnLabel('rowNumber')"
        width="65"
        sortable
        fixed="left"
      />
      <ElTableColumn
        v-if="showSheetColumn"
        prop="sheetName"
        :label="columnLabel('sheetName')"
        min-width="90"
        sortable
        fixed="left"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="deliveryDate"
        :label="columnLabel('deliveryDate')"
        width="110"
        sortable
      />
      <ElTableColumn prop="type" :label="columnLabel('type')" min-width="90" sortable>
        <template #default="{ row }">
          <input
            v-if="isInlineEditable(row, 'type')"
            :value="row['type']"
            type="text"
            class="detail-cell-input detail-cell-input--text"
            @input="(e: Event) => emitFieldChange(row, 'type', (e.target as HTMLInputElement).value)"
          />
          <FieldConsistencyHighlight v-else :row="row" field="type">
            {{ row['type'] }}
          </FieldConsistencyHighlight>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="packName"
        :label="columnLabel('packName')"
        min-width="160"
        sortable
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <FieldConsistencyHighlight :row="row" field="packName">
            {{ row['packName'] }}
          </FieldConsistencyHighlight>
        </template>
      </ElTableColumn>
      <ElTableColumn
        v-if="mode === 'detail'"
        :label="columnLabel('suggestedDepartment')"
        min-width="120"
      >
        <template #default="{ row }">
          <span
            v-if="rosterHintMap?.get(row['rowNumber'] as number)"
            class="text-primary text-xs"
          >
            {{ rosterHintMap.get(row['rowNumber'] as number)?.suggestedDepartment }}
            <span class="text-gray-400">
              ({{ rosterHintMap.get(row['rowNumber'] as number)?.matchedDoctor }})
            </span>
          </span>
          <span v-else class="text-gray-300 text-xs">—</span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="packageMaterial"
        :label="columnLabel('packageMaterial')"
        min-width="110"
        sortable
      >
        <template #default="{ row }">
          <input
            v-if="isInlineEditable(row, 'packageMaterial')"
            :value="row['packageMaterial']"
            type="text"
            class="detail-cell-input detail-cell-input--text"
            @input="
              (e: Event) =>
                emitFieldChange(row, 'packageMaterial', (e.target as HTMLInputElement).value)
            "
          />
          <FieldConsistencyHighlight v-else :row="row" field="packageMaterial">
            {{ row['packageMaterial'] }}
          </FieldConsistencyHighlight>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="instrumentCount"
        :label="columnLabel('instrumentCount')"
        width="80"
        sortable
        align="right"
      >
        <template #default="{ row }">
          <input
            v-if="isInlineEditable(row, 'instrumentCount')"
            :value="row['instrumentCount']"
            type="number"
            min="0"
            step="1"
            class="detail-cell-input"
            @input="
              (e: Event) =>
                emitFieldChange(row, 'instrumentCount', Number((e.target as HTMLInputElement).value))
            "
          />
          <FieldConsistencyHighlight v-else :row="row" field="instrumentCount">
            {{ row['instrumentCount'] }}
          </FieldConsistencyHighlight>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="packCount"
        :label="columnLabel('packCount')"
        width="70"
        sortable
        align="right"
      />
      <ElTableColumn
        :label="columnLabel('unitPrice')"
        width="100"
        sortable
        prop="unitPrice"
        align="right"
      >
        <template #default="{ row }">{{ formatNumber(row['unitPrice'] as number | null) }}</template>
      </ElTableColumn>
      <ElTableColumn
        :label="columnLabel('expectedUnitPrice')"
        width="100"
        sortable
        prop="expectedUnitPrice"
        align="right"
      >
        <template #default="{ row }">
          <span class="pricing-value-with-indicator">
            <span>{{ formatNumber(row['expectedUnitPrice'] as number | null) }}</span>
            <ElTooltip
              v-if="shouldShowValidationIndicator(row)"
              placement="top"
              :content="validationIndicatorTooltip(row)"
            >
              <span class="pricing-blocked-indicator" aria-label="validation issue">!</span>
            </ElTooltip>
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="columnLabel('totalPrice')"
        width="100"
        sortable
        prop="totalPrice"
        align="right"
      >
        <template #default="{ row }">{{ formatNumber(row['totalPrice'] as number | null) }}</template>
      </ElTableColumn>
      <ElTableColumn
        :label="columnLabel('correctedTotalPrice')"
        :width="mode === 'detail' ? 120 : 100"
        sortable
        prop="correctedTotalPrice"
        align="right"
      >
        <template #default="{ row }">
          <span class="pricing-value-with-indicator">
            <input
              v-if="isInlineEditable(row, 'correctedTotalPrice')"
              :value="row['correctedTotalPrice']"
              type="number"
              step="0.01"
              min="0"
              class="detail-cell-input"
              @input="
                (e: Event) =>
                  emitFieldChange(row, 'correctedTotalPrice', (e.target as HTMLInputElement).value)
              "
            />
            <span v-else>{{ formatNumber(row['correctedTotalPrice'] as number | null) }}</span>
            <ElTooltip
              v-if="shouldShowValidationIndicator(row)"
              placement="top"
              :content="validationIndicatorTooltip(row)"
            >
              <span class="pricing-blocked-indicator" aria-label="validation issue">!</span>
            </ElTooltip>
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="columnLabel('difference')"
        width="100"
        sortable
        prop="difference"
        align="right"
      >
        <template #default="{ row }">
          <span
            v-if="isInlineEditable(row)"
            :class="((row['difference'] as number) ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'"
          >
            {{ formatSignedNumber(row['difference'] as number | null) }}
          </span>
          <span v-else>{{ formatSignedNumber(row['difference'] as number | null) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn type="expand" width="42" :label="t('table.column.expand')">
        <template #default="{ row }">
          <ReconciliationBillingDetail v-if="hasPricingDetail(row)" :row="row" expanded />
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('reconciliation.detail.pricingRuleColumn')" min-width="140">
        <template #default="{ row }">
          <PricingPathTag :row="row" @open-detail="emitOpenPricingFlow" />
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('reconciliation.detail.billingNotes')" min-width="160">
        <template #default="{ row }">
          <span v-if="mode === 'detail' && row['isUrgent']" class="mr-1 text-xs text-warning">{{
            t('reconciliation.detail.urgentTag')
          }}</span>
          <ReconciliationBillingDetail
            :row="row"
            show-view-detail-link
            @open-detail="emitOpenPricingFlow"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="columnLabel('status')"
        :width="mode === 'detail' ? 110 : 90"
        sortable
        prop="status"
      >
        <template #default="{ row }">
          <template v-if="isInlineEditable(row, 'status')">
            <select
              :value="row['status']"
              class="detail-cell-select"
              @change="
                (e: Event) =>
                  emitFieldChange(row, 'status', (e.target as HTMLSelectElement).value)
              "
            >
              <option value="corrected">已修正</option>
              <option value="unchanged">无需修改</option>
              <option value="warning">人工复核</option>
              <option value="skipped">已跳过</option>
            </select>
          </template>
          <template v-else-if="mode === 'detail' && editable">
            <button
              v-if="hasRowDifference(row) && row['status'] !== 'corrected'"
              class="detail-cell-btn-warning"
              @click="emitFixSingleRow(row)"
            >
              修正
            </button>
            <span
              v-else-if="hasRowDifference(row) && row['status'] === 'corrected'"
              class="detail-cell-tag detail-cell-tag-success"
              >已修正</span
            >
            <select
              v-else
              v-model="row['status']"
              class="detail-cell-select"
              @change="emitRowChange(row)"
            >
              <option value="corrected">已修正</option>
              <option value="unchanged">无需修改</option>
              <option value="warning">人工复核</option>
              <option value="skipped">已跳过</option>
            </select>
          </template>
          <ElTag
            v-else-if="mode === 'preview'"
            :type="statusTagType(row['status'] as string)"
            size="small"
            effect="plain"
          >
            {{ statusLabels[row['status'] as string] ?? row['status'] }}
          </ElTag>
          <span v-else class="detail-cell-tag" :class="statusTagClass(row['status'] as string)">
            {{ statusLabels[row['status'] as string] ?? row['status'] }}
          </span>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import type { RosterMatchHint } from '@/api/billing-config/allocationApi'
  import FieldConsistencyHighlight from '@/components/business/reconciliation/FieldConsistencyHighlight.vue'
  import PricingPathTag from '@/components/business/reconciliation/PricingPathTag.vue'
  import ReconciliationBillingDetail from '@/components/business/reconciliation/ReconciliationBillingDetail.vue'
  import { useReconciliationTableColumns } from '@/composables/useReconciliationTableColumns'
  import { isAnomalyEditableRow } from '@/composables/useReconciliationEntryEditing'
  import {
    shouldShowValidationIndicator,
    validationIndicatorMessages
  } from '@/utils/reconciliationBillingNotes'
  import { hasPricingDetail } from '@/utils/reconciliationPricingPath'

  const props = withDefaults(
    defineProps<{
      rows: Record<string, unknown>[]
      mode?: 'preview' | 'detail'
      showSheetColumn?: boolean
      maxHeight?: string
      editable?: boolean
      editableSourceFields?: boolean
      rosterHintMap?: Map<number, RosterMatchHint>
      rowClassName?: (ctx: { row: Record<string, unknown> }) => string
      rowSelectable?: (row: Record<string, unknown>) => boolean
    }>(),
    {
      mode: 'preview',
      showSheetColumn: true,
      maxHeight: '500px',
      editable: false,
      editableSourceFields: false,
      rosterHintMap: undefined,
      rowClassName: undefined,
      rowSelectable: undefined
    }
  )

  const emit = defineEmits<{
    'open-pricing-flow': [row: Record<string, unknown>]
    'selection-change': [rows: Record<string, unknown>[]]
    'row-edit': [row: Record<string, unknown>, value: string]
    'row-field-change': [row: Record<string, unknown>, field: string, value: unknown]
    'row-change': [row: Record<string, unknown>]
    'fix-single-row': [row: Record<string, unknown>]
  }>()

  const { t } = useI18n()
  const {
    columnLabel,
    formatNumber,
    formatSignedNumber,
    statusLabels,
    statusTagType,
    statusTagClass
  } = useReconciliationTableColumns()

  const defaultSort = computed(() => {
    if (props.showSheetColumn) {
      return { prop: 'sheetName', order: 'ascending' as const }
    }
    return { prop: 'rowNumber', order: 'ascending' as const }
  })

  function validationIndicatorTooltip(row: Record<string, unknown>): string {
    const messages = validationIndicatorMessages(row)
    if (messages.length > 0) return messages.join('\n')
    return t('reconciliation.detail.pricingBlockedHint')
  }

  function hasRowDifference(row: Record<string, unknown>): boolean {
    const diff = row['difference'] as number | null | undefined
    return diff !== null && diff !== undefined && diff !== 0
  }

  function emitOpenPricingFlow(row: Record<string, unknown>) {
    emit('open-pricing-flow', row)
  }

  function onSelectionChange(rows: Record<string, unknown>[]) {
    emit('selection-change', rows)
  }

  function isInlineEditable(row: Record<string, unknown>, field?: string): boolean {
    if (!props.editable || !isAnomalyEditableRow(row)) return false
    if (props.mode === 'detail') {
      if (field === 'correctedTotalPrice' || field === 'status' || !field) return true
      return Boolean(props.editableSourceFields)
    }
    if (props.mode === 'preview') {
      if (field === 'correctedTotalPrice' || field === 'status') return true
      if (props.editableSourceFields && field) {
        return field === 'packageMaterial' || field === 'instrumentCount' || field === 'type'
      }
    }
    return false
  }

  function emitRowEdit(row: Record<string, unknown>, value: string) {
    emit('row-edit', row, value)
    emitFieldChange(row, 'correctedTotalPrice', value)
  }

  function emitFieldChange(row: Record<string, unknown>, field: string, value: unknown) {
    emit('row-field-change', row, field, value)
  }

  function emitRowChange(row: Record<string, unknown>) {
    emit('row-change', row)
  }

  function emitFixSingleRow(row: Record<string, unknown>) {
    emit('fix-single-row', row)
  }
</script>

<style scoped>
  .reconciliation-data-table :deep(.detail-row-diff td.el-table__cell) {
    background-color: #fef0f0 !important;
  }

  .reconciliation-data-table :deep(.detail-row-diff:hover td.el-table__cell) {
    background-color: #fde2e2 !important;
  }

  .reconciliation-data-table :deep(.detail-row-ok td.el-table__cell) {
    background-color: #f0f9eb !important;
  }

  .reconciliation-data-table :deep(.detail-row-ok:hover td.el-table__cell) {
    background-color: #e1f3d8 !important;
  }

  .reconciliation-data-table :deep(.field-consistency-row td.el-table__cell) {
    background-color: #fff7f7 !important;
  }

  .reconciliation-data-table :deep(.field-consistency-row:hover td.el-table__cell) {
    background-color: #ffeded !important;
  }

  .reconciliation-data-table :deep(.detail-row-urgent td.el-table__cell) {
    background-color: #fdf6ec !important;
  }

  .reconciliation-data-table :deep(.el-table__fixed .el-table__cell) {
    background-color: inherit;
  }

  .reconciliation-data-table :deep(.el-table__body tr.el-table__row--striped td.el-table__cell) {
    background-color: var(--el-fill-color-lighter);
  }

  .reconciliation-data-table :deep(.detail-row-diff.el-table__row--striped td.el-table__cell),
  .reconciliation-data-table :deep(.detail-row-diff td.el-table__cell) {
    background-color: #fef0f0 !important;
  }

  .reconciliation-data-table :deep(.detail-row-ok.el-table__row--striped td.el-table__cell),
  .reconciliation-data-table :deep(.detail-row-ok td.el-table__cell) {
    background-color: #f0f9eb !important;
  }

  .reconciliation-data-table :deep(.field-consistency-row.el-table__row--striped td.el-table__cell),
  .reconciliation-data-table :deep(.field-consistency-row td.el-table__cell) {
    background-color: #fff7f7 !important;
  }

  .detail-cell-input--text {
    width: 100%;
    min-width: 72px;
    text-align: left;
  }

  /* 宽度 100% 适配列宽：固定 100px 在 80px 器械数列中会被裁掉一截 */
  .detail-cell-input {
    width: 100%;
    padding: 4px 8px;
    font-size: 12px;
    text-align: right;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    outline: none;
    transition: border-color 0.2s;
  }

  .detail-cell-input:focus {
    border-color: #409eff;
  }

  .detail-cell-input::-webkit-inner-spin-button,
  .detail-cell-input::-webkit-outer-spin-button {
    height: 24px;
    opacity: 1;
  }

  /* 宽度 100% 适配列宽：固定 95px 在 90px 状态列中会被裁掉一截 */
  .detail-cell-select {
    width: 100%;
    padding: 4px;
    font-size: 12px;
    background: #fff;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    outline: none;
  }

  .detail-cell-select:focus {
    border-color: #409eff;
  }

  .detail-cell-btn-warning {
    display: inline-block;
    padding: 4px 8px;
    font-size: 12px;
    color: #e6a23c;
    white-space: nowrap;
    cursor: pointer;
    background: #fdf6ec;
    border: 1px solid #e6a23c;
    border-radius: 4px;
  }

  .detail-cell-btn-warning:hover {
    background: #f5dab1;
  }

  .detail-cell-tag {
    display: inline-block;
    padding: 2px 6px;
    font-size: 12px;
    white-space: nowrap;
    border-radius: 4px;
  }

  .detail-cell-tag-primary {
    color: #409eff;
    background: #ecf5ff;
    border: 1px solid #409eff;
  }

  .detail-cell-tag-success {
    color: #67c23a;
    background: #f0f9eb;
    border: 1px solid #67c23a;
  }

  .detail-cell-tag-info {
    color: #909399;
    background: #f4f4f5;
    border: 1px solid #c0c4cc;
  }

  .detail-cell-tag-warning {
    color: #e6a23c;
    background: #fdf6ec;
    border: 1px solid #e6a23c;
  }

  .pricing-value-with-indicator {
    display: inline-flex;
    gap: 4px;
    align-items: center;
    justify-content: flex-end;
  }

  .pricing-blocked-indicator {
    display: inline-flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    font-size: 14px;
    font-weight: 700;
    line-height: 1;
    color: #f56c6c;
    cursor: help;
  }

  /* 列总宽超出容器时显示原生横向滚动条：Element Plus 的 el-scrollbar__wrap--hidden-default
     会隐藏原生滚动条、自定义滚动条又仅悬停可见，导致右侧列被裁剪且用户无从察觉 */
  .reconciliation-data-table :deep(.el-scrollbar__wrap) {
    scrollbar-width: thin;
  }

  .reconciliation-data-table :deep(.el-scrollbar__wrap)::-webkit-scrollbar {
    display: block;
    width: 8px;
    height: 8px;
  }

  .reconciliation-data-table :deep(.el-scrollbar__wrap)::-webkit-scrollbar-thumb {
    background: var(--el-border-color-dark, #c0c4cc);
    border-radius: 4px;
  }

  .reconciliation-data-table :deep(.el-scrollbar__wrap)::-webkit-scrollbar-track {
    background: transparent;
  }
</style>
