<template>
  <div
    class="export-profile-banner rounded-lg border-2 px-4 py-3"
    :class="bannerClass"
  >
    <div class="flex flex-wrap items-center gap-2 mb-2">
      <ElTag :type="exportTypeTag" effect="dark" size="large" class="font-semibold">
        {{ exportTypeLabel }}
      </ElTag>
      <ElTag :type="layoutTagType" effect="plain" size="large">
        {{ layoutLabel }}
      </ElTag>
      <ElTag v-if="profile.strategyKey" type="info" size="small">
        {{ t('reconciliation.exportWizard.strategy') }}: {{ profile.strategyKey }}
      </ElTag>
    </div>
    <div class="text-sm leading-relaxed space-y-1">
      <div v-if="profile.exportProfileLabel" class="font-medium">
        {{ profile.exportProfileLabel }}
      </div>
      <div class="text-gray-600">
        {{ d8Label }}
      </div>
      <div class="text-gray-600">
        {{ expectedLabel }}
      </div>
      <div v-if="profile.distinctSheetCount != null" class="text-gray-600">
        {{ t('reconciliation.exportWizard.profile.sheetCount') }}：
        {{ profile.distinctSheetCount }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import type { ExportPreviewResult, ExportValidationResult } from '@/api/hospital/exportTemplatesApi'

  const props = defineProps<{
    profile: ExportPreviewResult | ExportValidationResult
    layoutMismatch?: boolean
  }>()

  const { t } = useI18n()

  const isSpecial = computed(() => props.profile.billingEnabled === true)

  const exportTypeLabel = computed(() =>
    isSpecial.value
      ? t('reconciliation.exportWizard.profile.specialExport')
      : t('reconciliation.exportWizard.profile.standardExport')
  )

  const exportTypeTag = computed(() => (isSpecial.value ? 'warning' : 'info'))

  const layoutLabel = computed(() => {
    const layout = props.profile.billLayout ?? 'auto'
    if (layout === 'dept_split') return t('reconciliation.exportWizard.profile.layoutDeptSplit')
    if (layout === 'combined') return t('reconciliation.exportWizard.profile.layoutCombined')
    return t('reconciliation.exportWizard.profile.layoutAuto')
  })

  const layoutTagType = computed(() => {
    const layout = props.profile.billLayout ?? 'auto'
    if (layout === 'dept_split') return 'success'
    if (layout === 'combined') return 'primary'
    return 'info'
  })

  const d8Label = computed(() => {
    const src = props.profile.d8DisplaySource ?? 'auto'
    if (src === 'hospitalName') return t('reconciliation.exportWizard.profile.d8HospitalName')
    if (src === 'ruleName') return t('reconciliation.exportWizard.profile.d8RuleName')
    return t('reconciliation.exportWizard.profile.d8Auto')
  })

  const expectedLabel = computed(() => {
    const mode = props.profile.expectedSheetMode
    if (mode === 'multi_dept') return t('reconciliation.exportWizard.profile.expectedMultiDept')
    if (mode === 'single_combined') return t('reconciliation.exportWizard.profile.expectedCombined')
    return t('reconciliation.exportWizard.profile.layoutAuto')
  })

  const bannerClass = computed(() => {
    if (props.layoutMismatch) {
      return 'border-red-400 bg-red-50'
    }
    if (isSpecial.value) {
      return 'border-amber-400 bg-amber-50'
    }
    return 'border-gray-200 bg-gray-50'
  })
</script>

<style scoped>
  .export-profile-banner {
    box-shadow: 0 1px 4px rgb(0 0 0 / 6%);
  }
</style>
