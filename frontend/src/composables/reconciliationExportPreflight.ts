import { ElMessageBox } from 'element-plus'
import { updateHospitalReconciliationReview } from '@/api/hospital/reconciliationsApi'

export type ReconciliationJobRef = Api.Hospital.ReconciliationJob

export interface ExportPreflightOptions {
  reviewerName: string
  t: (key: string, params?: Record<string, unknown>) => string
  onOpenDetail?: () => void
}

export type ExportPreflightOutcome =
  | { proceed: true; job: ReconciliationJobRef }
  | { proceed: false }

function pendingReviewCount(job: ReconciliationJobRef): number {
  return job.warningRows ?? 0
}

/** 导出前：待复核提醒 / 无待复核时可选同步标记审核通过 */
export async function runExportPreflight(
  job: ReconciliationJobRef,
  options: ExportPreflightOptions
): Promise<ExportPreflightOutcome> {
  let currentJob = job
  const pending = pendingReviewCount(job)

  if (pending > 0) {
    try {
      await ElMessageBox.confirm(
        options.t('reconciliation.exportPreflight.pendingReviewMessage', { count: pending }),
        options.t('reconciliation.exportPreflight.pendingReviewTitle'),
        {
          type: 'warning',
          confirmButtonText: options.t('reconciliation.exportPreflight.proceedExport'),
          cancelButtonText: options.t('reconciliation.exportPreflight.viewDetail'),
          distinguishCancelAndClose: true
        }
      )
    } catch (action) {
      if (action === 'cancel') {
        options.onOpenDetail?.()
      }
      return { proceed: false }
    }
  } else if (currentJob.reviewStatus === 'pending') {
    try {
      await ElMessageBox.confirm(
        options.t('reconciliation.exportPreflight.markApprovedMessage'),
        options.t('reconciliation.exportPreflight.markApprovedTitle'),
        {
          type: 'info',
          confirmButtonText: options.t('reconciliation.exportPreflight.markApprovedAndExport'),
          cancelButtonText: options.t('reconciliation.exportPreflight.exportOnly'),
          distinguishCancelAndClose: true
        }
      )
      const updated = await updateHospitalReconciliationReview(currentJob.id, {
        reviewStatus: 'approved',
        reviewComment: '',
        reviewerName: options.reviewerName
      })
      currentJob = { ...currentJob, ...updated }
    } catch (action) {
      if (action === 'close') {
        return { proceed: false }
      }
    }
  }

  return { proceed: true, job: currentJob }
}

/** 打开审核弹窗前：仍有待复核时提醒可先查看详情 */
export async function runReviewPreflight(
  job: ReconciliationJobRef,
  options: Omit<ExportPreflightOptions, 'reviewerName'>
): Promise<boolean> {
  const pending = pendingReviewCount(job)
  if (pending <= 0) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      options.t('reconciliation.exportPreflight.reviewPendingMessage', { count: pending }),
      options.t('reconciliation.exportPreflight.reviewPendingTitle'),
      {
        type: 'warning',
        confirmButtonText: options.t('reconciliation.exportPreflight.proceedReview'),
        cancelButtonText: options.t('reconciliation.exportPreflight.viewDetail'),
        distinguishCancelAndClose: true
      }
    )
    return true
  } catch (action) {
    if (action === 'cancel') {
      options.onOpenDetail?.()
    }
    return false
  }
}
