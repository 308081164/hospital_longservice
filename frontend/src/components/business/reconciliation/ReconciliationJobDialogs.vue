<template>
  <ReconciliationExportWizard
    v-model="exportWizardVisible"
    :job-id="exportWizardJob?.id"
    :hospital-name="exportWizardJob?.hospitalName"
    :initial-export-type="exportWizardInitialType"
    :allowed-export-types="exportWizardAllowedTypes"
    :monthly-breakdown="exportWizardJob?.monthlyBreakdown ?? null"
    :logistics-fee="exportWizardJob?.logisticsFee ?? null"
    :settlement-adjustment="exportWizardJob?.settlementAdjustment ?? null"
    @exported="handleWizardExported"
  />
  <ElDialog
    v-model="detailVisible"
    title="校对详情"
    width="90%"
    top="3vh"
    class="reconciliation-detail-dialog max-h-[90vh] flex flex-col"
  >
    <template v-if="detailLoading">
      <div class="py-10 text-center text-sm text-gray-400">正在加载详情...</div>
    </template>
    <template v-else-if="detailData">
      <div
        v-if="hasSettlementSummary"
        class="mb-4 rounded-lg border border-primary/20 bg-primary/5 p-4"
      >
        <div class="mb-2 text-sm font-semibold text-gray-800">
          {{ t('reconciliation.settlementSummary.title') }}
        </div>
        <div class="grid grid-cols-2 gap-3 text-sm md:grid-cols-4 lg:grid-cols-6">
          <div v-if="detailData.monthlyBreakdown?.rawSterilizeTotal != null">
            <span class="text-gray-500"
              >{{ t('reconciliation.settlementSummary.sterilizeTotal') }}：</span
            >
            <span class="font-medium">{{
              formatNumber(detailData.monthlyBreakdown.rawSterilizeTotal)
            }}</span>
          </div>
          <div v-if="detailData.logisticsFee != null && detailData.logisticsFee > 0">
            <span class="text-gray-500"
              >{{ t('reconciliation.settlementSummary.logisticsFee') }}：</span
            >
            <span class="font-medium">{{ formatNumber(detailData.logisticsFee) }}</span>
            <span
              v-if="detailData.logisticsBreakdown?.tripCount != null"
              class="ml-1 text-xs text-gray-400"
            >
              ({{ detailData.logisticsBreakdown.tripCount }}
              {{ t('reconciliation.settlementSummary.tripCount') }})
            </span>
          </div>
          <div v-if="detailData.monthlyBreakdown?.minCharge != null">
            <span class="text-gray-500"
              >{{ t('reconciliation.settlementSummary.minCharge') }}：</span
            >
            <span class="font-medium">{{
              formatNumber(detailData.monthlyBreakdown.minCharge)
            }}</span>
          </div>
          <div v-if="detailData.monthlyBreakdown?.maxCap != null">
            <span class="text-gray-500">{{ t('reconciliation.settlementSummary.maxCap') }}：</span>
            <span class="font-medium">{{ formatNumber(detailData.monthlyBreakdown.maxCap) }}</span>
          </div>
          <div
            v-if="detailData.settlementAdjustment != null && detailData.settlementAdjustment !== 0"
          >
            <span class="text-gray-500"
              >{{ t('reconciliation.settlementSummary.adjustment') }}：</span
            >
            <span class="font-medium text-primary">{{
              formatSignedNumber(detailData.settlementAdjustment)
            }}</span>
          </div>
          <div v-if="detailData.monthlyBreakdown?.adjustedTotal != null">
            <span class="text-gray-500"
              >{{ t('reconciliation.settlementSummary.adjustedTotal') }}：</span
            >
            <span class="font-medium">{{
              formatNumber(detailData.monthlyBreakdown.adjustedTotal)
            }}</span>
          </div>
          <div v-if="detailData.urgentBreakdown?.urgentRowCount">
            <span class="text-gray-500"
              >{{ t('reconciliation.settlementSummary.urgentRows') }}：</span
            >
            <span class="font-medium">{{ detailData.urgentBreakdown.urgentRowCount }}</span>
            <span
              v-if="detailData.urgentBreakdown.adjustedSurcharge != null"
              class="ml-1 text-xs text-gray-400"
            >
              (+{{ formatNumber(detailData.urgentBreakdown.adjustedSurcharge) }})
            </span>
          </div>
          <div v-if="detailData.deductionBreakdown?.deductionAmount">
            <span class="text-gray-500"
              >{{ t('reconciliation.settlementSummary.deduction') }}：</span
            >
            <span class="font-medium">{{
              formatSignedNumber(detailData.deductionBreakdown.deductionAmount)
            }}</span>
          </div>
        </div>
      </div>
      <ElCollapse
        v-if="detailLogisticsAllocation?.deptAllocations?.length"
        v-model="detailLogisticsCollapseActive"
        class="logistics-allocation-collapse mb-4"
      >
        <ElCollapseItem name="logistics-allocation">
          <template #title>
            <div class="flex w-full min-w-0 items-center justify-between gap-3 pr-2">
              <span class="truncate text-sm font-semibold text-gray-800">
                {{ t('reconciliation.logisticsAllocation.title') }}
                <span class="ml-1 text-xs font-normal text-gray-500">
                  {{
                    t('reconciliation.logisticsAllocation.deptCount', {
                      count: detailLogisticsAllocation.deptAllocations.length
                    })
                  }}
                </span>
              </span>
              <span class="shrink-0 text-xs text-gray-500">
                {{ t('reconciliation.logisticsAllocation.total') }}：
                {{ formatNumber(detailLogisticsAllocation.totalLogisticsFee) }}
              </span>
            </div>
          </template>
          <ElTable
            :data="detailLogisticsDeptPageData"
            size="small"
            border
            stripe
            max-height="200"
          >
            <ElTableColumn
              prop="department"
              :label="t('reconciliation.logisticsAllocation.department')"
              min-width="140"
            />
            <ElTableColumn
              prop="sterilizeTotal"
              :label="t('reconciliation.logisticsAllocation.sterilizeTotal')"
              width="120"
              align="right"
            >
              <template #default="{ row }">{{ formatNumber(row.sterilizeTotal) }}</template>
            </ElTableColumn>
            <ElTableColumn
              prop="ratio"
              :label="t('reconciliation.logisticsAllocation.ratio')"
              width="100"
              align="right"
            >
              <template #default="{ row }">{{ ((row.ratio ?? 0) * 100).toFixed(2) }}%</template>
            </ElTableColumn>
            <ElTableColumn
              prop="allocatedFee"
              :label="t('reconciliation.logisticsAllocation.allocatedFee')"
              width="120"
              align="right"
            >
              <template #default="{ row }">{{ formatNumber(row.allocatedFee) }}</template>
            </ElTableColumn>
          </ElTable>
          <div
            v-if="detailLogisticsAllocation.deptAllocations.length > detailLogisticsPageSize"
            class="mt-2 flex justify-end"
          >
            <ElPagination
              v-model:current-page="detailLogisticsPage"
              :page-size="detailLogisticsPageSize"
              :total="detailLogisticsAllocation.deptAllocations.length"
              layout="total, prev, pager, next"
              size="small"
              background
            />
          </div>
        </ElCollapseItem>
      </ElCollapse>
      <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div class="grid grid-cols-2 gap-4 rounded-lg bg-gray-50 p-4 text-sm md:grid-cols-4 flex-1">
          <div>
            <span class="text-gray-500">医院：</span>
            <span class="font-medium">{{
              displayHospitalNameForJob(detailData.hospitalName, detailData.sourceFileName)
            }}</span>
          </div>
          <div>
            <span class="text-gray-500">版本：</span>
            <span class="font-medium">V{{ detailData.versionNo }}</span>
            <span class="text-gray-400 ml-1 text-xs">{{
              formatDateTime(detailData.createdAt)
            }}</span>
          </div>
          <div>
            <span class="text-gray-500">操作人：</span>
            <span class="font-medium">{{ detailData.operatorName }}</span>
          </div>
          <div>
            <span class="text-gray-500">文件：</span>
            <span class="font-medium">{{ detailData.sourceFileName }}</span>
          </div>
          <div>
            <span class="text-gray-500">总行数：</span>
            <span class="font-medium">{{ detailData.totalRows }}</span>
          </div>
          <div>
            <span class="text-gray-500">已修正：</span>
            <span class="font-medium text-primary">{{ detailData.correctedRows }}</span>
          </div>
          <div>
            <span class="text-gray-500">待复核：</span>
            <span class="font-medium text-warning">{{ detailData.warningRows }}</span>
          </div>
          <div>
            <span class="text-gray-500"
              >{{ t('reconciliation.detail.pendingReviewDifference') }}：</span
            >
            <span
              class="font-medium"
              :class="(detailData.totalDifference ?? 0) >= 0 ? 'text-success' : 'text-danger'"
              >{{ formatSignedNumber(detailData.totalDifference) }}</span
            >
          </div>
          <div
            v-if="detailData.settlementAdjustment != null && detailData.settlementAdjustment !== 0"
          >
            <span class="text-gray-500">月度调整：</span>
            <span class="font-medium text-primary">{{
              formatSignedNumber(detailData.settlementAdjustment)
            }}</span>
            <span
              v-if="detailData.monthlyBreakdown?.adjustedTotal != null"
              class="text-xs text-gray-400 ml-1"
            >
              （结算 {{ formatNumber(detailData.monthlyBreakdown.adjustedTotal) }}）
            </span>
          </div>
          <div v-if="detailData.reviewComment" class="col-span-2">
            <span class="text-gray-500">审核备注：</span>
            <span class="font-medium">{{ detailData.reviewComment }}</span>
          </div>
        </div>
        <div class="flex flex-col gap-2 self-start">
          <ElButton
            type="warning"
            size="small"
            :disabled="
              detailSelectedRows.length === 0 ||
              detailData.reviewStatus !== 'pending' ||
              isMarkingUrgent ||
              !canEditReconciliationRows
            "
            :loading="isMarkingUrgent"
            @click="handleMarkUrgent(true)"
          >
            {{ t('reconciliation.detail.markUrgent') }}
          </ElButton>
          <ElButton
            size="small"
            :disabled="
              detailSelectedRows.length === 0 ||
              detailData.reviewStatus !== 'pending' ||
              isMarkingUrgent ||
              !canEditReconciliationRows
            "
            @click="handleMarkUrgent(false)"
          >
            {{ t('reconciliation.detail.unmarkUrgent') }}
          </ElButton>
          <ElButton
            type="warning"
            size="small"
            :disabled="
              !activeRule ||
              isFixingDetailRows ||
              detailData.reviewStatus !== 'pending' ||
              !canEditReconciliationRows
            "
            :loading="isFixingDetailRows"
            @click="handleFixDetailRows"
          >
            一键修正
          </ElButton>
          <ElButton
            type="primary"
            size="small"
            :disabled="
              isSavingDetailRows ||
              detailData.reviewStatus !== 'pending' ||
              !canEditReconciliationRows
            "
            :loading="isSavingDetailRows"
            @click="handleSaveDetailRows"
          >
            保存修改
          </ElButton>
        </div>
      </div>
      <UatHelperPanel
        v-if="detailData?.reviewStatus === 'approved'"
        :hospital-name="detailData?.hospitalName"
        :job-id="detailData?.id"
      />
      <ReconciliationAllocationPanel
        :allocation="detailAllocation"
        :running="isRunningAllocation"
        :exporting="isExportingOrchestrated"
        :can-operate="canEditReconciliationRows"
        :can-export="canExport"
        @run-allocation="handleRunAllocation"
        @export-orchestrated="handleExportOrchestrated"
      />
      <ElTabs v-model="detailRowTab" class="mb-3">
        <ElTabPane :label="t('reconciliation.detail.tabRegular')" name="regular" />
        <ElTabPane
          :label="t('reconciliation.detail.tabExternal', { count: detailExternalRows.length })"
          name="external"
        />
      </ElTabs>

      <ElTable
        v-if="detailRowTab === 'external'"
        :data="detailExternalRows"
        border
        stripe
        size="small"
        max-height="500"
      >
        <ElTableColumn prop="department" label="科室" min-width="100" />
        <ElTableColumn prop="categoryNo" label="包类别号" min-width="120" />
        <ElTableColumn prop="packName" label="包名" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="usageDate" label="使用日期" width="110" />
        <ElTableColumn prop="packCount" label="包数" width="70" align="right" />
        <ElTableColumn label="单价" width="90" align="right">
          <template #default="{ row }">{{ formatNumber(row.unitPrice) }}</template>
        </ElTableColumn>
        <ElTableColumn label="合计" width="90" align="right">
          <template #default="{ row }">{{ formatNumber(row.totalAmount) }}</template>
        </ElTableColumn>
      </ElTable>

      <ReconciliationDataTable
        v-show="detailRowTab === 'regular'"
        :rows="detailPaginatedRows"
        mode="detail"
        show-sheet-column
        max-height="500"
        :editable="detailData?.reviewStatus === 'pending'"
        :roster-hint-map="detailRosterHintMap"
        :row-class-name="detailRowClassName"
        :row-selectable="detailRowSelectable"
        @selection-change="onDetailSelectionChange"
        @open-pricing-flow="openPricingFlowDetail"
        @row-edit="onDetailRowEdit"
        @row-change="onDetailRowChange"
        @fix-single-row="handleFixSingleRow"
      />

      <div class="mt-3 flex items-center justify-between">
        <span class="text-xs text-gray-400">共 {{ detailRowsTotal }} 行</span>
        <ElPagination
          v-if="detailRowsTotal > detailPageSize"
          :current-page="detailPage"
          :page-size="detailPageSize"
          :total="detailRowsTotal"
          layout="prev, pager, next"
          size="small"
          background
          @current-change="onDetailPageChange"
        />
      </div>

      <div v-if="detailData?.reviewStatus !== 'pending'" class="mt-2 text-xs text-gray-400">
        该版本已审核，数据为只读。
      </div>
    </template>
  </ElDialog>

  <!-- 审核确认弹窗 -->
  <ElDialog v-model="reviewVisible" title="审核确认" width="420px">
    <template v-if="reviewTarget">
      <div class="space-y-4">
        <div class="rounded-lg bg-gray-50 p-3 text-sm">
          <div class="flex items-center justify-between">
            <span class="text-gray-500">任务：</span>
            <span class="font-medium"
              >{{ displayHospitalNameForJob(reviewTarget.hospitalName, reviewTarget.sourceFileName) }} · {{ formatDateTime(reviewTarget.createdAt) }}</span
            >
          </div>
          <div class="mt-1 flex items-center justify-between">
            <span class="text-gray-500">当前状态：</span>
            <ElTag :type="reviewTagType(reviewTarget.reviewStatus)" size="small" effect="plain">
              {{ reviewLabelMap[reviewTarget.reviewStatus] }}
            </ElTag>
          </div>
        </div>

        <div>
          <label class="mb-2 block text-sm font-medium text-gray-700">{{
            t('reconciliation.history.reviewConclusion.label')
          }}</label>
          <ElRadioGroup v-model="reviewForm.status" class="review-conclusion-group">
            <div
              class="review-conclusion-card"
              :class="{ 'is-selected is-approve': reviewForm.status === 'approved' }"
              @click="reviewForm.status = 'approved'"
            >
              <ElRadio value="approved" class="review-conclusion-radio">
                <span class="review-conclusion-label">
                  <ElIcon v-if="reviewForm.status === 'approved'" class="review-conclusion-check">
                    <Select />
                  </ElIcon>
                  {{ t('reconciliation.history.reviewConclusion.approve') }}
                </span>
              </ElRadio>
            </div>
            <div
              class="review-conclusion-card"
              :class="{ 'is-selected is-reject': reviewForm.status === 'rejected' }"
              @click="reviewForm.status = 'rejected'"
            >
              <ElRadio value="rejected" class="review-conclusion-radio">
                <span class="review-conclusion-label">
                  <ElIcon v-if="reviewForm.status === 'rejected'" class="review-conclusion-check">
                    <Select />
                  </ElIcon>
                  {{ t('reconciliation.history.reviewConclusion.reject') }}
                </span>
              </ElRadio>
            </div>
          </ElRadioGroup>
        </div>

        <ElFormItem label="审核备注">
          <ElInput
            v-model="reviewForm.comment"
            type="textarea"
            :rows="3"
            placeholder="可选，输入审核意见"
          />
        </ElFormItem>

        <div
          v-if="reviewForm.status === 'rejected'"
          class="rounded-lg bg-yellow-50 p-3 text-xs text-yellow-700"
        >
          驳回后该版本将标记为不可用，需要重新上传校对并保存为新版本。
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <ElButton @click="reviewVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="isReviewing" @click="confirmReview"> 确认提交 </ElButton>
      </div>
    </template>
  </ElDialog>

  <PricingFlowDrawer
    v-model:visible="pricingFlowDrawerVisible"
    :row="pricingFlowRow"
  />
