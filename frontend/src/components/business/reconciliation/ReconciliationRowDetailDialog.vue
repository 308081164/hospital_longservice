<template>
  <ElDialog
    :model-value="visible"
    class="reconciliation-row-detail-dialog"
    width="720px"
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => !v && emit('close')"
  >
    <template #header>
      <div class="row-detail-header">
        <span class="row-detail-header__title">
          行详情
          <span v-if="row" class="row-detail-header__meta">
            #{{ row['rowNumber'] }}
            <template v-if="row['sheetName']"> · {{ row['sheetName'] }}</template>
          </span>
        </span>
        <div class="row-detail-header__nav">
          <ElButton
            :icon="ArrowLeft"
            circle
            size="small"
            :disabled="!canGoPrev"
            aria-label="上一条"
            @click="emit('navigate', currentIndex - 1)"
          />
          <span class="row-detail-header__counter">{{ positionLabel }}</span>
          <ElButton
            :icon="ArrowRight"
            circle
            size="small"
            :disabled="!canGoNext"
            aria-label="下一条"
            @click="emit('navigate', currentIndex + 1)"
          />
        </div>
      </div>
    </template>

    <div v-if="row" class="row-detail-body">
      <div class="row-detail-grid">
        <div class="row-detail-field">
          <span class="row-detail-label">发货日期</span>
          <span class="row-detail-value">{{ row['deliveryDate'] ?? '—' }}</span>
        </div>
        <div class="row-detail-field row-detail-field--wide">
          <span class="row-detail-label">包名</span>
          <FieldConsistencyHighlight :row="row" field="packName">
            <span class="row-detail-value">{{ row['packName'] }}</span>
          </FieldConsistencyHighlight>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">类型</span>
          <input
            v-if="fieldEditable('type')"
            :value="row['type']"
            type="text"
            class="detail-cell-input detail-cell-input--text"
            @input="(e: Event) => onField('type', (e.target as HTMLInputElement).value)"
          />
          <FieldConsistencyHighlight v-else :row="row" field="type">
            <span class="row-detail-value">{{ row['type'] }}</span>
          </FieldConsistencyHighlight>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">包装材料</span>
          <input
            v-if="fieldEditable('packageMaterial')"
            :value="row['packageMaterial']"
            type="text"
            class="detail-cell-input detail-cell-input--text"
            @input="
              (e: Event) => onField('packageMaterial', (e.target as HTMLInputElement).value)
            "
          />
          <FieldConsistencyHighlight v-else :row="row" field="packageMaterial">
            <span class="row-detail-value">{{ row['packageMaterial'] }}</span>
          </FieldConsistencyHighlight>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">器械数</span>
          <input
            v-if="fieldEditable('instrumentCount')"
            :value="row['instrumentCount']"
            type="number"
            min="0"
            step="1"
            class="detail-cell-input"
            @input="
              (e: Event) =>
                onField('instrumentCount', Number((e.target as HTMLInputElement).value))
            "
          />
          <FieldConsistencyHighlight v-else :row="row" field="instrumentCount">
            <span class="row-detail-value">{{ row['instrumentCount'] }}</span>
          </FieldConsistencyHighlight>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">包数</span>
          <input
            v-if="fieldEditable('packCount')"
            :value="row['packCount']"
            type="number"
            min="1"
            step="1"
            class="detail-cell-input"
            @input="
              (e: Event) => onField('packCount', Number((e.target as HTMLInputElement).value))
            "
          />
          <span v-else class="row-detail-value">{{ row['packCount'] }}</span>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">原单价</span>
          <span class="row-detail-value">{{ formatNumber(row['unitPrice'] as number | null) }}</span>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">规则单价</span>
          <span class="row-detail-value">{{
            formatNumber(row['expectedUnitPrice'] as number | null)
          }}</span>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">原总价</span>
          <span class="row-detail-value">{{
            formatNumber(row['totalPrice'] as number | null)
          }}</span>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">修正总价</span>
          <input
            v-if="fieldEditable('correctedTotalPrice')"
            :value="row['correctedTotalPrice']"
            type="number"
            step="0.01"
            min="0"
            class="detail-cell-input"
            @input="
              (e: Event) =>
                onField('correctedTotalPrice', (e.target as HTMLInputElement).value)
            "
          />
          <span v-else class="row-detail-value">{{
            formatNumber(row['correctedTotalPrice'] as number | null)
          }}</span>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">差额</span>
          <span
            class="row-detail-value"
            :class="((row['difference'] as number) ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'"
          >
            {{ formatSignedNumber(row['difference'] as number | null) }}
          </span>
        </div>
        <div class="row-detail-field">
          <span class="row-detail-label">状态</span>
          <template v-if="fieldEditable('status')">
            <select
              :value="row['status']"
              class="detail-cell-select"
              @change="(e: Event) => onField('status', (e.target as HTMLSelectElement).value)"
            >
              <option value="corrected">已修正</option>
              <option value="unchanged">无需修改</option>
              <option value="warning">人工复核</option>
              <option value="skipped">已跳过</option>
            </select>
          </template>
          <ElTag v-else :type="statusTagType(row['status'] as string)" size="small" effect="plain">
            {{ statusLabels[row['status'] as string] ?? row['status'] }}
          </ElTag>
        </div>
      </div>

      <div class="row-detail-section">
        <span class="row-detail-label">计价规则</span>
        <PricingPathTag :row="row" @open-detail="emit('open-pricing-flow', row)" />
      </div>
      <div class="row-detail-section">
        <span class="row-detail-label">计费备注</span>
        <ReconciliationBillingDetail
          :row="row"
          show-view-detail-link
          @open-detail="emit('open-pricing-flow', row)"
        />
      </div>
    </div>

    <template #footer>
      <div class="row-detail-footer">
        <div class="row-detail-footer__left">
          <ElButton
            v-if="editable && row && canFixRow"
            type="warning"
            plain
            size="small"
            @click="emit('fix-row', row)"
          >
            修正
          </ElButton>
          <ElButton
            v-if="editable && row && rowRepriceEnabled && isAnomalyEditableRow(row)"
            type="primary"
            size="small"
            :loading="repricingRowId === row['id']"
            :disabled="repricingRowId != null && repricingRowId !== row['id']"
            @click="emit('reprice-row', row)"
          >
            保存并重算
          </ElButton>
        </div>
        <ElButton size="small" @click="emit('close')">关闭</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import { computed, onUnmounted, watch } from 'vue'
  import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
  import FieldConsistencyHighlight from '@/components/business/reconciliation/FieldConsistencyHighlight.vue'
  import PricingPathTag from '@/components/business/reconciliation/PricingPathTag.vue'
  import ReconciliationBillingDetail from '@/components/business/reconciliation/ReconciliationBillingDetail.vue'
  import { useReconciliationTableColumns } from '@/composables/useReconciliationTableColumns'
  import { isAnomalyEditableRow } from '@/composables/useReconciliationEntryEditing'

  const props = defineProps<{
    visible: boolean
    row: Record<string, unknown> | null
    currentIndex: number
    total: number
    editable?: boolean
    editableSourceFields?: boolean
    rowRepriceEnabled?: boolean
    repricingRowId?: number | null
  }>()

  const emit = defineEmits<{
    close: []
    navigate: [index: number]
    'field-change': [row: Record<string, unknown>, field: string, value: unknown]
    'fix-row': [row: Record<string, unknown>]
    'reprice-row': [row: Record<string, unknown>]
    'open-pricing-flow': [row: Record<string, unknown>]
  }>()

  const { formatNumber, formatSignedNumber, statusLabels, statusTagType } =
    useReconciliationTableColumns()

  const canGoPrev = computed(() => props.currentIndex > 0)
  const canGoNext = computed(() => props.currentIndex < props.total - 1)
  const positionLabel = computed(() =>
    props.total > 0 ? `${props.currentIndex + 1} / ${props.total}` : '—'
  )

  const canFixRow = computed(() => {
    if (!props.row) return false
    const diff = props.row['difference'] as number | null | undefined
    return diff != null && diff !== 0 && props.row['status'] !== 'corrected'
  })

  function fieldEditable(field: string): boolean {
    if (!props.editable || !props.row || !isAnomalyEditableRow(props.row)) return false
    if (field === 'correctedTotalPrice' || field === 'status') return true
    return Boolean(props.editableSourceFields)
  }

  function onField(field: string, value: unknown) {
    if (!props.row) return
    emit('field-change', props.row, field, value)
  }

  function onKeydown(event: KeyboardEvent) {
    if (!props.visible) return
    if (event.key === 'ArrowLeft' && canGoPrev.value) {
      event.preventDefault()
      emit('navigate', props.currentIndex - 1)
    } else if (event.key === 'ArrowRight' && canGoNext.value) {
      event.preventDefault()
      emit('navigate', props.currentIndex + 1)
    }
  }

  watch(
    () => props.visible,
    (open) => {
      if (open) window.addEventListener('keydown', onKeydown)
      else window.removeEventListener('keydown', onKeydown)
    }
  )

  onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
  .row-detail-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-right: 28px;
  }

  .row-detail-header__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .row-detail-header__meta {
    margin-left: 8px;
    font-size: 13px;
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  .row-detail-header__nav {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .row-detail-header__counter {
    min-width: 56px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    text-align: center;
  }

  .row-detail-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .row-detail-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px 16px;
  }

  .row-detail-field--wide {
    grid-column: 1 / -1;
  }

  .row-detail-field,
  .row-detail-section {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .row-detail-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .row-detail-value {
    font-size: 14px;
    line-height: 1.4;
    color: var(--el-text-color-primary);
    word-break: break-all;
  }

  .row-detail-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
  }

  .row-detail-footer__left {
    display: flex;
    gap: 8px;
  }
</style>
