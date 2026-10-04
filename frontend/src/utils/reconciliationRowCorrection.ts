import { applyRulePriceToRow } from '@/utils/reconciliationPricePersistence'

/** 单行「修正」：按规则单价补全修正总价，标为已修正并重算差额 */
export function applySingleRowCorrection(row: Record<string, unknown>): void {
  applyRulePriceToRow(row)
  const corrected = row['correctedTotalPrice'] as number | null
  const totalPrice = row['totalPrice'] as number | null
  if (corrected != null && totalPrice != null) {
    row['difference'] = Math.round((corrected - totalPrice) * 100) / 100
  }
  row['status'] = 'corrected'
}
