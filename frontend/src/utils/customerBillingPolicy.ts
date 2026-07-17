/**
 * Billing policy panel state helpers.
 *
 * Logistics extensions (card deduction, cross-hospital merge) persist in LOGISTICS policy params JSON.
 * Reconciliation rows link departments via sheetName; dept/physician master data is under billing-config.
 */
import type { ComposerTranslation } from 'vue-i18n'
import type { LogisticsAllocationMode } from '@/utils/logisticsAllocationConfig'
import {
  buildLogisticsAllocationSummary,
  configFromPanelState,
  type LogisticsAllocationConfig
} from '@/utils/logisticsAllocationConfig'

export type BillingPolicyTab =
  'discount' | 'logistics' | 'monthly' | 'urgent' | 'deduction' | 'settlement'

/** Panel-only field; stripped before API save */
export type PanelCustomerDiscount = Api.MasterData.CustomerDiscount & {
  longTermEffective?: boolean
}

export interface BillingPolicyPanelState {
  discounts: PanelCustomerDiscount[]
  logisticsFeePerTrip?: number
  logisticsPolicyId?: number
  logisticsPriority?: number
  logisticsActive?: boolean
  logisticsTripSource?: 'delivery_date' | 'import'
  logisticsAllocationMode?: LogisticsAllocationMode
  logisticsBillingWeekdays?: number[]
  logisticsExcludeDepartments?: string
  logisticsCardDeductionEnabled?: boolean
  logisticsCardDeductMode?: 'auto' | 'none'
  logisticsCardMonthlyCap?: number
  logisticsMergeGroupId?: number
  logisticsMergeSameDay?: boolean
  logisticsMergeShareRatios?: Record<number, number>
  logisticsAllocationSyncToMembers?: boolean
  logisticsSingleOwnerCustomerId?: number
  logisticsAllocationGroupName?: string
  logisticsAllocationMemberIds?: number[]
  monthlyMinCharge?: number
  monthlyMaxCap?: number
  monthlyPolicyId?: number
  monthlyPriority?: number
  monthlyActive?: boolean
  urgentPolicyId?: number
  urgentActive?: boolean
  urgentBaseMultiplier?: number
  urgentAdjustedMultiplier?: number
  urgentLogisticsFeePerTrip?: number
  urgentLogisticsDiscountRate?: number
  urgentPriority?: number
  deductionPolicyId?: number
  deductionActive?: boolean
  deductionMonthlyAmount?: number
  deductionPriority?: number
}

export interface SettlementPreviewLine {
  key: string
  label: string
  value: string
  hint?: string
}

export function createEmptyBillingPolicyState(): BillingPolicyPanelState {
  return {
    discounts: [],
    logisticsFeePerTrip: undefined,
    logisticsPolicyId: undefined,
    logisticsPriority: 100,
    logisticsActive: true,
    logisticsTripSource: 'delivery_date',
    logisticsAllocationMode: 'none',
    logisticsBillingWeekdays: [],
    logisticsExcludeDepartments: '',
    logisticsCardDeductionEnabled: true,
    logisticsCardDeductMode: 'auto',
    logisticsCardMonthlyCap: undefined,
    logisticsMergeGroupId: undefined,
    logisticsMergeSameDay: true,
    logisticsMergeShareRatios: {},
    logisticsAllocationSyncToMembers: true,
    logisticsSingleOwnerCustomerId: undefined,
    logisticsAllocationGroupName: undefined,
    logisticsAllocationMemberIds: [],
    monthlyMinCharge: undefined,
    monthlyMaxCap: undefined,
    monthlyPolicyId: undefined,
    monthlyPriority: 100,
    monthlyActive: true,
    urgentPolicyId: undefined,
    urgentActive: true,
    urgentBaseMultiplier: 1.25,
    urgentAdjustedMultiplier: 1.025,
    urgentLogisticsFeePerTrip: 150,
    urgentLogisticsDiscountRate: 0.9,
    urgentPriority: 100,
    deductionPolicyId: undefined,
    deductionActive: true,
    deductionMonthlyAmount: undefined,
    deductionPriority: 100
  }
}

export function isGlobalDiscountTemperature(temperature?: string | null): boolean {
  return !temperature || temperature === 'ANY'
}

export function isGlobalDiscount(discount: Api.MasterData.CustomerDiscount): boolean {
  return isGlobalDiscountTemperature(discount.temperature)
}

export function isDiscountLongTermEffective(
  discount: Pick<PanelCustomerDiscount, 'longTermEffective' | 'effectiveFrom' | 'effectiveTo'>
): boolean {
  if (discount.longTermEffective != null) return discount.longTermEffective
  return !discount.effectiveFrom && !discount.effectiveTo
}

