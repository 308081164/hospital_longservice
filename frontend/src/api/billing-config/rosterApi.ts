import request from '@/utils/http'

export interface RosterEntryRecord {
  id: number
  customerId: number
  doctorName: string
  department: string
  surgicalRoom?: string
  notes?: string
  isActive?: boolean
}

export interface RosterImportResult {
  importedCount: number
  skippedCount: number
  errors: string[]
}

export function listRosterEntries(customerId: number) {
  return request.get<RosterEntryRecord[]>({
    url: `/api/v1/customers/${customerId}/roster-entries`,
  })
}

export function createRosterEntry(
  customerId: number,
  payload: { doctorName: string; department: string; surgicalRoom?: string; notes?: string },
) {
  return request.post<RosterEntryRecord>({
    url: `/api/v1/customers/${customerId}/roster-entries`,
    data: payload,
  })
}

export function updateRosterEntry(
  customerId: number,
  entryId: number,
  payload: { doctorName: string; department: string; surgicalRoom?: string; notes?: string; isActive?: boolean },
) {
  return request.put<RosterEntryRecord>({
    url: `/api/v1/customers/${customerId}/roster-entries/${entryId}`,
    data: payload,
  })
}

export function deleteRosterEntry(customerId: number, entryId: number) {
  return request.del<boolean>({
    url: `/api/v1/customers/${customerId}/roster-entries/${entryId}`,
  })
}

export function importRosterExcel(customerId: number, file: File, replace = false) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<RosterImportResult>({
    url: `/api/v1/customers/${customerId}/roster-entries/import?replace=${replace}`,
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
