/** 常见科室/工作表名，不应作为医院全称展示或入库。 */
const DEPARTMENT_NAME_PATTERN =
  /^(手术室|门诊部|门诊$|供应室|消毒供应|内镜中心|产房|病区|病房|ICU|供应中心|消毒中心|美容科|骨科|内科|外科|妇科|产科|儿科|眼科|耳鼻喉|口腔科|康复科|急诊科|麻醉科|输血科|病理科|检验科|放射科|超声科|药剂科|营养科|中医科|皮肤科|精神科|肿瘤科|透析室|导管室|介入室|胃镜室|换药室|处置室|治疗室|护士站|静配中心|息肉中心|体检中心|伤口造口门诊)([（(].*[）)])?$/

const HOSPITAL_NAME_PATTERN = /(医院|诊所|集团|中心|卫生院|卫生服务中心|医疗美容|妇产医院|肛肠医院)$/

/** 铂康账单表头元数据区的账期行，不可当作医院名。 */
const DATE_RANGE_TEXT_PATTERN =
  /^(从|时间|日期)[：:]?\s*\d{4}.*(?:至|到).*\d{4}|^\d{4}[/-]\d{1,2}[/-]\d{1,2}.*(?:至|到).*\d{4}/

const HEADER_FIELD_PATTERN = /^发货日期$/
const PURE_NUMBER_PATTERN = /^[\d.]+$/
/** D8/D9 若正好落在表头行，这些列名不是医院。 */
const FIXED_CELL_HEADER_LABELS = new Set([
  '发货日期',
  '发货单号',
  '类型',
  '基本类型',
  '包名',
  '包装材料',
  '包数',
  '器械数',
  '单价',
  '总价',
  '科室',
  '包类别号',
  '灭菌日期'
])
/** 不含「医院」二字、但仍是机构全称的后缀。单独的「门诊部」是科室，不在此列。 */
const INSTITUTION_SUFFIX_PATTERN = /(诊所|门诊部|卫生院|卫生服务中心)$/

const FILE_BILL_SUFFIX_PATTERN = /(账单|结款函|汇总|发货单|明细|对账).*$/
const FILE_MONTH_SUFFIX_PATTERN = /\d{1,2}月.*$/
const FILE_YEAR_PREFIX_PATTERN = /^\d{4}[\s_-]?/

const PLACEHOLDER_HOSPITAL_NAMES = new Set(['未命名医院', '(未命名)', '未命名'])

/** Excel D 列固定位置（0-based row index） */
const EXCEL_D8_ROW_INDEX = 7
const EXCEL_D9_ROW_INDEX = 8
const STANDARD_HOSPITAL_NAME_COLUMN = 3

export function isLikelyDepartmentName(name?: string | null): boolean {
  const trimmed = (name ?? '').trim()
  if (!trimmed) return false
  if (DEPARTMENT_NAME_PATTERN.test(trimmed)) return true
  // 短科室名：如「美容科」「供应室」，但排除含机构后缀的名称
  if (trimmed.length <= 8 && /科$/.test(trimmed) && !HOSPITAL_NAME_PATTERN.test(trimmed)) {
    return true
  }
  return false
}

export function isPlaceholderHospitalName(name?: string | null): boolean {
  const trimmed = (name ?? '').trim()
  return !trimmed || PLACEHOLDER_HOSPITAL_NAMES.has(trimmed)
}

export function isDateRangeText(name?: string | null): boolean {
  const trimmed = (name ?? '').trim()
  if (!trimmed) return false
  return DATE_RANGE_TEXT_PATTERN.test(trimmed)
}

/**
 * 机构全称：含「医院」，或以诊所 / 门诊部 / 卫生院 / 卫生服务中心结尾。
 * 单独的「门诊部」「中西医科」以及「内镜中心」等科室名仍然拒绝。
 */
export function isValidHospitalName(name?: string | null): boolean {
  const trimmed = (name ?? '').trim()
  if (!trimmed) return false
  if (isLikelyDepartmentName(trimmed) || isDateRangeText(trimmed)) return false
  if (trimmed.includes('发货单汇总表') || HEADER_FIELD_PATTERN.test(trimmed)) return false
  if (PURE_NUMBER_PATTERN.test(trimmed)) return false
  if (trimmed.includes('医院') && trimmed.includes('至')) return false
  if (trimmed.includes('医院')) return true
  return INSTITUTION_SUFFIX_PATTERN.test(trimmed)
}

