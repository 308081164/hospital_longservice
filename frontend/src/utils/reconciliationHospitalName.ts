/** 常见科室/工作表名，不应作为医院全称展示或入库。 */
const DEPARTMENT_NAME_PATTERN =
  /^(手术室|门诊部|门诊$|供应室|消毒供应|内镜中心|产房|病区|病房|ICU|供应中心|消毒中心|美容科|骨科|内科|外科|妇科|产科|儿科|眼科|耳鼻喉|口腔科|康复科|急诊科|麻醉科|输血科|病理科|检验科|放射科|超声科|药剂科|营养科|中医科|皮肤科|精神科|肿瘤科|透析室|导管室|介入室|胃镜室|换药室|处置室|治疗室|护士站)([（(].*[）)])?$/

const HOSPITAL_NAME_PATTERN = /(医院|诊所|集团|中心|卫生院|卫生服务中心|医疗美容|妇产医院|肛肠医院)$/

const FILE_BILL_SUFFIX_PATTERN = /(账单|结款函|汇总|发货单|明细|对账).*$/
const FILE_MONTH_SUFFIX_PATTERN = /\d{1,2}月.*$/
const FILE_YEAR_PREFIX_PATTERN = /^\d{4}[\s_-]?/

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

export function isLikelyHospitalName(name?: string | null): boolean {
  const trimmed = (name ?? '').trim()
  if (!trimmed || isLikelyDepartmentName(trimmed)) return false
  return HOSPITAL_NAME_PATTERN.test(trimmed) || trimmed.length >= 6
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

export function buildHospitalNameCandidates(options: {
  fileName?: string | null
  currentName?: string | null
  sheetHospitalDisplayNames?: Array<string | null | undefined>
  ruleHospitalName?: string | null
  ruleName?: string | null
}): string[] {
  const seen = new Set<string>()
  const candidates: string[] = []

  const push = (value?: string | null) => {
    const trimmed = (value ?? '').trim()
    if (!trimmed || seen.has(trimmed)) return
    seen.add(trimmed)
    candidates.push(trimmed)
  }

  if (options.fileName) {
    push(inferHospitalNameFromFileName(options.fileName))
    push(options.fileName.replace(/\.[^.]+$/, '').replace(FILE_YEAR_PREFIX_PATTERN, ''))
  }
  push(options.ruleHospitalName)
  push(options.ruleName)
  if (options.currentName && !isLikelyDepartmentName(options.currentName)) {
    push(options.currentName)
  }
  for (const name of options.sheetHospitalDisplayNames ?? []) {
    if (isLikelyHospitalName(name)) push(name)
  }

  return candidates
}

export function resolveReconciliationHospitalName(options: {
  fileName?: string | null
  currentName?: string | null
  sheetHospitalDisplayNames?: Array<string | null | undefined>
  ruleHospitalName?: string | null
  ruleName?: string | null
}): string {
  const candidates = buildHospitalNameCandidates(options)
  const nonDepartment = candidates.find((name) => !isLikelyDepartmentName(name))
  return nonDepartment ?? candidates[0] ?? ''
}

export function displayHospitalNameForJob(
  hospitalName?: string | null,
  sourceFileName?: string | null
): string {
  const trimmed = (hospitalName ?? '').trim()
  if (trimmed && !isLikelyDepartmentName(trimmed)) return trimmed
  const fromFile = inferHospitalNameFromFileName(sourceFileName)
  if (fromFile) return fromFile
  return trimmed || '(未命名)'
}
