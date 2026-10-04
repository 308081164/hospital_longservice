import { ElMessageBox } from 'element-plus'

export interface ExportPricePreflightOptions {
  t: (key: string, params?: Record<string, unknown>) => string
  unpersistedRulePriceRows?: number
  hasLocalUnsavedChanges?: boolean
}

/** 导出前：规则价未落库 / 本地未保存修改 提醒 */
export async function runExportPricePreflight(
  options: ExportPricePreflightOptions
): Promise<boolean> {
  const driftCount = options.unpersistedRulePriceRows ?? 0
  const hasLocal = options.hasLocalUnsavedChanges === true

  if (driftCount <= 0 && !hasLocal) {
    return true
  }

  const parts: string[] = []
  if (driftCount > 0) {
    parts.push(
      options.t('reconciliation.exportPreflight.priceDriftMessage', { count: driftCount })
    )
  }
  if (hasLocal) {
    parts.push(options.t('reconciliation.exportPreflight.localUnsavedMessage'))
  }
  parts.push(options.t('reconciliation.exportPreflight.priceDriftConsequence'))

  try {
    await ElMessageBox.confirm(parts.join('\n\n'), options.t('reconciliation.exportPreflight.priceDriftTitle'), {
      type: 'warning',
      confirmButtonText: options.t('reconciliation.exportPreflight.proceedExport'),
      cancelButtonText: options.t('common.cancel'),
      distinguishCancelAndClose: true
    })
    return true
  } catch {
    return false
  }
}
