import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { listHospitalReconciliations } from '@/api/hospital/reconciliationsApi'
import {
  buildReconciliationVersionGroupKey,
  compareReconciliationGroupsByLatestActivity
} from '@/utils/reconciliationVersionGroup'
import { displayHospitalNameForJob } from '@/utils/reconciliationHospitalName'
import { formatReconciliationDateTime } from '@/utils/reconciliationFormat'

export interface ReconciliationHistoryGroup {
  key: string
  hospitalName: string
  sourceFileName: string
  versions: Api.Hospital.ReconciliationJob[]
}

export interface ReconciliationHistorySearchForm {
  keyword: string
  reviewStatus?: string
  operator: string
  dateRange: [string, string] | null
}

export interface ReconciliationHistoryCard {
  key: string
  hospitalName: string
  sourceFileName: string
  versions: Api.Hospital.ReconciliationJob[]
  item: Api.Hospital.ReconciliationJob
}

export function createEmptyHistorySearch(): ReconciliationHistorySearchForm {
  return {
    keyword: '',
    reviewStatus: undefined,
    operator: '',
    dateRange: null
  }
}

export function buildEntryScopeKeys(
  entries: Array<{ hospitalName: string; file: { name: string } }>
): Set<string> {
  return new Set(
    entries.map((entry) => buildReconciliationVersionGroupKey(entry.hospitalName, entry.file.name))
  )
}

export function findHistoryGroupForEntry(
  groups: ReconciliationHistoryGroup[],
  hospitalName: string,
  fileName: string
): ReconciliationHistoryGroup | null {
  const key = buildReconciliationVersionGroupKey(hospitalName, fileName)
  return groups.find((group) => group.key === key) ?? null
}

