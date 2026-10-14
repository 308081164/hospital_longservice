import * as XLSX from 'xlsx'
import {
  isHospitalSummaryRow,
  isInlineDepartmentMarkerRow,
  readHospitalWorkbook
} from './reconciliationWorkbookParse.ts'

function assertEqual(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`)
  }
}

function assertTrue(value: boolean, message: string) {
  if (!value) throw new Error(message)
}

assertTrue(isHospitalSummaryRow('哈尔滨红十字妇产医院'), 'institution row is a hospital summary')
assertTrue(!isHospitalSummaryRow('美容科（朝鲜医院）'), 'parenthetical hospital is a department')
assertTrue(!isHospitalSummaryRow('生殖中心'), 'short center is a department')
assertTrue(
  isInlineDepartmentMarkerRow('美容科（朝鲜医院）', '', '', ''),
  '美容科（朝鲜医院） is a department marker'
)
assertTrue(isInlineDepartmentMarkerRow('生殖中心', '', '', ''), '生殖中心 is a department marker')
assertTrue(
  !isInlineDepartmentMarkerRow('哈尔滨红十字妇产医院', '', '', ''),
  'hospital summary is not a department'
)

function cell(rows: unknown[][], row: number, col: number, value: unknown) {
  while (rows.length <= row) rows.push([])
  const line = rows[row] as unknown[]
  while (line.length <= col) line.push('')
  line[col] = value
}

const grid: unknown[][] = []
cell(grid, 0, 1, '发货单汇总表-显示包装材料')
cell(grid, 3, 1, '从:2026/5/1 至: 2026/10/13')
cell(grid, 7, 8, '包数合计:21854')
cell(grid, 8, 12, '处理费用合计:1')
const headers = [
  '发货日期',
  '',
  '发货单号',
  '基本类型',
  '类型',
  '',
  '包名',
  '',
  '',
  '包装材料',
  '包数',
  '',
  '',
  '器械数',
  '单价',
  '总价'
]
headers.forEach((header, index) => cell(grid, 11, index, header))
cell(grid, 12, 0, '哈尔滨红十字妇产医院')
cell(grid, 13, 0, 'ICU病房')
cell(grid, 14, 0, '2026-05-07')
cell(grid, 14, 2, '1600711')
cell(grid, 14, 3, '额外包')
cell(grid, 14, 4, '额外包(ETO)')
cell(grid, 14, 6, '湿化瓶-1/Z3032')
cell(grid, 14, 9, '高温纸塑袋300*320')
cell(grid, 14, 10, 1)
cell(grid, 14, 13, 1)
cell(grid, 14, 14, 22)
cell(grid, 15, 0, '美容科（朝鲜医院）')
cell(grid, 16, 0, '2026-05-08')
cell(grid, 16, 2, '1600712')
cell(grid, 16, 3, '额外包')
cell(grid, 16, 4, '额外包(ETO)')
cell(grid, 16, 6, '美容包-1')
cell(grid, 16, 9, '高温纸塑袋300*320')
cell(grid, 16, 10, 1)
cell(grid, 16, 13, 1)
cell(grid, 17, 0, '生殖中心')
cell(grid, 18, 0, '2026-05-09')
cell(grid, 18, 2, '1600713')
cell(grid, 18, 3, '额外包')
cell(grid, 18, 4, '额外包(ETO)')
cell(grid, 18, 6, '取卵针-1')
cell(grid, 18, 9, '高温纸塑袋300*320')
cell(grid, 18, 10, 1)
cell(grid, 18, 13, 1)

const sheet = XLSX.utils.aoa_to_sheet(grid)
const book = XLSX.utils.book_new()
XLSX.utils.book_append_sheet(book, sheet, 'DeliveryNoteSummaryWithPackingM')
const bytes = XLSX.write(book, { type: 'array', bookType: 'xlsx' }) as ArrayBuffer
const file = new File([bytes], '红十字.xlsx')
const workbook = await readHospitalWorkbook(file, {
  cleaning: {
    removeFirstRow: false,
    dropSummaryRows: true,
    summaryKeywords: ['合计', '小计', '总计'],
    trimPackagingMaterial: true,
    clearInstrumentColumnFormatting: false
  }
})

assertEqual(
  workbook.sheetMetas[0]?.hospitalDisplayName,
  '哈尔滨红十字妇产医院',
  'hospital name is not 额外包'
)
const previewNames = workbook.previews.map((item) => item.name)
assertTrue(previewNames.includes('ICU病房'), `previews ${previewNames.join('|')}`)
assertTrue(previewNames.includes('美容科（朝鲜医院）'), `previews ${previewNames.join('|')}`)
assertTrue(previewNames.includes('生殖中心'), `previews ${previewNames.join('|')}`)
assertTrue(
  !previewNames.includes('哈尔滨红十字妇产医院'),
  'hospital summary is not a department tab'
)
assertTrue(
  !previewNames.includes('DeliveryNoteSummaryWithPackingM'),
  'english sheet name is replaced by departments'
)

const wet = workbook.rows.find((row) => row.packName === '湿化瓶-1/Z3032')
assertTrue(Boolean(wet), 'missing humidifier row')
assertEqual(wet!.sheetName, 'ICU病房', 'humidifier stays in ICU')
assertEqual(
  workbook.rows.find((row) => row.packName === '美容包-1')?.sheetName,
  '美容科（朝鲜医院）',
  'beauty department rows'
)
assertEqual(
  workbook.rows.find((row) => row.packName === '取卵针-1')?.sheetName,
  '生殖中心',
  'reproduction center rows'
)

console.log('reconciliation red cross sheet tests passed')
