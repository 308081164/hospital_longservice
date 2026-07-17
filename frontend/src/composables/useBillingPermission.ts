import { computed } from 'vue'
import { useUserStore } from '@/store/modules/user'

export type BillingPersona = 'configurator' | 'operator' | 'auditor'

const CONFIGURATOR_ROLES = [
  'billing_configurator',
  'R_BILLING_CONFIG',
  'R_SUPER',
  'R_ADMIN',
  'admin'
]
const OPERATOR_ROLES = ['billing_operator', 'R_BILLING_OPERATOR', 'R_USER', 'user']
const AUDITOR_ROLES = [
  'billing_auditor',
  'R_BILLING_REVIEWER',
  'billing_configurator',
  'R_BILLING_CONFIG',
  'R_SUPER',
  'R_ADMIN'
]

function hasAnyRole(roles: string[], candidates: string[]): boolean {
  return candidates.some((role) => roles.includes(role))
}

/**
 * NFR-04: 账单系统角色视图 — 配置员 / 业务员 / 审核员
 */
export function useBillingPermission() {
  const userStore = useUserStore()

  const roles = computed(() => userStore.info?.roles ?? [])
  const isSuperUser = computed(
    () =>
      Boolean(userStore.info?.is_superuser) ||
      hasAnyRole(roles.value, ['R_SUPER', 'R_ADMIN', 'admin'])
  )

  const isConfigurator = computed(
    () => isSuperUser.value || hasAnyRole(roles.value, CONFIGURATOR_ROLES)
  )
  const isOperator = computed(() => isConfigurator.value || hasAnyRole(roles.value, OPERATOR_ROLES))
  const isAuditor = computed(() => isSuperUser.value || hasAnyRole(roles.value, AUDITOR_ROLES))

  const persona = computed<BillingPersona>(() => {
    if (isConfigurator.value) return 'configurator'
    if (isAuditor.value && !isOperator.value) return 'auditor'
    return 'operator'
  })

  const canEditCustomerConfig = computed(() => isConfigurator.value)
  const canImportRules = computed(() => isConfigurator.value)
  const canRunReconciliation = computed(() => isOperator.value)
  const canEditReconciliationRows = computed(() => isOperator.value)
  const canReviewReconciliation = computed(() => isAuditor.value || isConfigurator.value)
  const canExport = computed(() => isOperator.value)
  const isReadOnlyConfig = computed(() => !isConfigurator.value)

  return {
    roles,
    persona,
    isConfigurator,
    isOperator,
    isAuditor,
    canEditCustomerConfig,
    canImportRules,
    canRunReconciliation,
    canEditReconciliationRows,
    canReviewReconciliation,
    canExport,
    isReadOnlyConfig
  }
}
