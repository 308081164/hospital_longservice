<template>
  <div v-if="hasAnyToggle" class="pricing-rule-panel rounded-lg border border-gray-200 bg-white p-3">
    <div class="mb-2 flex items-center justify-between">
      <div class="text-sm font-medium text-gray-700">{{ t('reconciliation.pricingRules.title') }}</div>
      <ElButton
        v-if="dirty"
        type="primary"
        size="small"
        :loading="applying"
        :disabled="!canEdit"
        @click="applyChanges"
      >
        {{ t('reconciliation.pricingRules.applyAndReprice') }}
      </ElButton>
    </div>

    <p class="mb-3 text-xs text-gray-500">{{ t('reconciliation.pricingRules.categoryHint') }}</p>

    <div class="space-y-3">
      <div
        v-for="toggle in toggles"
        :key="toggle.category"
        class="flex items-center justify-between gap-3 rounded border border-gray-200 p-3"
      >
        <div class="min-w-0">
          <div class="text-sm font-medium text-gray-800">{{ toggle.label }}</div>
          <div v-if="toggle.available" class="mt-0.5 text-xs text-gray-500">
            {{ t('reconciliation.pricingRules.ruleCount', { count: toggle.rule_count }) }}
          </div>
          <div v-else class="mt-0.5 text-xs text-gray-400">
            {{ t('reconciliation.pricingRules.notAvailable') }}
          </div>
        </div>
        <ElSwitch
          :model-value="toggle.enabled"
          :disabled="!canEdit || applying || !toggle.available"
          @change="(val: boolean) => onToggle(toggle.category, val)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
  import { ElMessage } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import {
    fetchPricingRules,
    repriceReconciliation,
    updatePricingRuleOverrides,
    type PricingCategoryToggle
  } from '@/api/hospital/reconciliationsApi'

  const props = defineProps<{
    jobId: number | null | undefined
    canEdit: boolean
  }>()

  const emit = defineEmits<{
    repriced: [rows: Record<string, unknown>[]]
  }>()

  const { t } = useI18n()
  const toggles = ref<PricingCategoryToggle[]>([])
  const disabledCategories = ref<string[]>([])
  const savedDisabledCategories = ref<string[]>([])
  const applying = ref(false)

  const hasAnyToggle = computed(() => toggles.value.some((toggle) => toggle.available))
  const dirty = computed(() => {
    const current = [...disabledCategories.value].sort().join('|')
    const saved = [...savedDisabledCategories.value].sort().join('|')
    return current !== saved
  })

  watch(
    () => props.jobId,
    async (jobId) => {
      toggles.value = []
      disabledCategories.value = []
      savedDisabledCategories.value = []
      if (!jobId) return
      try {
        const info = await fetchPricingRules(jobId)
        toggles.value = info.toggles ?? []
        disabledCategories.value = [...(info.disabled_categories ?? [])]
        savedDisabledCategories.value = [...disabledCategories.value]
      } catch {
        toggles.value = []
      }
    },
    { immediate: true }
  )

  function onToggle(category: string, enabled: boolean) {
    toggles.value = toggles.value.map((toggle) =>
      toggle.category === category ? { ...toggle, enabled } : toggle
    )
    if (enabled) {
      disabledCategories.value = disabledCategories.value.filter((item) => item !== category)
    } else if (!disabledCategories.value.includes(category)) {
      disabledCategories.value = [...disabledCategories.value, category]
    }
  }

  async function applyChanges() {
    if (!props.jobId || !dirty.value) return
    applying.value = true
    try {
      const info = await updatePricingRuleOverrides(props.jobId, disabledCategories.value)
      toggles.value = info.toggles ?? toggles.value
      const result = await repriceReconciliation(props.jobId, disabledCategories.value)
      savedDisabledCategories.value = [...disabledCategories.value]
      emit('repriced', result.rows)
      ElMessage.success(t('reconciliation.pricingRules.applySuccess'))
    } catch (error) {
      ElMessage.error(
        error instanceof Error ? error.message : t('reconciliation.pricingRules.applyFailed')
      )
    } finally {
      applying.value = false
    }
  }
</script>
