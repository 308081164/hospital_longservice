import request from '@/utils/http'

export interface ExternalInstrumentRecord {
  id: number
  customerId: number
  reconciliationJobId?: number | null
  categoryNo: string
  packName: string
  department?: string
  packageMaterial?: string
  patientName?: string
  usageDate?: string
  packCount?: number
  instrumentCount?: number
  unitPrice: number
  totalAmount?: number
  notes?: string
  isActive?: boolean
}

export function listExternalInstrumentCatalog(customerId: number) {
  return request.get<ExternalInstrumentRecord[]>({
    url: `/api/v1/customers/${customerId}/external-instruments`,
  })
}

export function createExternalInstrumentCatalog(
  customerId: number,
  payload: Partial<ExternalInstrumentRecord> & { categoryNo: string; packName: string; unitPrice: number },
) {
  return request.post<ExternalInstrumentRecord>({
    url: `/api/v1/customers/${customerId}/external-instruments`,
    data: payload,
  })
}

export function listJobExternalInstruments(jobId: number) {
  return request.get<ExternalInstrumentRecord[]>({
    url: `/api/hospital-reconciliations/${jobId}/external-instruments`,
  })
}

export function createJobExternalInstrument(
  jobId: number,
  payload: Partial<ExternalInstrumentRecord> & { categoryNo: string; packName: string; unitPrice: number },
) {
  return request.post<ExternalInstrumentRecord>({
    url: `/api/hospital-reconciliations/${jobId}/external-instruments`,
    data: payload,
  })
}

export function importJobExternalInstruments(jobId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<number>({
    url: `/api/hospital-reconciliations/${jobId}/external-instruments/import`,
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteExternalInstrument(id: number) {
  return request.del<boolean>({
    url: `/api/v1/external-instruments/${id}`,
  })
}
