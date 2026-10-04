const PRICE_TOLERANCE = 0.001

function toNumber(value: unknown): number | null {
  if (value == null || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/** 规则单价与原单价不一致。 */
export function hasUnitPriceMismatch(row: Record<string, unknown>): boolean {
  const unitPrice = toNumber(row.unitPrice ?? row.unit_price)
  const expectedUnitPrice = toNumber(row.expectedUnitPrice ?? row.expected_unit_price)
  if (unitPrice == null || expectedUnitPrice == null) return false
  return Math.abs(unitPrice - expectedUnitPrice) > PRICE_TOLERANCE
}

/** 按规则单价推算的修正总价。 */
export function expectedCorrectedTotal(row: Record<string, unknown>): number | null {
  const expectedUnitPrice = toNumber(row.expectedUnitPrice ?? row.expected_unit_price)
  if (expectedUnitPrice == null) return null
  const packCount = Math.max(1, toNumber(row.packCount ?? row.pack_count) ?? 1)
  return Math.round(expectedUnitPrice * packCount * 100) / 100
}

/**
 * 规则价已算出但未落库到修正总价（或修正总价仍等于原价）。
 * 导出阶段可能仍按原价出账。
 */
export function isRulePriceNotPersisted(row: Record<string, unknown>): boolean {
  if (!hasUnitPriceMismatch(row)) return false
  const expectedTotal = expectedCorrectedTotal(row)
  if (expectedTotal == null) return false

  const correctedTotalPrice = toNumber(row.correctedTotalPrice ?? row.corrected_total_price)
  const totalPrice = toNumber(row.totalPrice ?? row.total_price)

  if (correctedTotalPrice == null) return true
  if (totalPrice != null && Math.abs(correctedTotalPrice - totalPrice) <= PRICE_TOLERANCE) {
    return true
  }
  return Math.abs(correctedTotalPrice - expectedTotal) > PRICE_TOLERANCE
}

export function countRulePriceNotPersisted(rows: Record<string, unknown>[]): number {
  return rows.reduce((count, row) => count + (isRulePriceNotPersisted(row) ? 1 : 0), 0)
}

/** 单行修正：用规则单价补全修正总价与差额。 */
export function applyRulePriceToRow(row: Record<string, unknown>): boolean {
  const expectedTotal = expectedCorrectedTotal(row)
  if (expectedTotal == null) return false

  row.correctedTotalPrice = expectedTotal
  const totalPrice = toNumber(row.totalPrice ?? row.total_price)
  if (totalPrice != null) {
    row.difference = Math.round((expectedTotal - totalPrice) * 100) / 100
  }
  const expectedUnit = toNumber(row.expectedUnitPrice ?? row.expected_unit_price)
  if (expectedUnit != null) {
    row.status = Math.abs((row.difference as number) ?? 0) > PRICE_TOLERANCE ? 'corrected' : 'unchanged'
  }
  return true
}
