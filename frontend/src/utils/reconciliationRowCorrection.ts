/** 单行「修正」：将状态标为已修正并重算差额 */
export function applySingleRowCorrection(row: Record<string, unknown>): void {
  const corrected = row['correctedTotalPrice'] as number | null
  const totalPrice = row['totalPrice'] as number | null
  if (corrected != null && totalPrice != null) {
    row['difference'] = Math.round((corrected - totalPrice) * 100) / 100
  }
  row['status'] = 'corrected'
}
