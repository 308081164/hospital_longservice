/** 常见科室/工作表名，不应作为医院全称展示或入库。 */
const DEPARTMENT_NAME_PATTERN =
  /^(手术室|门诊部|门诊$|供应室|消毒供应|内镜中心|产房|病区|病房|ICU|供应中心|消毒中心|美容科|骨科|内科|外科|妇科|产科|儿科|眼科|耳鼻喉|口腔科|康复科|急诊科|麻醉科|输血科|病理科|检验科|放射科|超声科|药剂科|营养科|中医科|皮肤科|精神科|肿瘤科|透析室|导管室|介入室|胃镜室|换药室|处置室|治疗室|护士站|静配中心|息肉中心|体检中心|伤口造口门诊)([（(].*[）)])?$/

const HOSPITAL_NAME_PATTERN = /(医院|诊所|集团|中心|卫生院|卫生服务中心|医疗美容|妇产医院|肛肠医院)$/

/** 铂康账单表头元数据区的账期行，不可当作医院名。 */
const DATE_RANGE_TEXT_PATTERN =
  /^(从|时间|日期)[：:]?\s*\d{4}.*(?:至|到).*\d{4}|^\d{4}[/-]\d{1,2}[/-]\d{1,2}.*(?:至|到).*\d{4}/

const HEADER_FIELD_PATTERN = /^发货日期$/
const PURE_NUMBER_PATTERN = /^[\d.]+$/

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

/** 铂康标准账单 D8/D9 医院名校验：必须含「医院」，拒绝日期/数字/表头字段/科室名。 */
export function isValidHospitalName(name?: string | null): boolean {
  const trimmed = (name ?? '').trim()
  if (!trimmed || !trimmed.includes('医院')) return false
  if (isLikelyDepartmentName(trimmed) || isDateRangeText(trimmed)) return false
  if (trimmed.includes('发货单汇总表') || HEADER_FIELD_PATTERN.test(trimmed)) return false
  if (PURE_NUMBER_PATTERN.test(trimmed)) return false
  return true
}

export function isLikelyHospitalName(name?: string | null): boolean {
  return isValidHospitalName(name)
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
  if (isValidHospitalName(d9)) return d9
  const d8 = readColumnDCell(matrix, EXCEL_D8_ROW_INDEX)
  if (isValidHospitalName(d8)) return d8
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
