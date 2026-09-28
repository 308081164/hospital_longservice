<template>
  <ElDrawer
    :model-value="visible"
    :title="t('reconciliation.pricingRules.title')"
    size="420px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <p class="mb-4 text-xs text-gray-500">
      仅两个总开关：内勤计价层、内勤折扣层。修改后点「应用并重算」预览结果（不自动保存，需点「保存修改」落库）。
    </p>
    <ReconciliationPricingRulePanel
      :job-id="jobId"
      :can-edit="canEdit"
      @repriced="(rows) => emit('repriced', rows)"
    />
  </ElDrawer>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'
  import ReconciliationPricingRulePanel from '@/components/business/reconciliation/ReconciliationPricingRulePanel.vue'

  defineOptions({ name: 'ReconciliationClerkRulesDrawer' })

  withDefaults(
    defineProps<{
      visible: boolean
      jobId: number | null
      canEdit?: boolean
    }>(),
    { canEdit: true }
  )

  const emit = defineEmits<{
    'update:visible': [value: boolean]
    repriced: [rows: Record<string, unknown>[]]
  }>()

  const { t } = useI18n()
</script>
