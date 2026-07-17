import request from '@/utils/http'

export interface CustomerGroupMemberRecord {
  id?: number
  group_id?: number
  groupId?: number
  customer_id?: number
  customerId?: number
  share_ratio?: number | null
  shareRatio?: number | null
}

export interface CustomerGroupRecord {
  id: number
  name: string
  group_type?: string
  groupType?: string
  config?: string | null
  is_active?: boolean
  isActive?: boolean
  members?: CustomerGroupMemberRecord[]
}

export interface SaveCustomerGroupPayload {
  name: string
  groupType: string
  config?: string | null
  isActive?: boolean
  members?: Array<{ customerId: number; shareRatio?: number | null }>
}

export function listCustomerGroups(groupType?: string) {
  return request.get<CustomerGroupRecord[]>({
    url: '/api/v1/customer-groups',
    params: groupType ? { groupType } : undefined
  })
}

export function getCustomerGroup(id: number) {
  return request.get<CustomerGroupRecord>({
    url: `/api/v1/customer-groups/${id}`
  })
}

export function createCustomerGroup(payload: SaveCustomerGroupPayload) {
  return request.post<CustomerGroupRecord>({
    url: '/api/v1/customer-groups',
    data: payload
  })
}

export function updateCustomerGroup(id: number, payload: SaveCustomerGroupPayload) {
  return request.put<CustomerGroupRecord>({
    url: `/api/v1/customer-groups/${id}`,
    data: payload
  })
}

export function deleteCustomerGroup(id: number) {
  return request.del<boolean>({
    url: `/api/v1/customer-groups/${id}`
  })
}

export interface SaveLogisticsAllocationConfigPayload {
  allocationMode: string
  groupName?: string
  memberCustomerIds?: number[]
  shareRatios?: Record<number, number>
  mergeSameDay?: boolean
  syncToMembers?: boolean
  billingWeekdays?: number[]
  excludeDepartments?: string[]
  singleOwnerCustomerId?: number
}

export interface LogisticsAllocationConfigRecord {
  group_id?: number
  groupId?: number
  group_name?: string
  groupName?: string
  allocation_mode?: string
  allocationMode?: string
  member_customer_ids?: number[]
  memberCustomerIds?: number[]
  share_ratios?: Record<number, number>
  shareRatios?: Record<number, number>
  merge_same_day?: boolean
  mergeSameDay?: boolean
  sync_to_members?: boolean
  syncToMembers?: boolean
  billing_weekdays?: number[]
  billingWeekdays?: number[]
  exclude_departments?: string[]
  excludeDepartments?: string[]
  single_owner_customer_id?: number
  singleOwnerCustomerId?: number
  synced_policy_count?: number
  syncedPolicyCount?: number
}

export function syncCustomerGroupAllocationConfig(
  groupId: number,
  payload: SaveLogisticsAllocationConfigPayload
) {
  return request.put<LogisticsAllocationConfigRecord>({
    url: `/api/v1/customer-groups/${groupId}/allocation-config`,
    data: payload
  })
}
