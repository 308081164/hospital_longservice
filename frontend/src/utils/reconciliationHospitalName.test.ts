import {
  displayHospitalNameForJob,
  inferHospitalNameFromFileName,
  isLikelyDepartmentName,
  resolveReconciliationHospitalName
} from './reconciliationHospitalName.ts'

function assertEqual(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`)
  }
}

function assertTrue(value: boolean, message: string) {
  if (!value) throw new Error(message)
}

assertEqual(inferHospitalNameFromFileName('东大肛肠3月账单.xlsx'), '东大肛肠', 'strip month and bill suffix')
assertEqual(inferHospitalNameFromFileName('东大肛肠2月账单.xlsx'), '东大肛肠', 'strip month suffix for feb')
assertTrue(isLikelyDepartmentName('门诊部'), 'outpatient dept')
assertTrue(isLikelyDepartmentName('手术室'), 'operating room')
assertEqual(
  resolveReconciliationHospitalName({
    fileName: '东大肛肠3月账单.xlsx',
    currentName: '门诊部',
    sheetHospitalDisplayNames: ['黑龙江东大肛肠医院', '门诊部', '手术室']
  }),
  '黑龙江东大肛肠医院',
  'prefer excel sheet hospital name over filename'
)
assertEqual(
  resolveReconciliationHospitalName({
    fileName: '东大肛肠3月账单.xlsx',
    currentName: '门诊部',
    sheetHospitalDisplayNames: ['门诊部', '手术室']
  }),
  '东大肛肠',
  'fallback to filename when sheet has no hospital name'
)
assertEqual(
  displayHospitalNameForJob('门诊部', '东大肛肠3月账单.xlsx'),
  '东大肛肠',
  'card display fallback'
)

console.log('reconciliationHospitalName tests passed')
