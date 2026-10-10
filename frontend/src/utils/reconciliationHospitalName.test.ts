import {
  displayHospitalNameForJob,
  extractStandardHospitalNameFromMatrix,
  inferHospitalNameFromFileName,
  resolveHospitalNameFromColumnASummary,
  isDateRangeText,
  isLikelyDepartmentName,
  isPlaceholderHospitalName,
  isValidHospitalName,
  pickBestHospitalDisplayName,
  resolveHospitalBadgeName,
  resolveHospitalNameFromColumnD,
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
assertTrue(isLikelyDepartmentName('静配中心'), 'jvp center dept')
assertTrue(!isValidHospitalName('静配中心'), 'center without hospital rejected')
assertTrue(!isValidHospitalName('4419.799999999998'), 'numeric summary rejected')
assertEqual(
  resolveReconciliationHospitalName({
    fileName: '东大肛肠3月账单.xlsx',
    currentName: '门诊部',
    sheetHospitalDisplayNames: ['黑龙江东大肛肠医院', '门诊部', '手术室']
  }),
  '黑龙江东大肛肠医院',
  'prefer excel sheet hospital name over filename when sheet has valid D8/D9 name'
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
  resolveReconciliationHospitalName({
    fileName: '省二院南岗.xlsx',
    currentName: '',
    sheetHospitalDisplayNames: ['4419.799999999998', '静配中心']
  }),
  '省二院南岗',
  'filename fallback when all sheet D8/D9 cells invalid'
)
assertEqual(
  resolveReconciliationHospitalName({
    fileName: '省二院南岗.xlsx',
    currentName: '',
    sheetHospitalDisplayNames: ['4419.799999999998', '黑龙江省第二医院（南岗区）']
  }),
  '黑龙江省第二医院（南岗区）',
  'first valid D8/D9 sheet name beats filename'
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
  'excel hospital beats filename when sheet has valid name'
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
assertTrue(
  isDateRangeText('从:2026/6/1 00:00:00 至: 2026/6/30 23:59:59.999'),
  'detect date range row'
)
assertEqual(
  pickBestHospitalDisplayName([
    '从:2026/6/1 00:00:00 至: 2026/6/30 23:59:59.999',
    '哈尔滨谋大医院'
  ]),
  '哈尔滨谋大医院',
  'prefer hospital name over date range'
)
assertEqual(
  resolveReconciliationHospitalName({
    fileName: '谋大6月账单-未改.xlsx',
    currentName: '',
    sheetHospitalDisplayNames: [
      '从:2026/6/1 00:00:00 至: 2026/6/30 23:59:59.999',
      '黑龙江谋大医院'
    ]
  }),
  '黑龙江谋大医院',
  'first valid D8/D9 sheet name used when present'
)
assertEqual(
  resolveReconciliationHospitalName({
    fileName: '省医院香坊.xlsx',
    currentName: '省医院香坊',
    sheetHospitalDisplayNames: ['黑龙江省医院（香坊院区）']
  }),
  '黑龙江省医院（香坊院区）',
  'sheet campus name wins over filename that also contains 医院'
)
assertEqual(
  resolveReconciliationHospitalName({
    fileName: '省医院南岗.xlsx',
    currentName: '省医院南岗',
    sheetHospitalDisplayNames: ['黑龙江省医院（南岗院区）']
  }),
  '黑龙江省医院（南岗院区）',
  '省医院南岗 uses D9 full name'
)

const d8Matrix: unknown[][] = Array.from({ length: 10 }, () => [])
d8Matrix[3] = ['', '', '', '从:2026/6/1 00:00:00 至: 2026/6/30 23:59:59.999']
d8Matrix[7] = ['', '', '', '黑龙江谋大医院']
d8Matrix[8] = ['发货日期', '发货单号', '类型', '包名', '包装材料', '包数', '器械数', '单价', '总价']
assertEqual(
  resolveHospitalNameFromColumnD(d8Matrix, 8),
  '黑龙江谋大医院',
  'D8 layout: hospital at D8 when D9 is header row'
)
assertEqual(
  extractStandardHospitalNameFromMatrix(d8Matrix, 8),
  '黑龙江谋大医院',
  'extractStandardHospitalNameFromMatrix delegates to D8/D9'
)

const d9Matrix: unknown[][] = Array.from({ length: 10 }, () => [])
d9Matrix[7] = ['发货日期', '发货单号', '类型', '包名', '包装材料', '包数', '器械数', '单价', '总价']
d9Matrix[8] = ['', '', '', '呼兰中医院']
assertEqual(
  resolveHospitalNameFromColumnD(d9Matrix, 7),
  '呼兰中医院',
  'D9 layout: hospital at D9 when header at row 8'
)

assertTrue(isPlaceholderHospitalName('未命名医院'), 'placeholder hospital')
assertEqual(
  resolveHospitalBadgeName({
    hospitalName: '未命名医院',
    fileName: '哈尔滨谋大医院.xlsx',
    sheetHospitalDisplayNames: ['哈尔滨谋大医院']
  }),
  '哈尔滨谋大医院',
  'badge prefers excel name over placeholder'
)
assertEqual(
  displayHospitalNameForJob('未命名医院', '谋大6月账单-未改.xlsx'),
  '谋大',
  'history card uses filename when db has placeholder'
)

assertEqual(
  resolveHospitalNameFromColumnASummary(
    [
      ['发货日期', '包名'],
      ['哈尔滨红十字妇产医院'],
      ['ICU病房'],
      ['2026-08-11', '湿化瓶-1/Z3032']
    ],
    0
  ),
  '哈尔滨红十字妇产医院',
  'column A summary row is the hospital when D8/D9 is empty'
)

console.log('reconciliationHospitalName tests passed')
