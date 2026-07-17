<template>
  <ElCollapse v-model="active" class="uat-helper-panel mb-4">
    <ElCollapseItem name="uat" :title="t('reconciliation.uat.title')">
      <ElAlert
        type="info"
        :closable="false"
        class="mb-3"
        :title="t('reconciliation.uat.subtitle')"
      />
      <ElRadioGroup v-model="phase" size="small" class="mb-3">
        <ElRadioButton value="phase3">{{ t('reconciliation.uat.phase3') }}</ElRadioButton>
        <ElRadioButton value="phase4">{{ t('reconciliation.uat.phase4') }}</ElRadioButton>
        <ElRadioButton value="phase6">{{ t('reconciliation.uat.phase6') }}</ElRadioButton>
        <ElRadioButton value="phase7">{{ t('reconciliation.uat.phase7') }}</ElRadioButton>
      </ElRadioGroup>

      <ul class="uat-checklist text-sm space-y-2 mb-4">
        <li v-for="key in checklistKeys" :key="key" class="flex items-start gap-2">
          <ElCheckbox v-model="checked[key]" />
          <span>{{ t(`reconciliation.uat.items.${phase}.${key}`) }}</span>
        </li>
      </ul>

      <div class="rounded border bg-gray-50 p-3 text-xs font-mono break-all">
        <div class="mb-1 text-gray-500">{{ t('reconciliation.uat.diffCommand') }}</div>
        <code>{{ diffCommand }}</code>
        <ElButton link type="primary" size="small" class="ml-2" @click="copyCommand">
          {{ t('reconciliation.uat.copyCommand') }}
        </ElButton>
      </div>

      <div class="mt-3 text-xs text-gray-500">
        {{ t('reconciliation.uat.toleranceHint') }}
      </div>
    </ElCollapseItem>
  </ElCollapse>
</template>

<script setup lang="ts">
  import { computed, reactive, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'

  const props = defineProps<{
    hospitalName?: string
    jobId?: number | null
  }>()

  const { t } = useI18n()

  const active = ref<string[]>([])
  const phase = ref<'phase3' | 'phase4' | 'phase6' | 'phase7'>('phase3')
  const checked = reactive<Record<string, boolean>>({})

  const CHECKLIST_BY_PHASE: Record<string, string[]> = {
    phase3: ['exportWizard', 'validation', 'billDiff', 'settlementReconcile'],
    phase4: ['exportDiscount', 'zeroPrice', 'splitRow', 'exportDiff'],
    phase6: ['urgentRows', 'deductionLine', 'settlementMat03'],
    phase7: ['allocation', 'orchestratedExport', 'deptSheets', 'grandTotal']
  }

  const checklistKeys = computed(() => CHECKLIST_BY_PHASE[phase.value] ?? [])

  const safeHospital = computed(() =>
    (props.hospitalName ?? 'hospital').replace(/[^\w\u4e00-\u9fa5-]+/g, '_')
  )

  const diffCommand = computed(() => {
    const golden = `golden/${safeHospital.value}-期望.xlsx`
    const actual = `exports/${safeHospital.value}-实际-${props.jobId ?? 'job'}.xlsx`
    return `python scripts/compare_export.py ${golden} ${actual} --tolerance 0.01`
  })

  async function copyCommand() {
    try {
      await navigator.clipboard.writeText(diffCommand.value)
      ElMessage.success(t('reconciliation.uat.copied'))
    } catch {
      ElMessage.error(t('reconciliation.uat.copyFailed'))
    }
  }
</script>

<style scoped>
  .uat-checklist {
    margin: 0;
    padding: 0;
    list-style: none;
  }
</style>