export function setDiscountLongTermEffective(
  discount: PanelCustomerDiscount,
  enabled: boolean
): void {
  discount.longTermEffective = enabled
  if (enabled) {
    discount.effectiveFrom = undefined
    discount.effectiveTo = undefined
  }
}

export function enrichDiscountForPanel(
  discount: Api.MasterData.CustomerDiscount
): PanelCustomerDiscount {
  return {
    ...discount,
    longTermEffective: isDiscountLongTermEffective(discount)
  }
}

export function normalizeDiscountForSave(
  discount: PanelCustomerDiscount
): Api.MasterData.CustomerDiscount {
  const result: Api.MasterData.CustomerDiscount = { ...discount }
  delete (result as PanelCustomerDiscount).longTermEffective
  if (isDiscountLongTermEffective(discount)) {
    result.effectiveFrom = undefined
    result.effectiveTo = undefined
  }
  return result
}

export function formatDiscountRate(rate?: number | null): string {
  if (rate == null) return '—'
  return `${(rate * 100).toFixed(0)}%`
}

export function formatTemperatureLabel(
  temperature: string | undefined | null,
  t: ComposerTranslation
): string {
  if (!temperature || temperature === 'ANY') {
    return t('menus.masterData.customerForm.discountTemperatureAny')
  }
  if (temperature === 'HT') {
    return t('menus.masterData.customerForm.discountTemperatureHt')
  }
  if (temperature === 'LT') {
    return t('menus.masterData.customerForm.discountTemperatureLt')
  }
  return temperature
}

export function formatDiscountSummary(
  discounts: Api.MasterData.CustomerDiscount[] | undefined,
  t: ComposerTranslation
): string | null {
  const active = (discounts ?? []).filter((d) => d.isActive !== false && d.discountRate != null)
  if (active.length === 0) return null
  return active
    .map((d) => {
      const temp = isGlobalDiscountTemperature(d.temperature)
        ? ''
        : `${formatTemperatureLabel(d.temperature, t).replace(/\s*\(.*\)/, '')} `
      return `${temp}${formatDiscountRate(d.discountRate)}`
    })
    .join(' / ')
}

export function formatLogisticsAllocationSummary(
  state: Pick<
    BillingPolicyPanelState,
    | 'logisticsAllocationMode'
    | 'logisticsBillingWeekdays'
    | 'logisticsExcludeDepartments'
    | 'logisticsMergeGroupId'
    | 'logisticsMergeSameDay'
    | 'logisticsMergeShareRatios'
    | 'logisticsAllocationSyncToMembers'
    | 'logisticsSingleOwnerCustomerId'
    | 'logisticsAllocationGroupName'
    | 'logisticsAllocationMemberIds'
  >,
  customerNameMap: Record<number, string>,
  t: ComposerTranslation
): string {
  return buildLogisticsAllocationSummary(configFromPanelState(state), customerNameMap, t)
}

export function getLogisticsAllocationConfig(state: BillingPolicyPanelState): LogisticsAllocationConfig {
  return configFromPanelState(state)
}

export function formatLogisticsSummary(feePerTrip?: number | null): string | null {
  if (feePerTrip == null || feePerTrip <= 0) return null
  return `¥${feePerTrip.toFixed(2)}/趟`
}

export function formatMonthlySummary(
  minCharge?: number | null,
  maxCap?: number | null,
  t: ComposerTranslation
): string | null {
  const parts: string[] = []
  if (minCharge != null && minCharge > 0) {
    parts.push(`${t('menus.masterData.customerBillingPolicy.minChargeShort')} ¥${minCharge}`)
  }
  if (maxCap != null && maxCap > 0) {
    parts.push(`${t('menus.masterData.customerBillingPolicy.maxCapShort')} ¥${maxCap}`)
  }
  return parts.length > 0 ? parts.join(' · ') : null
}

export function formatPolicySummary(
  state: Pick<
    BillingPolicyPanelState,
    'discounts' | 'logisticsFeePerTrip' | 'monthlyMinCharge' | 'monthlyMaxCap'
  >,
  t: ComposerTranslation
): string | null {
  const parts: string[] = []
  const discount = formatDiscountSummary(state.discounts, t)
  if (discount) parts.push(discount)
  const logistics = formatLogisticsSummary(state.logisticsFeePerTrip)
  if (logistics) parts.push(logistics)
  const monthly = formatMonthlySummary(state.monthlyMinCharge, state.monthlyMaxCap, t)
  if (monthly) parts.push(monthly)
  if (state.urgentActive !== false && state.urgentBaseMultiplier != null) {
    parts.push(
      `加急 ${(state.urgentBaseMultiplier * 100).toFixed(0)}%→${((state.urgentAdjustedMultiplier ?? 1.025) * 100).toFixed(1)}%`
    )
  }
  if (
    state.deductionActive !== false &&
    state.deductionMonthlyAmount != null &&
    state.deductionMonthlyAmount > 0
  ) {
    parts.push(`抵扣 ¥${state.deductionMonthlyAmount}`)
  }
  return parts.length > 0 ? parts.join(' · ') : null
}

