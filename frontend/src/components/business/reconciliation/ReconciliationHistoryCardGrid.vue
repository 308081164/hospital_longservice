<template>
  <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
    <div
      v-for="group in cards"
      :key="group.key"
      class="rounded-lg border p-4"
      :class="[
        highlightedJobIds.has(group.item.id)
          ? 'border-blue-300 bg-blue-50'
          : 'border-gray-200 bg-gray-50',
        { 'opacity-60': group.item.reviewStatus === 'rejected' }
      ]"
    >
      <div class="flex items-start justify-between gap-2">
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <span class="truncate text-sm font-semibold text-gray-800">{{
              displayHospitalNameForJob(group.hospitalName, group.sourceFileName)
            }}</span>
            <ElTag :type="reviewTagType(group.item.reviewStatus)" size="small" effect="plain">
              {{ reviewLabelMap[group.item.reviewStatus] ?? group.item.reviewStatus }}
            </ElTag>
            <ElTag
              v-if="jobHasSpecialExport(group.item)"
              type="warning"
              size="small"
              effect="plain"
            >
              {{
                jobExportProfileLabel(group.item) ||
                t('reconciliation.history.specialExportBadge')
              }}
            </ElTag>
            <span v-if="group.versions.length > 1" class="text-xs text-gray-400">
              {{ t('reconciliation.history.versionCount', { count: group.versions.length }) }}
            </span>
          </div>
          <div class="mt-1 truncate text-xs font-medium text-gray-600">
            {{ group.sourceFileName }}
          </div>
          <div v-if="group.versions.length > 1" class="mt-2">
            <ElSelect
              :model-value="group.item.id"
              size="small"
              class="w-full"
              @change="(id: number) => emit('version-change', group.key, id)"
            >
              <ElOption
                v-for="version in group.versions"
                :key="version.id"
                :value="version.id"
                :label="formatVersionLabel(version)"
              />
            </ElSelect>
          </div>
          <div v-else class="mt-2 text-xs text-gray-500">
            V{{ group.item.versionNo }} · {{ formatDateTime(group.item.createdAt) }}
          </div>
          <div class="mt-2 flex flex-wrap gap-x-3 text-xs text-gray-500 leading-relaxed">
            <span
              >{{ t('reconciliation.history.operator') }}：{{ group.item.operatorName }}</span
            >
            <span v-if="group.item.reviewerName">
              {{ t('reconciliation.history.reviewer') }}：{{ group.item.reviewerName }}
            </span>
          </div>
          <div v-if="group.item.ruleName" class="text-xs text-gray-500 leading-relaxed">
            {{ t('reconciliation.history.rule') }}：{{ group.item.ruleName
            }}{{ group.item.ruleVersion ? `(${group.item.ruleVersion})` : '' }}
          </div>
        </div>
      </div>

      <div class="mt-3 flex flex-wrap gap-3 text-xs text-gray-500">
        <span>{{ t('reconciliation.history.stats.totalRows') }} {{ group.item.totalRows }}</span>
        <span
          >{{ t('reconciliation.history.stats.correctedRows') }}
          {{ group.item.correctedRows }}</span
        >
        <span
          >{{ t('reconciliation.history.stats.warningRows') }} {{ group.item.warningRows }}</span
        >
        <span>
          {{ t('reconciliation.history.stats.difference') }}
          {{ formatSignedNumber(group.item.totalDifference) }}
        </span>
      </div>

      <div class="mt-3 flex flex-wrap items-center gap-2 border-t border-gray-200 pt-3">
        <ElButton size="small" @click="emit('detail', group.item)">
          {{ t('reconciliation.history.actions.detail') }}
        </ElButton>
        <ElTooltip
          :disabled="canReview(group.item)"
          :content="reviewDisabledReason(group.item)"
          placement="top"
        >
          <span class="inline-flex">
            <ElButton
              size="small"
              :disabled="!canReview(group.item)"
              @click="emit('review', group.item)"
            >
              {{ t('reconciliation.history.actions.review') }}
            </ElButton>
          </span>
        </ElTooltip>
        <ElTooltip
          :disabled="canExport"
          :content="exportDisabledReason()"
          placement="top"
        >
          <span class="inline-flex">
            <ElDropdown
              trigger="click"
              :disabled="!canExport"
              @command="(cmd: string) => emit('export', group.item, cmd)"
            >
              <ElButton size="small" :disabled="!canExport">
                {{ t('reconciliation.history.actions.export') }}
                <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
              </ElButton>
              <template #dropdown>
                <ElDropdownMenu>
                  <ElDropdownItem
                    v-for="exportType in resolveJobExportTypes(group.item)"
                    :key="exportType"
                    :command="exportType"
                  >
                    {{ t(exportTypeI18nKey(exportType)) }}
                  </ElDropdownItem>
                </ElDropdownMenu>
              </template>
            </ElDropdown>
          </span>
        </ElTooltip>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ArrowDown } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import type { ReconciliationHistoryCard } from '@/composables/useReconciliationHistory'
  import { useReconciliationTableColumns } from '@/composables/useReconciliationTableColumns'
  import { useBillingPermission } from '@/composables/useBillingPermission'
  import { formatReconciliationDateTime } from '@/utils/reconciliationFormat'
  import { displayHospitalNameForJob } from '@/utils/reconciliationHospitalName'
  import {
    exportTypeI18nKey,
    jobExportProfileLabel,
    jobHasSpecialExport,
    resolveJobExportTypes
  } from '@/utils/hospitalExportCapabilities'

  defineProps<{
    cards: ReconciliationHistoryCard[]
    highlightedJobIds: Set<number>
    formatVersionLabel: (version: Api.Hospital.ReconciliationJob) => string
  }>()

  const emit = defineEmits<{
    detail: [item: Api.Hospital.ReconciliationJob]
    review: [item: Api.Hospital.ReconciliationJob]
    export: [item: Api.Hospital.ReconciliationJob, type: string]
    'version-change': [groupKey: string, jobId: number]
  }>()

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

  function canReview(item: Api.Hospital.ReconciliationJob) {
    return canReviewReconciliation.value && item.reviewStatus === 'pending'
  }

  function reviewDisabledReason(item: Api.Hospital.ReconciliationJob) {
    if (!canReviewReconciliation.value) {
      return t('reconciliation.history.actions.noReviewPermission')
    }
    if (item.reviewStatus !== 'pending') {
      return t('reconciliation.history.actions.alreadyReviewed', {
        status: reviewLabelMap[item.reviewStatus] ?? item.reviewStatus
      })
    }
    return ''
  }

  function exportDisabledReason() {
    if (!canExport.value) {
      return t('reconciliation.history.actions.noExportPermission')
    }
    return ''
  }
</script>
