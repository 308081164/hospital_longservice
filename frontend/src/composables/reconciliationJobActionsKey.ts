import type { InjectionKey } from 'vue'

export interface ReconciliationJobActions {
  openDetail: (item: Api.Hospital.ReconciliationJob) => void | Promise<void>
  openReview: (item: Api.Hospital.ReconciliationJob) => void | Promise<void>
  requestExport: (item: Api.Hospital.ReconciliationJob, type: string) => void | Promise<void>
}

export const reconciliationJobActionsKey: InjectionKey<ReconciliationJobActions> = Symbol(
  'reconciliationJobActions'
)
