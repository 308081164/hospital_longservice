<template>
  <div
    class="reconciliation-billing-detail"
    :class="{ 'reconciliation-billing-detail--expanded': expanded }"
  >
    <template v-if="!hasContent">
      <span v-if="expanded" class="text-xs text-gray-400">{{
        t('reconciliation.detail.billingEmpty')
      }}</span>
    </template>

    <template v-else>
      <div v-if="!expanded" class="compact-view">
        <div class="flex flex-wrap items-center gap-1">
          <ElTag
            v-if="ctx.isMultiPrice"
            :type="ctx.isMatched ? 'success' : ctx.isMismatch ? 'warning' : 'info'"
            size="small"
            effect="plain"
          >
            {{
              ctx.isMatched
                ? t('reconciliation.detail.multiPriceTagHit')
                : t('reconciliation.detail.multiPriceTag')
            }}
          </ElTag>

          <ElTooltip
            v-if="ctx.isMatched && ctx.matchedPrice != null"
            placement="top"
            :show-after="200"
          >
            <template #content>
              <div class="max-w-xs space-y-1 text-xs">
                <div
                  >{{ t('reconciliation.detail.matchedPrice') }}：{{
                    formatCurrency(ctx.matchedPrice)
                  }}</div
                >
                <div v-if="ctx.expectedUnitPrice != null">
                  {{ t('reconciliation.detail.expectedPrice') }}：{{
                    formatCurrency(ctx.expectedUnitPrice)
                  }}
                </div>
                <div v-if="ctx.candidates.length"
                  >{{ t('reconciliation.detail.candidates') }}：{{
                    formatPriceList(ctx.candidates)
                  }}</div
                >
              </div>
            </template>
            <span class="text-xs font-medium text-green-700">
              {{ formatCurrency(ctx.matchedPrice) }}
            </span>
          </ElTooltip>

          <ElTooltip v-else-if="ctx.isMismatch" placement="top" :show-after="200">
            <template #content>
              <div class="max-w-xs space-y-1 text-xs">
                <div
                  >{{ t('reconciliation.detail.billPrice') }}：{{
                    formatCurrency(ctx.billUnitPrice)
                  }}</div
                >
                <div
                  >{{ t('reconciliation.detail.candidates') }}：{{
                    formatPriceList(ctx.candidates)
                  }}</div
                >
                <div v-if="ctx.expectedUnitPrice != null">
                  {{ t('reconciliation.detail.expectedPrice') }}：{{
                    formatCurrency(ctx.expectedUnitPrice)
                  }}
                </div>
              </div>
            </template>
            <span class="text-xs font-medium text-orange-600">
              {{ t('reconciliation.detail.multiPriceMismatch') }}
            </span>
          </ElTooltip>

          <ElTooltip v-if="ctx.ruleName" placement="top" :content="ruleTooltip" :show-after="200">
            <ElTag size="small" type="info" effect="plain" class="max-w-[120px] truncate">
              {{ ctx.ruleName }}
            </ElTag>
          </ElTooltip>

          <ElTooltip v-if="ctx.discountChain.length" placement="top" :show-after="200">
            <template #content>
              <div class="max-w-sm space-y-1 text-xs">
                <div v-for="(step, index) in ctx.discountChain" :key="index">
                  {{ step.detail ?? step.label }}
                </div>
              </div>
            </template>
            <ElTag size="small" effect="plain">
              {{ t('reconciliation.detail.discountChain') }} ({{ ctx.discountChain.length }})
            </ElTag>
          </ElTooltip>

          <ElTooltip v-if="ctx.policyTraces.length" placement="top" :show-after="200">
            <template #content>
              <div class="max-w-sm space-y-1 text-xs">
                <div v-for="(step, index) in ctx.policyTraces" :key="index">
                  {{ step.label }}{{ step.detail ? ` — ${step.detail}` : '' }}
                </div>
              </div>
            </template>
            <ElTag size="small" type="warning" effect="plain">
              {{ t('reconciliation.detail.policyTraces') }} ({{ ctx.policyTraces.length }})
            </ElTag>
          </ElTooltip>

          <ElTooltip
            v-if="ctx.hasFieldConsistencyIssues"
            placement="top"
            :show-after="200"
          >
            <template #content>
              <div class="max-w-sm space-y-1 text-xs">
                <div v-for="(item, index) in ctx.fieldConsistencyViolations" :key="index">
                  {{ item.message }}
                </div>
              </div>
            </template>
            <ElTag size="small" type="danger" effect="plain">
              {{ t('reconciliation.detail.fieldConsistencyTag') }}
              ({{ ctx.fieldConsistencyViolations.length }})
            </ElTag>
          </ElTooltip>

          <ElTooltip
            v-if="pricingRuleSummary"
            placement="top"
            :content="pricingRuleSummary"
            :show-after="200"
          >
            <ElTag size="small" type="info" effect="plain" class="max-w-[120px] truncate">
              {{ pricingRuleShort }}
            </ElTag>
          </ElTooltip>

          <button
            v-if="showViewDetailLink"
            type="button"
            class="text-xs text-primary hover:underline"
            @click.stop="emit('open-detail', row)"
          >
            {{ t('pricingFlow.viewDetail') }}
          </button>
        </div>
      </div>

      <div v-else class="expanded-view space-y-3 px-2 py-1">
        <section v-if="pricingRuleSummary" class="detail-section">
          <div class="detail-section-title">{{ t('pricingFlow.ruleSummary') }}</div>
          <div class="detail-value text-sm">{{ pricingRuleSummary }}</div>
        </section>

        <section v-if="ctx.isMultiPrice" class="detail-section">
          <div class="detail-section-title">{{ t('reconciliation.detail.multiPriceSection') }}</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">{{ t('reconciliation.detail.billPrice') }}</span>
              <span class="detail-value">{{ formatCurrency(ctx.billUnitPrice) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">{{ t('reconciliation.detail.expectedPrice') }}</span>
              <span class="detail-value">{{ formatCurrency(ctx.expectedUnitPrice) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">{{ t('reconciliation.detail.matchedPrice') }}</span>
              <span
                class="detail-value"
                :class="ctx.isMatched ? 'text-green-600' : 'text-gray-400'"
              >
                {{ ctx.matchedPrice != null ? formatCurrency(ctx.matchedPrice) : '—' }}
              </span>
            </div>
          </div>

          <div v-if="ctx.candidates.length" class="mt-2">
            <span class="detail-label mr-2">{{ t('reconciliation.detail.candidates') }}</span>
            <div class="mt-1 flex flex-wrap gap-1">
              <ElTag
                v-for="price in ctx.candidates"
                :key="price"
                size="small"
                :type="isCandidateHit(price) ? 'success' : 'info'"
                effect="plain"
              >
                {{ formatCurrency(price) }}
                <span v-if="isCandidateHit(price)" class="ml-1">✓</span>
              </ElTag>
            </div>
          </div>

          <ElAlert v-if="ctx.isMismatch" class="mt-2" type="warning" :closable="false" show-icon>
            <template #title>
              <span class="text-xs">
                {{
                  t('reconciliation.detail.multiPriceMismatchHint', {
                    bill: formatCurrency(ctx.billUnitPrice),
                    candidates: formatPriceList(ctx.candidates)
                  })
                }}
              </span>
            </template>
          </ElAlert>
        </section>

        <section v-if="ctx.ruleName || ctx.matchedRuleId != null" class="detail-section">
          <div class="detail-section-title">{{ t('reconciliation.detail.ruleTraceSection') }}</div>
          <div class="detail-grid">
            <div v-if="ctx.ruleName" class="detail-item">
              <span class="detail-label">{{ t('reconciliation.detail.ruleName') }}</span>
              <span class="detail-value">{{ ctx.ruleName }}</span>
            </div>
            <div v-if="ctx.matchedRuleId != null" class="detail-item">
              <span class="detail-label">{{ t('reconciliation.detail.matchedRuleId') }}</span>
              <span class="detail-value font-mono text-xs">{{ ctx.matchedRuleId }}</span>
            </div>
          </div>
        </section>

        <section v-if="ctx.policyTraces.length" class="detail-section">
          <div class="detail-section-title">{{
            t('reconciliation.detail.policyTracesSection')
          }}</div>
          <ol class="discount-chain-list">
            <li v-for="(step, index) in ctx.policyTraces" :key="index">
              <span class="font-medium">{{ step.label }}</span>
              <span v-if="step.policyType" class="ml-1 text-gray-400">({{ step.policyType }})</span>
              <div v-if="step.detail" class="text-gray-500">{{ step.detail }}</div>
            </li>
          </ol>
        </section>

        <section v-if="ctx.hasFieldConsistencyIssues" class="detail-section">
          <div class="detail-section-title">{{
            t('reconciliation.detail.fieldConsistencySection')
          }}</div>
          <ElAlert type="error" :closable="false" show-icon class="mb-2">
            <template #title>
              <span class="text-xs">{{ t('reconciliation.detail.fieldConsistencyHint') }}</span>
            </template>
          </ElAlert>
          <ul class="trace-notes-list">
            <li v-for="(item, index) in ctx.fieldConsistencyViolations" :key="index">
              <span class="font-medium">{{
                fieldConsistencyViolationLabel(item.code, t)
              }}</span>
              <span class="ml-1">{{ item.message }}</span>
            </li>
          </ul>
        </section>

        <section v-if="ctx.discountChain.length" class="detail-section">
          <div class="detail-section-title">{{
            t('reconciliation.detail.discountChainSection')
          }}</div>
          <ol class="discount-chain-list">
            <li v-for="(step, index) in ctx.discountChain" :key="index">
              <ElTooltip v-if="step.detail" placement="top" :content="step.detail">
                <span>{{ step.label }}</span>
              </ElTooltip>
              <span v-else>{{ step.label }}</span>
            </li>
          </ol>
        </section>

        <section v-if="ctx.traceNotes.length" class="detail-section">
          <div class="detail-section-title">{{ t('reconciliation.detail.otherNotes') }}</div>
          <ul class="trace-notes-list">
            <li v-for="(note, index) in ctx.traceNotes" :key="index">{{ note }}</li>
          </ul>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import {
    fieldConsistencyViolationLabel,
    formatReconciliationCurrency,
    hasBillingDetail,
    parseReconciliationBillingContext
  } from '@/utils/reconciliationBillingNotes'
  import { hasPricingDetail } from '@/utils/reconciliationPricingPath'

  defineOptions({ name: 'ReconciliationBillingDetail' })

  const props = withDefaults(
    defineProps<{
      row: Record<string, unknown>
      expanded?: boolean
      showViewDetailLink?: boolean
    }>(),
    {
      expanded: false,
      showViewDetailLink: false
    }
  )

  const emit = defineEmits<{
    'open-detail': [row: Record<string, unknown>]
  }>()

  const { t } = useI18n()

  const ctx = computed(() => parseReconciliationBillingContext(props.row))
  const hasContent = computed(
    () => hasBillingDetail(props.row) || hasPricingDetail(props.row)
  )

  const pricingRuleSummary = computed(() => {
    const raw = props.row.pricingRule ?? props.row.pricing_rule
    return raw == null ? '' : String(raw).trim()
  })

  const pricingRuleShort = computed(() => {
    const text = pricingRuleSummary.value
    if (!text) return ''
    return text.length > 16 ? `${text.slice(0, 16)}…` : text
  })

  const ruleTooltip = computed(() => {
    const parts = [ctx.value.ruleName]
    if (ctx.value.matchedRuleId != null) {
      parts.push(`${t('reconciliation.detail.matchedRuleId')}: ${ctx.value.matchedRuleId}`)
    }
    return parts.filter(Boolean).join(' · ')
  })

  function formatCurrency(value: number | null | undefined): string {
    return formatReconciliationCurrency(value)
  }

  function formatPriceList(prices: number[]): string {
    if (!prices.length) return '—'
    return prices.map((price) => formatCurrency(price)).join(' / ')
  }

  function isCandidateHit(price: number): boolean {
    if (ctx.value.matchedPrice != null && Math.abs(ctx.value.matchedPrice - price) <= 0.001) {
      return true
    }
    if (ctx.value.billUnitPrice != null && Math.abs(ctx.value.billUnitPrice - price) <= 0.001) {
      return true
    }
    return false
  }
</script>

<style scoped>
  .reconciliation-billing-detail--expanded {
    background: #fafafa;
    border-radius: 6px;
  }

  .detail-section-title {
    margin-bottom: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #606266;
  }

  .detail-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: 8px;
  }

  .detail-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .detail-label {
    font-size: 11px;
    color: #909399;
  }

  .detail-value {
    font-size: 12px;
    color: #303133;
  }

  .discount-chain-list,
  .trace-notes-list {
    margin: 0;
    padding-left: 18px;
    font-size: 12px;
    line-height: 1.6;
    color: #606266;
  }

  .discount-chain-list li + li,
  .trace-notes-list li + li {
    margin-top: 4px;
  }
</style>
