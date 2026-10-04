import { describe, expect, it } from 'vitest'
import {
  applyRulePriceToRow,
  isRulePriceNotPersisted,
  countRulePriceNotPersisted
} from './reconciliationPricePersistence'

describe('reconciliationPricePersistence', () => {
  it('detects unpersisted rule price when corrected total is null', () => {
    const row = {
      unitPrice: 88,
      expectedUnitPrice: 40,
      packCount: 1,
      totalPrice: 88,
      correctedTotalPrice: null
    }
    expect(isRulePriceNotPersisted(row)).toBe(true)
  })

  it('detects unpersisted when corrected still equals original total', () => {
    const row = {
      unitPrice: 88,
      expectedUnitPrice: 40,
      packCount: 1,
      totalPrice: 88,
      correctedTotalPrice: 88
    }
    expect(isRulePriceNotPersisted(row)).toBe(true)
  })

  it('passes when corrected matches rule total', () => {
    const row = {
      unitPrice: 88,
      expectedUnitPrice: 40,
      packCount: 1,
      totalPrice: 88,
      correctedTotalPrice: 40,
      difference: -48
    }
    expect(isRulePriceNotPersisted(row)).toBe(false)
  })

  it('applyRulePriceToRow writes corrected total from expected unit', () => {
    const row: Record<string, unknown> = {
      unitPrice: 88,
      expectedUnitPrice: 40,
      packCount: 2,
      totalPrice: 176,
      correctedTotalPrice: null
    }
    expect(applyRulePriceToRow(row)).toBe(true)
    expect(row.correctedTotalPrice).toBe(80)
    expect(row.difference).toBe(-96)
    expect(countRulePriceNotPersisted([row])).toBe(0)
  })
})
