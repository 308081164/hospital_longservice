<template>
  <div class="version-mgt-page p-6">
    <ElCard shadow="never">
      <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 class="text-base font-semibold text-gray-800">校对版本记录</h3>
          <p class="mt-1 text-sm text-gray-500">追踪系统所有校对版本的完整生命周期与审计信息。</p>
        </div>
        <ElButton size="small" :loading="loading" @click="loadData">刷新</ElButton>
      </div>

      <div class="mb-4 flex flex-wrap items-center gap-3">
        <ElInput v-model="searchText" placeholder="搜索医院名称 / 源文件名" clearable class="w-60" @input="currentPage = 1" />
        <ElSelect v-model="filterReviewStatus" clearable placeholder="审核状态" class="w-36" @change="currentPage = 1">
          <ElOption value="pending" label="待审核" />
          <ElOption value="approved" label="已通过" />
          <ElOption value="rejected" label="已驳回" />
        </ElSelect>
        <ElSelect v-model="pageSize" @change="currentPage = 1" class="w-28">
          <ElOption :value="10" label="10 条/页" />
          <ElOption :value="20" label="20 条/页" />
          <ElOption :value="50" label="50 条/页" />
        </ElSelect>
      </div>

      <!-- 按医院分组，同院同源文件折叠为一行，版本下拉切换 -->
      <div v-if="displayedHospitalGroups.length === 0 && !loading" class="py-10 text-center text-sm text-gray-400">
        {{ t('versionManagement.list.empty') }}
      </div>
      <div v-for="group in displayedHospitalGroups" :key="group.hospitalName" class="mb-6 last:mb-0">
        <div
          class="mb-2 flex cursor-pointer items-center gap-2 rounded-md bg-gray-100 px-3 py-2 hover:bg-gray-200"
          @click="toggleGroup(group.hospitalName)"
        >
          <ElIcon class="text-xs text-gray-400 transition-transform" :class="{ 'rotate-90': expandedGroups.has(group.hospitalName) }">
            <ArrowRight />
          </ElIcon>
          <span class="text-sm font-semibold text-gray-700">{{ group.hospitalName }}</span>
          <span class="text-xs text-gray-500">{{ t('versionManagement.list.sheetCount', { count: group.sheetCount }) }}</span>
        </div>

        <div v-show="expandedGroups.has(group.hospitalName)">
          <div v-for="fileGroup in group.fileGroups" :key="fileGroup.key" class="mb-4 last:mb-0">
            <div class="mb-2 flex flex-wrap items-center gap-2 px-1">
              <span class="text-sm font-medium text-gray-700">{{ fileGroup.sourceFileName }}</span>
              <span class="text-xs text-gray-400">
                {{ t('versionManagement.list.versionCount', { count: fileGroup.versions.length }) }}
              </span>
              <ElSelect
                :model-value="getSelectedJob(fileGroup).id"
                size="small"
                class="min-w-56"
                @change="(id: number) => setFileGroupSelectedVersion(fileGroup.key, id)"
              >
                <ElOption
                  v-for="version in fileGroup.versions"
                  :key="version.id"
                  :value="version.id"
                  :label="formatVersionLabel(version)"
                />
              </ElSelect>
            </div>
            <ElTable :data="getFileGroupRows(fileGroup)" stripe size="default" style="width: 100%" class="no-inner-border">
              <ElTableColumn prop="sheetName" label="科室" width="130" show-overflow-tooltip />
              <ElTableColumn label="数据摘要" width="150" align="center">
                <template #default="{ row }">
                  <div class="text-sm">
                    <div>总 {{ sheetRowCount(row) }} 行</div>
                    <div v-if="sheetWarningCount(row) > 0" class="text-warning">{{ sheetWarningCount(row) }} 项异常</div>
                    <div v-else class="text-gray-400">无异常</div>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作人" width="130">
                <template #default="{ row }">
                  <div class="text-sm">
                    <div>{{ row.operatorName }}</div>
                    <div class="text-gray-400">{{ formatTime(row.createdAt) }}</div>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="审核" width="130">
                <template #default="{ row }">
                  <div>
                    <ElTag :type="reviewTagType(row.reviewStatus)" size="small" effect="plain">
                      {{ reviewLabelMap[row.reviewStatus] ?? row.reviewStatus }}
                    </ElTag>
                    <div v-if="row.reviewerName" class="mt-1 text-sm text-gray-400">审核人：{{ row.reviewerName }}</div>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="160">
                <template #default="{ row }">
                  <ElButton size="small" @click="openDetail(row)">详情</ElButton>
                  <ElButton
                    size="small"
                    type="primary"
                    :disabled="row.reviewStatus !== 'pending' || !canReviewReconciliation"
                    @click="openReview(row)"
                  >
                    审核
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
        </div>
      </div>

      <div class="mt-4 flex items-center justify-between">
        <span class="text-sm text-gray-500">{{ t('versionManagement.list.totalFiles', { count: filteredFileGroups.length }) }}</span>
        <ElPagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="filteredFileGroups.length"
          layout="prev, pager, next"
          small
        />
      </div>
    </ElCard>

    <!-- 详情弹窗 -->
    <ElDialog v-model="detailVisible" :title="t('versionManagement.detail.title')" width="90%" top="3vh" class="max-h-[90vh]">
      <template v-if="detailLoading">
        <div class="py-10 text-center text-sm text-gray-400">{{ t('versionManagement.detail.loading') }}</div>
      </template>
      <template v-else-if="detailTarget">
        <div class="grid grid-cols-2 gap-4 rounded-lg bg-gray-50 p-4 text-sm md:grid-cols-4">
          <div><span class="text-gray-500">医院：</span><span class="font-medium">{{ detailTarget.hospitalName }}</span></div>
          <div><span class="text-gray-500">版本：</span><span class="font-medium">V{{ detailTarget.versionNo }}</span></div>
          <div><span class="text-gray-500">源文件：</span><span class="font-medium">{{ detailTarget.sourceFileName }}</span></div>
          <div><span class="text-gray-500">使用规则：</span><span class="font-medium">{{ detailTarget.ruleName || '-' }}{{ detailTarget.ruleVersion ? `(${detailTarget.ruleVersion})` : '' }}</span></div>
          <div><span class="text-gray-500">操作人：</span><span class="font-medium">{{ detailTarget.operatorName }}</span></div>
          <div><span class="text-gray-500">创建时间：</span><span class="font-medium">{{ formatTime(detailTarget.createdAt) }}</span></div>
          <div><span class="text-gray-500">审核状态：</span>
            <ElTag :type="reviewTagType(detailTarget.reviewStatus)" size="small" effect="plain">
              {{ reviewLabelMap[detailTarget.reviewStatus] }}
            </ElTag>
          </div>
          <div><span class="text-gray-500">审核人：</span><span class="font-medium">{{ detailTarget.reviewerName || '-' }}</span></div>
          <div v-if="detailSheetFilter" class="col-span-2">
            <span class="text-gray-500">{{ t('versionManagement.detail.sheetFilter', { sheet: detailSheetFilter }) }}</span>
          </div>
          <div class="col-span-2"><span class="text-gray-500">审核备注：</span><span class="font-medium">{{ detailTarget.reviewComment || '-' }}</span></div>
        </div>
        <div class="mt-4 grid grid-cols-2 gap-4 rounded-lg bg-gray-50 p-4 text-sm md:grid-cols-4">
          <div><span class="text-gray-500">总行数：</span><span class="font-medium">{{ detailTarget.totalRows }}</span></div>
          <div><span class="text-gray-500">已修正：</span><span class="font-medium text-primary">{{ detailTarget.correctedRows }}</span></div>
          <div><span class="text-gray-500">异常项：</span><span class="font-medium" :class="detailTarget.warningRows > 0 ? 'text-warning' : ''">{{ detailTarget.warningRows }}</span></div>
          <div><span class="text-gray-500">{{ t('versionManagement.detail.pendingReviewDifference') }}：</span><span class="font-medium" :class="(detailTarget.totalDifference ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'">{{ formatSignedNumber(detailTarget.totalDifference) }}</span></div>
        </div>

        <div class="mt-4">
          <h4 class="mb-2 text-sm font-medium text-gray-700">{{ t('versionManagement.detail.lineItems') }}</h4>
          <div v-if="detailLoadingRows" class="py-6 text-center text-sm text-gray-400">
            {{ t('versionManagement.detail.loadingRows') }}
          </div>
          <template v-else>
            <ElTable
              v-if="detailPaginatedRows.length > 0"
              :data="detailPaginatedRows"
              border
              stripe
              size="small"
              style="width: 100%"
              max-height="420"
              :default-sort="{ prop: 'rowNumber', order: 'ascending' }"
              :row-class-name="detailRowClassName"
            >
              <ElTableColumn prop="rowNumber" :label="t('versionManagement.detail.columns.rowNumber')" width="65" sortable />
              <ElTableColumn prop="sheetName" :label="t('versionManagement.detail.columns.sheetName')" min-width="90" />
              <ElTableColumn prop="deliveryDate" :label="t('versionManagement.detail.columns.deliveryDate')" width="110" />
              <ElTableColumn prop="type" :label="t('versionManagement.detail.columns.type')" min-width="90" />
              <ElTableColumn prop="packName" :label="t('versionManagement.detail.columns.packName')" min-width="140" show-overflow-tooltip />
              <ElTableColumn prop="packageMaterial" :label="t('versionManagement.detail.columns.packageMaterial')" min-width="110" />
              <ElTableColumn prop="instrumentCount" :label="t('versionManagement.detail.columns.instrumentCount')" width="70" align="right" />
              <ElTableColumn prop="packCount" :label="t('versionManagement.detail.columns.packCount')" width="60" align="right" />
              <ElTableColumn :label="t('versionManagement.detail.columns.unitPrice')" width="80" align="right">
                <template #default="{ row }">{{ formatNumber(row['unitPrice'] as number | null) }}</template>
              </ElTableColumn>
              <ElTableColumn :label="t('versionManagement.detail.columns.expectedUnitPrice')" width="80" align="right">
                <template #default="{ row }">{{ formatNumber(row['expectedUnitPrice'] as number | null) }}</template>
              </ElTableColumn>
              <ElTableColumn :label="t('versionManagement.detail.columns.totalPrice')" width="80" align="right">
                <template #default="{ row }">{{ formatNumber(row['totalPrice'] as number | null) }}</template>
              </ElTableColumn>
              <ElTableColumn :label="t('versionManagement.detail.columns.correctedTotalPrice')" width="90" align="right">
                <template #default="{ row }">{{ formatNumber(row['correctedTotalPrice'] as number | null) }}</template>
              </ElTableColumn>
              <ElTableColumn :label="t('versionManagement.detail.columns.difference')" width="100" align="right">
                <template #default="{ row }">
                  <span :class="((row['difference'] as number) ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'">
                    {{ formatSignedNumber(row['difference'] as number | null) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn :label="t('versionManagement.detail.columns.pricingRule')" min-width="140">
                <template #default="{ row }">
                  <PricingPathTag :row="row" @open-detail="openPricingFlowDetail" />
                </template>
              </ElTableColumn>
              <ElTableColumn :label="t('versionManagement.detail.columns.status')" width="100">
                <template #default="{ row }">
                  <ElTag :type="statusTagType(row['status'] as string)" size="small" effect="plain">
                    {{ statusLabel(row['status'] as string) }}
                  </ElTag>
                </template>
              </ElTableColumn>
            </ElTable>
            <div v-else class="text-sm text-gray-400">{{ t('versionManagement.detail.lineItemsEmpty') }}</div>
            <div v-if="detailPaginatedRows.length > 0" class="mt-3 flex items-center justify-between">
              <span class="text-xs text-gray-400">{{ t('versionManagement.detail.rowTotal', { total: detailRowsTotal }) }}</span>
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
          </template>
        </div>

        <div class="mt-4">
          <h4 class="mb-2 text-sm font-medium text-gray-700">导出/打印记录</h4>
          <div v-if="detailTarget.exports && detailTarget.exports.length > 0">
            <ElTable :data="detailTarget.exports" size="small" border style="width: 100%">
              <ElTableColumn prop="exportType" label="操作类型" width="130">
                <template #default="{ row }">
                  {{ exportTypeLabel(row.exportType) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
              <ElTableColumn prop="operatorName" label="操作人" width="90" />
              <ElTableColumn label="时间" width="150">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </ElTableColumn>
            </ElTable>
          </div>
          <div v-else class="text-sm text-gray-400">暂无导出/打印记录。</div>
        </div>
      </template>
    </ElDialog>

    <!-- 审核弹窗 -->
    <ElDialog v-model="reviewVisible" title="审核确认" width="420px">
      <template v-if="reviewTarget">
        <div class="space-y-4">
          <div class="rounded-lg bg-gray-50 p-3 text-sm">
            <div class="flex items-center justify-between">
              <span class="text-gray-500">任务：</span>
              <span class="font-medium">{{ reviewTarget.hospitalName }} · V{{ reviewTarget.versionNo }}</span>
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
                    <ElIcon
                      v-if="reviewForm.status === 'approved'"
                      class="review-conclusion-check"
                    >
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
                    <ElIcon
                      v-if="reviewForm.status === 'rejected'"
                      class="review-conclusion-check"
                    >
                      <Select />
                    </ElIcon>
                    {{ t('reconciliation.history.reviewConclusion.reject') }}
                  </span>
                </ElRadio>
              </div>
            </ElRadioGroup>
          </div>

          <ElFormItem label="审核备注">
            <ElInput v-model="reviewForm.comment" type="textarea" :rows="3" placeholder="可选，输入审核意见" />
          </ElFormItem>

          <div v-if="reviewForm.status === 'rejected'" class="rounded-lg bg-yellow-50 p-3 text-xs text-yellow-700">
            驳回后该版本将标记为不可用，需要重新上传校对并保存为新版本。
          </div>
        </div>
      </template>

      <template #footer>
        <div class="flex justify-end gap-2">
          <ElButton @click="reviewVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="isReviewing" @click="confirmReview">确认提交</ElButton>
        </div>
      </template>
    </ElDialog>

    <PricingFlowDrawer
      v-model:visible="pricingFlowDrawerVisible"
      :row="pricingFlowRow"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowRight, Select } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import {
  listHospitalReconciliations,
  updateHospitalReconciliationReview,
  getReconciliationDetail,
  getReconciliationRows,
} from '@/api/hospital/reconciliationsApi'
import { buildReconciliationVersionGroupKey, compareReconciliationGroupsByLatestActivity } from '@/utils/reconciliationVersionGroup'
import { useBillingPermission } from '@/composables/useBillingPermission'
import { runReviewPreflight } from '@/composables/reconciliationExportPreflight'
import PricingFlowDrawer from '@/components/business/reconciliation/PricingFlowDrawer.vue'
import PricingPathTag from '@/components/business/reconciliation/PricingPathTag.vue'

defineOptions({ name: 'VersionManagement' })

const { t } = useI18n()
const { canReviewReconciliation } = useBillingPermission()

const userStore = useUserStore()
const operatorName = ref(userStore.info.userName || '')

// 数据
const loading = ref(false)
const allJobs = ref<Api.Hospital.ReconciliationJob[]>([])
const searchText = ref('')
const filterReviewStatus = ref<string | undefined>(undefined)
const currentPage = ref(1)
const pageSize = ref(20)

const expandedGroups = ref<Set<string>>(new Set())
const fileGroupSelectedVersion = ref<Map<string, number>>(new Map())

interface FileVersionGroup {
  key: string
  hospitalName: string
  sourceFileName: string
  versions: Api.Hospital.ReconciliationJob[]
}

type SheetRow = Api.Hospital.ReconciliationJob & { sheetName: string }

const toggleGroup = (name: string) => {
  if (expandedGroups.value.has(name)) {
    expandedGroups.value.delete(name)
    expandedGroups.value = new Set(expandedGroups.value)
  } else {
    expandedGroups.value = new Set([...expandedGroups.value, name])
  }
}

// 详情弹窗
const detailVisible = ref(false)
const detailTarget = ref<Api.Hospital.ReconciliationJob | null>(null)
const detailLoading = ref(false)
const detailLoadingRows = ref(false)
const detailSheetFilter = ref<string | null>(null)
const detailRowsCache = ref(new Map<number, Record<string, unknown>[]>())
const detailRowsTotal = ref(0)
const detailPage = ref(1)
const detailPageSize = ref(200)

const detailPaginatedRows = computed(() => detailRowsCache.value.get(detailPage.value) ?? [])

const pricingFlowDrawerVisible = ref(false)
const pricingFlowRow = ref<Record<string, unknown> | null>(null)

function openPricingFlowDetail(row: Record<string, unknown>) {
  pricingFlowRow.value = row
  pricingFlowDrawerVisible.value = true
}

// 审核弹窗
const reviewVisible = ref(false)
const reviewTarget = ref<Api.Hospital.ReconciliationJob | null>(null)
const reviewForm = ref({ status: 'approved', comment: '' })
const isReviewing = ref(false)

const reviewLabelMap: Record<string, string> = {
  pending: '待审核', approved: '已通过', rejected: '已驳回',
}

const statusLabel = (status: string): string =>
  t(`versionManagement.detail.status.${status}`, status)

const statusTagType = (status: string): 'primary' | 'success' | 'info' | 'warning' => {
  switch (status) {
    case 'corrected': return 'primary'
    case 'unchanged': return 'success'
    case 'skipped': return 'info'
    case 'warning': return 'warning'
    default: return 'info'
  }
}

const reviewTagType = (status: string): 'warning' | 'success' | 'danger' => {
  switch (status) {
    case 'pending': return 'warning'
    case 'approved': return 'success'
    case 'rejected': return 'danger'
    default: return 'warning'
  }
}

const exportTypeLabel = (type: string): string => {
  const map: Record<string, string> = {
    bill: '导出账单',
    settlement: '导出结款函',
    html_settlement: '导出结款函(HTML)',
    print_bill: '打印账单',
    print_settlement: '打印结款函',
    warning: '导出异常项',
    result: '导出账单',
    department_summary: '导出分科室汇总',
  }
  return map[type] ?? type
}

// 搜索筛选（作用于聚合后的文件组）
function getFileGroupKey(job: Api.Hospital.ReconciliationJob): string {
  return buildReconciliationVersionGroupKey(job.hospitalName, job.sourceFileName)
}

const allFileGroups = computed<FileVersionGroup[]>(() => {
  const map = new Map<string, Api.Hospital.ReconciliationJob[]>()
  for (const job of allJobs.value) {
    const key = getFileGroupKey(job)
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(job)
  }
  return Array.from(map.entries())
    .map(([key, versions]) => {
    const sorted = versions.sort(
      (a, b) =>
        b.versionNo - a.versionNo
        || new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
    const hospitalName = sorted[0].hospitalName?.trim() || '(未命名)'
    const sourceFileName = sorted[0].sourceFileName?.trim() || '(未命名)'
    return { key, hospitalName, sourceFileName, versions: sorted }
  })
    .sort(compareReconciliationGroupsByLatestActivity)
})

const filteredFileGroups = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  const reviewStatus = filterReviewStatus.value

  return allFileGroups.value.filter((group) => {
    if (keyword) {
      const matched =
        group.hospitalName.toLowerCase().includes(keyword)
        || group.sourceFileName.toLowerCase().includes(keyword)
      if (!matched) return false
    }
    if (reviewStatus && group.versions[0]?.reviewStatus !== reviewStatus) {
      return false
    }
    return true
  })
})

const paginatedFileGroups = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredFileGroups.value.slice(start, start + pageSize.value)
})

