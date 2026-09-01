import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  repriceReconciliation,
  updateHospitalReconciliationRows
} from '@/api/hospital/reconciliationsApi'

const SOURCE_FIELDS = new Set(['packageMaterial', 'instrumentCount', 'type'])

export function buildReconciliationRowKey(row: Record<string, unknown>): string {
  const sheet = String(row.sheetName ?? row.sheet_name ?? '')
  const rowNumber = row.rowNumber ?? row.row_number
  return `${sheet}::${rowNumber}`
}

export function isAnomalyEditableRow(row: Record<string, unknown>): boolean {
  const status = String(row.status ?? '')
  if (status === 'warning' || status === 'corrected') return true
  const billingNotes = row.billingNotes ?? row.billing_notes
  if (billingNotes && typeof billingNotes === 'object') {
    const notes = billingNotes as Record<string, unknown>
    if (notes.blocksPricing === true) return true
    const validation = notes.billingValidation as Record<string, unknown> | undefined
    if (validation?.blocksPricing === true) return true
  }
  return false
}

export function useReconciliationEntryEditing() {
  const { t } = useI18n()
  const dirtyRows = ref(new Map<string, Record<string, unknown>>())
  const dirtyFields = ref(new Map<string, Set<string>>())
  const isSaving = ref(false)
  const isRepricing = ref(false)
  const pendingRepricedRows = ref<Record<string, unknown>[] | null>(null)

  const hasDirty = computed(() => dirtyRows.value.size > 0 || pendingRepricedRows.value !== null)

  const needsRepriceOnSave = computed(() => {
    for (const fields of dirtyFields.value.values()) {
      for (const field of fields) {
        if (SOURCE_FIELDS.has(field)) return true
      }
    }
    return false
  })

  function markDirty(row: Record<string, unknown>, field: string, value: unknown) {
    const key = buildReconciliationRowKey(row)
    const patch = dirtyRows.value.get(key) ?? {}
    patch[field] = value
    dirtyRows.value.set(key, patch)
    dirtyRows.value = new Map(dirtyRows.value)

    const fields = dirtyFields.value.get(key) ?? new Set<string>()
    fields.add(field)
    dirtyFields.value.set(key, fields)
    dirtyFields.value = new Map(dirtyFields.value)

    row[field] = value
    pendingRepricedRows.value = null

    if (field === 'correctedTotalPrice') {
      const corrected = Number(value)
      const total = Number(row.totalPrice ?? 0)
      if (Number.isFinite(corrected) && Number.isFinite(total)) {
        row.difference = corrected - total
      }
    }
  }

  function clearDirty() {
    dirtyRows.value = new Map()
    dirtyFields.value = new Map()
    pendingRepricedRows.value = null
  }

  function mergeRowsWithDirty(allRows: Record<string, unknown>[]): Record<string, unknown>[] {
    if (dirtyRows.value.size === 0) return allRows
    return allRows.map((row) => {
      const key = buildReconciliationRowKey(row)
      const patch = dirtyRows.value.get(key)
      return patch ? { ...row, ...patch } : row
    })
  }

  function applySummaryToEntry(
    entry: {
      savedSummary: {
        total: number
        corrected: number
        unchanged: number
        warning: number
        skipped: number
        totalDifference: number
        originalTotalPrice: number
        correctedTotalPrice: number
      } | null
      displayTotal: number
      savedJobId: number | null
    },
    job: Api.Hospital.ReconciliationJob
  ) {
    entry.savedJobId = job.id
    entry.savedSummary = {
      total: job.totalRows ?? 0,
      corrected: job.correctedRows ?? 0,
      unchanged: job.unchangedRows ?? 0,
      warning: job.warningRows ?? 0,
      skipped: job.skippedRows ?? 0,
      totalDifference: job.totalDifference ?? 0,
      originalTotalPrice: job.originalTotalPrice ?? 0,
      correctedTotalPrice: job.correctedTotalPrice ?? 0
    }
    entry.displayTotal = job.totalRows ?? entry.displayTotal
  }

  async function saveEntryRows(
    jobId: number,
    fetchAllRows: () => Promise<Record<string, unknown>[]>,
    options?: {
      onJobUpdated?: (job: Api.Hospital.ReconciliationJob) => void | Promise<void>
      reloadCurrentPage?: () => Promise<void>
    }
  ): Promise<boolean> {
    if (!hasDirty.value && !needsRepriceOnSave.value) return false
    isSaving.value = true
    try {
      let rowsToSave: Record<string, unknown>[]
      if (pendingRepricedRows.value) {
        rowsToSave = mergeRowsWithDirty(pendingRepricedRows.value)
      } else {
        const allRows = await fetchAllRows()
        rowsToSave = mergeRowsWithDirty(allRows)
      }

      let updated = await updateHospitalReconciliationRows(jobId, rowsToSave)

      if (needsRepriceOnSave.value && !pendingRepricedRows.value) {
        const repriceResult = await repriceReconciliation(updated.id)
        const repricedRows = (repriceResult.rows ?? []) as Record<string, unknown>[]
        updated = await updateHospitalReconciliationRows(updated.id, repricedRows)
      }

      clearDirty()
      await options?.onJobUpdated?.(updated)
      await options?.reloadCurrentPage?.()
      ElMessage.success(t('reconciliation.inlineEdit.saveSuccess'))
      return true
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : t('reconciliation.detail.saveFailed'))
      return false
    } finally {
      isSaving.value = false
    }
  }

  async function repriceAndStage(
    jobId: number,
    options?: {
      onRepriced?: (rows: Record<string, unknown>[], summary: Record<string, unknown>) => void
    }
  ): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        t('reconciliation.detail.batchFixConfirmMessage'),
        t('reconciliation.detail.batchFixConfirmTitle'),
        {
          confirmButtonText: t('reconciliation.detail.batchFixConfirmButton'),
          cancelButtonText: t('reconciliation.detail.batchFixCancelButton'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }

    isRepricing.value = true
    try {
      const result = await repriceReconciliation(jobId)
      const rows = (result.rows ?? []) as Record<string, unknown>[]
      pendingRepricedRows.value = rows
      dirtyRows.value = new Map()
      dirtyFields.value = new Map()
      options?.onRepriced?.(rows, result.summary as Record<string, unknown>)
      ElMessage.success(t('reconciliation.inlineEdit.repriceStaged'))
      return true
    } catch (error) {
      ElMessage.error(
        error instanceof Error ? error.message : t('reconciliation.detail.batchFixFailed')
      )
      return false
    } finally {
      isRepricing.value = false
    }
  }

  return {
    dirtyRows,
    hasDirty,
    needsRepriceOnSave,
    isSaving,
    isRepricing,
    markDirty,
    clearDirty,
    mergeRowsWithDirty,
    saveEntryRows,
    repriceAndStage,
    applySummaryToEntry
  }
}
