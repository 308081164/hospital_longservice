<template>
  <div v-if="!jobId" class="pricing-rule-panel rounded-lg border border-gray-200 bg-gray-50 p-4 text-sm text-gray-500">
    请先完成「校对并保存」后再配置内勤规则开关。
  </div>
  <div
    v-else
    v-loading="loading"
    class="pricing-rule-panel rounded-lg border border-gray-200 bg-white p-3"
  >
    <div class="mb-2 flex items-center justify-between gap-2">
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

    <ElAlert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      class="mb-3"
      :title="loadError"
    />

    <p v-if="customerCode" class="mb-3 text-xs text-gray-400">
      医院编码：{{ customerCode }}
    </p>

    <div class="space-y-3">
      <div
        v-for="toggle in displayToggles"
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
          :disabled="!canEdit || applying"
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

  const CLERK_CATEGORIES = ['clerk_price', 'clerk_discount'] as const

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
  const customerCode = ref<string | undefined>()
  const loading = ref(false)
  const loadError = ref('')
  const applying = ref(false)

  const dirty = computed(() => {
    const current = [...disabledCategories.value].sort().join('|')
    const saved = [...savedDisabledCategories.value].sort().join('|')
    return current !== saved
  })

  function defaultToggle(category: (typeof CLERK_CATEGORIES)[number]): PricingCategoryToggle {
    const label =
      category === 'clerk_price'
        ? t('reconciliation.pricingRules.clerkPriceGroup')
        : t('reconciliation.pricingRules.clerkDiscountGroup')
    return {
      category,
      label,
      enabled: true,
      available: false,
      rule_count: 0
    }
  }

  function normalizeToggles(apiToggles: PricingCategoryToggle[]): PricingCategoryToggle[] {
    const byCategory = new Map(apiToggles.map((toggle) => [toggle.category, toggle]))
    return CLERK_CATEGORIES.map((category) => {
      const fromApi = byCategory.get(category)
      if (!fromApi) return defaultToggle(category)
      return {
        ...fromApi,
        label:
          fromApi.label ||
          (category === 'clerk_price'
            ? t('reconciliation.pricingRules.clerkPriceGroup')
            : t('reconciliation.pricingRules.clerkDiscountGroup'))
      }
    })
  }

  const displayToggles = computed(() => {
    if (toggles.value.length > 0) {
      return normalizeToggles(toggles.value)
    }
    return CLERK_CATEGORIES.map((category) => defaultToggle(category))
  })

  watch(
    () => props.jobId,
    async (jobId) => {
      toggles.value = []
      disabledCategories.value = []
      savedDisabledCategories.value = []
      customerCode.value = undefined
      loadError.value = ''
      if (!jobId) return
      loading.value = true
      try {
        const info = await fetchPricingRules(jobId)
        customerCode.value = info.customer_code
        toggles.value = normalizeToggles(info.toggles ?? [])
        disabledCategories.value = [...(info.disabled_categories ?? [])]
        savedDisabledCategories.value = [...disabledCategories.value]
      } catch (error) {
        toggles.value = CLERK_CATEGORIES.map((category) => defaultToggle(category))
        loadError.value =
          error instanceof Error ? error.message : t('reconciliation.pricingRules.applyFailed')
      } finally {
        loading.value = false
      }
    },
    { immediate: true }
  )

  function onToggle(category: string, enabled: boolean) {
    toggles.value = normalizeToggles(toggles.value).map((toggle) =>
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
      toggles.value = normalizeToggles(info.toggles ?? toggles.value)
      customerCode.value = info.customer_code ?? customerCode.value
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
