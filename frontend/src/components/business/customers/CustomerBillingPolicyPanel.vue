<template>
  <div class="billing-policy-panel">
    <RuleSectionBlock
      :title="$t('menus.masterData.customerBillingPolicy.title')"
      :subtitle="$t('menus.masterData.customerBillingPolicy.subtitle')"
    >
      <div v-if="policySummary" class="billing-policy-panel__summary">
        <ElTag type="info" effect="plain" size="small">
          {{ $t('menus.masterData.customerBillingPolicy.summaryLabel') }}
        </ElTag>
        <span class="billing-policy-panel__summary-text">{{ policySummary }}</span>
      </div>
      <div v-else class="billing-policy-panel__summary billing-policy-panel__summary--empty">
        {{ $t('menus.masterData.customerBillingPolicy.summaryEmpty') }}
      </div>

      <ElTabs v-model="activeTab" class="billing-policy-panel__tabs">
        <ElTabPane
          :label="$t('menus.masterData.customerBillingPolicy.tabDiscount')"
          name="discount"
        >
          <p class="billing-policy-panel__tab-desc">
            {{ $t('menus.masterData.customerBillingPolicy.discountDesc') }}
          </p>
          <div v-for="(disc, idx) in state.discounts" :key="idx" class="billing-policy-panel__card">
            <div class="billing-policy-panel__card-header">
              <span class="billing-policy-panel__card-title">
                {{ disc.name || $t('menus.masterData.customerBillingPolicy.defaultDiscountName') }}
              </span>
              <ElSwitch
                v-model="disc.isActive"
                :disabled="readOnly"
                :active-text="$t('menus.masterData.customerBillingPolicy.enabled')"
                :inactive-text="$t('menus.masterData.customerBillingPolicy.disabled')"
              />
            </div>
            <div class="billing-policy-panel__grid">
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.policyName') }}</label>
                <ElInput
                  v-model="disc.name"
                  :disabled="readOnly"
                  :placeholder="$t('menus.masterData.customerBillingPolicy.policyNamePlaceholder')"
                />
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerForm.discountTemperature') }}</label>
                <ElSelect
                  v-model="disc.temperature"
                  class="w-full"
                  :disabled="readOnly"
                  @change="(val: 'HT' | 'LT' | 'ANY') => handleDiscountTemperatureChange(disc, val)"
                >
                  <ElOption
                    :label="$t('menus.masterData.customerForm.discountTemperatureAny')"
                    value="ANY"
                  />
                  <ElOption
                    :label="$t('menus.masterData.customerForm.discountTemperatureHt')"
                    value="HT"
                  />
                  <ElOption
                    :label="$t('menus.masterData.customerForm.discountTemperatureLt')"
                    value="LT"
                  />
                </ElSelect>
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.discountRate') }}</label>
                <ElInputNumber
                  v-model="disc.discountRate"
                  :min="0.0001"
                  :max="1"
                  :step="0.05"
                  :precision="4"
                  :disabled="readOnly"
                  class="w-full"
                />
                <span v-if="disc.discountRate != null" class="billing-policy-panel__hint">
                  ≈ {{ formatDiscountRate(disc.discountRate) }}
                </span>
                <div
                  v-if="disc.discountRate != null && disc.temperature !== 'ANY'"
                  class="billing-policy-panel__temp-bar"
                >
                  <span class="billing-policy-panel__temp-label">
                    {{ disc.temperature === 'HT' ? '高温' : '低温' }}折扣强度
                  </span>
                  <ElProgress
                    :percentage="Math.round((1 - disc.discountRate) * 100)"
                    :stroke-width="10"
                    :color="disc.temperature === 'HT' ? '#f56c6c' : '#409eff'"
                  />
                </div>
              </div>
              <div class="billing-policy-panel__field billing-policy-panel__field--full">
                <ElSwitch
                  :model-value="isDiscountLongTermEffective(disc)"
                  :active-text="$t('menus.masterData.customerBillingPolicy.longTermEffective')"
                  :disabled="readOnly"
                  @update:model-value="(val) => setDiscountLongTermEffective(disc, val === true)"
                />
              </div>
              <div v-if="!isDiscountLongTermEffective(disc)" class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.effectiveFrom') }}</label>
                <ElDatePicker
                  v-model="disc.effectiveFrom"
                  type="date"
                  value-format="YYYY-MM-DD"
                  class="w-full"
                  clearable
                  :disabled="readOnly"
                />
              </div>
              <div v-if="!isDiscountLongTermEffective(disc)" class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.effectiveTo') }}</label>
                <ElDatePicker
                  v-model="disc.effectiveTo"
                  type="date"
                  value-format="YYYY-MM-DD"
                  class="w-full"
                  clearable
                  :disabled="readOnly"
                />
              </div>
              <div class="billing-policy-panel__field billing-policy-panel__field--full">
                <ElCheckbox v-model="disc.skipWhenFixedPrice" :disabled="readOnly">
                  {{ $t('menus.masterData.customerBillingPolicy.skipWhenFixedPrice') }}
                </ElCheckbox>
              </div>
            </div>
            <div class="billing-policy-panel__card-actions">
              <ElButton type="danger" link :disabled="readOnly" @click="removeDiscount(idx)">
                {{ $t('menus.masterData.customerBillingPolicy.removePolicy') }}
              </ElButton>
            </div>
          </div>
          <ElButton :disabled="readOnly || hasGlobalDiscount" @click="addDiscount">
            {{ $t('menus.masterData.customerBillingPolicy.addDiscount') }}
          </ElButton>
        </ElTabPane>

        <ElTabPane :label="$t('menus.masterData.customerBillingPolicy.tabUrgent')" name="urgent">
          <p class="billing-policy-panel__tab-desc">
            {{ $t('menus.masterData.customerBillingPolicy.urgentDesc') }}
          </p>
          <div class="billing-policy-panel__card">
            <div class="billing-policy-panel__card-header">
              <span class="billing-policy-panel__card-title">
                {{ $t('menus.masterData.customerBillingPolicy.urgentTitle') }}
              </span>
              <ElSwitch
                v-model="state.urgentActive"
                :disabled="readOnly"
                :active-text="$t('menus.masterData.customerBillingPolicy.enabled')"
                :inactive-text="$t('menus.masterData.customerBillingPolicy.disabled')"
              />
            </div>
            <div class="billing-policy-panel__grid">
              <div class="billing-policy-panel__field">
                <label>{{
                  $t('menus.masterData.customerBillingPolicy.urgentBaseMultiplier')
                }}</label>
                <ElInputNumber
                  v-model="state.urgentBaseMultiplier"
                  :min="1"
                  :max="3"
                  :step="0.05"
                  :precision="3"
                  :disabled="readOnly"
                  class="w-full"
                />
              </div>
              <div class="billing-policy-panel__field">
                <label>{{
                  $t('menus.masterData.customerBillingPolicy.urgentAdjustedMultiplier')
                }}</label>
                <ElInputNumber
                  v-model="state.urgentAdjustedMultiplier"
                  :min="1"
                  :max="3"
                  :step="0.025"
                  :precision="3"
                  :disabled="readOnly"
                  class="w-full"
                />
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.urgentLogisticsFee') }}</label>
                <ElInputNumber
                  v-model="state.urgentLogisticsFeePerTrip"
                  :min="0"
                  :step="10"
                  :precision="2"
                  :disabled="readOnly"
                  class="w-full"
                />
              </div>
              <div class="billing-policy-panel__field">
                <label>{{
                  $t('menus.masterData.customerBillingPolicy.urgentLogisticsDiscount')
                }}</label>
                <ElInputNumber
                  v-model="state.urgentLogisticsDiscountRate"
                  :min="0.0001"
                  :max="1"
                  :step="0.05"
                  :precision="2"
                  :disabled="readOnly"
                  class="w-full"
                />
              </div>
            </div>
          </div>
        </ElTabPane>
      </ElTabs>
    </RuleSectionBlock>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import RuleSectionBlock from '@/components/business/pricing-rules/RuleSectionBlock.vue'
  import {
    createDefaultDiscount,
    formatDiscountRate,
    formatPricingPolicySummary,
    isDiscountLongTermEffective,
    isGlobalDiscount,
    setDiscountLongTermEffective,
    type BillingPolicyPanelState,
    type BillingPolicyTab,
    type PanelCustomerDiscount
  } from '@/utils/customerBillingPolicy'

  defineOptions({ name: 'CustomerBillingPolicyPanel' })

  const props = defineProps<{
    state: BillingPolicyPanelState
    readOnly?: boolean
  }>()

  const readOnly = computed(() => props.readOnly === true)
  const { t } = useI18n()
  const activeTab = ref<BillingPolicyTab>('discount')

  const hasGlobalDiscount = computed(() => (props.state.discounts ?? []).some(isGlobalDiscount))

  const policySummary = computed(() => formatPricingPolicySummary(props.state, t))

  function showGlobalDiscountExistsWarning() {
    ElMessage.warning(t('menus.masterData.customerForm.globalDiscountExistsWarning'))
  }

  function handleDiscountTemperatureChange(
    disc: PanelCustomerDiscount,
    newVal: 'HT' | 'LT' | 'ANY'
  ) {
    if (newVal !== 'ANY') return
    const globalCount = (props.state.discounts ?? []).filter(isGlobalDiscount).length
    if (globalCount > 1) {
      showGlobalDiscountExistsWarning()
      disc.temperature = 'HT'
    }
  }

  function addDiscount() {
    if (hasGlobalDiscount.value) {
      showGlobalDiscountExistsWarning()
      return
    }
    props.state.discounts = props.state.discounts ?? []
    props.state.discounts.push(createDefaultDiscount())
  }

  function removeDiscount(idx: number) {
    props.state.discounts?.splice(idx, 1)
  }
