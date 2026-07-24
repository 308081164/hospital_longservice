<template>
  <ElDialog
    v-model="visible"
    :title="t('reconciliation.exportWizard.title')"
    width="640px"
    destroy-on-close
    @closed="handleClosed"
  >
    <ElSteps :active="step" finish-status="success" align-center class="mb-6">
      <ElStep :title="t('reconciliation.exportWizard.stepType')" />
      <ElStep :title="t('reconciliation.exportWizard.stepPreview')" />
      <ElStep :title="t('reconciliation.exportWizard.stepDownload')" />
    </ElSteps>

    <!-- Step 1 -->
    <div v-if="step === 0" class="export-wizard-step">
      <ElForm label-width="100px">
        <ElFormItem :label="t('reconciliation.exportWizard.exportType')">
          <ElRadioGroup v-model="exportType">
            <ElRadio value="bill">{{ t('reconciliation.history.export.bill') }}</ElRadio>
            <ElRadio value="settlement">{{
              t('reconciliation.history.export.settlement')
            }}</ElRadio>
            <ElRadio value="dept_summary">{{
              t('reconciliation.history.export.departmentSummary')
            }}</ElRadio>
            <ElRadio value="price_summary">{{
              t('reconciliation.history.export.priceSummary')
            }}</ElRadio>
            <ElRadio value="instrument_audit">{{
              t('reconciliation.history.export.instrumentAudit')
            }}</ElRadio>
            <ElRadio value="logistics_allocation">{{
              t('reconciliation.history.export.logisticsAllocation')
            }}</ElRadio>
            <ElRadio value="grand_summary">{{
              t('reconciliation.history.export.grandSummary')
            }}</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem
          v-if="templateOptions.length"
          :label="t('reconciliation.exportWizard.template')"
        >
          <ElSelect
            v-model="templateId"
            clearable
            class="w-full"
            :placeholder="t('reconciliation.exportWizard.templateAuto')"
          >
            <ElOption
              v-for="tpl in templateOptions"
              :key="tpl.id"
              :label="`${tpl.name}${tpl.customerId ? '' : ' (全局)'}`"
              :value="tpl.id"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
    </div>

    <!-- Step 2 -->
    <div v-else-if="step === 1" v-loading="previewLoading" class="export-wizard-step">
      <template v-if="preview">
        <ElDescriptions :column="1" border size="small">
          <ElDescriptionsItem :label="t('reconciliation.exportWizard.hospital')">
            {{ preview.hospitalName }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('reconciliation.exportWizard.templateName')">
            {{ preview.templateName }}
            <ElTag v-if="preview.customerOverride" size="small" type="warning" class="ml-2">
              {{ t('reconciliation.exportWizard.customerOverride') }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('reconciliation.exportWizard.strategy')">
            {{ preview.strategyKey }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('reconciliation.exportWizard.rowCount')">
            {{ preview.rowCount }}
          </ElDescriptionsItem>
        </ElDescriptions>
        <ElAlert
          type="info"
          :closable="false"
          class="mt-4"
          :title="t('reconciliation.exportWizard.previewHint')"
        />
      </template>
      <div v-else class="py-8 text-center text-sm text-gray-400">
        {{ t('reconciliation.exportWizard.previewEmpty') }}
      </div>
    </div>

    <!-- Step 3 -->
    <div v-else class="export-wizard-step">
      <ElAlert
        v-if="validation"
        :type="validation.ready ? 'success' : 'warning'"
        :closable="false"
        show-icon
        class="mb-4"
        :title="validation.message"
      />
      <ElDescriptions v-if="validation" :column="2" border size="small">
        <ElDescriptionsItem :label="t('reconciliation.history.stats.totalRows')">
          {{ validation.totalRows }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('reconciliation.history.stats.warningRows')">
          {{ validation.warningRows }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('reconciliation.history.stats.correctedRows')">
          {{ validation.correctedRows }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('reconciliation.history.stats.difference')">
          {{ formatSigned(validation.totalDifference) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('reconciliation.settlementSummary.logisticsFee')">
          {{ formatNumber(validation.logisticsFee) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('reconciliation.exportWizard.settlementAdjustment')">
          {{ formatNumber(validation.settlementAdjustment) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem
          v-if="monthlyReconciliationHint"
          :label="t('reconciliation.exportWizard.settlementReconcile')"
        >
          <ElTag :type="monthlyReconcileOk ? 'success' : 'warning'" size="small">
            {{ monthlyReconciliationHint }}
          </ElTag>
        </ElDescriptionsItem>
      </ElDescriptions>
    </div>

    <template #footer>
      <ElButton @click="visible = false">{{ t('common.cancel') }}</ElButton>
      <ElButton v-if="step > 0" @click="step -= 1">{{
        t('reconciliation.exportWizard.back')
      }}</ElButton>
      <ElButton v-if="step < 2" type="primary" :loading="previewLoading" @click="goNext">
        {{ t('reconciliation.exportWizard.next') }}
      </ElButton>
      <ElButton v-else type="success" :loading="downloading" @click="confirmDownload">
        {{ t('reconciliation.exportWizard.download') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import {
    exportReconciliationV2,
    listExportTemplates,
    previewReconciliationExport,
    triggerBlobDownload,
    validateReconciliationExport,
    type ExportPreviewResult,
    type ExportTemplateRecord,
    type ExportValidationResult
  } from '@/api/hospital/exportTemplatesApi'

  const props = defineProps<{
    jobId?: number | null
    hospitalName?: string
    initialExportType?:
      | 'bill'
      | 'settlement'
      | 'dept_summary'
      | 'price_summary'
      | 'instrument_audit'
      | 'logistics_allocation'
      | 'grand_summary'
    monthlyBreakdown?: {
      rawSterilizeTotal?: number
      adjustedTotal?: number
      minCharge?: number
      maxCap?: number
    } | null
    logisticsFee?: number | null
    settlementAdjustment?: number | null
  }>()

  const emit = defineEmits<{
    exported: [payload: { exportType: string; fileName: string }]
  }>()

  const { t } = useI18n()

  const visible = defineModel<boolean>({ default: false })

  const step = ref(0)
  const exportType = ref<
    | 'bill'
    | 'settlement'
    | 'dept_summary'
    | 'price_summary'
    | 'instrument_audit'
    | 'logistics_allocation'
    | 'grand_summary'
  >('bill')
  const templateId = ref<number | undefined>()
  const templates = ref<ExportTemplateRecord[]>([])
  const preview = ref<ExportPreviewResult | null>(null)
  const validation = ref<ExportValidationResult | null>(null)
  const previewLoading = ref(false)
  const downloading = ref(false)

  const templateOptions = computed(() =>
    templates.value.filter((tpl) => tpl.templateType === exportType.value && tpl.isActive !== false)
  )

  const monthlyReconcileOk = computed(() => {
    const mb = props.monthlyBreakdown
    if (!mb || mb.adjustedTotal == null) return true
    const logistics = props.logisticsFee ?? validation.value?.logisticsFee ?? 0
    const adjustment = props.settlementAdjustment ?? validation.value?.settlementAdjustment ?? 0
    const raw = mb.rawSterilizeTotal ?? 0
    const expected = raw + logistics + adjustment
    const adjusted = mb.adjustedTotal ?? 0
    if (mb.minCharge != null || mb.maxCap != null) return true
    return Math.abs(expected - adjusted) <= 0.02
  })

  const monthlyReconciliationHint = computed(() => {
    const mb = props.monthlyBreakdown
    if (!mb || mb.adjustedTotal == null) return ''
    const logistics = props.logisticsFee ?? validation.value?.logisticsFee ?? 0
    const adjustment = props.settlementAdjustment ?? validation.value?.settlementAdjustment ?? 0
    const raw = mb.rawSterilizeTotal ?? 0
    return t('reconciliation.exportWizard.settlementFormula', {
      raw: formatNumber(raw),
      logistics: formatNumber(logistics),
      adjustment: formatSigned(adjustment),
      total: formatNumber(mb.adjustedTotal)
    })
  })

  function formatNumber(val?: number | null) {
    if (val == null || Number.isNaN(val)) return '—'
    return val.toFixed(2)
  }

  function formatSigned(val?: number | null) {
    if (val == null || Number.isNaN(val)) return '—'
    const prefix = val >= 0 ? '+' : ''
    return `${prefix}${val.toFixed(2)}`
  }

  function buildFileName(prefix: string) {
    const hospital = (props.hospitalName ?? preview.value?.hospitalName ?? 'hospital').trim()
    const safe = hospital.replace(/[^\w\u4e00-\u9fa5-]+/g, '_')
    return `${prefix}-${safe}-${Date.now()}.xlsx`
  }

  async function loadTemplates() {
    try {
      templates.value = await listExportTemplates()
    } catch {
      templates.value = []
    }
  }

  async function loadPreview() {
    if (!props.jobId) return
    previewLoading.value = true
    try {
      preview.value = await previewReconciliationExport(
        props.jobId,
        exportType.value,
        templateId.value
      )
    } catch (e: unknown) {
      preview.value = null
      ElMessage.error(
        e instanceof Error ? e.message : t('reconciliation.exportWizard.previewFailed')
      )
      throw e
    } finally {
      previewLoading.value = false
    }
  }

  async function loadValidation() {
    if (!props.jobId) return
    validation.value = await validateReconciliationExport(props.jobId)
  }

  async function goNext() {
    if (step.value === 0) {
      try {
        await loadPreview()
        step.value = 1
      } catch {
        // stay
      }
      return
    }
    if (step.value === 1) {
      try {
        await loadValidation()
        step.value = 2
      } catch (e: unknown) {
        ElMessage.error(
          e instanceof Error ? e.message : t('reconciliation.exportWizard.validationFailed')
        )
      }
    }
  }

  async function confirmDownload() {
    if (!props.jobId) return
    if (validation.value && !validation.value.ready) {
      try {
        await ElMessageBox.confirm(
          t('reconciliation.exportWizard.forceExportMessage', {
            count: validation.value.warningRows
          }),
          t('reconciliation.exportWizard.forceExportTitle'),
          {
            type: 'warning',
            confirmButtonText: t('reconciliation.exportWizard.forceExportConfirm')
          }
        )
      } catch {
        return
      }
    }
    downloading.value = true
    try {
      const blob = await exportReconciliationV2(props.jobId, {
        exportType: exportType.value,
        templateId: templateId.value,
        useStrategyEngine: true
      })
      const prefixMap: Record<string, string> = {
        bill: '账单',
        settlement: '结款函',
        dept_summary: '分科室汇总',
        price_summary: '价格汇总',
        instrument_audit: '器械把数表',
        logistics_allocation: '物流分摊',
        grand_summary: '总汇总'
      }
      const fileName = buildFileName(prefixMap[exportType.value])
      triggerBlobDownload(blob, fileName)
      emit('exported', { exportType: exportType.value, fileName })
      ElMessage.success(t('reconciliation.exportWizard.downloadSuccess'))
      visible.value = false
    } catch (e: unknown) {
      ElMessage.error(
        e instanceof Error ? e.message : t('reconciliation.exportWizard.downloadFailed')
      )
    } finally {
      downloading.value = false
    }
  }

  function handleClosed() {
    step.value = 0
    exportType.value = 'bill'
    templateId.value = undefined
    preview.value = null
    validation.value = null
  }

  watch(visible, (open) => {
    if (open) {
      exportType.value = props.initialExportType ?? 'bill'
      templateId.value = undefined
      step.value = 0
      preview.value = null
      validation.value = null
      void loadTemplates()
    }
  })

  watch(exportType, () => {
    templateId.value = undefined
    preview.value = null
  })
</script>

<style scoped>
  .export-wizard-step {
    min-height: 180px;
  }
</style>
