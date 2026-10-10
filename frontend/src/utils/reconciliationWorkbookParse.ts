import * as XLSX from 'xlsx'
import {
  extractStandardHospitalNameFromMatrix,
  resolveHospitalNameFromColumnASummary
} from './reconciliationHospitalName'

/** 对账页上传预览使用的清洗开关（与计费规则 cleaning 同构）。 */
export interface ReconciliationWorkbookCleaningRules {
  removeFirstRow: boolean
  dropSummaryRows: boolean
  summaryKeywords: string[]
  trimPackagingMaterial: boolean
  clearInstrumentColumnFormatting: boolean
}

export interface ReconciliationWorkbookParseRules {
  cleaning: ReconciliationWorkbookCleaningRules
}

export interface SheetTemplateMeta {
  sheetName: string
  titleText: string
  dateRangeText: string
  hospitalDisplayName: string
}

export interface SheetPreview {
  name: string
  totalRows: number
  dataRows: number
  headerRowIndex: number
}

export interface HospitalRow {
  sheetName: string
  rowNumber: number
  deliveryDateRaw: string | number | null
  deliveryDate: string
  orderNo: string
  type: string
  categoryNo: string
  packName: string
  packageMaterial: string
  packCount: number
  instrumentCount: number
  unitPrice: number | null
  totalPrice: number | null
  original: Record<string, unknown>
}

export interface RawWorkbook {
  fileName: string
  sheetNames: string[]
  previews: SheetPreview[]
  sheetMetas: SheetTemplateMeta[]
  rows: HospitalRow[]
}

export const NO_DETAIL_ROWS_MESSAGE = '没有识别到有效明细行，请确认 Excel 格式与示例一致。'

export function isExcelDateNumber(value: unknown): boolean {
  return typeof value === 'number' && value > 40000 && value < 60000
}

/** 表头归一化：去掉换行/空格。「器械\\n数量」「器械数量」等价于「器械数」。 */
export function canonicalHeaderKey(value: unknown): string {
  const key = String(value ?? '')
    .replace(/\s+/g, '')
    .trim()
  if (key === '器械数量' || key === '单包内器械数量/把') return '器械数'
  return key
}

export function findHeaderRowIndex(matrix: unknown[][]): number {
  return matrix.findIndex((row) => {
    const normalized = row.map((cell) => canonicalHeaderKey(cell))
    return (
      normalized.includes('发货日期') &&
      normalized.includes('包名') &&
      normalized.includes('包装材料') &&
      normalized.includes('器械数') &&
      normalized.includes('单价') &&
      normalized.includes('总价')
    )
  })
}

export function createHeaderMap(headerRow: unknown[]): Map<string, number> {
  const map = new Map<string, number>()
  headerRow.forEach((cell, index) => {
    const key = canonicalHeaderKey(cell)
    if (key && !map.has(key)) map.set(key, index)
  })
  return map
}

function sanitizeCellText(value: unknown, normalizeFormatting = false): string {
  const text = String(value ?? '').trim()
  return normalizeFormatting
    ? text
        .replace(/[\r\n\t]+/g, ' ')
        .replace(/[：:]\s*$/g, '')
        .replace(/\s+/g, ' ')
        .trim()
    : text
}

function toNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  const normalized = String(value ?? '')
    .replace(/,/g, '')
    .replace(/￥/g, '')
    .trim()
  if (!normalized) return null
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : null
}

function excelDateParser() {
  const ns = XLSX as typeof XLSX & { default?: typeof XLSX }
  return ns.SSF ?? ns.default?.SSF
}

function formatExcelDate(value: unknown): string {
  if (isExcelDateNumber(value)) {
    const parsed = excelDateParser()?.parse_date_code(value as number)
    if (!parsed) return String(value)
    return `${parsed.y}-${String(parsed.m).padStart(2, '0')}-${String(parsed.d).padStart(2, '0')}`
  }
  return String(value ?? '').trim()
}

function getCell(row: unknown[], headerMap: Map<string, number>, headerName: string): unknown {
  const index = headerMap.get(canonicalHeaderKey(headerName))
  return index === undefined ? null : (row[index] ?? null)
}

function findRowText(rows: unknown[][], keyword: string): string {
  for (const row of rows) {
    for (const cell of row) {
      const text = String(cell ?? '').trim()
      if (text && text.includes(keyword)) return text
    }
  }
  return ''
}

function findDateRangeText(rows: unknown[][]): string {
  const prefixes = ['从:', '从：', '时间:', '时间：', '日期:', '日期：']
  for (const prefix of prefixes) {
    const found = findRowText(rows, prefix)
    if (found) return found
  }
  for (const row of rows) {
    for (const cell of row) {
      const text = String(cell ?? '').trim()
      if (text && /\d{4}.*(?:至|到).*\d{4}/.test(text)) return text
    }
  }
  return ''
}

function resolveSheetHospitalDisplayName(matrix: unknown[][], headerRowIndex: number): string {
  return (
    extractStandardHospitalNameFromMatrix(matrix, headerRowIndex) ||
    resolveHospitalNameFromColumnASummary(matrix, headerRowIndex)
  )
}

