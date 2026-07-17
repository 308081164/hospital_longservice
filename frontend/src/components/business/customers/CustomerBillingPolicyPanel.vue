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
                :active-text="$t('menus.masterData.customerBillingPolicy.enabled')"
                :inactive-text="$t('menus.masterData.customerBillingPolicy.disabled')"
              />
            </div>
            <div class="billing-policy-panel__grid">
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.policyName') }}</label>
                <ElInput
                  v-model="disc.name"
                  :placeholder="$t('menus.masterData.customerBillingPolicy.policyNamePlaceholder')"
                />
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerForm.discountTemperature') }}</label>
                <ElSelect
                  v-model="disc.temperature"
                  class="w-full"
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
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.priority') }}</label>
                <ElInputNumber
                  v-model="disc.priority"
                  :min="1"
                  :max="999"
                  :step="10"
                  class="w-full"
                />
              </div>
              <div class="billing-policy-panel__field billing-policy-panel__field--full">
                <ElSwitch
                  :model-value="isDiscountLongTermEffective(disc)"
                  :active-text="$t('menus.masterData.customerBillingPolicy.longTermEffective')"
                  :disabled="readOnly"
                  @update:model-value="(val: boolean) => setDiscountLongTermEffective(disc, val)"
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
                <ElCheckbox v-model="disc.skipWhenFixedPrice">
                  {{ $t('menus.masterData.customerBillingPolicy.skipWhenFixedPrice') }}
                </ElCheckbox>
              </div>
              <div class="billing-policy-panel__field billing-policy-panel__field--full">
                <label>{{ $t('menus.masterData.customerBillingPolicy.applyStage') }}</label>
                <ElSelect v-model="disc.applyStage" class="w-full">
                  <ElOption label="账单明细" value="bill_detail" />
                  <ElOption label="仅结款函" value="settlement_only" />
                  <ElOption label="仅导出" value="export_only" />
                </ElSelect>
              </div>
            </div>
            <div class="billing-policy-panel__card-actions">
              <ElButton type="danger" link @click="removeDiscount(idx)">
                {{ $t('menus.masterData.customerBillingPolicy.removePolicy') }}
              </ElButton>
            </div>
          </div>
          <ElButton :disabled="hasGlobalDiscount" @click="addDiscount">
            {{ $t('menus.masterData.customerBillingPolicy.addDiscount') }}
          </ElButton>
        </ElTabPane>

        <ElTabPane
          :label="$t('menus.masterData.customerBillingPolicy.tabLogistics')"
          name="logistics"
        >
          <p class="billing-policy-panel__tab-desc">
            {{ $t('menus.masterData.customerForm.logisticsFeePerTripHint') }}
          </p>
          <div class="billing-policy-panel__card">
            <div class="billing-policy-panel__card-header">
              <span class="billing-policy-panel__card-title">
                {{ $t('menus.masterData.customerForm.logisticsTitle') }}
              </span>
              <ElSwitch
                v-model="state.logisticsActive"
                :active-text="$t('menus.masterData.customerBillingPolicy.enabled')"
                :inactive-text="$t('menus.masterData.customerBillingPolicy.disabled')"
              />
            </div>
            <div class="billing-policy-panel__grid">
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerForm.logisticsFeePerTrip') }}</label>
                <ElInputNumber
                  v-model="state.logisticsFeePerTrip"
                  :min="0"
                  :step="0.5"
                  :precision="2"
                  :placeholder="$t('menus.masterData.customerForm.logisticsFeePerTripPlaceholder')"
                  class="w-full"
                />
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.priority') }}</label>
                <ElInputNumber
                  v-model="state.logisticsPriority"
                  :min="1"
                  :max="999"
                  :step="10"
                  class="w-full"
                />
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.tripSource') }}</label>
                <ElSelect v-model="state.logisticsTripSource" class="w-full">
                  <ElOption
                    :label="$t('menus.masterData.customerBillingPolicy.tripSourceDelivery')"
                    value="delivery_date"
                  />
                  <ElOption
                    :label="$t('menus.masterData.customerBillingPolicy.tripSourceImport')"
                    value="import"
                  />
                </ElSelect>
              </div>
            </div>
          </div>

          <div class="billing-policy-panel__card">
            <div class="billing-policy-panel__card-header">
              <span class="billing-policy-panel__card-title">
                {{ $t('menus.masterData.customerBillingPolicy.allocationSection') }}
              </span>
            </div>
            <div class="billing-policy-panel__allocation-row">
              <div class="billing-policy-panel__allocation-summary">
                <ElTag type="info" effect="plain" size="small">
                  {{ allocationSummary }}
                </ElTag>
              </div>
              <ElButton :disabled="readOnly" @click="allocationDialogVisible = true">
                {{ $t('menus.masterData.customerBillingPolicy.configureAllocation') }}
              </ElButton>
            </div>
          </div>

          <LogisticsAllocationConfigDialog
            v-model:visible="allocationDialogVisible"
            :config="allocationConfig"
            :customers="customers"
            :customer-name-map="customerNameMap"
            :current-customer-id="customerId"
            :read-only="readOnly"
            :saving="allocationSaving"
            @confirm="handleAllocationConfirm"
          />

          <div class="billing-policy-panel__card">
            <div class="billing-policy-panel__card-header">
              <span class="billing-policy-panel__card-title">
                {{ $t('menus.masterData.customerBillingPolicy.logisticsCardSection') }}
              </span>
              <ElSwitch
                v-model="state.logisticsCardDeductionEnabled"
                :disabled="readOnly"
                :active-text="$t('menus.masterData.customerBillingPolicy.enabled')"
                :inactive-text="$t('menus.masterData.customerBillingPolicy.disabled')"
              />
            </div>
            <p class="billing-policy-panel__tab-desc">
              {{ $t('menus.masterData.customerBillingPolicy.logisticsCardDesc') }}
            </p>
            <div class="billing-policy-panel__grid">
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.cardDeductMode') }}</label>
                <ElSelect
                  v-model="state.logisticsCardDeductMode"
                  class="w-full"
                  :disabled="readOnly"
                >
                  <ElOption
                    :label="$t('menus.masterData.customerBillingPolicy.cardDeductAuto')"
                    value="auto"
                  />
                  <ElOption
                    :label="$t('menus.masterData.customerBillingPolicy.cardDeductNone')"
                    value="none"
                  />
                </ElSelect>
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.cardMonthlyCap') }}</label>
                <ElInputNumber
                  v-model="state.logisticsCardMonthlyCap"
                  :min="0"
                  :step="100"
                  :precision="2"
                  class="w-full"
                  :disabled="readOnly"
                />
                <span class="billing-policy-panel__hint">
                  {{ $t('menus.masterData.customerBillingPolicy.cardMonthlyCapHint') }}
                </span>
              </div>
              <div
                v-if="customerId"
                class="billing-policy-panel__field billing-policy-panel__field--full"
              >
                <label>{{ $t('menus.masterData.customerBillingPolicy.cardBalanceLink') }}</label>
                <div class="billing-policy-panel__inline-hint">
                  <template v-if="activeCard">
                    {{ activeCard.name }} · {{ $t('menus.billingConfig.balance') }} ¥{{
                      (activeCard.balance ?? 0).toFixed(2)
                    }}
                  </template>
                  <span v-else class="text-gray-400">
                    {{ $t('menus.masterData.customerBillingPolicy.noActiveCard') }}
                  </span>
                  <RouterLink
                    :to="{ name: 'BillingConfigLogisticsCard' }"
                    class="billing-policy-panel__link"
                  >
                    {{ $t('menus.masterData.customerBillingPolicy.manageLogisticsCard') }}
                  </RouterLink>
                </div>
              </div>
            </div>
          </div>
        </ElTabPane>

        <ElTabPane :label="$t('menus.masterData.customerBillingPolicy.tabMonthly')" name="monthly">
          <p class="billing-policy-panel__tab-desc">
            {{ $t('menus.masterData.customerForm.monthlySettlementDesc') }}
          </p>
          <div class="billing-policy-panel__card">
            <div class="billing-policy-panel__card-header">
              <span class="billing-policy-panel__card-title">
                {{ $t('menus.masterData.customerForm.monthlySettlementTitle') }}
              </span>
              <ElSwitch
                v-model="state.monthlyActive"
                :active-text="$t('menus.masterData.customerBillingPolicy.enabled')"
                :inactive-text="$t('menus.masterData.customerBillingPolicy.disabled')"
              />
            </div>
            <div class="billing-policy-panel__grid">
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerForm.monthlyMinCharge') }}</label>
                <ElInputNumber
                  v-model="state.monthlyMinCharge"
                  :min="0"
                  :step="100"
                  :precision="2"
                  class="w-full"
                />
                <span class="billing-policy-panel__hint">
                  {{ $t('menus.masterData.customerForm.monthlyMinChargeHint') }}
                </span>
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerForm.monthlyMaxCap') }}</label>
                <ElInputNumber
                  v-model="state.monthlyMaxCap"
                  :min="0"
                  :step="100"
                  :precision="2"
                  class="w-full"
                />
                <span class="billing-policy-panel__hint">
                  {{ $t('menus.masterData.customerForm.monthlyMaxCapHint') }}
                </span>
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.priority') }}</label>
                <ElInputNumber
                  v-model="state.monthlyPriority"
                  :min="1"
                  :max="999"
                  :step="10"
                  class="w-full"
                />
              </div>
            </div>
          </div>
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
                  class="w-full"
                />
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.priority') }}</label>
                <ElInputNumber
                  v-model="state.urgentPriority"
                  :min="1"
                  :max="999"
                  :step="10"
                  class="w-full"
                />
              </div>
            </div>
          </div>
        </ElTabPane>

        <ElTabPane
          :label="$t('menus.masterData.customerBillingPolicy.tabDeduction')"
          name="deduction"
        >
          <p class="billing-policy-panel__tab-desc">
            {{ $t('menus.masterData.customerBillingPolicy.deductionDesc') }}
          </p>
          <div class="billing-policy-panel__card">
            <div class="billing-policy-panel__card-header">
              <span class="billing-policy-panel__card-title">
                {{ $t('menus.masterData.customerBillingPolicy.deductionTitle') }}
              </span>
              <ElSwitch
                v-model="state.deductionActive"
                :active-text="$t('menus.masterData.customerBillingPolicy.enabled')"
                :inactive-text="$t('menus.masterData.customerBillingPolicy.disabled')"
              />
            </div>
            <div class="billing-policy-panel__grid">
              <div class="billing-policy-panel__field">
                <label>{{
                  $t('menus.masterData.customerBillingPolicy.deductionMonthlyAmount')
                }}</label>
                <ElInputNumber
                  v-model="state.deductionMonthlyAmount"
                  :min="0"
                  :step="100"
                  :precision="2"
                  class="w-full"
                />
                <span class="billing-policy-panel__hint">
                  {{ $t('menus.masterData.customerBillingPolicy.deductionHint') }}
                </span>
              </div>
              <div class="billing-policy-panel__field">
                <label>{{ $t('menus.masterData.customerBillingPolicy.priority') }}</label>
                <ElInputNumber
                  v-model="state.deductionPriority"
                  :min="1"
                  :max="999"
                  :step="10"
                  class="w-full"
                />
              </div>
            </div>
          </div>
        </ElTabPane>

        <ElTabPane
          :label="$t('menus.masterData.customerBillingPolicy.tabSettlement')"
          name="settlement"
        >
          <p class="billing-policy-panel__tab-desc">
            {{ $t('menus.masterData.customerBillingPolicy.settlementDesc') }}
          </p>
          <ElAlert
            type="info"
            :closable="false"
            show-icon
            class="billing-policy-panel__settlement-alert"
          >
            {{ $t('menus.masterData.customerBillingPolicy.settlementExportNote') }}
          </ElAlert>
          <div class="billing-policy-panel__settlement-preview">
            <div
              v-for="line in settlementPreviewLines"
              :key="line.key"
              class="billing-policy-panel__settlement-row"
            >
              <span class="billing-policy-panel__settlement-label">{{ line.label }}</span>
              <span class="billing-policy-panel__settlement-value">{{ line.value }}</span>
              <span v-if="line.hint" class="billing-policy-panel__settlement-hint">{{
                line.hint
              }}</span>
            </div>
          </div>
        </ElTabPane>
      </ElTabs>
    </RuleSectionBlock>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import RuleSectionBlock from '@/components/business/pricing-rules/RuleSectionBlock.vue'
  import {
    applyConfigToPanelState,
    toAllocationConfigPayload
  } from '@/utils/logisticsAllocationConfig'
  import {
    buildSettlementPreviewLines,
    createDefaultDiscount,
    formatDiscountRate,
    formatLogisticsAllocationSummary,
    formatPolicySummary,
    getLogisticsAllocationConfig,
    isDiscountLongTermEffective,
    isGlobalDiscount,
    setDiscountLongTermEffective,
    type BillingPolicyPanelState,
    type BillingPolicyTab,
    type PanelCustomerDiscount
  } from '@/utils/customerBillingPolicy'
  import { listLogisticsCards, type LogisticsCardRecord } from '@/api/billing-config/logisticsApi'
  import {
    createCustomerGroup,
    syncCustomerGroupAllocationConfig,
    getCustomerGroup,
    listCustomerGroups,
    type CustomerGroupRecord
  } from '@/api/billing-config/customerGroupsApi'
  import { listCustomers } from '@/api/master-data/customersApi'
  import LogisticsAllocationConfigDialog from '@/components/business/customers/LogisticsAllocationConfigDialog.vue'
  import type { LogisticsAllocationConfig } from '@/utils/logisticsAllocationConfig'

  defineOptions({ name: 'CustomerBillingPolicyPanel' })

  const props = defineProps<{
    state: BillingPolicyPanelState
    readOnly?: boolean
    customerId?: number | null
  }>()

  const emit = defineEmits<{
    allocationSaved: []
  }>()

  const readOnly = computed(() => props.readOnly === true)
  const customerId = computed(() => props.customerId ?? null)

  const { t } = useI18n()
  const activeTab = ref<BillingPolicyTab>('discount')

  const logisticsMergeGroups = ref<CustomerGroupRecord[]>([])
  const customers = ref<Api.MasterData.CustomerRecord[]>([])
  const customerNameMap = ref<Record<number, string>>({})
  const activeCard = ref<LogisticsCardRecord | null>(null)
  const allocationDialogVisible = ref(false)
  const allocationSaving = ref(false)

  const allocationConfig = computed(() => getLogisticsAllocationConfig(props.state))

  const allocationSummary = computed(() =>
    formatLogisticsAllocationSummary(props.state, customerNameMap.value, t)
  )

  const hasGlobalDiscount = computed(() => (props.state.discounts ?? []).some(isGlobalDiscount))

  const policySummary = computed(() => formatPolicySummary(props.state, t))

  const settlementPreviewLines = computed(() => buildSettlementPreviewLines(props.state, t))

  async function loadCustomers() {
    try {
      customers.value = await listCustomers()
      customerNameMap.value = Object.fromEntries(
        customers.value.map((c) => [c.id, c.canonical_name ?? c.code])
      )
    } catch {
      customers.value = []
      customerNameMap.value = {}
    }
  }

  async function loadLogisticsContext() {
    if (!customerId.value) {
      activeCard.value = null
      return
    }
    try {
      const cards = await listLogisticsCards(customerId.value)
      activeCard.value = cards.find((c) => c.is_active !== false) ?? cards[0] ?? null
    } catch {
      activeCard.value = null
    }
  }

  async function loadMergeGroups() {
    try {
      logisticsMergeGroups.value = await listCustomerGroups('logistics_merge')
    } catch {
      logisticsMergeGroups.value = []
    }
  }

  async function hydrateAllocationGroupMembers(groupId?: number) {
    if (!groupId) return
    try {
      const group = await getCustomerGroup(groupId)
      props.state.logisticsAllocationGroupName = group.name
      props.state.logisticsAllocationMemberIds = (group.members ?? []).map(
        (m) => m.customer_id ?? m.customerId ?? 0
      )
      const ratios: Record<number, number> = {}
      ;(group.members ?? []).forEach((m) => {
        const id = m.customer_id ?? m.customerId ?? 0
        const ratio = m.share_ratio ?? m.shareRatio
        if (ratio != null) ratios[id] = ratio
      })
      props.state.logisticsMergeShareRatios = ratios
    } catch {
      // keep local state
    }
  }

  async function handleAllocationConfirm(config: LogisticsAllocationConfig) {
    allocationSaving.value = true
    try {
      applyConfigToPanelState(config, props.state)
      let groupId = config.groupId
      if (config.mode !== 'none' && config.mode !== 'dept_ratio') {
        if (!groupId) {
          const created = await createCustomerGroup({
            name:
              config.groupName ||
              t('menus.masterData.customerBillingPolicy.allocationDefaultGroupName', {
                count: config.memberCustomerIds.length
              }),
            groupType: 'logistics_merge',
            isActive: true,
            members: config.memberCustomerIds.map((id) => ({
              customerId: id,
              shareRatio: config.shareRatios[id] ?? null
            }))
          })
          groupId = created.id
          props.state.logisticsMergeGroupId = groupId
        }
        if (groupId) {
          const synced = await syncCustomerGroupAllocationConfig(
            groupId,
            toAllocationConfigPayload({ ...config, groupId })
          )
          props.state.logisticsMergeGroupId = synced.group_id ?? synced.groupId ?? groupId
        }
      }
      allocationDialogVisible.value = false
      emit('allocationSaved')
      ElMessage.success(t('menus.masterData.customerBillingPolicy.allocationSaved'))
    } catch {
      ElMessage.error(t('menus.masterData.customerBillingPolicy.allocationSaveFailed'))
    } finally {
      allocationSaving.value = false
    }
  }

  onMounted(async () => {
    await Promise.all([loadMergeGroups(), loadCustomers(), loadLogisticsContext()])
    if (props.state.logisticsMergeGroupId) {
      await hydrateAllocationGroupMembers(props.state.logisticsMergeGroupId)
    }
  })

  watch(customerId, () => {
    loadLogisticsContext()
  })

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

  .billing-policy-panel__settlement-alert {
    margin-bottom: 12px;
  }

  .billing-policy-panel__settlement-preview {
    border: 1px dashed var(--el-border-color);
    border-radius: 8px;
    overflow: hidden;
  }

  .billing-policy-panel__settlement-row {
    display: grid;
    grid-template-columns: 140px 1fr;
    gap: 8px 16px;
    padding: 10px 14px;
    font-size: 13px;
    border-bottom: 1px solid var(--el-border-color-extra-light);
  }

  .billing-policy-panel__settlement-row:last-child {
    border-bottom: none;
  }

  .billing-policy-panel__settlement-label {
    color: var(--el-text-color-secondary);
  }

  .billing-policy-panel__settlement-value {
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  .billing-policy-panel__settlement-hint {
    grid-column: 2;
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }

  .billing-policy-panel__inline-hint {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }

  .billing-policy-panel__link {
    font-size: 12px;
    color: var(--el-color-primary);
    text-decoration: none;
  }

  .billing-policy-panel__link:hover {
    text-decoration: underline;
  }

  .billing-policy-panel__share-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .billing-policy-panel__share-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    font-size: 13px;
  }

  .billing-policy-panel__allocation-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .billing-policy-panel__allocation-summary {
    flex: 1;
    min-width: 200px;
  }

  @media (max-width: 640px) {
    .billing-policy-panel__grid {
      grid-template-columns: 1fr;
    }

    .billing-policy-panel__settlement-row {
      grid-template-columns: 1fr;
    }
  }
</style>
