<template>
  <div class="reconciliation-history-workspace">
    <div class="mb-4 flex items-start justify-between gap-3">
      <div>
        <h3 class="text-base font-semibold text-gray-800">{{ title }}</h3>
        <p v-if="subtitle" class="mt-1 text-sm text-gray-500">{{ subtitle }}</p>
      </div>
      <ElButton size="small" :loading="history.isHistoryLoading.value" @click="emit('refresh')">
        {{ t('reconciliation.history.refresh') }}
      </ElButton>
    </div>

    <ReconciliationHistoryFilters
      v-if="showFilters"
      :search-draft="history.historySearchDraft.value"
      :is-loading="history.isHistoryLoading.value"
      :has-any-history="history.historyItems.value.length > 0"
      @search="history.applyHistorySearch"
      @reset="history.resetHistorySearch"
    />

    <div
      v-if="history.isHistoryLoading.value"
      class="rounded-lg border border-dashed border-gray-300 px-4 py-6 text-center text-sm text-gray-400"
    >
      {{ t('reconciliation.history.loading') }}
    </div>

    <div
      v-else-if="history.historyItems.value.length === 0"
      class="rounded-lg border border-dashed border-gray-300 px-4 py-6 text-center text-sm text-gray-400"
    >
      {{ t('reconciliation.history.empty') }}
    </div>

    <div
      v-else-if="history.filteredHistoryGroups.value.length === 0"
      class="rounded-lg border border-dashed border-gray-300 px-4 py-6 text-center text-sm text-gray-400"
    >
      {{ t('reconciliation.history.noMatch') }}
    </div>

    <div v-else>
      <ReconciliationHistoryCardGrid
        :cards="history.paginatedHistoryCards.value"
        :highlighted-job-ids="history.highlightedJobIds.value"
        :format-version-label="history.formatHistoryVersionLabel"
        @detail="(item) => emit('detail', item)"
        @review="(item) => emit('review', item)"
        @export="(item, type) => emit('export', item, type)"
        @version-change="history.setGroupSelectedVersion"
      />
      <ReconciliationHistoryPagination
        v-if="showPagination"
        :total="history.filteredHistoryGroups.value.length"
        :current-page="history.historyFilterPage.value"
        :page-size="history.historyFilterPageSize.value"
        @page-change="(p) => (history.historyFilterPage.value = p)"
        @page-size-change="onPageSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'
  import type { useReconciliationHistory } from '@/composables/useReconciliationHistory'
  import ReconciliationHistoryCardGrid from '@/components/business/reconciliation/ReconciliationHistoryCardGrid.vue'
  import ReconciliationHistoryFilters from '@/components/business/reconciliation/ReconciliationHistoryFilters.vue'
  import ReconciliationHistoryPagination from '@/components/business/reconciliation/ReconciliationHistoryPagination.vue'

  const props = withDefaults(
    defineProps<{
      history: ReturnType<typeof useReconciliationHistory>
      title: string
      subtitle?: string
      showFilters?: boolean
      showPagination?: boolean
    }>(),
    {
      showFilters: true,
      showPagination: true
    }
  )

  const emit = defineEmits<{
    refresh: []
    detail: [item: Api.Hospital.ReconciliationJob]
    review: [item: Api.Hospital.ReconciliationJob]
    export: [item: Api.Hospital.ReconciliationJob, type: string]
  }>()

  const { t } = useI18n()

  function onPageSizeChange(size: number) {
    props.history.historyFilterPageSize.value = size
    props.history.historyFilterPage.value = 1
  }
</script>