const displayedHospitalGroups = computed(() => {
  const hospitalMap = new Map<string, FileVersionGroup[]>()
  for (const fileGroup of paginatedFileGroups.value) {
    const key = fileGroup.hospitalName
    if (!hospitalMap.has(key)) hospitalMap.set(key, [])
    hospitalMap.get(key)!.push(fileGroup)
  }
  return Array.from(hospitalMap.entries())
    .sort(([a], [b]) => a.localeCompare(b, 'zh-CN'))
    .map(([hospitalName, fileGroups]) => {
      const allSheetNames = new Set(
        fileGroups.flatMap((fg) => {
          const job = getSelectedJob(fg)
          return job.sheetNames && job.sheetNames.length > 0 ? job.sheetNames : ['(默认)']
        }),
      )
      return { hospitalName, fileGroups, sheetCount: allSheetNames.size }
    })
})

function getSelectedJob(group: FileVersionGroup): Api.Hospital.ReconciliationJob {
  const selectedId = fileGroupSelectedVersion.value.get(group.key)
  if (selectedId) {
    const matched = group.versions.find((v) => v.id === selectedId)
    if (matched) return matched
  }
  return group.versions[0]
}

function setFileGroupSelectedVersion(groupKey: string, jobId: number) {
  fileGroupSelectedVersion.value.set(groupKey, jobId)
  fileGroupSelectedVersion.value = new Map(fileGroupSelectedVersion.value)
}

