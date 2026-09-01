<template>
  <div
    v-if="savedJobId && group && item"
    class="entry-version-bar mt-3 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2.5"
    :class="{ 'border-blue-300 bg-blue-50': highlighted }"
  >
    <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
      <span class="text-xs font-medium text-gray-700">{{
        t('reconciliation.history.scoped.entryVersionTitle')
      }}</span>
      <RouterLink
        to="/settings/version-management"
        class="text-xs text-primary hover:underline"
      >
        {{ t('reconciliation.history.scoped.viewAllHistory') }} →
      </RouterLink>
    </div>
    <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
      <ElTag :type="reviewTagType(item.reviewStatus)" size="small" effect="plain">
        {{ reviewLabelMap[item.reviewStatus] ?? item.reviewStatus }}
      </ElTag>
      <ElSelect
        v-if="group.versions.length > 1"
        :model-value="item.id"
        size="small"
        class="min-w-52"
        @change="(id: number) => group && emit('version-change', group.key, id)"
      >
        <ElOption
          v-for="version in group.versions"
          :key="version.id"
          :value="version.id"
          :label="formatVersionLabel(version)"
        />
      </ElSelect>
      <span v-else class="text-xs text-gray-500">
        V{{ item.versionNo }} · {{ formatDateTime(item.createdAt) }}
      </span>
      <span class="text-xs text-gray-500">
        {{ t('reconciliation.history.stats.totalRows') }} {{ item.totalRows }}
      </span>
      <span class="text-xs text-gray-500">
        {{ t('reconciliation.history.stats.warningRows') }} {{ item.warningRows }}
      </span>
      <span class="text-xs text-gray-500">
        {{ t('reconciliation.history.stats.difference') }}
        {{ formatSignedNumber(item.totalDifference) }}
      </span>
      <div class="ml-auto flex flex-wrap items-center gap-2">
        <ElButton size="small" @click="actions?.openDetail(item)">
          {{ t('reconciliation.history.actions.detail') }}
        </ElButton>
        <ElButton
          size="small"
          :disabled="!canReview(item)"
          @click="actions?.openReview(item)"
        >
          {{ t('reconciliation.history.actions.review') }}
        </ElButton>
        <ElDropdown
          trigger="click"
          :disabled="!canExport"
          @command="(cmd: string) => item && actions?.requestExport(item, cmd)"
        >
          <ElButton size="small" :disabled="!canExport">
            {{ t('reconciliation.history.actions.export') }}
            <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
          </ElButton>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem
                v-for="exportType in resolveJobExportTypes(item)"
                :key="exportType"
                :command="exportType"
              >
                {{ t(exportTypeI18nKey(exportType)) }}
              </ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
      </div>
    </div>
  </div>
  <div
    v-else-if="savedJobId && !group && !loading"
    class="entry-version-bar mt-3 rounded-md border border-dashed border-gray-200 px-3 py-2 text-xs text-gray-400"
  >
    {{ t('reconciliation.history.scoped.noVersionsYet') }}
  </div>
</template>

<script setup lang="ts">
  import { inject } from 'vue'
  import { ArrowDown } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import { reconciliationJobActionsKey } from '@/composables/reconciliationJobActionsKey'
  import type { ReconciliationHistoryGroup } from '@/composables/useReconciliationHistory'
  import { useReconciliationTableColumns } from '@/composables/useReconciliationTableColumns'
  import { useBillingPermission } from '@/composables/useBillingPermission'
  import { formatReconciliationDateTime } from '@/utils/reconciliationFormat'
  import { exportTypeI18nKey, resolveJobExportTypes } from '@/utils/hospitalExportCapabilities'

  const props = defineProps<{
    group: ReconciliationHistoryGroup | null
    item: Api.Hospital.ReconciliationJob | null
    savedJobId: number | null
    highlighted?: boolean
    loading?: boolean
    formatVersionLabel: (version: Api.Hospital.ReconciliationJob) => string
  }>()

  const emit = defineEmits<{
    'version-change': [groupKey: string, jobId: number]
  }>()

  const actions = inject(reconciliationJobActionsKey, null)
  const { t } = useI18n()
  const { formatSignedNumber } = useReconciliationTableColumns()
  const { canReviewReconciliation, canExport } = useBillingPermission()

  const reviewLabelMap: Record<string, string> = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已驳回'
  }

  function reviewTagType(status: string): 'warning' | 'success' | 'danger' {
    switch (status) {
      case 'pending':
        return 'warning'
      case 'approved':
        return 'success'
      case 'rejected':
        return 'danger'
      default:
        return 'warning'
    }
  }

  function formatDateTime(value: string) {
    return formatReconciliationDateTime(value)
  }

  function canReview(job: Api.Hospital.ReconciliationJob) {
    return canReviewReconciliation.value && job.reviewStatus === 'pending'
  }
</script>
