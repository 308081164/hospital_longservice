<template>
  <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
    <span class="text-sm text-gray-500">
      {{ t('reconciliation.history.pagination.total', { count: total }) }}
    </span>
    <div class="flex items-center gap-3">
      <ElSelect :model-value="pageSize" class="w-28" @change="onPageSizeChange">
        <ElOption :value="6" :label="t('reconciliation.history.pagination.size6')" />
        <ElOption :value="9" :label="t('reconciliation.history.pagination.size9')" />
        <ElOption :value="12" :label="t('reconciliation.history.pagination.size12')" />
      </ElSelect>
      <ElPagination
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        small
        background
        @current-change="(p: number) => emit('page-change', p)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'

  defineProps<{
    total: number
    currentPage: number
    pageSize: number
  }>()

  const emit = defineEmits<{
    'page-change': [page: number]
    'page-size-change': [size: number]
  }>()

  const { t } = useI18n()

  function onPageSizeChange(size: number) {
    emit('page-size-change', size)
  }
</script>
