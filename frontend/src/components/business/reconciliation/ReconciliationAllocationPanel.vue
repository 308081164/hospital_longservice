<template>
  <div
    v-if="allocation || loading"
    class="allocation-panel mb-4 rounded-lg border p-4"
    :class="
      allocation?.balanced ? 'border-green-200 bg-green-50/50' : 'border-blue-200 bg-blue-50/50'
    "
  >
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <div class="flex items-center gap-2">
        <span class="text-sm font-semibold text-gray-800">
          {{ t('reconciliation.allocation.title') }}
        </span>
        <ElTag v-if="allocation" :type="allocation.balanced ? 'success' : 'warning'" size="small">
          {{
            allocation.balanced
              ? t('reconciliation.allocation.balanced')
              : t('reconciliation.allocation.unbalanced')
          }}
        </ElTag>
      </div>
      <div class="flex flex-wrap gap-2">
        <ElButton v-if="canOperate" size="small" :loading="running" @click="emit('run-allocation')">
          {{ t('reconciliation.allocation.run') }}
        </ElButton>
        <ElButton
          v-if="canExport"
          size="small"
          type="success"
          :loading="exporting"
          @click="emit('export-orchestrated')"
        >
          {{ t('reconciliation.allocation.exportOrchestrated') }}
        </ElButton>
      </div>
    </div>

    <div v-if="allocation?.balanceMessage" class="mb-3 text-xs text-gray-700">
      {{ allocation.balanceMessage }}
    </div>

    <div v-if="allocation" class="grid grid-cols-2 gap-3 text-sm md:grid-cols-3 lg:grid-cols-5">
      <div>
        <span class="text-gray-500">{{ t('reconciliation.allocation.originalTotal') }}：</span>
        <span class="font-medium">{{ formatNum(allocation.originalGrandTotal) }}</span>
      </div>
      <div>
        <span class="text-gray-500">{{ t('reconciliation.allocation.adjustmentTotal') }}：</span>
        <span class="font-medium">{{ formatNum(allocation.adjustmentTotal) }}</span>
      </div>
      <div>
        <span class="text-gray-500">{{ t('reconciliation.allocation.externalTotal') }}：</span>
        <span class="font-medium">{{ formatNum(allocation.externalInstrumentTotal) }}</span>
      </div>
      <div>
        <span class="text-gray-500">{{ t('reconciliation.allocation.logisticsTotal') }}：</span>
        <span class="font-medium">{{ formatNum(allocation.logisticsTotal) }}</span>
      </div>
      <div>
        <span class="text-gray-500">{{ t('reconciliation.allocation.reconciledTotal') }}：</span>
        <span class="font-semibold text-primary">{{
          formatNum(allocation.reconciledGrandTotal)
        }}</span>
      </div>
    </div>

    <ElTable
      v-if="priceSummaryRows.length"
      :data="priceSummaryRows"
      size="small"
      border
      stripe
      class="mt-3"
    >
      <ElTableColumn
        prop="category"
        :label="t('reconciliation.allocation.category')"
        min-width="140"
      />
      <ElTableColumn
        prop="amount"
        :label="t('reconciliation.allocation.amount')"
        width="120"
        align="right"
      >
        <template #default="{ row }">{{ formatNum(row.amount) }}</template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import type { AllocationResult } from '@/api/billing-config/allocationApi'

  const props = defineProps<{
    allocation?: AllocationResult | null
    loading?: boolean
    running?: boolean
    exporting?: boolean
    canOperate?: boolean
    canExport?: boolean
  }>()

  const emit = defineEmits<{
    'run-allocation': []
    'export-orchestrated': []
  }>()

  const { t } = useI18n()

  const priceSummaryRows = computed(() => {
    const summary = props.allocation?.priceSummaryByCategory
    if (!summary) return []
    return Object.entries(summary).map(([category, amount]) => ({ category, amount }))
  })

  function formatNum(val?: number | null) {
    if (val == null || Number.isNaN(val)) return '—'
    return val.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }
</script>
