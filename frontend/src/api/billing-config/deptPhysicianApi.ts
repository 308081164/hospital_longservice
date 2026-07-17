import request from '@/utils/http'

export interface DepartmentEntryRecord {
  id: number
  customer_id?: number
  customerId?: number
  department_name?: string
  departmentName?: string
  code?: string | null
  notes?: string | null
  usage_count?: number
  usageCount?: number
  is_active?: boolean
  isActive?: boolean
}

export interface PhysicianEntryRecord {
  id: number
  customer_id?: number
  customerId?: number
  physician_name?: string
  physicianName?: string
  department_entry_id?: number | null
  departmentEntryId?: number | null
  department_name?: string | null
  departmentName?: string | null
  code?: string | null
  notes?: string | null
  usage_count?: number
  usageCount?: number
  is_active?: boolean
  isActive?: boolean
}

export interface SaveDepartmentEntryPayload {
  departmentName: string
  code?: string | null
  notes?: string | null
  isActive?: boolean
}

export interface SavePhysicianEntryPayload {
  physicianName: string
  departmentEntryId?: number | null
  departmentName?: string | null
  code?: string | null
  notes?: string | null
  isActive?: boolean
}

export interface ListDeptPhysicianQuery {
  keyword?: string
  isActive?: boolean
}

export function listDepartmentEntries(customerId: number, query?: ListDeptPhysicianQuery) {
  return request.get<DepartmentEntryRecord[]>({
    url: `/api/v1/customers/${customerId}/departments`,
    params: query
  })
}

export function createDepartmentEntry(customerId: number, payload: SaveDepartmentEntryPayload) {
  return request.post<DepartmentEntryRecord>({
    url: `/api/v1/customers/${customerId}/departments`,
    data: payload
  })
}

export function updateDepartmentEntry(
  customerId: number,
  entryId: number,
  payload: SaveDepartmentEntryPayload
) {
  return request.put<DepartmentEntryRecord>({
    url: `/api/v1/customers/${customerId}/departments/${entryId}`,
    data: payload
  })
}

export function deleteDepartmentEntry(customerId: number, entryId: number) {
  return request.del<boolean>({
    url: `/api/v1/customers/${customerId}/departments/${entryId}`
  })
}

export function listPhysicianEntries(customerId: number, query?: ListDeptPhysicianQuery) {
  return request.get<PhysicianEntryRecord[]>({
    url: `/api/v1/customers/${customerId}/physicians`,
    params: query
  })
}

export function createPhysicianEntry(customerId: number, payload: SavePhysicianEntryPayload) {
  return request.post<PhysicianEntryRecord>({
    url: `/api/v1/customers/${customerId}/physicians`,
    data: payload
  })
}

export function updatePhysicianEntry(
  customerId: number,
  entryId: number,
  payload: SavePhysicianEntryPayload
) {
  return request.put<PhysicianEntryRecord>({
    url: `/api/v1/customers/${customerId}/physicians/${entryId}`,
    data: payload
  })
}

export function deletePhysicianEntry(customerId: number, entryId: number) {
  return request.del<boolean>({
    url: `/api/v1/customers/${customerId}/physicians/${entryId}`
  })
}

export function deptName(row: DepartmentEntryRecord): string {
  return row.department_name ?? row.departmentName ?? ''
}

export function physicianName(row: PhysicianEntryRecord): string {
  return row.physician_name ?? row.physicianName ?? ''
}