function formatVersionLabel(version: Api.Hospital.ReconciliationJob): string {
  return t('versionManagement.list.versionOption', {
    version: version.versionNo,
    time: formatTime(version.createdAt),
  })
}

function getFileGroupRows(group: FileVersionGroup): SheetRow[] {
  const job = getSelectedJob(group)
  const sheets = job.sheetNames && job.sheetNames.length > 0 ? job.sheetNames : ['(默认)']
  return sheets.map((sheetName) => ({ ...job, sheetName }))
}

const loadData = async () => {
  try {
    loading.value = true
    allJobs.value = await listHospitalReconciliations()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载校对版本记录失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (value: string): string => {
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('zh-CN', { hour12: false })
}

const formatSignedNumber = (value: number | null): string => {
  if (value === null || value === undefined) return '-'
  const abs = Math.abs(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  if (value > 0) return `+${abs}`
  if (value < 0) return `-${abs}`
  return abs
}

const formatNumber = (value: number | null | undefined): string => {
  if (value == null) return '-'
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const detailRowClassName = ({ row }: { row: Record<string, unknown> }): string => {
  const diff = row['difference'] as number | null | undefined
  if (diff === null || diff === undefined) return ''
  return diff !== 0 ? 'detail-row-diff' : 'detail-row-ok'
}

const sheetRowCount = (row: Api.Hospital.ReconciliationJob & { sheetName: string }): number => {
  return row.sheetRowCounts?.[row.sheetName] ?? row.totalRows
}

const sheetWarningCount = (row: Api.Hospital.ReconciliationJob & { sheetName: string }): number => {
  return row.sheetWarningCounts?.[row.sheetName] ?? row.warningRows
}

const openDetail = async (row: Api.Hospital.ReconciliationJob & { sheetName?: string }) => {
  detailVisible.value = true
  detailLoading.value = true
  detailTarget.value = null
  detailRowsCache.value = new Map()
  detailRowsTotal.value = 0
  detailPage.value = 1
  detailSheetFilter.value = row.sheetName && row.sheetName !== '(默认)' ? row.sheetName : null
  try {
    const data = await getReconciliationDetail(row.id)
    detailTarget.value = data
    await loadDetailPage(1)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载版本详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function loadDetailPage(page: number) {
  if (detailRowsCache.value.has(page)) return
  if (!detailTarget.value) return
  detailLoadingRows.value = true
  try {
    const result = await getReconciliationRows(detailTarget.value.id, page, detailPageSize.value)
    detailRowsCache.value.set(page, (result.rows ?? []) as unknown as Record<string, unknown>[])
    detailRowsTotal.value = result.total
    detailRowsCache.value = new Map(detailRowsCache.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载条目失败')
  } finally {
    detailLoadingRows.value = false
  }
}

function onDetailPageChange(page: number) {
  detailPage.value = page
  loadDetailPage(page)
}

const openReview = async (item: Api.Hospital.ReconciliationJob) => {
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

const confirmReview = async () => {
  if (!reviewTarget.value) return
  isReviewing.value = true
  try {
    const updated = await updateHospitalReconciliationReview(reviewTarget.value.id, {
      reviewStatus: reviewForm.value.status,
      reviewComment: reviewForm.value.comment,
      reviewerName: operatorName.value.trim() || '未命名审核人',
    })
    ElMessage.success(`已${reviewForm.value.status === 'approved' ? '通过' : '驳回'}审核`)
    reviewVisible.value = false
    const idx = allJobs.value.findIndex((item) => item.id === reviewTarget.value!.id)
    if (idx >= 0) {
      allJobs.value[idx] = { ...allJobs.value[idx], ...updated }
    }
    if (detailTarget.value?.id === reviewTarget.value.id) {
      detailTarget.value = { ...detailTarget.value, ...updated }
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核失败')
  } finally {
    isReviewing.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* 去掉提交组表格的内部横线，只保留 stripe 色度区分 */
.no-inner-border :deep(td.el-table__cell),
.no-inner-border :deep(th.el-table__cell) {
  border-bottom: none;
}
.no-inner-border :deep(.el-table__inner-wrapper::before) {
  display: none;
}
/* 行高拉宽，占满屏幕 */
.no-inner-border :deep(td.el-table__cell) {
  padding: 14px 8px;
}
:deep(.detail-row-diff) {
  --el-table-tr-bg-color: var(--el-color-warning-light-9);
}
:deep(.detail-row-ok) {
  --el-table-tr-bg-color: var(--el-color-success-light-9);
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