export function buildSettlementPreviewLines(
  state: BillingPolicyPanelState,
  t: ComposerTranslation
): SettlementPreviewLine[] {
  const lines: SettlementPreviewLine[] = []

  const activeDiscounts = (state.discounts ?? []).filter(
    (d) => d.isActive !== false && d.discountRate != null
  )
  if (activeDiscounts.length === 0) {
    lines.push({
      key: 'discount-none',
      label: t('menus.masterData.customerBillingPolicy.settlementDiscount'),
      value: t('menus.masterData.customerBillingPolicy.settlementNone')
    })
  } else {
    activeDiscounts.forEach((d, idx) => {
      lines.push({
        key: `discount-${idx}`,
        label: d.name || t('menus.masterData.customerBillingPolicy.settlementDiscount'),
        value: `${formatTemperatureLabel(d.temperature, t)} · ${formatDiscountRate(d.discountRate)}`,
        hint: d.skipWhenFixedPrice
          ? t('menus.masterData.customerBillingPolicy.skipFixedPriceHint')
          : undefined
      })
    })
  }

  if (
    state.logisticsActive !== false &&
    state.logisticsFeePerTrip != null &&
    state.logisticsFeePerTrip > 0
  ) {
    lines.push({
      key: 'logistics',
      label: t('menus.masterData.customerBillingPolicy.settlementLogistics'),
      value: `¥${state.logisticsFeePerTrip.toFixed(2)} / ${t('menus.masterData.customerBillingPolicy.tripUnit')}`
    })
  }

  const hasMin = state.monthlyMinCharge != null && state.monthlyMinCharge > 0
  const hasMax = state.monthlyMaxCap != null && state.monthlyMaxCap > 0
  if (state.monthlyActive !== false && (hasMin || hasMax)) {
    lines.push({
      key: 'monthly',
      label: t('menus.masterData.customerBillingPolicy.settlementMonthly'),
      value: formatMonthlySummary(state.monthlyMinCharge, state.monthlyMaxCap, t) ?? '—',
      hint: t('menus.masterData.customerBillingPolicy.settlementMonthlyHint')
    })
  }

  if (state.urgentActive !== false && state.urgentBaseMultiplier != null) {
    lines.push({
      key: 'urgent',
      label: t('menus.masterData.customerBillingPolicy.settlementUrgent'),
      value: `${(state.urgentBaseMultiplier * 100).toFixed(0)}% → ${((state.urgentAdjustedMultiplier ?? 1.025) * 100).toFixed(1)}%`,
      hint: t('menus.masterData.customerBillingPolicy.settlementUrgentHint')
    })
  }

  if (
    state.deductionActive !== false &&
    state.deductionMonthlyAmount != null &&
    state.deductionMonthlyAmount > 0
  ) {
    lines.push({
      key: 'deduction',
      label: t('menus.masterData.customerBillingPolicy.settlementDeduction'),
      value: `-¥${state.deductionMonthlyAmount.toFixed(2)}`
    })
  }

  return lines
}

