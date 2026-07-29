export const DEFAULT_EXPORT_TYPES = ['bill', 'settlement'] as const

export type HospitalExportType =
  | 'bill'
  | 'settlement'
  | 'dept_summary'
  | 'price_summary'
  | 'instrument_audit'
  | 'logistics_allocation'
  | 'grand_total'

/** 与 backend hospital-export-capabilities.json 同步；API 未返回 exportTypes 时的前端回退 */
const STATIC_HOSPITAL_EXPORT_TYPES: Record<string, string[]> = {
  黑龙江中医药大学附属第一医院: ['bill', 'settlement', 'dept_summary', 'logistics_allocation'],
  '黑龙江省中医药大学附属第三医院（电力）': ['bill', 'settlement', 'instrument_audit'],
  国药总医院主院区: ['bill', 'settlement'],
  国药总医院第二院区: ['bill', 'settlement'],
  国药总医院第三院区: ['bill', 'settlement'],
  哈尔滨市第二医院: ['bill', 'settlement'],
  哈尔滨市第五医院: [
    'bill',
    'settlement',
    'dept_summary',
    'price_summary',
    'instrument_audit',
    'grand_total'
  ],
  '哈尔滨市第五医院（二门诊）': ['bill', 'settlement', 'grand_total'],
  新发红十字医院: ['bill', 'settlement'],
  '黑龙江省医院（南岗院区）': [
    'bill',
    'settlement',
    'price_summary',
    'instrument_audit',
    'logistics_allocation'
  ],
  '黑龙江省医院（香坊院区）': [
    'bill',
    'settlement',
    'price_summary',
    'instrument_audit',
    'logistics_allocation'
  ],
  '祖研-黑龙江省中医医院（南岗院区）': ['bill', 'settlement', 'price_summary'],
  '祖研-黑龙江省中医医院（三辅院区）': ['bill', 'settlement', 'price_summary'],
  '祖研-黑龙江省中医医院（香安院区）': ['bill', 'settlement', 'price_summary'],
  南岗区妇产医院: ['bill', 'settlement'],
  黑龙江省社会康复医院: ['bill', 'settlement'],
  道外区人民医院: ['bill', 'settlement'],
  太平人民医院: ['bill', 'settlement'],
  三精肾病医院: ['bill', 'settlement'],
  黑龙江维多利亚妇产医院: ['bill', 'settlement'],
  黑龙江九洲妇科医院: ['bill', 'settlement'],
  呼兰区红十字医院: ['bill', 'settlement'],
  呼兰中医院: ['bill', 'settlement'],
  '黑龙江中医药大学附属第二医院（南岗）': [
    'bill',
    'settlement',
    'price_summary',
    'instrument_audit'
  ],
  '黑龙江中医药大学附属第二医院（哈南分院）': [
    'bill',
    'settlement',
    'price_summary',
    'instrument_audit'
  ],
  哈尔滨仁胜医院: ['bill', 'settlement'],
  哈尔滨华夏眼科医院: ['bill', 'settlement'],
  哈尔滨冰城医疗美容医院: ['bill', 'settlement'],
  香坊中医院: ['bill', 'settlement'],
  武警黑龙江省总队医院: ['bill', 'settlement'],
  悦美芳华医疗门诊医院: ['bill', 'settlement'],
  '黑龙江省第二医院（南岗院区）': ['bill', 'settlement'],
  '黑龙江省第二医院（松北院区）': ['bill', 'settlement'],
  哈尔滨市呼兰区第一人民医院: ['bill', 'settlement'],
  哈尔滨市红十字妇产医院: ['bill', 'settlement'],
  哈尔滨工业大学医院: ['bill', 'settlement'],
  哈尔滨工程大学医院: ['bill', 'settlement'],
  哈尔滨长健医院: ['bill', 'settlement']
}

const EXPORT_TYPE_I18N_KEYS: Record<string, string> = {
  bill: 'reconciliation.history.export.bill',
  settlement: 'reconciliation.history.export.settlement',
  dept_summary: 'reconciliation.history.export.departmentSummary',
  price_summary: 'reconciliation.history.export.priceSummary',
  instrument_audit: 'reconciliation.history.export.instrumentAudit',
  logistics_allocation: 'reconciliation.history.export.logisticsAllocation',
  grand_total: 'reconciliation.history.export.grandTotal'
}

const EXPORT_FILE_PREFIX: Record<string, string> = {
  bill: '账单',
  settlement: '结款函',
  dept_summary: '分科室汇总',
  price_summary: '价格汇总',
  instrument_audit: '器械把数表',
  logistics_allocation: '物流分摊',
  grand_total: '总汇总'
}

export function resolveExportTypesForHospital(hospitalName?: string | null): string[] {
  const name = hospitalName?.trim()
  if (!name) return [...DEFAULT_EXPORT_TYPES]
  return STATIC_HOSPITAL_EXPORT_TYPES[name] ?? [...DEFAULT_EXPORT_TYPES]
}

export function resolveJobExportTypes(job: Api.Hospital.ReconciliationJob): string[] {
  if (job.exportTypes?.length) return [...job.exportTypes]
  return resolveExportTypesForHospital(job.hospitalName)
}

export function exportTypeI18nKey(type: string): string {
  return EXPORT_TYPE_I18N_KEYS[type] ?? type
}

export function exportFilePrefix(type: string): string {
  return EXPORT_FILE_PREFIX[type] ?? type
}

export function jobHasSpecialExport(job: Api.Hospital.ReconciliationJob): boolean {
  if (job.hasSpecialExport != null) return job.hasSpecialExport
  if (job.billingEnabled) return true
  const types = resolveJobExportTypes(job)
  return types.some((type) => type !== 'bill' && type !== 'settlement')
}

export function jobExportProfileLabel(job: Api.Hospital.ReconciliationJob): string {
  return job.exportProfileLabel?.trim() || ''
}