</script>

<style scoped>
  .billing-policy-panel__summary {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 16px;
    padding: 10px 12px;
    border-radius: 6px;
    background: var(--el-fill-color-lighter);
  }

  .billing-policy-panel__summary--empty {
    margin-bottom: 16px;
    font-size: 13px;
    color: var(--el-text-color-placeholder);
  }

  .billing-policy-panel__summary-text {
    font-size: 13px;
    color: var(--el-text-color-regular);
  }

  .billing-policy-panel__tabs {
    margin-top: 4px;
  }

  .billing-policy-panel__tab-desc {
    margin: 0 0 12px;
    font-size: 12px;
    line-height: 1.6;
    color: var(--el-text-color-secondary);
  }

  .billing-policy-panel__card {
    margin-bottom: 12px;
    padding: 14px 16px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    background: var(--el-fill-color-blank);
  }

  .billing-policy-panel__card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
  }

  .billing-policy-panel__card-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .billing-policy-panel__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px 16px;
  }

  .billing-policy-panel__field {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .billing-policy-panel__field--full {
    grid-column: 1 / -1;
  }

  .billing-policy-panel__field label {
    font-size: 13px;
    color: var(--el-text-color-regular);
  }

  .billing-policy-panel__hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .billing-policy-panel__temp-bar {
    margin-top: 8px;
  }

  .billing-policy-panel__temp-label {
    display: block;
    font-size: 11px;
    color: var(--el-text-color-secondary);
    margin-bottom: 4px;
  }

  .billing-policy-panel__card-actions {
    margin-top: 8px;
    text-align: right;
  }

  @media (max-width: 640px) {
    .billing-policy-panel__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
