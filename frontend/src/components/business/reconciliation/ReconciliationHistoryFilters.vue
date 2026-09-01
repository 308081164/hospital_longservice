<template>
  <div
    v-if="showFilters && !isLoading && hasAnyHistory"
    class="mb-4 flex flex-wrap items-center gap-3"
  >
    <ElInput
      v-model="searchDraft.keyword"
      :placeholder="t('reconciliation.history.filters.keywordPlaceholder')"
      clearable
      class="w-60"
      @keyup.enter="emit('search')"
    />
    <ElSelect
      v-model="searchDraft.reviewStatus"
      clearable
      :placeholder="t('reconciliation.history.filters.reviewStatus')"
      class="w-36"
    >
      <ElOption value="pending" :label="t('reconciliation.history.reviewStatus.pending')" />
      <ElOption value="approved" :label="t('reconciliation.history.reviewStatus.approved')" />
      <ElOption value="rejected" :label="t('reconciliation.history.reviewStatus.rejected')" />
    </ElSelect>
    <ElInput
      v-model="searchDraft.operator"
      :placeholder="t('reconciliation.history.filters.operatorPlaceholder')"
      clearable
      class="w-36"
      @keyup.enter="emit('search')"
    />
    <ElDatePicker
      v-model="searchDraft.dateRange"
      type="daterange"
      value-format="YYYY-MM-DD"
      :start-placeholder="t('reconciliation.history.filters.dateStart')"
      :end-placeholder="t('reconciliation.history.filters.dateEnd')"
      class="w-72"
    />
    <ElButton type="primary" size="small" @click="emit('search')">
      {{ t('reconciliation.history.filters.search') }}
    </ElButton>
    <ElButton size="small" @click="emit('reset')">
      {{ t('reconciliation.history.filters.reset') }}
    </ElButton>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'
  import type { ReconciliationHistorySearchForm } from '@/composables/useReconciliationHistory'

  defineProps<{
    searchDraft: ReconciliationHistorySearchForm
    isLoading: boolean
    hasAnyHistory: boolean
    showFilters?: boolean
  }>()

  const emit = defineEmits<{
    search: []
    reset: []
  }>()

  const { t } = useI18n()
</script>
