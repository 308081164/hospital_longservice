import request from '@/utils/http'
import { useUserStore } from '@/store/modules/user'

export interface ExportTemplateRecord {
  id: number
  customerId?: number | null
  templateType: string
  name: string
  storagePath?: string
  columnMapping?: string
  sheetConfig?: string
  isActive?: boolean
  strategyKey?: string
}

export interface SaveExportTemplatePayload {
  customerId?: number | null
  templateType: string
  name: string
  storagePath?: string
  columnMapping?: string
  sheetConfig?: string
  isActive?: boolean
}

export interface ColumnMappingModel {
  removeColumns?: string[]
  keepColumns?: string[]
  renameColumns?: Record<string, string>
}

export interface ExportV2Payload {
  exportType?:
    | 'bill'
    | 'settlement'
    | 'dept_summary'
    | 'price_summary'
    | 'instrument_audit'
    | 'logistics_allocation'
    | 'grand_summary'
  templateId?: number
  useStrategyEngine?: boolean
}

export interface ExportPreviewResult {
  jobId: number
  exportType: string
  templateId?: number
  templateName: string
  strategyKey: string
  customerOverride: boolean
  rowCount: number
  hospitalName: string
}

export interface ExportValidationResult {
  jobId: number
  totalRows: number
  warningRows: number
  correctedRows: number
  totalDifference?: number
  logisticsFee?: number
  settlementAdjustment?: number
  ready: boolean
  message: string
}

export const EXPORT_TEMPLATE_TYPES = [
  'bill',
  'settlement',
  'dept_summary',
  'price_summary',
  'instrument_audit',
  'logistics_allocation',
  'grand_summary'
] as const

export const EXPORT_STRATEGY_OPTIONS = [
  { value: 'standard_bill', labelKey: 'exportTemplates.strategy.standardBill' },
  { value: 'standard_settlement', labelKey: 'exportTemplates.strategy.standardSettlement' },
  { value: 'sheng_er_bill', labelKey: 'exportTemplates.strategy.shengErBill' },
  { value: 'daowai_bill', labelKey: 'exportTemplates.strategy.daowaiBill' },
  { value: 'guoyao_bill', labelKey: 'exportTemplates.strategy.guoyaoBill' }
] as const

export const DEFAULT_BILL_COLUMNS = [
  '发货日期',
  '单号',
  '类型',
  '包类别号',
  '包名',
  '器械数',
  '包数',
  '单价',
  '总价'
]

export function listExportTemplates(params?: { customerId?: number; templateType?: string }) {
  return request.get<ExportTemplateRecord[]>({
    url: '/api/v1/export-templates',
    params
  })
}

export function getExportTemplate(id: number) {
  return request.get<ExportTemplateRecord>({
    url: `/api/v1/export-templates/${id}`
  })
}

export function createExportTemplate(payload: SaveExportTemplatePayload) {
  return request.post<ExportTemplateRecord>({
    url: '/api/v1/export-templates',
    data: payload
  })
}

export function updateExportTemplate(id: number, payload: SaveExportTemplatePayload) {
  return request.put<ExportTemplateRecord>({
    url: `/api/v1/export-templates/${id}`,
    data: payload
  })
}

export function deleteExportTemplate(id: number) {
  return request.del<boolean>({
    url: `/api/v1/export-templates/${id}`
  })
}

export function parseColumnMapping(raw?: string | null): ColumnMappingModel {
  if (!raw || !raw.trim()) return {}
  try {
    return JSON.parse(raw) as ColumnMappingModel
  } catch {
    return {}
  }
}

export function buildColumnMappingJson(model: ColumnMappingModel): string {
  const payload: ColumnMappingModel = {}
  if (model.removeColumns?.length) payload.removeColumns = model.removeColumns
  if (model.keepColumns?.length) payload.keepColumns = model.keepColumns
  if (model.renameColumns && Object.keys(model.renameColumns).length) {
    payload.renameColumns = model.renameColumns
  }
  return JSON.stringify(payload)
}

export function buildSheetConfigJson(strategyKey: string, extra?: Record<string, unknown>): string {
  return JSON.stringify({ strategyKey, ...extra })
}

export function parseSheetConfig(raw?: string | null): { strategyKey?: string } {
  if (!raw?.trim()) return {}
  try {
    return JSON.parse(raw) as { strategyKey?: string }
  } catch {
    return {}
  }
}

function resolveApiRequestUrl(url: string) {
  const baseURL = (import.meta.env.VITE_API_URL || '').trim()
  if (!baseURL || baseURL === '/') return url
  return new URL(url, `${baseURL.replace(/\/$/, '')}/`).toString()
}

export async function exportReconciliationV2(
  jobId: number,
  payload?: ExportV2Payload
): Promise<Blob> {
  const userStore = useUserStore()
  const response = await fetch(
    resolveApiRequestUrl(`/api/hospital-reconciliations/${jobId}/export-v2`),
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(userStore.accessToken ? { Authorization: `Bearer ${userStore.accessToken}` } : {})
      },
      body: JSON.stringify(payload ?? { exportType: 'bill', useStrategyEngine: true })
    }
  )
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.blob()
}

export function previewReconciliationExport(
  jobId: number,
  exportType = 'bill',
  templateId?: number
) {
  return request.get<ExportPreviewResult>({
    url: `/api/hospital-reconciliations/${jobId}/export-preview`,
    params: { exportType, templateId }
  })
}

export function validateReconciliationExport(jobId: number) {
  return request.get<ExportValidationResult>({
    url: `/api/hospital-reconciliations/${jobId}/export-validation`
  })
}

export function triggerBlobDownload(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
