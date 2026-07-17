import type { ComposerTranslation } from 'vue-i18n'

/** Unified logistics allocation modes (customer group + policy JSON). */
export type LogisticsAllocationMode =
  | 'none'
  | 'dept_ratio'
  | 'equal'
  | 'proportional'
  | 'single_owner'
  | 'cross_hospital_merge'

export interface LogisticsAllocationConfig {
  mode: LogisticsAllocationMode
  groupId?: number
  groupName?: string
  memberCustomerIds: number[]
  shareRatios: Record<number, number>
  mergeSameDay: boolean
  syncToMembers: boolean
  billingWeekdays: number[]
  excludeDepartments: string
  singleOwnerCustomerId?: number
}

export function createDefaultLogisticsAllocationConfig(
  customerId?: number | null
): LogisticsAllocationConfig {
  return {
    mode: 'none',
    memberCustomerIds: customerId ? [customerId] : [],
    shareRatios: {},
    mergeSameDay: true,
    syncToMembers: true,
    billingWeekdays: [],
    excludeDepartments: ''
  }
}

export function isCrossHospitalMode(mode: LogisticsAllocationMode): boolean {
  return ['equal', 'proportional', 'single_owner', 'cross_hospital_merge'].includes(mode)
}

export function allocationModeLabel(
  mode: LogisticsAllocationMode,
  t: ComposerTranslation
): string {
  const key = `menus.masterData.customerBillingPolicy.allocationMode_${mode}`
  const translated = t(key)
  return translated === key ? mode : translated
}

export function buildLogisticsAllocationSummary(
  config: LogisticsAllocationConfig,
  customerNameMap: Record<number, string>,
  t: ComposerTranslation
): string {
  if (config.mode === 'none') {
    return t('menus.masterData.customerBillingPolicy.allocationSummaryNone')
  }

  const parts: string[] = [allocationModeLabel(config.mode, t)]

  if (isCrossHospitalMode(config.mode) && config.memberCustomerIds.length > 0) {
    const names = config.memberCustomerIds
      .map((id) => customerNameMap[id] ?? `#${id}`)
      .slice(0, 3)
    const suffix =
      config.memberCustomerIds.length > 3
        ? ` +${config.memberCustomerIds.length - 3}`
        : ''
    parts.push(`${t('menus.masterData.customerBillingPolicy.allocationSummaryGroup')}: ${names.join('、')}${suffix}`)
  }

  if (config.mode === 'single_owner' && config.singleOwnerCustomerId) {
    const owner = customerNameMap[config.singleOwnerCustomerId] ?? `#${config.singleOwnerCustomerId}`
    parts.push(`${t('menus.masterData.customerBillingPolicy.allocationSummaryOwner')}: ${owner}`)
  }

  if (config.mode === 'dept_ratio' && config.excludeDepartments.trim()) {
    parts.push(
      `${t('menus.masterData.customerBillingPolicy.excludeDepartments')}: ${config.excludeDepartments.trim()}`
    )
  }

  if (isCrossHospitalMode(config.mode) && config.mergeSameDay) {
    parts.push(t('menus.masterData.customerBillingPolicy.mergeSameDay'))
  }

  return parts.join(' · ')
}

export function configFromPanelState(state: {
  logisticsAllocationMode?: LogisticsAllocationMode | 'none' | 'dept_ratio'
  logisticsBillingWeekdays?: number[]
  logisticsExcludeDepartments?: string
  logisticsMergeGroupId?: number
  logisticsMergeSameDay?: boolean
  logisticsMergeShareRatios?: Record<number, number>
  logisticsAllocationSyncToMembers?: boolean
  logisticsSingleOwnerCustomerId?: number
  logisticsAllocationGroupName?: string
  logisticsAllocationMemberIds?: number[]
}): LogisticsAllocationConfig {
  const legacyMode = state.logisticsAllocationMode ?? 'none'
  let mode: LogisticsAllocationMode = legacyMode as LogisticsAllocationMode
  if (state.logisticsMergeGroupId && legacyMode === 'none') {
    mode = 'cross_hospital_merge'
  }

  const memberIds = state.logisticsAllocationMemberIds?.length
    ? [...state.logisticsAllocationMemberIds]
    : Object.keys(state.logisticsMergeShareRatios ?? {}).map(Number)

  return {
    mode,
    groupId: state.logisticsMergeGroupId,
    groupName: state.logisticsAllocationGroupName,
    memberCustomerIds: memberIds,
    shareRatios: { ...(state.logisticsMergeShareRatios ?? {}) },
    mergeSameDay: state.logisticsMergeSameDay ?? true,
    syncToMembers: state.logisticsAllocationSyncToMembers ?? true,
    billingWeekdays: state.logisticsBillingWeekdays ?? [],
    excludeDepartments: state.logisticsExcludeDepartments ?? '',
    singleOwnerCustomerId: state.logisticsSingleOwnerCustomerId
  }
}

export function applyConfigToPanelState(
  config: LogisticsAllocationConfig,
  state: {
    logisticsAllocationMode?: LogisticsAllocationMode
    logisticsBillingWeekdays?: number[]
    logisticsExcludeDepartments?: string
    logisticsMergeGroupId?: number
    logisticsMergeSameDay?: boolean
    logisticsMergeShareRatios?: Record<number, number>
    logisticsAllocationSyncToMembers?: boolean
    logisticsSingleOwnerCustomerId?: number
    logisticsAllocationGroupName?: string
    logisticsAllocationMemberIds?: number[]
  }
): void {
  state.logisticsAllocationMode = config.mode
  state.logisticsBillingWeekdays = [...config.billingWeekdays]
  state.logisticsExcludeDepartments = config.excludeDepartments
  state.logisticsMergeGroupId = config.groupId
  state.logisticsMergeSameDay = config.mergeSameDay
  state.logisticsMergeShareRatios = { ...config.shareRatios }
  state.logisticsAllocationSyncToMembers = config.syncToMembers
  state.logisticsSingleOwnerCustomerId = config.singleOwnerCustomerId
  state.logisticsAllocationGroupName = config.groupName
  state.logisticsAllocationMemberIds = [...config.memberCustomerIds]
}

export function toAllocationConfigPayload(config: LogisticsAllocationConfig) {
  return {
    allocationMode: config.mode,
    groupName: config.groupName,
    memberCustomerIds: config.memberCustomerIds,
    shareRatios: config.shareRatios,
    mergeSameDay: config.mergeSameDay,
    syncToMembers: config.syncToMembers,
    billingWeekdays: config.billingWeekdays.length ? config.billingWeekdays : undefined,
    excludeDepartments: config.excludeDepartments
      ? config.excludeDepartments
          .split(/[、,，]/)
          .map((s) => s.trim())
          .filter(Boolean)
      : undefined,
    singleOwnerCustomerId: config.singleOwnerCustomerId
  }
}
