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

  it('totals from the unrounded unit before the displayed cents', () => {
    const threePacks = {
      unitPrice: 21.83,
      expectedUnitPrice: 21.83,
      packCount: 3,
      totalPrice: 65.48,
      billingNotes: { unroundedUnitPrice: 21.828 }
    }
    expect(applyRulePriceToRow({ ...threePacks })).toBe(true)
    const three = { ...threePacks }
    applyRulePriceToRow(three)
    expect(three.correctedTotalPrice).toBe(65.48)
    expect(three.difference).toBe(0)

    const four = {
      unitPrice: 21.83,
      expectedUnitPrice: 21.83,
      packCount: 4,
      totalPrice: 87.31,
      billingNotes: { unroundedUnitPrice: 21.828 }
    }
    applyRulePriceToRow(four)
    expect(four.correctedTotalPrice).toBe(87.31)
    expect(four.difference).toBe(0)
  })

  it('keeps exact-cent unit times pack count when no unrounded price is stored', () => {
    const row: Record<string, unknown> = {
      unitPrice: 16.5,
      expectedUnitPrice: 16.5,
      packCount: 3,
      totalPrice: 49.5,
      correctedTotalPrice: null
    }
    applyRulePriceToRow(row)
    expect(row.correctedTotalPrice).toBe(49.5)
    expect(row.difference).toBe(0)
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
