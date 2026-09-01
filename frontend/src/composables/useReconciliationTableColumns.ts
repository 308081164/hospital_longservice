import { useI18n } from 'vue-i18n'

export function formatReconciliationNumber(value: number | null | undefined): string {
  if (value == null) return '-'
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function formatReconciliationSignedNumber(value: number | null | undefined): string {
  if (value == null) return '-'
  const abs = formatReconciliationNumber(Math.abs(value))
  if (value > 0) return `+${abs}`
  if (value < 0) return `-${abs}`
  return abs
}

export const RECONCILIATION_STATUS_LABELS: Record<string, string> = {
  corrected: '已修正',
  unchanged: '无需修改',
  skipped: '已跳过',
  warning: '人工复核'
}

export function reconciliationStatusTagType(
  status: string
): 'primary' | 'success' | 'info' | 'warning' {
  switch (status) {
    case 'corrected':
      return 'primary'
    case 'unchanged':
      return 'success'
    case 'skipped':
      return 'info'
    case 'warning':
      return 'warning'
    default:
      return 'info'
  }
}

export function reconciliationStatusTagClass(status: string): string {
  switch (status) {
    case 'corrected':
      return 'detail-cell-tag-primary'
    case 'unchanged':
      return 'detail-cell-tag-success'
    case 'skipped':
      return 'detail-cell-tag-info'
    case 'warning':
      return 'detail-cell-tag-warning'
    default:
      return 'detail-cell-tag-info'
  }
}

export function useReconciliationTableColumns() {
  const { t } = useI18n()

  const columnLabel = (key: string) => t(`reconciliation.columns.${key}`)

  return {
    columnLabel,
    formatNumber: formatReconciliationNumber,
    formatSignedNumber: formatReconciliationSignedNumber,
    statusLabels: RECONCILIATION_STATUS_LABELS,
    statusTagType: reconciliationStatusTagType,
    statusTagClass: reconciliationStatusTagClass
  }
}
