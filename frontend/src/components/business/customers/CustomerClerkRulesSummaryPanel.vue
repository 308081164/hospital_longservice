<template>
  <RuleSectionBlock
    :title="$t('menus.masterData.customerClerkRules.title')"
    :subtitle="$t('menus.masterData.customerClerkRules.subtitle')"
  >
    <template #actions>
      <RouterLink to="/billing-config/clerk-rules">
        <ElButton type="primary" link size="small">
          {{ $t('menus.masterData.customerClerkRules.goConfigure') }}
        </ElButton>
      </RouterLink>
    </template>

    <div v-if="!customerCode?.trim()" class="customer-clerk-rules__hint">
      {{ $t('menus.masterData.customerClerkRules.codeRequired') }}
    </div>

    <div v-else-if="loading" v-loading="true" class="customer-clerk-rules__loading" />

    <div v-else-if="loadError" class="customer-clerk-rules__hint customer-clerk-rules__hint--warn">
      {{ loadError }}
    </div>

    <div v-else-if="!hasRules" class="customer-clerk-rules__empty">
      {{ $t('menus.masterData.customerClerkRules.empty') }}
    </div>

    <template v-else>
      <div class="customer-clerk-rules__stats">
        <ElTag type="success" effect="plain" size="small">
          {{
            $t('menus.masterData.customerClerkRules.activeCount', {
              active: activeRules.length,
              total: allRules.length
            })
          }}
        </ElTag>
        <ElTag v-if="pendingCount > 0" type="warning" effect="plain" size="small">
          {{ $t('menus.masterData.customerClerkRules.pendingCount', { count: pendingCount }) }}
        </ElTag>
      </div>

      <ul class="customer-clerk-rules__list">
        <li v-for="(rule, idx) in displayRules" :key="idx" class="customer-clerk-rules__item">
          <ElTag size="small" effect="plain" class="customer-clerk-rules__type">
            {{ ruleTypeLabel(rule.ruleType) }}
          </ElTag>
          <span class="customer-clerk-rules__name">{{ rule.name || rule.ruleType }}</span>
          <ElTag
            v-if="rule.isActive === false"
            size="small"
            type="info"
            class="customer-clerk-rules__status"
          >
            {{ $t('menus.masterData.customerClerkRules.disabled') }}
          </ElTag>
        </li>
      </ul>

      <p v-if="hiddenRuleCount > 0" class="customer-clerk-rules__more">
        {{
          $t('menus.masterData.customerClerkRules.moreRules', {
            count: hiddenRuleCount
          })
        }}
      </p>
    </template>
  </RuleSectionBlock>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import RuleSectionBlock from '@/components/business/pricing-rules/RuleSectionBlock.vue'
  import { getClerkRuleCustomer } from '@/api/billing/clerkRulesApi'

  defineOptions({ name: 'CustomerClerkRulesSummaryPanel' })

  const props = defineProps<{
    customerCode?: string | null
  }>()

  const { t } = useI18n()
  const loading = ref(false)
  const loadError = ref('')
  const allRules = ref<ClerkRuleRow[]>([])

  const MAX_DISPLAY = 8

  interface ClerkRuleRow {
    name?: string
    ruleType?: string
    stage?: string
    isActive?: boolean
    migrationStatus?: string
  }

  const customerCode = computed(() => props.customerCode?.trim() ?? '')

  const activeRules = computed(() =>
    allRules.value.filter((r) => r.isActive !== false && r.ruleType)
  )

  const hasRules = computed(() => allRules.value.length > 0)

  const pendingCount = computed(
    () => allRules.value.filter((r) => r.migrationStatus === 'pending').length
  )

  const displayRules = computed(() => activeRules.value.slice(0, MAX_DISPLAY))

  const hiddenRuleCount = computed(() =>
    Math.max(0, activeRules.value.length - displayRules.value.length)
  )

  const RULE_TYPE_I18N: Record<string, string> = {
    BILL_EXPORT_PRICE_RULE: 'menus.masterData.customerClerkRules.typeBillExportPrice',
    SETTLEMENT_DISCOUNT: 'menus.masterData.customerClerkRules.typeSettlementDiscount',
    SETTLEMENT_MIN_CHARGE: 'menus.masterData.customerClerkRules.typeSettlementMinCharge',
    LOGISTICS_FEE: 'menus.masterData.customerClerkRules.typeLogisticsFee',
    LOGISTICS_WAIVE: 'menus.masterData.customerClerkRules.typeLogisticsWaive',
    LOGISTICS_CARD_DEDUCT: 'menus.masterData.customerClerkRules.typeLogisticsCard',
    GUOYAO_SETTLEMENT_FORMULA: 'menus.masterData.customerClerkRules.typeGuoyaoSettlement',
    PRICE_VALIDATE_ONLY: 'menus.masterData.customerClerkRules.typePriceValidate',
    EXPORT_LAYOUT: 'menus.masterData.customerClerkRules.typeExportLayout',
    ZERO_PRICE_PACK_MATERIAL: 'menus.masterData.customerClerkRules.typeZeroPrice',
    MONTHLY_SUPPLEMENT_REPORT: 'menus.masterData.customerClerkRules.typeMonthlySupplement',
    MERGED_SETTLEMENT: 'menus.masterData.customerClerkRules.typeMergedSettlement'
  }

  function ruleTypeLabel(ruleType?: string) {
    if (!ruleType) return '—'
    const key = RULE_TYPE_I18N[ruleType]
    return key ? t(key) : ruleType
  }

  async function loadRules() {
    loadError.value = ''
    allRules.value = []
    const code = customerCode.value
    if (!code) return

    loading.value = true
    try {
      const detail = await getClerkRuleCustomer(code)
      const rules = detail?.baseline?.rules
      allRules.value = Array.isArray(rules) ? (rules as ClerkRuleRow[]) : []
    } catch {
      loadError.value = t('menus.masterData.customerClerkRules.loadFailed')
      allRules.value = []
    } finally {
      loading.value = false
    }
  }

  watch(customerCode, loadRules, { immediate: true })
</script>

<style scoped>
  .customer-clerk-rules__loading {
    min-height: 72px;
  }

  .customer-clerk-rules__hint {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    line-height: 1.6;
  }

  .customer-clerk-rules__hint--warn {
    color: var(--el-color-warning);
  }

  .customer-clerk-rules__empty {
    font-size: 13px;
    color: var(--el-text-color-placeholder);
    padding: 8px 0;
  }

  .customer-clerk-rules__stats {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
  }

  .customer-clerk-rules__list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .customer-clerk-rules__item {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    padding: 8px 10px;
    border-radius: 6px;
    background: var(--el-fill-color-lighter);
    font-size: 13px;
  }

  .customer-clerk-rules__type {
    flex-shrink: 0;
  }

  .customer-clerk-rules__name {
    flex: 1;
    min-width: 120px;
    color: var(--el-text-color-primary);
  }

  .customer-clerk-rules__status {
    flex-shrink: 0;
  }

  .customer-clerk-rules__more {
    margin: 10px 0 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
</style>