</template>

<script setup lang="ts">
  import { ref, computed, provide } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { Select } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import { useUserStore } from '@/store/modules/user'
  import {
    repriceReconciliation,
    updateHospitalReconciliationReview,
    createHospitalReconciliationExportLog,
    getReconciliationDetail,
    updateHospitalReconciliationRows,
    updateReconciliationRowsUrgent,
    getReconciliationRows
  } from '@/api/hospital/reconciliationsApi'
  import {
    exportOrchestratedWorkbook,
    getJobAllocationResult,
    getJobRosterHints,
    runJobAllocation,
    type AllocationResult,
    type RosterMatchHint
  } from '@/api/billing-config/allocationApi'
  import {
    listJobExternalInstruments,
    type ExternalInstrumentRecord
  } from '@/api/billing-config/externalInstrumentsApi'
  import {
    getLogisticsAllocationPreview,
    type LogisticsAllocationPreview
  } from '@/api/billing-config/logisticsApi'
  import ReconciliationDataTable from '@/components/business/reconciliation/ReconciliationDataTable.vue'
  import ReconciliationExportWizard from '@/components/business/reconciliation/ReconciliationExportWizard.vue'
  import ReconciliationAllocationPanel from '@/components/business/reconciliation/ReconciliationAllocationPanel.vue'
  import UatHelperPanel from '@/components/business/reconciliation/UatHelperPanel.vue'
  import PricingFlowDrawer from '@/components/business/reconciliation/PricingFlowDrawer.vue'
  import { useBillingPermission } from '@/composables/useBillingPermission'
  import { useReconciliationTableColumns } from '@/composables/useReconciliationTableColumns'
  import { reconciliationJobActionsKey } from '@/composables/reconciliationJobActionsKey'
  import {
    runExportPreflight,
    runReviewPreflight
  } from '@/composables/reconciliationExportPreflight'
  import { fieldConsistencyRowClass } from '@/utils/reconciliationBillingNotes'
  import { formatReconciliationDateTime } from '@/utils/reconciliationFormat'
  import { displayHospitalNameForJob } from '@/utils/reconciliationHospitalName'
  import { resolveJobExportTypes } from '@/utils/hospitalExportCapabilities'

  const props = defineProps<{
    activeRule: Api.Hospital.PricingRuleRecord | null
    operatorName?: string
  }>()

  const emit = defineEmits<{
    'history-changed': []
    'patch-history': [job: Api.Hospital.ReconciliationJob]
  }>()

  const { t } = useI18n()
  const { formatNumber, formatSignedNumber } = useReconciliationTableColumns()
  const { canEditReconciliationRows, canReviewReconciliation, canExport } = useBillingPermission()
  const userStore = useUserStore()
  const operatorName = computed(() => props.operatorName?.trim() || userStore.info.userName || '')

  const reviewLabelMap: Record<string, string> = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已驳回'
  }

  const reviewTagType = (status: string): 'warning' | 'success' | 'danger' => {
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

  const detailVisible = ref(false)
  const detailData = ref<Api.Hospital.ReconciliationJob | null>(null)
  const detailLoading = ref(false)
  const detailRowsCache = ref(new Map<number, Record<string, unknown>[]>())
  const detailRowsTotal = ref(0)
  const detailPage = ref(1)
  const detailPageSize = ref(200)
  const detailLoadingRows = ref(false)
  const detailPaginatedRows = computed(() => detailRowsCache.value.get(detailPage.value) ?? [])
  const detailRosterHints = ref<RosterMatchHint[]>([])
  const detailRosterHintMap = computed(() => {
    const map = new Map<number, RosterMatchHint>()
    for (const hint of detailRosterHints.value) {
      if (hint.rowNumber != null) map.set(hint.rowNumber, hint)
    }
    return map
  })
  const hasSettlementSummary = computed(() => {
    const job = detailData.value
    if (!job) return false
    return Boolean(
      job.monthlyBreakdown ||
        job.urgentBreakdown?.urgentRowCount ||
        job.deductionBreakdown?.deductionAmount ||
        (job.logisticsFee != null && job.logisticsFee > 0) ||
        (job.settlementAdjustment != null && job.settlementAdjustment !== 0)
    )
  })
  const detailSelectedRows = ref<Record<string, unknown>[]>([])
  const isMarkingUrgent = ref(false)
  const isFixingDetailRows = ref(false)
  const isSavingDetailRows = ref(false)
  const detailRowTab = ref<'regular' | 'external'>('regular')
  const detailExternalRows = ref<ExternalInstrumentRecord[]>([])
  const detailAllocation = ref<AllocationResult | null>(null)
  const detailLogisticsAllocation = ref<LogisticsAllocationPreview | null>(null)
  const detailLogisticsCollapseActive = ref<string[]>([])
  const detailLogisticsPage = ref(1)
  const detailLogisticsPageSize = 10
  const detailLogisticsDeptPageData = computed(() => {
    const rows = detailLogisticsAllocation.value?.deptAllocations ?? []
    const start = (detailLogisticsPage.value - 1) * detailLogisticsPageSize
    return rows.slice(start, start + detailLogisticsPageSize)
  })
  const isRunningAllocation = ref(false)
  const isExportingOrchestrated = ref(false)
  const pricingFlowDrawerVisible = ref(false)
  const pricingFlowRow = ref<Record<string, unknown> | null>(null)

  const reviewVisible = ref(false)
  const reviewTarget = ref<Api.Hospital.ReconciliationJob | null>(null)
  const reviewForm = ref({ status: 'approved', comment: '' })
  const isReviewing = ref(false)

  const exportWizardVisible = ref(false)
  const exportWizardJob = ref<Api.Hospital.ReconciliationJob | null>(null)
  const exportWizardInitialType = ref('bill')
  const exportWizardAllowedTypes = ref<string[]>(['bill', 'settlement'])

  function openPricingFlowDetail(row: Record<string, unknown>) {
    pricingFlowRow.value = row
    pricingFlowDrawerVisible.value = true
  }

  async function openDetail(item: Api.Hospital.ReconciliationJob) {
    detailVisible.value = true
    detailLoading.value = true
    detailData.value = null
    detailRowsCache.value = new Map()
    detailRowsTotal.value = 0
    detailPage.value = 1
    detailRowTab.value = 'regular'
    detailExternalRows.value = []
    detailRosterHints.value = []
    detailAllocation.value = null
    detailLogisticsAllocation.value = null
    detailLogisticsCollapseActive.value = []
    detailLogisticsPage.value = 1
    try {
      const data = await getReconciliationDetail(item.id)
      detailData.value = data
      await loadDetailPage(1)
      await loadDetailL3Context(item.id)
      await loadDetailLogisticsAllocation(item.id)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '加载详情失败')
      detailVisible.value = false
    } finally {
      detailLoading.value = false
    }
  }

  async function loadDetailL3Context(jobId: number) {
    try {
      detailRosterHints.value = await getJobRosterHints(jobId)
    } catch {
      detailRosterHints.value = []
    }
    try {
      detailExternalRows.value = await listJobExternalInstruments(jobId)
    } catch {
      detailExternalRows.value = []
    }
    try {
      detailAllocation.value = (await getJobAllocationResult(jobId)) ?? null
    } catch {
      detailAllocation.value = null
    }
  }

  async function loadDetailLogisticsAllocation(jobId: number) {
    try {
      detailLogisticsAllocation.value = await getLogisticsAllocationPreview(jobId)
    } catch {
      detailLogisticsAllocation.value = null
    }
  }

  async function loadDetailPage(page: number) {
    if (detailRowsCache.value.has(page)) return
    if (!detailData.value) return
    detailLoadingRows.value = true
    try {
      const result = await getReconciliationRows(detailData.value.id, page, detailPageSize.value)
      detailRowsCache.value.set(page, (result.rows ?? []) as unknown as Record<string, unknown>[])
      detailRowsTotal.value = result.total
      detailRowsCache.value = new Map(detailRowsCache.value)
    } finally {
      detailLoadingRows.value = false
    }
  }

  function onDetailPageChange(p: number) {
    detailPage.value = p
    loadDetailPage(p)
  }

  function onDetailRowEdit(row: Record<string, unknown>, val: string | number | null) {
    const ctp = typeof val === 'string' ? (val ? parseFloat(val) : null) : (val as number | null)
    row['correctedTotalPrice'] = ctp
    const tp = row['totalPrice'] as number | null | undefined
    if (ctp != null && tp != null) {
      row['difference'] = Math.round((ctp - tp) * 100) / 100
    } else {
      row['difference'] = null
    }
  }

  function onDetailRowChange() {
    void updateDetailSummary()
  }

  function detailRowClassName({ row }: { row: Record<string, unknown> }): string {
    const classes: string[] = []
    if (row['isUrgent']) classes.push('detail-row-urgent')
    const fieldClass = fieldConsistencyRowClass(row)
    if (fieldClass) classes.push(fieldClass)
    const diff = row['difference'] as number | null | undefined
    if (diff === null || diff === undefined) return classes.join(' ')
    classes.push(diff !== 0 ? 'detail-row-diff' : 'detail-row-ok')
    return classes.join(' ')
  }

  function detailRowSelectable() {
    return detailData.value?.reviewStatus === 'pending'
  }

  function onDetailSelectionChange(rows: Record<string, unknown>[]) {
    detailSelectedRows.value = rows
  }

  async function fetchAllDetailRows(): Promise<Record<string, unknown>[]> {
    if (!detailData.value) return []
    const totalPages = Math.ceil(detailRowsTotal.value / detailPageSize.value) || 1
    const tasks: Promise<void>[] = []
    for (let p = 1; p <= totalPages; p++) {
      if (!detailRowsCache.value.has(p)) tasks.push(loadDetailPage(p))
    }
    await Promise.all(tasks)
    const all: Record<string, unknown>[] = []
    for (let p = 1; p <= totalPages; p++) {
      const pageRows = detailRowsCache.value.get(p)
      if (pageRows) all.push(...pageRows)
    }
    return all
  }

  async function updateDetailSummary() {
    if (!detailData.value) return
    const allRows = await fetchAllDetailRows()
    let corrected = 0
    let warning = 0
    let totalDiff = 0
    for (const row of allRows) {
      const status = row['status'] as string
      if (status === 'corrected') corrected++
      if (status === 'warning') {
        warning++
        totalDiff += (row['difference'] as number) ?? 0
      }
    }
    detailData.value = {
      ...detailData.value,
      correctedRows: corrected,
      warningRows: warning,
      totalDifference: Math.round(totalDiff * 100) / 100
    }
  }

  function applySingleRowCorrection(row: Record<string, unknown>) {
    const ctp = row['correctedTotalPrice'] as number | null
    const totalPrice = row['totalPrice'] as number | null
    if (ctp != null && totalPrice != null) {
      row['difference'] = Math.round((ctp - totalPrice) * 100) / 100
    }
    row['status'] = 'corrected'
  }

  async function handleFixSingleRow(row: Record<string, unknown>) {
    applySingleRowCorrection(row)
    await updateDetailSummary()
  }

  async function handleMarkUrgent(isUrgent: boolean) {
    if (!detailData.value || detailSelectedRows.value.length === 0) return
    isMarkingUrgent.value = true
    try {
      const rowIds = detailSelectedRows.value
        .map((row) => row['id'] as number | undefined)
        .filter((id): id is number => id != null)
      const rows = detailSelectedRows.value.map((row) => ({
        sheetName: String(row['sheetName'] ?? ''),
        rowNumber: Number(row['rowNumber'] ?? 0)
      }))
      const updated = await updateReconciliationRowsUrgent(detailData.value.id, {
        isUrgent,
        rowIds: rowIds.length > 0 ? rowIds : undefined,
        rows: rowIds.length > 0 ? undefined : rows
      })
      detailData.value = { ...detailData.value, ...updated }
      detailRowsCache.value = new Map()
      detailPage.value = 1
      detailSelectedRows.value = []
      await loadDetailPage(1)
      ElMessage.success(
        isUrgent
          ? t('reconciliation.detail.markUrgentSuccess')
          : t('reconciliation.detail.unmarkUrgentSuccess')
      )
    } catch (error) {
      ElMessage.error(
        error instanceof Error ? error.message : t('reconciliation.detail.markUrgentFailed')
      )
    } finally {
      isMarkingUrgent.value = false
    }
  }

  async function handleFixDetailRows() {
    if (!detailData.value) return
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
      return
    }
    isFixingDetailRows.value = true
    try {
      const result = await repriceReconciliation(detailData.value.id)
      const allRows = result.rows
      const newCache = new Map<number, Record<string, unknown>[]>()
      const pageSize = detailPageSize.value
      for (let i = 0; i < allRows.length; i += pageSize) {
        const page = Math.floor(i / pageSize) + 1
        newCache.set(page, allRows.slice(i, i + pageSize))
      }
      detailRowsTotal.value = allRows.length
      detailRowsCache.value = newCache
      const maxPage = Math.ceil(allRows.length / detailPageSize.value) || 1
      if (detailPage.value > maxPage) detailPage.value = 1
      detailData.value = {
        ...detailData.value,
        totalRows: result.summary.total,
        correctedRows: result.summary.corrected,
        unchangedRows: result.summary.unchanged,
        warningRows: result.summary.warning,
        skippedRows: result.summary.skipped,
        totalDifference: result.summary.totalDifference
      }
      const changedCount = result.summary.corrected + result.summary.warning
      ElMessage.success(
        `一键修正完成，共 ${result.summary.total} 行已重新计算（${changedCount} 行有差异），请确认后点击「保存修改」`
      )
    } catch (error) {
      ElMessage.error(
        error instanceof Error ? error.message : t('reconciliation.detail.batchFixFailed')
      )
    } finally {
      isFixingDetailRows.value = false
    }
  }

  async function handleSaveDetailRows() {
    if (!detailData.value) return
    const allRows = await fetchAllDetailRows()
    if (allRows.length === 0) return
    const previousJobId = detailData.value.id
    const previousVersionNo = detailData.value.versionNo
    isSavingDetailRows.value = true
    try {
      const updated = await updateHospitalReconciliationRows(detailData.value.id, allRows)
      const versionUpgraded =
        updated.id !== previousJobId || updated.versionNo !== previousVersionNo
      detailData.value = { ...detailData.value, ...updated }
      detailRowsCache.value = new Map()
      detailPage.value = 1
      await loadDetailPage(1)
      emit('patch-history', updated)
      emit('history-changed')
      if (versionUpgraded) {
        ElMessage.success(t('reconciliation.detail.saveSuccess', { version: updated.versionNo }))
      } else {
        ElMessage.info(t('reconciliation.detail.saveNoChange'))
      }
    } catch (error) {
      ElMessage.error(
        error instanceof Error ? error.message : t('reconciliation.detail.saveFailed')
      )
    } finally {
      isSavingDetailRows.value = false
    }
  }

  async function handleRunAllocation() {
    if (!detailData.value) return
    isRunningAllocation.value = true
    try {
      detailAllocation.value = await runJobAllocation(detailData.value.id)
      ElMessage.success(detailAllocation.value.balanceMessage ?? '科室分配完成')
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '分配失败')
    } finally {
      isRunningAllocation.value = false
    }
  }

  async function handleExportOrchestrated() {
    if (!detailData.value) return
    isExportingOrchestrated.value = true
    try {
      const blob = await exportOrchestratedWorkbook(detailData.value.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${detailData.value.hospitalName}_L3导出.xlsx`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      isExportingOrchestrated.value = false
    }
  }

  function applyJobPatchToLists(updated: Api.Hospital.ReconciliationJob) {
    if (detailData.value?.id === updated.id) {
      detailData.value = { ...detailData.value, ...updated }
    }
    if (exportWizardJob.value?.id === updated.id) {
      exportWizardJob.value = { ...exportWizardJob.value, ...updated }
    }
    emit('patch-history', updated)
  }

  async function openReview(item: Api.Hospital.ReconciliationJob) {
    const ok = await runReviewPreflight(item, {
      t,
      onOpenDetail: () => {
        void openDetail(item)
      }
    })
    if (!ok) return
    reviewTarget.value = item
    reviewForm.value = { status: 'approved', comment: '' }
    reviewVisible.value = true
  }

  async function requestExport(item: Api.Hospital.ReconciliationJob, type: string) {
    if (!canExport.value) return
    const outcome = await runExportPreflight(item, {
      reviewerName: operatorName.value.trim() || '未命名审核人',
      t,
      onOpenDetail: () => {
        void openDetail(item)
      }
    })
    if (!outcome.proceed) return
    applyJobPatchToLists(outcome.job)
    openExportWizard(outcome.job, type)
  }

  function openExportWizard(item: Api.Hospital.ReconciliationJob, type: string) {
    exportWizardJob.value = item
    exportWizardAllowedTypes.value = resolveJobExportTypes(item)
    exportWizardInitialType.value = exportWizardAllowedTypes.value.includes(type)
      ? type
      : (exportWizardAllowedTypes.value[0] ?? 'bill')
    exportWizardVisible.value = true
  }

  async function handleWizardExported(payload: { exportType: string; fileName: string }) {
    if (!exportWizardJob.value) return
    try {
      await createHospitalReconciliationExportLog(exportWizardJob.value.id, {
        exportType: payload.exportType,
        fileName: payload.fileName,
        operatorName: operatorName.value.trim() || '未命名操作人'
      })
    } catch {
      // ignore
    }
  }

  async function confirmReview() {
    if (!reviewTarget.value) return
    isReviewing.value = true
    try {
      const updated = await updateHospitalReconciliationReview(reviewTarget.value.id, {
        reviewStatus: reviewForm.value.status,
        reviewComment: reviewForm.value.comment,
        reviewerName: operatorName.value.trim() || '未命名审核人'
      })
      ElMessage.success(`已${reviewForm.value.status === 'approved' ? '通过' : '驳回'}审核`)
      reviewVisible.value = false
      emit('patch-history', updated)
      if (detailData.value?.id === reviewTarget.value.id) {
        detailData.value = { ...detailData.value, ...updated }
      }
      emit('history-changed')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '审核失败')
    } finally {
      isReviewing.value = false
    }
  }

  provide(reconciliationJobActionsKey, {
    openDetail,
    openReview,
    requestExport
  })

  defineExpose({ openDetail, openReview, requestExport })
</script>


<style scoped>
  :deep(.reconciliation-detail-dialog.el-dialog) {
    display: flex;
    flex-direction: column;
    max-height: 90vh;
  }

  :deep(.reconciliation-detail-dialog .el-dialog__body) {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
    overscroll-behavior: contain;
  }

  .logistics-allocation-collapse {
    border: none;
  }

  .logistics-allocation-collapse :deep(.el-collapse-item__header) {
    height: auto;
    min-height: 40px;
    padding: 8px 16px;
    line-height: 1.4;
    background-color: rgb(255 251 235 / 0.6);
    border: 1px solid rgb(253 230 138);
    border-radius: 0.5rem;
  }

  .logistics-allocation-collapse :deep(.el-collapse-item__wrap) {
    background-color: rgb(255 251 235 / 0.6);
    border: 1px solid rgb(253 230 138);
    border-top: none;
    border-radius: 0 0 0.5rem 0.5rem;
  }

  .logistics-allocation-collapse :deep(.el-collapse-item.is-active > .el-collapse-item__header) {
    border-radius: 0.5rem 0.5rem 0 0;
  }

  .logistics-allocation-collapse :deep(.el-collapse-item__content) {
    padding: 0 16px 12px;
  }

  .review-conclusion-group {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    width: 100%;
  }

  .review-conclusion-card {
    display: flex;
    align-items: center;
    min-height: 44px;
    padding: 10px 14px;
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    background: var(--el-fill-color-blank);
    cursor: pointer;
    transition:
      border-color 0.15s ease,
      background-color 0.15s ease,
      box-shadow 0.15s ease;
  }

  .review-conclusion-card:hover {
    border-color: var(--el-color-primary-light-5);
  }

  .review-conclusion-card.is-selected.is-approve {
    border-color: var(--el-color-success);
    background: var(--el-color-success-light-9);
    box-shadow: inset 0 0 0 1px var(--el-color-success-light-5);
  }

  .review-conclusion-card.is-selected.is-reject {
    border-color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
    box-shadow: inset 0 0 0 1px var(--el-color-danger-light-5);
  }

  .review-conclusion-radio {
    margin-right: 0;
    height: auto;
    width: 100%;
  }

  .review-conclusion-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-weight: 500;
  }

  .review-conclusion-check {
    font-size: 16px;
  }

  .review-conclusion-card.is-approve .review-conclusion-check {
    color: var(--el-color-success);
  }

  .review-conclusion-card.is-reject .review-conclusion-check {
    color: var(--el-color-danger);
  }
</style>