function extractSheetTemplateMeta(
  sheetName: string,
  matrix: unknown[][],
  headerRowIndex: number
): SheetTemplateMeta {
  const titleText =
    findRowText(matrix.slice(0, headerRowIndex), '发货单汇总表') || '发货单汇总表-显示包装材料'
  const dateRangeText = findDateRangeText(matrix.slice(0, headerRowIndex))
  const hospitalDisplayName = resolveSheetHospitalDisplayName(matrix, headerRowIndex)
  return { sheetName, titleText, dateRangeText, hospitalDisplayName }
}

function isDetailRow(
  row: {
    deliveryDateRaw: unknown
    orderNo: string
    type: string
    packName: string
    packageMaterial: string
  },
  rules: ReconciliationWorkbookParseRules
): boolean {
  const combinedText = [row.orderNo, row.type, row.packName, row.packageMaterial].join(' ')
  const hasDate =
    isExcelDateNumber(row.deliveryDateRaw) ||
    /\d{4}[/-]\d{1,2}[/-]\d{1,2}/.test(String(row.deliveryDateRaw ?? ''))
  const hasKeyFields = Boolean(row.type && row.packName)
  const looksLikeSummary =
    rules.cleaning.dropSummaryRows &&
    rules.cleaning.summaryKeywords.some((kw) => combinedText.includes(kw))
  const looksInvalid = !row.type || !row.packName
  return hasDate && hasKeyFields && !looksLikeSummary && !looksInvalid
}

function extractHospitalRows(
  sheetName: string,
  matrix: unknown[][],
  headerRowIndex: number,
  headerMap: Map<string, number>,
  rules: ReconciliationWorkbookParseRules
): HospitalRow[] {
  const rows: HospitalRow[] = []
  for (let i = headerRowIndex + 1; i < matrix.length; i += 1) {
    const row = matrix[i] ?? []
    const deliveryDateRaw = getCell(row, headerMap, '发货日期') as string | number | null
    const orderNo = sanitizeCellText(getCell(row, headerMap, '发货单号'))
    const type = sanitizeCellText(getCell(row, headerMap, '类型'))
    const categoryNo = sanitizeCellText(getCell(row, headerMap, '包类别号'))
    const packName = sanitizeCellText(
      getCell(row, headerMap, '包名'),
      rules.cleaning.clearInstrumentColumnFormatting
    )
    const packageMaterial = sanitizeCellText(
      getCell(row, headerMap, '包装材料'),
      rules.cleaning.trimPackagingMaterial || rules.cleaning.clearInstrumentColumnFormatting
    )
    const packCount = toNumber(getCell(row, headerMap, '包数')) ?? 0
    const instrumentCount = toNumber(getCell(row, headerMap, '器械数')) ?? 0
    const unitPrice = toNumber(getCell(row, headerMap, '单价'))
    const totalPrice = toNumber(getCell(row, headerMap, '总价'))

    if (!isDetailRow({ deliveryDateRaw, orderNo, type, packName, packageMaterial }, rules)) continue

    rows.push({
      sheetName,
      rowNumber: i + 1,
      deliveryDateRaw,
      deliveryDate: formatExcelDate(deliveryDateRaw),
      orderNo,
      type,
      categoryNo,
      packName,
      packageMaterial,
      packCount,
      instrumentCount,
      unitPrice,
      totalPrice,
      original: {
        deliveryDateRaw,
        orderNo,
        type,
        categoryNo,
        packName,
        packageMaterial,
        packCount,
        instrumentCount,
        unitPrice,
        totalPrice
      }
    })
  }
  return rows
}

/**
 * 对账上传条目的客户端预览解析。与页面 `reparseEntry` 使用同一实现。
 * 表头「器械\\n数量」会归一成「器械数」，否则整表会被当成没有明细行。
 */
export async function readHospitalWorkbook(
  file: Blob & { name?: string },
  rules: ReconciliationWorkbookParseRules
): Promise<RawWorkbook> {
  const buffer = await file.arrayBuffer()
  const workbook = XLSX.read(buffer, { type: 'array', cellDates: false })
  const sheetNames = workbook.SheetNames
  const previews: SheetPreview[] = []
  const sheetMetas: SheetTemplateMeta[] = []
  const rows: HospitalRow[] = []

  for (const sheetName of sheetNames) {
    const worksheet = workbook.Sheets[sheetName]
    const rawMatrix = XLSX.utils.sheet_to_json<(string | number)[]>(worksheet, {
      header: 1,
      defval: '',
      blankrows: false,
      raw: true
    })
    const matrix = rules.cleaning.removeFirstRow ? rawMatrix.slice(1) : rawMatrix

    const headerRowIndex = findHeaderRowIndex(matrix)
    if (headerRowIndex < 0) continue

    const headerMap = createHeaderMap(matrix[headerRowIndex] as unknown[])
    const sheetRows = extractHospitalRows(sheetName, matrix, headerRowIndex, headerMap, rules)
    sheetMetas.push(extractSheetTemplateMeta(sheetName, matrix, headerRowIndex))

    previews.push({
      name: sheetName,
      totalRows: matrix.length,
      dataRows: sheetRows.length,
      headerRowIndex
    })
    rows.push(...sheetRows)
  }

  if (rows.length === 0) {
    throw new Error(NO_DETAIL_ROWS_MESSAGE)
  }

  return { fileName: file.name ?? '', sheetNames, previews, sheetMetas, rows }
}
