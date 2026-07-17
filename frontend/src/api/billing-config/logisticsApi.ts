import request from '@/utils/http'

export interface LogisticsImportRecord {
  id: number
  customer_id: number
  job_id?: number | null
  billing_month?: string | null
  trip_date: string
  route?: string | null
  trip_count: number
  fee_amount?: number | null
  notes?: string | null
}

export interface SaveLogisticsImportPayload {
  jobId?: number | null
  billingMonth?: string | null
  tripDate: string
  route?: string | null
  tripCount?: number
  feeAmount?: number | null
  notes?: string | null
}

export function listLogisticsImports(customerId: number, billingMonth?: string) {
  return request.get<LogisticsImportRecord[]>({
    url: `/api/v1/customers/${customerId}/logistics-imports`,
    params: billingMonth ? { billingMonth } : undefined,
  })
}

export function createLogisticsImport(customerId: number, payload: SaveLogisticsImportPayload) {
  return request.post<LogisticsImportRecord>({
    url: `/api/v1/customers/${customerId}/logistics-imports`,
    data: payload,
  })
}

export function updateLogisticsImport(
  customerId: number,
  importId: number,
  payload: SaveLogisticsImportPayload,
) {
  return request.put<LogisticsImportRecord>({
    url: `/api/v1/customers/${customerId}/logistics-imports/${importId}`,
    data: payload,
  })
}

export function deleteLogisticsImport(customerId: number, importId: number) {
  return request.del<boolean>({
    url: `/api/v1/customers/${customerId}/logistics-imports/${importId}`,
  })
}

export interface LogisticsCardRecord {
  id: number
  customer_id: number
  name: string
  balance: number
  initial_balance: number
  is_active: boolean
}

export interface SaveLogisticsCardPayload {
  customerId: number
  name: string
  initialBalance?: number
  isActive?: boolean
}

export function listLogisticsCards(customerId?: number) {
  return request.get<LogisticsCardRecord[]>({
    url: '/api/v1/logistics-cards',
    params: customerId ? { customerId } : undefined,
  })
}

export function createLogisticsCard(payload: SaveLogisticsCardPayload) {
  return request.post<LogisticsCardRecord>({
    url: '/api/v1/logistics-cards',
    data: payload,
  })
}

export function rechargeLogisticsCard(id: number, amount: number, remark?: string) {
  return request.post<LogisticsCardRecord>({
    url: `/api/v1/logistics-cards/${id}/recharge`,
    data: { amount, remark },
  })
}

export function deductLogisticsCard(id: number, amount: number, remark?: string) {
  return request.post<LogisticsCardRecord>({
    url: `/api/v1/logistics-cards/${id}/deduct`,
    data: { amount, remark },
  })
}

export interface LogisticsAllocationPreview {
  jobId: number
  totalLogisticsFee?: number
  allocationSum?: number
  deptAllocations?: Array<{
    department: string
    sterilizeTotal: number
    ratio: number
    allocatedFee: number
  }>
  logisticsBreakdown?: Record<string, unknown>
}

export function getLogisticsAllocationPreview(jobId: number) {
  return request.get<LogisticsAllocationPreview>({
    url: `/api/hospital-reconciliations/${jobId}/logistics-allocation`,
  })
}