export function useReconciliationHistory() {
  const { t } = useI18n()

  const historyItems = ref<Api.Hospital.ReconciliationJob[]>([])
  const isHistoryLoading = ref(false)
  const historySearchDraft = ref<ReconciliationHistorySearchForm>(createEmptyHistorySearch())
  const historySearchApplied = ref<ReconciliationHistorySearchForm>(createEmptyHistorySearch())
  const historyFilterPage = ref(1)
  const historyFilterPageSize = ref(9)
  const historyGroupSelectedVersion = ref<Map<string, number>>(new Map())
  const highlightedJobIds = ref<Set<number>>(new Set())
  const scopeKeys = ref<Set<string> | null>(null)

  function getHistoryGroupKey(item: Api.Hospital.ReconciliationJob): string {
    return buildReconciliationVersionGroupKey(item.hospitalName, item.sourceFileName)
  }

  const historyGroups = computed<ReconciliationHistoryGroup[]>(() => {
    const map = new Map<string, Api.Hospital.ReconciliationJob[]>()
    for (const item of historyItems.value) {
      const key = getHistoryGroupKey(item)
      if (!map.has(key)) map.set(key, [])
      map.get(key)!.push(item)
    }
    return Array.from(map.entries())
      .map(([key, versions]) => {
        const sorted = versions.sort(
          (a, b) =>
            b.versionNo - a.versionNo ||
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )
        return {
          key,
          hospitalName:
            sorted[0].hospitalName?.trim() || t('reconciliation.history.unnamedHospital'),
          sourceFileName: sorted[0].sourceFileName?.trim() || '(未命名)',
          versions: sorted
        }
      })
      .sort(compareReconciliationGroupsByLatestActivity)
  })

  const scopedHistoryGroups = computed(() => {
    if (!scopeKeys.value || scopeKeys.value.size === 0) {
      return historyGroups.value
    }
    return historyGroups.value.filter((group) => scopeKeys.value!.has(group.key))
  })

  const filteredHistoryGroups = computed(() => {
    const { keyword, reviewStatus, operator, dateRange } = historySearchApplied.value
    const normalizedKeyword = keyword.trim().toLowerCase()
    const normalizedOperator = operator.trim().toLowerCase()

    return scopedHistoryGroups.value.filter((group) => {
      if (normalizedKeyword) {
        const keywordMatched =
          displayHospitalNameForJob(group.hospitalName, group.sourceFileName)
            .toLowerCase()
            .includes(normalizedKeyword) ||
          group.hospitalName.toLowerCase().includes(normalizedKeyword) ||
          group.sourceFileName.toLowerCase().includes(normalizedKeyword) ||
          group.versions.some((version) =>
            version.sourceFileName.toLowerCase().includes(normalizedKeyword)
          )
        if (!keywordMatched) return false
      }

      if (reviewStatus && group.versions[0]?.reviewStatus !== reviewStatus) {
        return false
      }

      if (
        normalizedOperator &&
        !group.versions.some((version) =>
          version.operatorName.toLowerCase().includes(normalizedOperator)
        )
      ) {
        return false
      }

      if (dateRange?.[0] && dateRange?.[1]) {
        const start = new Date(`${dateRange[0]}T00:00:00`).getTime()
        const end = new Date(`${dateRange[1]}T23:59:59`).getTime()
        const dateMatched = group.versions.some((version) => {
          const createdAt = new Date(version.createdAt).getTime()
          return createdAt >= start && createdAt <= end
        })
        if (!dateMatched) return false
      }

      return true
    })
  })

  const paginatedHistoryGroups = computed(() => {
    const start = (historyFilterPage.value - 1) * historyFilterPageSize.value
    return filteredHistoryGroups.value.slice(start, start + historyFilterPageSize.value)
  })

  const paginatedHistoryCards = computed<ReconciliationHistoryCard[]>(() =>
    paginatedHistoryGroups.value.map((group) => ({
      ...group,
      item: getGroupSelectedVersion(group)
    }))
  )

  function getGroupSelectedVersion(group: ReconciliationHistoryGroup): Api.Hospital.ReconciliationJob {
    const selectedId = historyGroupSelectedVersion.value.get(group.key)
    if (selectedId) {
      const matched = group.versions.find((version) => version.id === selectedId)
      if (matched) return matched
    }
    return group.versions[0]
  }

  function setGroupSelectedVersion(groupKey: string, jobId: number) {
    historyGroupSelectedVersion.value.set(groupKey, jobId)
    historyGroupSelectedVersion.value = new Map(historyGroupSelectedVersion.value)
  }

  function formatHistoryVersionLabel(version: Api.Hospital.ReconciliationJob): string {
    return `V${version.versionNo} · ${formatReconciliationDateTime(version.createdAt)} · ${version.sourceFileName}`
  }

  function applyHistorySearch() {
    historySearchApplied.value = {
      keyword: historySearchDraft.value.keyword,
      reviewStatus: historySearchDraft.value.reviewStatus,
      operator: historySearchDraft.value.operator,
      dateRange: historySearchDraft.value.dateRange
        ? [...historySearchDraft.value.dateRange]
        : null
    }
    historyFilterPage.value = 1
  }

  function resetHistorySearch() {
    const empty = createEmptyHistorySearch()
    historySearchDraft.value = { ...empty }
    historySearchApplied.value = { ...empty }
    historyFilterPage.value = 1
  }

  function patchHistoryItem(updated: Api.Hospital.ReconciliationJob) {
    const idx = historyItems.value.findIndex((item) => item.id === updated.id)
    if (idx >= 0) {
      historyItems.value[idx] = { ...historyItems.value[idx], ...updated }
      historyItems.value = [...historyItems.value]
    }
  }

  function highlightJob(jobId: number) {
    highlightedJobIds.value.add(jobId)
    highlightedJobIds.value = new Set(highlightedJobIds.value)
  }

  async function loadHistoryAll() {
    scopeKeys.value = null
    await fetchAndSetHistory([undefined])
  }

  async function loadHistoryForHospitals(hospitalNames: string[]) {
    const unique = [...new Set(hospitalNames.map((name) => name.trim()).filter(Boolean))]
    if (unique.length === 0) {
      historyItems.value = []
      return
    }
    await fetchAndSetHistory(unique)
  }

  async function fetchAndSetHistory(hospitalFilters: Array<string | undefined>) {
    try {
      isHistoryLoading.value = true
      const results = await Promise.all(
        hospitalFilters.map((name) => listHospitalReconciliations(name))
      )
      const merged = new Map<number, Api.Hospital.ReconciliationJob>()
      for (const list of results) {
        for (const item of list) {
          merged.set(item.id, item)
        }
      }
      historyItems.value = Array.from(merged.values())
    } catch (error) {
      ElMessage.error(
        error instanceof Error ? error.message : t('reconciliation.history.loadFailed')
      )
    } finally {
      isHistoryLoading.value = false
    }
  }

  function setScopeKeys(keys: Set<string> | null) {
    scopeKeys.value = keys
  }

  return {
    historyItems,
    isHistoryLoading,
    historySearchDraft,
    historySearchApplied,
    historyFilterPage,
    historyFilterPageSize,
    highlightedJobIds,
    historyGroups,
    scopedHistoryGroups,
    filteredHistoryGroups,
    paginatedHistoryCards,
    getGroupSelectedVersion,
    setGroupSelectedVersion,
    formatHistoryVersionLabel,
    applyHistorySearch,
    resetHistorySearch,
    patchHistoryItem,
    highlightJob,
    loadHistoryAll,
    loadHistoryForHospitals,
    setScopeKeys,
    findGroupForEntry: (hospitalName: string, fileName: string) =>
      findHistoryGroupForEntry(scopedHistoryGroups.value, hospitalName, fileName)
  }
}
