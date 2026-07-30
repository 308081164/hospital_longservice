import request from '@/utils/http'

export function saveHospitalReconciliation(payload: {
  file: File
  hospitalName?: string
  operatorName: string
  ruleId?: number
  ruleName?: string
  ruleVersion?: string
  sourceDateRange?: string
  summary: Api.Hospital.ReconciliationJobSummary
  rows: Api.Hospital.ReconciliationRowPayload[]
}) {
  const formData = new FormData()
  formData.append('payload_json', JSON.stringify({
    hospitalName: payload.hospitalName,
    operatorName: payload.operatorName,
    ruleId: payload.ruleId,
    ruleName: payload.ruleName,
    ruleVersion: payload.ruleVersion,
    sourceDateRange: payload.sourceDateRange,
    summary: payload.summary,
    rows: payload.rows,
  }))
  formData.append('source_file', payload.file)
  return request.post<Api.Hospital.ReconciliationJob>({
    url: '/api/hospital-reconciliations',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function listHospitalReconciliations(hospitalName?: string) {
  return request.get<Api.Hospital.ReconciliationJob[]>({
    url: '/api/hospital-reconciliations',
    params: hospitalName ? { hospital_name: hospitalName } : undefined,
  })
}

export function updateHospitalReconciliationReview(
  jobId: number,
  payload: { reviewStatus: string; reviewComment?: string; reviewerName: string },
) {
  return request.request<Api.Hospital.ReconciliationJob>({
    url: `/api/hospital-reconciliations/${jobId}/review`,
    method: 'PATCH',
    data: payload,
  })
}

export function createHospitalReconciliationExportLog(
  jobId: number,
  payload: { exportType: string; fileName?: string; operatorName: string },
) {
  return request.post<Api.Hospital.ReconciliationExportLog>({
    url: `/api/hospital-reconciliations/${jobId}/exports`,
    data: payload,
  })
}

export interface BackendTemplateRef {
  id: string
  name: string
  description: string
}

export function getReconciliationDetail(jobId: number) {
  return request.get<Api.Hospital.ReconciliationJob>({
    url: `/api/hospital-reconciliations/${jobId}`,
  })
}

export function updateHospitalReconciliationRows(
  jobId: number,
  rows: Record<string, unknown>[],
) {
  return request.put<Api.Hospital.ReconciliationJob>({
    url: `/api/hospital-reconciliations/${jobId}/rows`,
    data: rows,
  })
}

export function updateReconciliationRowsUrgent(
  jobId: number,
  payload: {
    isUrgent: boolean
    rowIds?: number[]
    rows?: Array<{ sheetName: string; rowNumber: number }>
  },
) {
  return request.request<Api.Hospital.ReconciliationJob>({
    url: `/api/hospital-reconciliations/${jobId}/rows/urgent`,
    method: 'PATCH',
    data: payload,
  })
}

export function listSettlementTemplates() {
  return request.get<BackendTemplateRef[]>({
    url: '/api/hospital-reconciliations/templates/settlement',
  })
}

export function listBillTemplates() {
  return request.get<BackendTemplateRef[]>({
    url: '/api/hospital-reconciliations/templates/bill',
  })
}

export interface ReconciliationRowsPage {
  rows: Record<string, unknown>[]
  total: number
  page: number
  size: number
  sheetName?: string
}

/** 分页获取核对明细行（从数据库 table 直接查询，不经过 rowsJson） */
export function getReconciliationRows(jobId: number, page = 1, size = 200, sheetName?: string) {
  return request.get<ReconciliationRowsPage>({
    url: `/api/hospital-reconciliations/${jobId}/rows`,
    params: {
      page,
      size,
      ...(sheetName ? { sheetName } : {})
    },
  })
}

export interface UnmatchedProductItem {
  pack_name: string
  type?: string
  package_material?: string
  row_count: number
  total_difference?: number
  suggested_family?: string
  spec_fingerprint?: string
  suggested_category_code?: string
  likely_small_item?: boolean
  matched_needle_keywords?: string[]
}

export function getUnmatchedProducts(jobId: number) {
  return request.get<{ job_id: number; unmatched_count: number; items: UnmatchedProductItem[] }>({
    url: `/api/hospital-reconciliations/${jobId}/unmatched-products`,
  })
}

/** 重新定价：使用任务关联的计费规则重新计算所有行（不保存，仅供预览） */
export interface RepriceResult {
  rows: Record<string, unknown>[]
  summary: {
    total: number
    corrected: number
    unchanged: number
    warning: number
    skipped: number
    totalDifference: number
  }
}

export function repriceReconciliation(jobId: number) {
  return request.request<RepriceResult>({
    url: `/api/hospital-reconciliations/${jobId}/reprice`,
    method: 'POST',
  })
}

/** 后端引擎导入：上传 Excel + 规则 → 后端处理全部行并保存，一步完成 */
export function importHospitalReconciliation(payload: {
  file: File
  ruleId: number
  operatorName: string
  hospitalName?: string
}) {
  const formData = new FormData()
  formData.append('source_file', payload.file)
  formData.append('rule_id', String(payload.ruleId))
  formData.append('operator_name', payload.operatorName)
  if (payload.hospitalName) formData.append('hospital_name', payload.hospitalName)
  return request.post<Api.Hospital.ReconciliationJob>({
    url: '/api/hospital-reconciliations/import',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