export function isLikelyHospitalName(name?: string | null): boolean {
  return isValidHospitalName(name)
}

/**
 * D8/D9 固定格：单元格里写什么就用什么。
 * 不要求带「医院 / 诊所 / 门诊部」。仍排除空值、表头列名、日期区间和纯数字。
 */
export function isAcceptableFixedCellHospitalName(name?: string | null): boolean {
  const trimmed = (name ?? '').trim()
  if (!trimmed) return false
  if (isLikelyDepartmentName(trimmed) || isDateRangeText(trimmed)) return false
  if (trimmed.includes('发货单汇总表') || FIXED_CELL_HEADER_LABELS.has(trimmed)) return false
  if (PURE_NUMBER_PATTERN.test(trimmed)) return false
  if (trimmed.includes('医院') && trimmed.includes('至')) return false
  return true
}

function readColumnDCell(matrix: unknown[][], rowIndex: number): string {
  if (rowIndex < 0 || rowIndex >= matrix.length) return ''
  const row = matrix[rowIndex]
  return String(row?.[STANDARD_HOSPITAL_NAME_COLUMN] ?? '').trim()
}

/** 铂康标准账单：医院全称仅在 D8 或 D9（二选一），D9 优先。 */
export function resolveHospitalNameFromColumnD(
  matrix: unknown[][],
  _headerRowIndex?: number
): string {
  const d9 = readColumnDCell(matrix, EXCEL_D9_ROW_INDEX)
  if (isAcceptableFixedCellHospitalName(d9)) return d9
  const d8 = readColumnDCell(matrix, EXCEL_D8_ROW_INDEX)
  if (isAcceptableFixedCellHospitalName(d8)) return d8
  return ''
}

/**
 * 表头下一行起，沿「发货日期」列找机构汇总带。
 * 只接受没有发货单号、类型、包名的行，避免把正文里的「医院」当成机构名。
 * 左边距不是 3 列时，诊所名不会落在固定的 D9。
 */
export function resolveHospitalNameFromDeliveryDateColumn(
  matrix: unknown[][],
  headerRowIndex: number
): string {
  if (headerRowIndex < 0 || headerRowIndex >= matrix.length) return ''
  const headerMap = new Map<string, number>()
  ;(matrix[headerRowIndex] ?? []).forEach((cell, index) => {
    const key = String(cell ?? '')
      .replace(/\s+/g, '')
      .trim()
    if (key && !headerMap.has(key)) headerMap.set(key, index)
  })
  const dateCol = headerMap.get('发货日期')
  if (dateCol === undefined) return ''
  const orderCol = headerMap.get('发货单号')
  const typeCol = headerMap.get('类型') ?? headerMap.get('基本类型')
  const packCol = headerMap.get('包名')
  const cellText = (row: unknown[], col: number | undefined) =>
    col === undefined ? '' : String(row[col] ?? '').trim()

  for (let i = headerRowIndex + 1; i < matrix.length; i += 1) {
    const row = matrix[i] ?? []
    if (cellText(row, orderCol) || cellText(row, typeCol) || cellText(row, packCol)) continue
    const text = cellText(row, dateCol)
    if (isValidHospitalName(text)) return text
  }
  return ''
}

/**
 * 合并单 sheet（发货单汇总表）：医院全称在表头之后的 A 列汇总行，D8/D9 为空。
 * 科室行（如 ICU病房、单独的门诊部）会被跳过。
 */
export function resolveHospitalNameFromColumnASummary(
  matrix: unknown[][],
  headerRowIndex: number
): string {
  if (headerRowIndex < 0) return ''
  for (let i = headerRowIndex + 1; i < matrix.length; i += 1) {
    const text = String(matrix[i]?.[0] ?? '').trim()
    if (isValidHospitalName(text)) return text
  }
  return ''
}

/** @deprecated 使用 resolveHospitalNameFromColumnD；保留同名导出供现有调用方。 */
export function extractStandardHospitalNameFromMatrix(
  matrix: unknown[][],
  headerRowIndex: number
): string {
  return resolveHospitalNameFromColumnD(matrix, headerRowIndex)
}

