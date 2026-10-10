import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { readHospitalWorkbook } from './reconciliationWorkbookParse.ts'

const here = path.dirname(fileURLToPath(import.meta.url))
const fixture = path.resolve(
  here,
  '../../../测试用例/fixtures/DeliveryNoteSummaryWithPackingMaterial_golden.xlsx'
)

function assertTrue(value: boolean, message: string) {
  if (!value) throw new Error(message)
}

const bytes = fs.readFileSync(fixture)
const file = new File([bytes], 'DeliveryNoteSummaryWithPackingMeterial (2).xlsx')
const workbook = await readHospitalWorkbook(file, {
  cleaning: {
    removeFirstRow: false,
    dropSummaryRows: true,
    summaryKeywords: ['合计', '小计', '总计'],
    trimPackagingMaterial: true,
    clearInstrumentColumnFormatting: false
  }
})

assertTrue(workbook.rows.length > 100, `expected detail rows, got ${workbook.rows.length}`)
assertTrue(
  workbook.sheetMetas.some((meta) => meta.hospitalDisplayName.includes('红十字妇产医院')),
  `expected hospital name from column A, got ${workbook.sheetMetas.map((m) => m.hospitalDisplayName).join('|')}`
)

const wet = workbook.rows.find((row) => row.packName === '湿化瓶-1/Z3032')
assertTrue(Boolean(wet), 'missing 湿化瓶-1/Z3032')
assertTrue(wet!.instrumentCount === 2, `instrumentCount ${wet!.instrumentCount}`)
assertTrue(String(wet!.packageMaterial).includes('高温纸塑袋'), `material ${wet!.packageMaterial}`)
assertTrue(wet!.deliveryDate.startsWith('2026-'), `date ${wet!.deliveryDate}`)

console.log(
  JSON.stringify({
    rows: workbook.rows.length,
    hospital: workbook.sheetMetas[0]?.hospitalDisplayName,
    sample: {
      packName: wet!.packName,
      instrumentCount: wet!.instrumentCount,
      packageMaterial: wet!.packageMaterial,
      deliveryDate: wet!.deliveryDate,
      unitPrice: wet!.unitPrice
    }
  })
)