export function applyBillingPoliciesToState(
  state: BillingPolicyPanelState,
  policies: Api.MasterData.CustomerBillingPolicyRecord[]
): void {
  const logistics = policies.find((p) => (p.policy_type ?? p.policyType) === 'LOGISTICS')
  if (logistics) {
    state.logisticsPolicyId = logistics.id
    state.logisticsFeePerTrip = logistics.fee_per_trip ?? logistics.feePerTrip
    state.logisticsPriority = logistics.priority ?? 100
    state.logisticsActive = logistics.is_active ?? logistics.isActive ?? true
    state.logisticsTripSource = (logistics.trip_source ??
      logistics.tripSource ??
      'delivery_date') as 'delivery_date' | 'import'
    state.logisticsAllocationMode = (logistics.allocation_mode ??
      logistics.allocationMode ??
      'none') as LogisticsAllocationMode
    state.logisticsBillingWeekdays = logistics.billing_weekdays ?? logistics.billingWeekdays ?? []
    state.logisticsExcludeDepartments = (
      logistics.exclude_departments ??
      logistics.excludeDepartments ??
      []
    ).join('、')
    state.logisticsCardDeductionEnabled =
      logistics.card_deduction_enabled ?? logistics.cardDeductionEnabled ?? true
    state.logisticsCardDeductMode = (logistics.card_deduct_mode ??
      logistics.cardDeductMode ??
      'auto') as 'auto' | 'none'
    state.logisticsCardMonthlyCap = logistics.card_monthly_cap ?? logistics.cardMonthlyCap
    state.logisticsMergeGroupId =
      logistics.logistics_merge_group_id ?? logistics.logisticsMergeGroupId
    state.logisticsMergeSameDay = logistics.merge_same_day ?? logistics.mergeSameDay ?? true
    state.logisticsSingleOwnerCustomerId =
      logistics.single_owner_customer_id ?? logistics.singleOwnerCustomerId
    state.logisticsAllocationSyncToMembers = true
    state.logisticsAllocationMemberIds = []
  } else {
    state.logisticsPolicyId = undefined
    state.logisticsFeePerTrip = undefined
    state.logisticsPriority = 100
    state.logisticsActive = true
    state.logisticsTripSource = 'delivery_date'
    state.logisticsAllocationMode = 'none'
    state.logisticsBillingWeekdays = []
    state.logisticsExcludeDepartments = ''
    state.logisticsCardDeductionEnabled = true
    state.logisticsCardDeductMode = 'auto'
    state.logisticsCardMonthlyCap = undefined
    state.logisticsMergeGroupId = undefined
    state.logisticsMergeSameDay = true
    state.logisticsMergeShareRatios = {}
    state.logisticsAllocationSyncToMembers = true
    state.logisticsSingleOwnerCustomerId = undefined
    state.logisticsAllocationGroupName = undefined
    state.logisticsAllocationMemberIds = []
  }

  const monthly = policies.find((p) => (p.policy_type ?? p.policyType) === 'MONTHLY_SETTLEMENT')
  if (monthly) {
    state.monthlyPolicyId = monthly.id
    state.monthlyMinCharge = monthly.min_charge ?? monthly.minCharge
    state.monthlyMaxCap = monthly.max_cap ?? monthly.maxCap
    state.monthlyPriority = monthly.priority ?? 100
    state.monthlyActive = monthly.is_active ?? monthly.isActive ?? true
  } else {
    state.monthlyPolicyId = undefined
    state.monthlyMinCharge = undefined
    state.monthlyMaxCap = undefined
    state.monthlyPriority = 100
    state.monthlyActive = true
  }

  const urgent = policies.find((p) => (p.policy_type ?? p.policyType) === 'URGENT')
  if (urgent) {
    state.urgentPolicyId = urgent.id
    state.urgentBaseMultiplier = urgent.base_multiplier ?? urgent.baseMultiplier ?? 1.25
    state.urgentAdjustedMultiplier =
      urgent.adjusted_multiplier ?? urgent.adjustedMultiplier ?? 1.025
    state.urgentLogisticsFeePerTrip =
      urgent.urgent_logistics_fee_per_trip ?? urgent.urgentLogisticsFeePerTrip ?? 150
    state.urgentLogisticsDiscountRate =
      urgent.urgent_logistics_discount_rate ?? urgent.urgentLogisticsDiscountRate ?? 0.9
    state.urgentPriority = urgent.priority ?? 100
    state.urgentActive = urgent.is_active ?? urgent.isActive ?? true
  } else {
    state.urgentPolicyId = undefined
    state.urgentBaseMultiplier = 1.25
    state.urgentAdjustedMultiplier = 1.025
    state.urgentLogisticsFeePerTrip = 150
    state.urgentLogisticsDiscountRate = 0.9
    state.urgentPriority = 100
    state.urgentActive = true
  }

  const deduction = policies.find((p) => (p.policy_type ?? p.policyType) === 'DEDUCTION')
  if (deduction) {
    state.deductionPolicyId = deduction.id
    state.deductionMonthlyAmount = deduction.monthly_amount ?? deduction.monthlyAmount
    state.deductionPriority = deduction.priority ?? 100
    state.deductionActive = deduction.is_active ?? deduction.isActive ?? true
  } else {
    state.deductionPolicyId = undefined
    state.deductionMonthlyAmount = undefined
    state.deductionPriority = 100
    state.deductionActive = true
  }
}

export function createDefaultDiscount(): PanelCustomerDiscount {
  return {
    name: '默认折扣',
    discountRate: 0.7,
    temperature: 'ANY',
    applyStage: 'bill_detail',
    skipWhenFixedPrice: true,
    priority: 100,
    isActive: true,
    longTermEffective: true
  }
}
