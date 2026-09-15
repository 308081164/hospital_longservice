import {
  displayHospitalNameForJob,
  inferHospitalNameFromFileName,
  isLikelyDepartmentName,
  pickBestHospitalDisplayName,
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
assertEqual(
  pickBestHospitalDisplayName(['哈尔滨市第五医院', '黑龙江维多利亚妇产医院']),
  '黑龙江维多利亚妇产医院',
  'prefer longest hospital name'
)
assertEqual(
  resolveReconciliationHospitalName({
    fileName: '维多利亚.xlsx',
    currentName: '',
    sheetHospitalDisplayNames: ['黑龙江维多利亚妇产医院']
  }),
  '黑龙江维多利亚妇产医院',
  'excel hospital beats filename'
)
assertEqual(
  resolveReconciliationHospitalName({
    fileName: '三精肾病.xlsx',
    currentName: '',
    sheetHospitalDisplayNames: ['三精肾病医院']
  }),
  '三精肾病医院',
  'san jing sheet name preserved'
)

console.log('reconciliationHospitalName tests passed')