export function inferHospitalNameFromFileName(fileName?: string | null): string {
  const trimmed = (fileName ?? '').trim()
  if (!trimmed) return ''
  let base = trimmed.replace(/\.[^.]+$/, '').trim()
  base = base.replace(FILE_YEAR_PREFIX_PATTERN, '')
  base = base.replace(FILE_MONTH_SUFFIX_PATTERN, '')
  base = base.replace(FILE_BILL_SUFFIX_PATTERN, '')
  return base.trim()
}

export function pickBestHospitalDisplayName(
  names?: Array<string | null | undefined>
): string {
  let best = ''
  for (const name of names ?? []) {
    const trimmed = (name ?? '').trim()
    if (!isValidHospitalName(trimmed)) continue
    if (trimmed.length > best.length) best = trimmed
  }
  return best
}

export function buildHospitalNameCandidates(options: {
  fileName?: string | null
  currentName?: string | null
  sheetHospitalDisplayNames?: Array<string | null | undefined>
}): string[] {
  const seen = new Set<string>()
  const candidates: string[] = []

  const push = (value?: string | null) => {
    const trimmed = (value ?? '').trim()
    if (!trimmed || seen.has(trimmed)) return
    seen.add(trimmed)
    candidates.push(trimmed)
  }

  if (options.currentName && !isLikelyDepartmentName(options.currentName)) {
    push(options.currentName)
  }
  if (options.fileName) {
    push(inferHospitalNameFromFileName(options.fileName))
  }
  const firstSheetHospital = (options.sheetHospitalDisplayNames ?? [])
    .map((name) => (name ?? '').trim())
    .find((name) => isValidHospitalName(name))
  if (firstSheetHospital) push(firstSheetHospital)

  return candidates
}

export function resolveReconciliationHospitalName(options: {
  fileName?: string | null
  currentName?: string | null
  sheetHospitalDisplayNames?: Array<string | null | undefined>
}): string {
  const sheetName = (options.sheetHospitalDisplayNames ?? [])
    .map((name) => (name ?? '').trim())
    .find((name) => isAcceptableFixedCellHospitalName(name))
  // 文件名「省医院香坊」也含「医院」，不能盖过表内 D8/D9 的院区全称。
  if (sheetName) return sheetName

  const candidates = buildHospitalNameCandidates(options)
  for (const name of candidates) {
    if (isValidHospitalName(name)) return name
  }
  for (const name of candidates) {
    if (name && !isLikelyDepartmentName(name) && !isDateRangeText(name)) return name
  }
  return candidates[0] ?? ''
}

/** 文件条/历史卡片医院徽章：优先已保存名/文件名，再取首个 sheet 的 D8/D9。 */
export function resolveHospitalBadgeName(options: {
  hospitalName?: string | null
  fileName?: string | null
  sheetHospitalDisplayNames?: Array<string | null | undefined>
}): string {
  const resolved = resolveReconciliationHospitalName({
    fileName: options.fileName,
    currentName: isPlaceholderHospitalName(options.hospitalName) ? '' : options.hospitalName,
    sheetHospitalDisplayNames: options.sheetHospitalDisplayNames
  })
  if (resolved && !isPlaceholderHospitalName(resolved)) return resolved

  const stored = (options.hospitalName ?? '').trim()
  if (stored && !isPlaceholderHospitalName(stored) && !isLikelyDepartmentName(stored)) {
    return stored
  }

  const fromFile = inferHospitalNameFromFileName(options.fileName)
  if (fromFile) return fromFile

  return resolved || stored || ''
}

/** 表内机构名是否已在客户档案（规范名或别名完全一致）。 */
export function matchesCustomerArchive(
  name: string | null | undefined,
  customers: Array<{
    canonicalName?: string | null
    canonical_name?: string | null
    aliases?: Array<{ alias?: string | null; isActive?: boolean | null }> | null
  }>
): boolean {
  const target = (name ?? '').trim()
  if (!target || !isAcceptableFixedCellHospitalName(target)) return false
  return customers.some((customer) => {
    const canonical = (customer.canonicalName ?? customer.canonical_name ?? '').trim()
    if (canonical && canonical === target) return true
    return (customer.aliases ?? []).some((alias) => {
      if (alias.isActive === false) return false
      return (alias.alias ?? '').trim() === target
    })
  })
}

export function displayHospitalNameForJob(
  hospitalName?: string | null,
  sourceFileName?: string | null
): string {
  const badge = resolveHospitalBadgeName({
    hospitalName,
    fileName: sourceFileName
  })
  return badge || '(未能识别医院名)'
}
