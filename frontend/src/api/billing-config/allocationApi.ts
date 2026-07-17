import request from '@/utils/http'

export interface RosterMatchHint {
  rowId?: number
  rowNumber?: number
  packName?: string
  matchedDoctor?: string
  suggestedDepartment?: string
}

export interface AllocationResult {
  jobId?: number
  customerId?: number
  originalGrandTotal?: number
  adjustmentTotal?: number
  externalInstrumentTotal?: number
  logisticsTotal?: number
  reconciledGrandTotal?: number
  balanced?: boolean
  balanceMessage?: string
  rosterHints?: RosterMatchHint[]
  priceSummaryByCategory?: Record<string, number>
}

export function runJobAllocation(jobId: number, config?: Record<string, unknown>) {
  return request.post<AllocationResult>({
    url: `/api/hospital-reconciliations/${jobId}/allocate`,
    data: config ? { config } : {},
  })
}

export function getJobAllocationResult(jobId: number) {
  return request.get<AllocationResult>({
    url: `/api/hospital-reconciliations/${jobId}/allocation-result`,
  })
}

export function getJobRosterHints(jobId: number) {
  return request.get<RosterMatchHint[]>({
    url: `/api/hospital-reconciliations/${jobId}/roster-hints`,
  })
}

export function exportOrchestratedWorkbook(jobId: number) {
  return request.request<Blob>({
    url: `/api/hospital-reconciliations/${jobId}/export-orchestrated`,
    method: 'POST',
    responseType: 'blob',
  })
}
