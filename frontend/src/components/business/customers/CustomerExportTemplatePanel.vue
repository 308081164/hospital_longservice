<template>
  <div class="customer-export-template-panel">
    <ElDivider>{{ t('exportTemplates.customerBinding.title') }}</ElDivider>
    <p class="customer-export-template-panel__desc">
      {{ t('exportTemplates.customerBinding.desc') }}
    </p>

    <div v-if="!customerId" class="text-sm text-gray-400">
      {{ t('exportTemplates.customerBinding.saveFirst') }}
    </div>

    <template v-else>
      <div v-loading="loading" class="customer-export-template-panel__grid">
        <div
          v-for="slot in bindingSlots"
          :key="slot.type"
          class="customer-export-template-panel__row"
        >
          <label>{{ t(slot.labelKey) }}</label>
          <ElSelect
            v-model="bindings[slot.type]"
            class="w-full"
            clearable
            :placeholder="t('exportTemplates.customerBinding.useGlobalDefault')"
            @change="(val: number | undefined) => handleBindingChange(slot.type, val)"
          >
            <ElOptionGroup :label="t('exportTemplates.customerBinding.customerOverrides')">
              <ElOption
                v-for="tpl in customerTemplates(slot.type)"
                :key="tpl.id"
                :label="tpl.name"
                :value="tpl.id"
              />
            </ElOptionGroup>
            <ElOptionGroup :label="t('exportTemplates.customerBinding.globalTemplates')">
              <ElOption
                v-for="tpl in globalTemplates(slot.type)"
                :key="tpl.id"
                :label="`${tpl.name} (${tpl.strategyKey})`"
                :value="tpl.id"
              />
            </ElOptionGroup>
          </ElSelect>
          <span v-if="resolvedLabel(slot.type)" class="text-xs text-gray-500">
            {{ resolvedLabel(slot.type) }}
          </span>
        </div>
      </div>

      <ElDivider>{{ t('exportTemplates.customerBinding.strategySummaryTitle') }}</ElDivider>
      <ElDescriptions :column="1" border size="small" class="customer-export-template-panel__summary">
        <ElDescriptionsItem :label="t('exportTemplates.customerBinding.billStrategyLabel')">
          {{ billStrategyLabel }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('exportTemplates.customerBinding.settlementStrategyLabel')">
          {{ settlementStrategyLabel }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('exportTemplates.customerBinding.settlementDiscountSummary')">
          {{ settlementDiscountSummary }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('exportTemplates.customerBinding.exportStageDiscountSummary')">
          {{ exportStageDiscountSummary }}
        </ElDescriptionsItem>
      </ElDescriptions>
      <div class="customer-export-template-panel__links">
        <RouterLink to="/billing-config/export-templates" class="text-primary text-xs">
          {{ t('exportTemplates.customerBinding.openTemplateAdmin') }}
        </RouterLink>
        <span class="text-gray-300">|</span>
        <RouterLink to="/hospital/reconciliation" class="text-primary text-xs">
          {{ t('exportTemplates.customerBinding.openExportWizard') }}
        </RouterLink>
      </div>

      <ElDivider>{{ t('exportTemplates.customerBinding.nameMappingTitle') }}</ElDivider>
      <p class="customer-export-template-panel__desc">
        {{ t('exportTemplates.customerBinding.nameMappingDesc') }}
      </p>
      <div class="customer-export-template-panel__mapping-list">
        <div v-for="(entry, idx) in nameMappingEntries" :key="idx" class="flex gap-2 mb-2">
          <ElInput
            v-model="entry.from"
            :placeholder="t('exportTemplates.customerBinding.fromPlaceholder')"
            class="flex-1"
          />
          <span class="self-center text-gray-400">→</span>
          <ElInput
            v-model="entry.to"
            :placeholder="t('exportTemplates.customerBinding.toPlaceholder')"
            class="flex-1"
          />
          <ElButton type="danger" link @click="nameMappingEntries.splice(idx, 1)">
            {{ t('common.delete') }}
          </ElButton>
        </div>
        <ElButton size="small" @click="addNameMappingEntry">
          {{ t('exportTemplates.customerBinding.addMapping') }}
        </ElButton>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed, reactive, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import {
    buildSheetConfigJson,
    createExportTemplate,
    deleteExportTemplate,
    listExportTemplates,
    updateExportTemplate,
    type ExportTemplateRecord
  } from '@/api/hospital/exportTemplatesApi'
  import { listCustomerBillingPolicies } from '@/api/master-data/customersApi'
  import type { BillingPolicyPanelState } from '@/utils/customerBillingPolicy'
  import { parseDiscountApplyStages } from '@/utils/customerBillingPolicy'

  const props = defineProps<{
    customerId?: number | null
    customerCode?: string | null
    exportNameMapping?: string | null
    billingPolicyState?: BillingPolicyPanelState | null
  }>()

  const emit = defineEmits<{
    'update:exportNameMapping': [value: string | undefined]
  }>()

  const { t } = useI18n()

  const loading = ref(false)
  const allTemplates = ref<ExportTemplateRecord[]>([])
  const exportOnlyPolicyNames = ref<string[]>([])
  const settlementPolicySummaries = ref<string[]>([])
  const bindings = reactive<Record<string, number | undefined>>({
    bill: undefined,
    settlement: undefined,
    dept_summary: undefined
  })

  const nameMappingEntries = ref<Array<{ from: string; to: string }>>([])

  const bindingSlots = [
    { type: 'bill', labelKey: 'exportTemplates.types.bill' },
    { type: 'settlement', labelKey: 'exportTemplates.types.settlement' },
    { type: 'dept_summary', labelKey: 'exportTemplates.types.deptSummary' }
  ]

  function globalTemplates(type: string) {
    return allTemplates.value.filter(
      (tpl) => !tpl.customerId && tpl.templateType === type && tpl.isActive !== false
    )
  }

  function customerTemplates(type: string) {
    if (!props.customerId) return []
    return allTemplates.value.filter(
      (tpl) =>
        tpl.customerId === props.customerId && tpl.templateType === type && tpl.isActive !== false
    )
  }

  function resolvedLabel(type: string) {
    const id = bindings[type]
    if (!id) return t('exportTemplates.customerBinding.resolvedGlobal')
    const tpl = allTemplates.value.find((row) => row.id === id)
    if (!tpl) return ''
    return t('exportTemplates.customerBinding.resolvedTemplate', {
      name: tpl.name,
      strategy: tpl.strategyKey ?? ''
    })
  }

  function strategyForType(type: string): string {
    const id = bindings[type]
    if (id) {
      const tpl = allTemplates.value.find((row) => row.id === id)
      if (tpl?.strategyKey) return tpl.strategyKey
    }
    if (!props.customerCode) return 'standard_bill'
    const global = globalTemplates(type).find(
      (tpl) => tpl.sheetConfig?.includes(`"customerCode":"${props.customerCode}"`)
    )
    if (global?.strategyKey) return global.strategyKey
    return type === 'settlement' ? 'standard_settlement' : 'standard_bill'
  }

  const billStrategyLabel = computed(() => {
    const key = strategyForType('bill')
    return t(`exportTemplates.strategy.${strategyKeyToLabel(key)}`, key)
  })

  const settlementStrategyLabel = computed(() => {
    const key = strategyForType('settlement')
    return t(`exportTemplates.strategy.${strategyKeyToLabel(key)}`, key)
  })

  function strategyKeyToLabel(key: string): string {
    const map: Record<string, string> = {
      standard_bill: 'standardBill',
      standard_settlement: 'standardSettlement',
      sheng_er_bill: 'shengErBill',
      daowai_bill: 'daowaiBill',
      guoyao_bill: 'guoyaoBill'
    }
    return map[key] ?? 'standardBill'
  }

  const settlementDiscountSummary = computed(() => {
    const parts: string[] = [...settlementPolicySummaries.value]
    const discounts = props.billingPolicyState?.discounts ?? []
    for (const disc of discounts) {
      const stages = parseDiscountApplyStages(disc)
      if (!stages.includes('settlement_only')) continue
      if (disc.discountRate != null) {
        parts.push(`${disc.name || '折扣'} ${(disc.discountRate * 100).toFixed(0)}%`)
      }
    }
    return parts.length ? parts.join('；') : t('exportTemplates.customerBinding.noneConfigured')
  })

  const exportStageDiscountSummary = computed(() => {
    const fromPolicies = exportOnlyPolicyNames.value
    if (fromPolicies.length) return fromPolicies.join('；')
    const policies = props.billingPolicyState?.discounts ?? []
    const fromDiscounts = policies
      .filter((d) => parseDiscountApplyStages(d).includes('export_only'))
      .map((d) => d.name || t('exportTemplates.customerBinding.exportStageDefaultName'))
    if (fromDiscounts.length) return fromDiscounts.join('；')
    return t('exportTemplates.customerBinding.noneConfigured')
  })

  function parsePolicyParams(raw?: string | null): Record<string, unknown> {
    if (!raw?.trim()) return {}
    try {
      return JSON.parse(raw) as Record<string, unknown>
    } catch {
      return {}
    }
  }

  async function loadExportPolicies() {
    if (!props.customerId) {
      exportOnlyPolicyNames.value = []
      settlementPolicySummaries.value = []
      return
    }
    try {
      const policies = await listCustomerBillingPolicies(props.customerId)
      exportOnlyPolicyNames.value = policies
        .filter((p) => p.policyType === 'DISCOUNT' && p.isActive !== false)
        .filter((p) => {
          const params = parsePolicyParams(p.params)
          const stage = params.applyStage as string | undefined
          return stage === 'export_only'
        })
        .map((p) => p.name ?? t('exportTemplates.customerBinding.exportStageDefaultName'))
      settlementPolicySummaries.value = policies
        .filter((p) => p.policyType === 'DISCOUNT' && p.isActive !== false)
        .filter((p) => {
          const params = parsePolicyParams(p.params)
          const stage = params.applyStage as string | undefined
          return stage === 'settlement_only'
        })
        .map((p) => {
          const params = parsePolicyParams(p.params)
          const rate = params.rate as number | undefined
          const label = p.name ?? '结款折扣'
          if (rate != null && rate > 0 && rate < 1) {
            return `${label} ${(rate * 100).toFixed(0)}%`
          }
          return label
        })
    } catch {
      exportOnlyPolicyNames.value = []
      settlementPolicySummaries.value = []
    }
  }

  function parseNameMapping(raw?: string | null) {
    if (!raw?.trim()) {
      nameMappingEntries.value = []
      return
    }
    try {
      const obj = JSON.parse(raw) as Record<string, string>
      nameMappingEntries.value = Object.entries(obj).map(([from, to]) => ({ from, to }))
    } catch {
      nameMappingEntries.value = []
    }
  }

  function serializeNameMapping(): string | undefined {
    const entries = nameMappingEntries.value.filter((e) => e.from.trim() && e.to.trim())
    if (entries.length === 0) return undefined
    const obj: Record<string, string> = {}
    entries.forEach((e) => {
      obj[e.from.trim()] = e.to.trim()
    })
    return JSON.stringify(obj)
  }

  function addNameMappingEntry() {
    nameMappingEntries.value.push({ from: '', to: '' })
  }

  function syncBindingsFromTemplates() {
    bindingSlots.forEach((slot) => {
      const customer = customerTemplates(slot.type)
      bindings[slot.type] = customer.length > 0 ? customer[0].id : undefined
    })
  }

  async function loadTemplates() {
    if (!props.customerId) return
    loading.value = true
    try {
      allTemplates.value = await listExportTemplates()
      syncBindingsFromTemplates()
      await loadExportPolicies()
    } catch {
      ElMessage.error(t('exportTemplates.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  async function handleBindingChange(type: string, templateId: number | undefined) {
    if (!props.customerId) return
    const existing = customerTemplates(type)[0]
    if (templateId == null) {
      if (existing) {
        await deleteExportTemplate(existing.id)
        bindings[type] = undefined
      }
      return
    }
    const source = allTemplates.value.find((tpl) => tpl.id === templateId)
    if (!source) return
    if (existing && existing.id === templateId) return
    const payload = {
      customerId: props.customerId,
      templateType: type,
      name: `${source.name}（客户覆盖）`,
      storagePath: source.storagePath ?? '',
      columnMapping: source.columnMapping ?? '{}',
      sheetConfig:
        source.sheetConfig ?? buildSheetConfigJson(source.strategyKey ?? 'standard_bill'),
      isActive: true
    }
    if (existing) {
      await updateExportTemplate(existing.id, payload)
      bindings[type] = existing.id
    } else {
      const created = await createExportTemplate(payload)
      bindings[type] = created.id
    }
    await loadTemplates()
  }

  async function persistBindings() {
    emit('update:exportNameMapping', serializeNameMapping())
  }

  watch(
    () => props.customerId,
    (id) => {
      if (id) void loadTemplates()
    },
    { immediate: true }
  )

  watch(
    () => props.exportNameMapping,
    (val) => parseNameMapping(val),
    { immediate: true }
  )

  watch(nameMappingEntries, () => persistBindings(), { deep: true })

  defineExpose({
    getExportNameMapping: serializeNameMapping,
    reload: loadTemplates
  })
</script>

<style scoped>
  .customer-export-template-panel__desc {
    margin: 0 0 12px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.5;
  }

  .customer-export-template-panel__grid {
    display: grid;
    gap: 12px;
  }

  .customer-export-template-panel__row {
    display: grid;
    gap: 6px;
  }

  .customer-export-template-panel__row label {
    font-size: 13px;
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  .customer-export-template-panel__mapping-list {
    margin-top: 8px;
  }

  .customer-export-template-panel__summary {
    margin-bottom: 8px;
  }

  .customer-export-template-panel__links {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
</style>
