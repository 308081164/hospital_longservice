<template>
  <ElDrawer
    :model-value="visible"
    :title="t('pricingFlow.title')"
    size="480px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <template v-if="row">
      <section class="mb-4 rounded-lg bg-gray-50 p-3">
        <div class="mb-2 text-sm font-medium text-gray-800">{{ packTitle }}</div>
        <div class="grid grid-cols-2 gap-2 text-xs">
          <div>
            <span class="text-gray-500">{{ t('reconciliation.detail.billPrice') }}：</span>
            <span class="font-medium">{{ formatCurrency(billUnitPrice) }}</span>
          </div>
          <div>
            <span class="text-gray-500">{{ t('reconciliation.detail.expectedPrice') }}：</span>
            <ElTooltip
              v-if="ctx.blocksPricingDisplay"
              placement="top"
              :content="t('reconciliation.detail.pricingBlockedHint')"
            >
              <span class="pricing-blocked-indicator" aria-label="pricing blocked">!</span>
            </ElTooltip>
            <span v-else class="font-medium">{{ formatCurrency(expectedUnitPrice) }}</span>
          </div>
          <div>
            <span class="text-gray-500">{{ t('reconciliation.columns.packCount') }}：</span>
            <span class="font-medium">{{ formatCount(packCount) }}</span>
          </div>
          <div>
            <span class="text-gray-500">{{ t('reconciliation.columns.instrumentCount') }}：</span>
            <span class="font-medium">{{ formatCount(instrumentCount) }}</span>
          </div>
          <div>
            <span class="text-gray-500">{{ t('pricingFlow.difference') }}：</span>
            <span
              class="font-medium"
              :class="(difference ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'"
            >
              {{ formatSignedNumber(difference) }}
            </span>
          </div>
          <div class="flex items-center gap-1">
            <span class="text-gray-500">{{ t('pricingFlow.status') }}：</span>
            <ElTag :type="statusTagType" size="small" effect="plain">{{ statusLabel }}</ElTag>
          </div>
        </div>
        <div class="mt-2">
          <PricingPathTag :row="row" :clickable="false" />
        </div>
      </section>

      <section v-if="pricingRule" class="mb-4">
        <div class="mb-2 flex items-center justify-between">
          <h4 class="text-sm font-medium text-gray-700">{{ t('pricingFlow.ruleSummary') }}</h4>
          <ElButton size="small" text type="primary" @click="copyPricingRule">
            {{ t('pricingFlow.copyRule') }}
          </ElButton>
        </div>
        <div class="rounded border border-gray-200 bg-white p-3 text-sm text-gray-800">
          {{ localizedPricingRule }}
        </div>
      </section>

      <section class="mb-4">
        <h4 class="mb-2 text-sm font-medium text-gray-700">{{ t('pricingFlow.timeline') }}</h4>
        <ol v-if="timeline.length" class="pricing-flow-timeline space-y-3">
          <li v-for="(step, index) in timeline" :key="index" class="flex gap-2">
            <span class="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs text-primary">
              {{ index + 1 }}
            </span>
            <div class="min-w-0 flex-1">
              <div class="text-xs font-medium text-gray-600">{{ stepLabel(step, index) }}</div>
              <div v-if="step.detail" class="mt-0.5 text-sm text-gray-800 break-words">{{ localizeDisplayText(step.detail) }}</div>
            </div>
          </li>
        </ol>
        <div v-else class="text-sm text-gray-400">{{ t('reconciliation.detail.billingEmpty') }}</div>
      </section>

      <section v-if="pricingPathLabel || matchedProductId != null" class="mb-4">
        <h4 class="mb-2 text-sm font-medium text-gray-700">{{ t('pricingFlow.traceMeta') }}</h4>
        <div class="rounded border border-gray-200 p-3 text-sm space-y-1">
          <div v-if="pricingPathLabel">
            <span class="text-gray-500">{{ t('pricingFlow.pricingPath') }}：</span>
            {{ pricingPathLabel }}
          </div>
          <div v-if="matchedProductId != null">
            <span class="text-gray-500">{{ t('pricingFlow.matchedProductId') }}：</span>
            <span class="font-mono text-xs">{{ matchedProductId }}</span>
          </div>
        </div>
      </section>

      <section v-if="ctx.matchedRuleId != null" class="mb-2">
        <h4 class="mb-2 text-sm font-medium text-gray-700">{{ t('reconciliation.detail.ruleTraceSection') }}</h4>
        <div class="rounded border border-gray-200 p-3 text-sm">
          <div v-if="ctx.ruleName">
            <span class="text-gray-500">{{ t('reconciliation.detail.ruleName') }}：</span>
            {{ ctx.ruleName }}
          </div>
          <div class="mt-1 font-mono text-xs text-gray-600">
            {{ t('reconciliation.detail.matchedRuleId') }}: {{ ctx.matchedRuleId }}
          </div>
        </div>
      </section>

      <ReconciliationBillingDetail v-if="hasBillingContent" :row="row" expanded />
    </template>
  </ElDrawer>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { ElMessage } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import ReconciliationBillingDetail from '@/components/business/reconciliation/ReconciliationBillingDetail.vue'
  import PricingPathTag from '@/components/business/reconciliation/PricingPathTag.vue'
  import {
    formatReconciliationCurrency,
    hasBillingDetail,
    parseReconciliationBillingContext
  } from '@/utils/reconciliationBillingNotes'
  import { buildPricingFlowTimeline, type PricingFlowStep } from '@/utils/reconciliationPricingPath'
  import {
    formatPricingPathDisplay,
    formatReconciliationStatusDisplay,
    localizeReconciliationDisplayText
  } from '@/utils/reconciliationDisplayText'

  defineOptions({ name: 'PricingFlowDrawer' })

  const props = defineProps<{
    visible: boolean
    row: Record<string, unknown> | null
  }>()

  const emit = defineEmits<{
    'update:visible': [value: boolean]
  }>()

  const { t } = useI18n()

  const ctx = computed(() =>
    props.row ? parseReconciliationBillingContext(props.row) : parseReconciliationBillingContext({})
  )

  const timeline = computed(() => (props.row ? buildPricingFlowTimeline(props.row) : []))

  const pricingRule = computed(() => {
    if (!props.row) return ''
    const raw = props.row.pricingRule ?? props.row.pricing_rule
    return raw == null ? '' : String(raw).trim()
  })

  const localizedPricingRule = computed(() =>
    pricingRule.value ? localizeReconciliationDisplayText(pricingRule.value) : ''
  )

  const packTitle = computed(() => {
    if (!props.row) return '—'
    const packName = String(props.row.packName ?? props.row.pack_name ?? '—')
    const sheet = props.row.sheetName ?? props.row.sheet_name
    return sheet ? `${packName}（${sheet}）` : packName
  })

  const billUnitPrice = computed(() => {
    const v = props.row?.unitPrice ?? props.row?.unit_price
    return typeof v === 'number' ? v : null
  })

  const expectedUnitPrice = computed(() => ctx.value.expectedUnitPrice)

  const difference = computed(() => {
    const v = props.row?.difference
    return typeof v === 'number' ? v : null
  })

  const packCount = computed(() => readRowNumber(props.row, 'packCount', 'pack_count'))

  const instrumentCount = computed(() =>
    readRowNumber(props.row, 'instrumentCount', 'instrument_count')
  )

  const statusLabel = computed(() => formatReconciliationStatusDisplay(String(props.row?.status ?? '')))

  const statusTagType = computed(() => {
    const status = String(props.row?.status ?? '')
    if (status === 'corrected') return 'primary'
    if (status === 'unchanged') return 'success'
    if (status === 'warning') return 'warning'
    return 'info'
  })

  const hasBillingContent = computed(() => (props.row ? hasBillingDetail(props.row) : false))

  const matchedProductId = computed(() => {
    const v = props.row?.matchedProductId ?? props.row?.matched_product_id
    return typeof v === 'number' && Number.isFinite(v) ? v : null
  })

  const pricingPathLabel = computed(() => {
    const raw = props.row?.pricingPath ?? props.row?.pricing_path
    return raw == null || raw === '' ? '' : formatPricingPathDisplay(String(raw))
  })

  function localizeDisplayText(value: string): string {
    return localizeReconciliationDisplayText(value)
  }

  function formatCurrency(value: number | null | undefined): string {
    return formatReconciliationCurrency(value)
  }

  function formatSignedNumber(value: number | null | undefined): string {
    if (value == null) return '—'
    const prefix = value >= 0 ? '+' : ''
    return `${prefix}${value.toFixed(2)}`
  }

  function readRowNumber(
    row: Record<string, unknown> | null | undefined,
    camelKey: string,
    snakeKey: string
  ): number | null {
    if (!row) return null
    const value = row[camelKey] ?? row[snakeKey]
    if (typeof value === 'number' && Number.isFinite(value)) return value
    if (value == null || value === '') return null
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  function formatCount(value: number | null | undefined): string {
    if (value == null) return '—'
    return String(value)
  }

  function stepLabel(step: PricingFlowStep, index: number): string {
    if (step.kind === 'note') {
      return t('pricingFlow.stepNoteIndex', { index: index + 1 })
    }
    return t(step.label)
  }

  async function copyPricingRule() {
    if (!pricingRule.value) return
    try {
      await navigator.clipboard.writeText(pricingRule.value)
      ElMessage.success(t('pricingFlow.copySuccess'))
    } catch {
      ElMessage.error(t('pricingFlow.copyFailed'))
    }
  }
</script>

<style scoped>
  .pricing-flow-timeline {
    list-style: none;
    padding: 0;
    margin: 0;
  }

  .pricing-blocked-indicator {
    display: inline-flex;
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
</style>
