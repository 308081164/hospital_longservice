<template>
  <div class="p-4">
    <ElAlert
      v-if="!activeRule && !isRuleLoading"
      type="warning"
      :closable="false"
      class="mb-4"
      show-icon
    >
      当前未加载到后端医院规则，已禁止按本地默认规则计算。请先检查规则接口或确认已启用规则。
    </ElAlert>


    <div class="grid grid-cols-1 gap-6">
      <ElCard
        shadow="never"
        class="reconciliation-workspace"
        :class="{ 'workspace-dragover': isDragOverWorkspace }"
        @dragenter.prevent="onWorkspaceDragEnter"
        @dragover.prevent
        @dragleave.prevent="onWorkspaceDragLeave"
        @drop.prevent="onWorkspaceDrop"
      >
        <div class="workspace-header">
          <div class="workspace-header__leading">
            <div class="flex items-center gap-2">
              <h3 class="text-base font-semibold text-gray-800">{{
                t('reconciliation.upload.title')
              }}</h3>
              <BillingRoleBadge />
            </div>
            <p class="workspace-header__subtitle">{{ t('reconciliation.upload.subtitle') }}</p>
          </div>
          <div class="workspace-header__trailing">
            <span v-if="isRuleLoading" class="text-xs text-gray-400">
              {{ t('reconciliation.upload.ruleLoading') }}
            </span>
            <ReconciliationNoticeBell :active-rule="activeRule" />
            <ElUpload
              :auto-upload="false"
              accept=".xls,.xlsx"
              :show-file-list="false"
              :on-change="handleUploadChange"
              multiple
              class="header-upload"
            >
              <ElButton type="primary" plain size="small">
                {{ t('reconciliation.upload.dropHint') }}
              </ElButton>
            </ElUpload>
          </div>
        </div>

        <div
          v-if="uploadEntries.length === 0"
          class="workspace-empty"
        >
          <p class="workspace-empty__text">{{ t('reconciliation.upload.empty') }}</p>
          <p class="workspace-empty__note">{{ t('reconciliation.upload.dropNote') }}</p>
        </div>

        <div
          v-for="(entry, entryIndex) in uploadEntries"
          :key="entry.id"
          class="entry-section"
          :class="{ 'entry-section-first': entryIndex === 0 }"
        >
          <ReconciliationEntryPanel
            :file-name="entry.file.name"
            :rule-label="entryRuleDisplay(entry).label"
            :rule-tooltip="entryRuleDisplay(entry).tooltip"
            :rule-scope="entryRuleDisplay(entry).scope"
            :remove-disabled="
              entry.status === 'saving' ||
              entry.status === 'processing' ||
              entry.status === 'parsing'
            "
            :entry="entry"
            :summary="entrySummary(entry)"
            :display-rows="entryDisplayRowsAsRecords(entry)"
            :active-rule="activeRule"
            :is-rule-loading="isRuleLoading"
            :saved-version-label="findVersion(entry.savedJobId)"
            :show-field-consistency-legend="
              entry.processedRows.some((row) => hasFieldConsistencyIssues(rowAsRecord(row)))
            "
            :row-class-name="entryRowClassNameAsRecord"
            :version-group="findEntryVersionGroup(entry)"
            :version-item="findEntryVersionItem(entry)"
            :version-highlighted="
              entry.savedJobId ? highlightedJobIds.has(entry.savedJobId) : false
            "
            :format-version-label="formatHistoryVersionLabel"
            :can-edit="canEditEntry(entry)"
            :has-dirty="entryHasDirty(entry.id)"
            :is-saving="entryIsSaving(entry.id)"
            :is-repricing="entryIsRepricing(entry.id)"
            :repricing-row-id="entryRepricingRowId(entry.id)"
            @remove="removeUploadEntry(entry.id)"
            @select-sheet="(sheet) => selectEntrySheet(entry, sheet)"
            @process="handleProcessEntry(entry)"
            @toggle-anomaly="toggleAnomalyMode(entry)"
            @anomaly-category-change="(filters) => onAnomalyCategoryChange(entry, filters)"
            @save-changes="handleSaveEntryChanges(entry)"
            @reprice="handleRepriceEntry(entry)"
            @open-clerk-rules="openClerkRulesDrawer(entry)"
            @export-anomaly="openExportAnomalyDialog(entry)"
            @page-change="(p) => onEntryPageChange(entry, p)"
            @open-pricing-flow="openPricingFlowDetail"
            @row-field-change="(row, field, value) => handleEntryRowFieldChange(entry, row, field, value)"
            @fix-single-row="(row) => handleEntryFixSingleRow(entry, row)"
            @reprice-row="(row) => handleEntryRepriceRow(entry, row)"
            @retry-parse="handleRetryParseEntry(entry)"
            @version-change="setGroupSelectedVersion"
          >
            <template #status-badge>
              <EntryStatusBadge :status="entry.status" />
            </template>
            <template #file-meta>
              <template v-if="entry.workbook">
                {{
                  t('reconciliation.upload.sheetSummary', {
                    sheets: entry.workbook.sheetNames.length,
                    rows: entry.workbook.rows.length
                  })
                }}
                <span
                  v-if="(entry.status === 'error' || entry.status === 'process_error') && entry.errorMessage"
                  class="text-red-500 ml-2"
                >
                  {{ entry.errorMessage }}
                </span>
              </template>
              <template v-else-if="entry.status === 'error' || entry.status === 'process_error'">
                <span class="text-red-500">{{ entry.errorMessage }}</span>
              </template>
              <template v-else>{{ t('reconciliation.upload.parsing') }}</template>
            </template>
          </ReconciliationEntryPanel>
        </div>
      </ElCard>

    </div>
  </div>
  <ReconciliationJobDialogs
    ref="jobDialogsRef"
    :active-rule="activeRule"
    :operator-name="operatorName"
    @patch-history="patchHistoryItem"
    @history-changed="refreshEntryHistory"
  />
  <PricingFlowDrawer
    v-model:visible="pricingFlowDrawerVisible"
    :row="pricingFlowRow"
  />
  <ReconciliationClerkRulesDrawer
    v-model:visible="clerkRulesDrawerVisible"
    :job-id="clerkRulesJobId"
    :can-edit="clerkRulesCanEdit"
    @repriced="onClerkRulesRepriced"
    @persisted="onClerkRulesPersisted"
  />

  <ElDialog
    v-model="exportAnomalyDialogVisible"
    :title="t('reconciliation.exportAnomaly.title')"
    width="480px"
    destroy-on-close
  >
    <p class="text-sm text-gray-600">{{ t('reconciliation.exportAnomaly.description') }}</p>
    <ElCheckbox v-model="exportIncludeFieldConsistency" class="mt-4">
      {{ t('reconciliation.exportAnomaly.includeFieldConsistency') }}
    </ElCheckbox>
    <p class="mt-2 text-xs leading-relaxed text-gray-400">
      {{ t('reconciliation.exportAnomaly.fieldConsistencyHint') }}
    </p>
    <template #footer>
      <ElButton @click="exportAnomalyDialogVisible = false">{{ t('common.cancel') }}</ElButton>
      <ElButton type="primary" :loading="exportAnomalyLoading" @click="confirmExportAnomalies">
        {{ t('reconciliation.exportAnomaly.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script lang="ts">
  import * as XLSX from 'xlsx'
  import { isLikelyHospitalName } from '@/utils/reconciliationHospitalName'
  import {
    isExcelDateNumber,
    readHospitalWorkbook,
    type HospitalRow,
    type RawWorkbook,
    type SheetTemplateMeta
  } from '@/utils/reconciliationWorkbookParse'

  type ProcessedRow = HospitalRow & {
    id?: number
    expectedUnitPrice: number | null
    correctedTotalPrice: number | null
    difference: number | null
    status: 'corrected' | 'unchanged' | 'skipped' | 'warning'
    pricingRule: string
    notes: string[]
    matchedRuleId?: number | null
    matchedPriceOption?: number | null
    matchedProductId?: number | null
    matchedVariantId?: number | null
    pricingPath?: string | null
    billingNotes?: Record<string, unknown> | null
  }

  type EntryStatus =
    | 'pending'
    | 'parsing'
    | 'parsed'
    | 'processing'
    | 'saving'
    | 'saved'
    | 'error'
    | 'process_error'

  interface UploadEntry {
    id: string
    file: File
    workbook: RawWorkbook | null
    processedRows: ProcessedRow[]
    status: EntryStatus
    errorMessage: string
    hospitalName: string
    /** 该文件匹配到的计费规则（按医院名称解析），null 时使用全局 activeRule */
    rule: Api.Hospital.PricingRuleRecord | null
    savedJobId: number | null
    /** 处理进度百分比 (0-100)，仅在 processing 状态有效 */
    processingProgress: number
    /** 前端分页：当前显示页码 */
    displayPage: number
    /** 前端分页：每页行数 */
    displayPageSize: number
    /** 前端分页：总行数（来自后端） */
    displayTotal: number
    /** 后端返回的汇总数据（分页模式下不从 processedRows 计算） */
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
    /** 仅查看异常行 */
    onlyShowAbnormal: boolean
    /** 仅异常模式下的分类筛选（空数组=全部分类） */
    anomalyCategoryFilters: ReconciliationAnomalyCategory[]
    /** 仅查看异常模式：全量筛选结果缓存 */
    allAnomalyRows: ProcessedRow[] | null
    /** 异常模式加载中 */
    anomalyLoading: boolean
    /** 保存后按科室筛选（sheetName） */
    selectedSheetFilter: string | null
    /** 各科室行数（保存后来自后端） */
    savedSheetRowCounts: Record<string, number> | null
    /** 各科室待复核行数（保存后来自后端） */
    savedSheetWarningCounts: Record<string, number> | null
    /** 科室筛选切换中 */
    sheetFilterLoading: boolean
  }

  function roundCurrency(value: number): number {
    return Math.round(value * 100) / 100
  }

  function formatNumber(value: number | null | undefined): string {
    if (value == null) return '-'
    return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }

  function formatSignedNumber(value: number | null | undefined): string {
    if (value == null) return '-'
    const abs = formatNumber(Math.abs(value))
    if (value > 0) return `+${abs}`
    if (value < 0) return `-${abs}`
    return abs
  }

  function formatDateTime(value: string): string {
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) return value
    return parsed.toLocaleString('zh-CN', { hour12: false })
  }

  function buildExportFileName(prefix: string, hospitalName: string): string {
    const normalizedPrefix = prefix.trim() || 'hospital-export'
    const normalizedHospital = hospitalName.trim().replace(/[^\w\u4e00-\u9fa5-]+/g, '_')
    const segments = [normalizedPrefix]
    if (normalizedHospital) segments.push(normalizedHospital)
    segments.push(String(Date.now()))
    return `${segments.join('-')}.xlsx`
  }

  function coerceDateTime(value: unknown): Date | null {
    if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
    if (typeof value === 'number' && isExcelDateNumber(value)) {
      const parsed = XLSX.SSF.parse_date_code(value)
      if (!parsed) return null
      return new Date(parsed.y, parsed.m - 1, parsed.d, parsed.H ?? 0, parsed.M ?? 0, parsed.S ?? 0)
    }
    if (typeof value === 'string') {
      const normalized = value.trim().replace(/\./g, '-').replace(/\//g, '-')
      if (!normalized) return null
      const parsed = new Date(normalized.includes('T') ? normalized : normalized.replace(' ', 'T'))
      return Number.isNaN(parsed.getTime()) ? parseDateOnly(normalized) : parsed
    }
    return null
  }

  function hasExplicitTimeComponent(value: unknown): boolean {
    if (value instanceof Date)
      return value.getHours() !== 0 || value.getMinutes() !== 0 || value.getSeconds() !== 0
    if (typeof value === 'number') return Math.abs(value % 1) > 0.000001
    if (typeof value === 'string')
      return /\d{1,2}:\d{2}/.test(value) || /T\d{1,2}:\d{2}/.test(value)
    return false
  }

  function normalizeLogisticsDate(value: unknown, boundaryHour: number): Date | null {
    const parsed = coerceDateTime(value)
    if (!parsed) return null
    const normalized = new Date(parsed)
    if (hasExplicitTimeComponent(value) && normalized.getHours() < boundaryHour)
      normalized.setDate(normalized.getDate() - 1)
    normalized.setHours(0, 0, 0, 0)
    return normalized
  }

  function formatDateOnly(value: Date): string {
    return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
  }

  function parseDateOnly(value: string): Date | null {
    const normalized = value.trim().replace(/\./g, '-').replace(/\//g, '-')
    if (!normalized) return null
    const parsed = new Date(`${normalized}T00:00:00`)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }

  function uniqueDateKeys(dates: Date[]): string[] {
    return Array.from(new Set(dates.map((d) => formatDateOnly(d))))
  }

  function calculateLogisticsTripCount(
    dates: Date[],
    mergeAdjacentDays: boolean,
    mergeWindowDays: number
  ): number {
    if (dates.length === 0) return 0
    if (!mergeAdjacentDays) return uniqueDateKeys(dates).length
    const uniqueDates = uniqueDateKeys(dates)
      .map((v) => parseDateOnly(v))
      .filter((v): v is Date => v !== null)
      .sort((a, b) => a.getTime() - b.getTime())
    let trips = 0
    let lastDate: Date | null = null
    for (const current of uniqueDates) {
      if (!lastDate) {
        trips += 1
        lastDate = current
        continue
      }
      const diffDays = Math.round((current.getTime() - lastDate.getTime()) / 86400000)
      if (diffDays > mergeWindowDays) trips += 1
      lastDate = current
    }
    return trips
  }

  function formatSettlementDate(value: Date): string {
    return `${value.getFullYear()}年${value.getMonth() + 1}月${value.getDate()}日`
  }

  function toFullWidthNumber(value: number): string {
    return String(value).replace(/\d/g, (digit) => '０１２３４５６７８９'[Number(digit)] ?? digit)
  }

  function convertToChineseUppercase(value: number): string {
    if (!Number.isFinite(value)) return '-'
    if (value === 0) return '零元整'
    const digits = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖']
    const units = ['', '拾', '佰', '仟']
    const groupUnits = ['', '万', '亿', '兆']
    const [integerPart, decimalPartRaw = ''] = value.toFixed(2).split('.')
    const integerDigits = integerPart.split('').reverse()
    let integerText = ''
    let zeroPending = false
    for (let i = 0; i < integerDigits.length; i += 1) {
      const digit = Number(integerDigits[i])
      const unitIndex = i % 4
      const groupIndex = Math.floor(i / 4)
      if (digit === 0) {
        zeroPending = integerText.length > 0
        if (unitIndex === 0 && integerText && !integerText.startsWith(groupUnits[groupIndex]))
          integerText = groupUnits[groupIndex] + integerText
        continue
      }
      integerText = `${digits[digit]}${units[unitIndex]}${groupUnits[groupIndex]}${zeroPending ? '零' : ''}${integerText}`
      zeroPending = false
    }
    integerText = integerText
      .replace(/零+/g, '零')
      .replace(/零(万|亿|兆)/g, '$1')
      .replace(/亿万/g, '亿')
    if (!integerText.endsWith('元')) integerText += '元'
    const jiao = Number(decimalPartRaw[0] ?? '0')
    const fen = Number(decimalPartRaw[1] ?? '0')
    if (jiao === 0 && fen === 0) return `${integerText}整`
    let decimalText = ''
    if (jiao > 0) decimalText += `${digits[jiao]}角`
    if (fen > 0) {
      if (jiao === 0) decimalText += '零'
      decimalText += `${digits[fen]}分`
    }
    return `${integerText}${decimalText}`
  }

  function buildSettlementClosingText(lastDeliveryDate: Date | null, companyName: string): string {
    const closingDate = lastDeliveryDate ?? new Date()
    const resolvedCompanyName = companyName.trim() || '黑龙江省铂康医疗灭菌有限公司'
    return `${resolvedCompanyName}\n${closingDate.getFullYear()}年${closingDate.getMonth() + 1}月${closingDate.getDate()}日`
  }

  function resolveSettlementHospitalName(sheetMetas: SheetTemplateMeta[]): string {
    return (
      sheetMetas
        .map((item) => item.hospitalDisplayName.trim())
        .filter((name) => name && isLikelyHospitalName(name))
        .find(Boolean) || ''
    )
  }

  function normalizeMatchText(value: string): string {
    return value.replace(/\s+/g, '').trim().toLowerCase()
  }

  function resolveSettlementTemplate(
    rules: Api.Hospital.PricingRules,
    hospitalName: string,
    sheetMetas: SheetTemplateMeta[],
    workbookFileName: string
  ): Api.Hospital.SettlementLetterTemplate {
    const templates = rules.settlementLetter.templates
    const defaultTemplate =
      templates.find((item) => item.id === rules.settlementLetter.defaultTemplateId) ?? templates[0]
    const candidates = [
      resolveSettlementHospitalName(sheetMetas),
      hospitalName,
      workbookFileName.replace(/\.[^.]+$/, ''),
      ...sheetMetas.map((item) => item.sheetName),
      ...sheetMetas.map((item) => item.titleText)
    ]
      .map(normalizeMatchText)
      .filter(Boolean)

    for (const template of templates) {
      const keywords = [template.hospitalName, ...template.matchKeywords]
        .map(normalizeMatchText)
        .filter(Boolean)
      if (
        keywords.some((keyword) =>
          candidates.some((candidate) => candidate.includes(keyword) || keyword.includes(candidate))
        )
      ) {
        return template
      }
    }

    return defaultTemplate
  }

  /** @deprecated 结款函改由后端 ExportEngine v2 生成；保留供参考 */
  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- legacy client-side builder
  function buildSettlementLetterData(
    rows: ProcessedRow[],
    rules: Api.Hospital.PricingRules,
    hospitalName: string,
    sheetMetas: SheetTemplateMeta[],
    workbookFileName: string,
    logisticsTripCount?: number | null,
    logisticsFee?: number | null,
    logisticsBreakdown?: Api.Hospital.ReconciliationJob['logisticsBreakdown'] | null
  ): Record<string, unknown> {
    const settlementRules = rules.settlementLetter
    const settlementTemplate = resolveSettlementTemplate(
      rules,
      hospitalName,
      sheetMetas,
      workbookFileName
    )
    const companyName = settlementRules.companyName.trim()
    const exportHospitalName =
      resolveSettlementHospitalName(sheetMetas) ||
      settlementTemplate.hospitalName.trim() ||
      hospitalName.trim() ||
      '未命名医院'
    const validRows = rows.filter((row) => row.status !== 'skipped')
    const sterilizationFee = roundCurrency(
      validRows.reduce((sum, row) => sum + (row.correctedTotalPrice ?? row.totalPrice ?? 0), 0)
    )

    // 物流费：优先使用导入时后端保存的数据，旧数据回退到日期计算
    let logisticsTrips: number
    let finalLogisticsFee: number
    const breakdownFeePerTrip = logisticsBreakdown?.feePerTrip
    if (logisticsTripCount != null && logisticsTripCount > 0) {
      logisticsTrips = logisticsTripCount
      finalLogisticsFee =
        logisticsFee ??
        roundCurrency(logisticsTrips * (breakdownFeePerTrip ?? rules.logistics.feePerTrip))
    } else {
      const logisticsEntries = validRows
        .map((row) => {
          const rawDateTime = coerceDateTime(row.deliveryDateRaw)
          const normalizedDate = normalizeLogisticsDate(
            row.deliveryDateRaw,
            rules.logistics.dayBoundaryHour
          )
          return {
            date: normalizedDate,
            adjusted: Boolean(
              rawDateTime &&
              hasExplicitTimeComponent(row.deliveryDateRaw) &&
              rawDateTime.getHours() < rules.logistics.dayBoundaryHour
            )
          }
        })
        .filter((entry): entry is { date: Date; adjusted: boolean } => entry.date !== null)
        .sort((a, b) => a.date.getTime() - b.date.getTime())
      const logisticsDates = logisticsEntries.map((entry) => entry.date)
      logisticsTrips = rules.logistics.enabled
        ? calculateLogisticsTripCount(
            logisticsDates,
            rules.logistics.mergeAdjacentDays,
            rules.logistics.mergeWindowDays
          )
        : 0
      finalLogisticsFee = rules.logistics.enabled
        ? roundCurrency(logisticsTrips * rules.logistics.feePerTrip)
        : 0
    }

    const feeItems = [...settlementRules.feeItems]
      .filter((item) => item.enabled)
      .sort((a, b) => a.sortOrder - b.sortOrder)

    const feeRows = feeItems.map((item, index) => {
      const amount =
        item.key === 'sterilize'
          ? sterilizationFee
          : item.key === 'logistics'
            ? finalLogisticsFee
            : 0
      return {
        indexLabel: toFullWidthNumber(index + 1),
        itemLabel: item.label,
        amount,
        remark:
          item.key === 'logistics' && rules.logistics.enabled
            ? `${breakdownFeePerTrip ?? rules.logistics.feePerTrip}元/次`
            : item.remark || ''
      }
    })

    const totalAmount = roundCurrency(
      feeItems.reduce((sum, item) => {
        if (item.key === 'sterilize') return sum + sterilizationFee
        if (item.key === 'logistics') return sum + finalLogisticsFee
        return sum
      }, 0)
    )

    // 日期范围仍从发货日期计算
    const dateEntries = validRows
      .map((row) => coerceDateTime(row.deliveryDateRaw))
      .filter((v): v is Date => v !== null)
      .sort((a, b) => a.getTime() - b.getTime())
    const lastDeliveryDate = dateEntries.length > 0 ? dateEntries[dateEntries.length - 1] : null
    const dateRangeText = settlementRules.dateRangeTextTemplate
      .replace('{start}', dateEntries.length > 0 ? formatSettlementDate(dateEntries[0]) : '-')
      .replace('{end}', lastDeliveryDate ? formatSettlementDate(lastDeliveryDate) : '-')

    return {
      hospitalName: hospitalName.trim() || undefined,
      companyName: companyName || undefined,
      sheetName: settlementTemplate.templateSheetName || '结款函',
      titleText: settlementTemplate.titleText || '货款结算单',
      recipientLabel: '致：',
      hospitalDisplayName: exportHospitalName,
      dateRangeText,
      feeRows,
      totalAmount,
      uppercaseTotal: convertToChineseUppercase(totalAmount),
      closingText: buildSettlementClosingText(lastDeliveryDate, companyName),
      matchedTemplateId: settlementTemplate.id
    }
  }

</script>

<script setup lang="ts">
  import {
    ref,
    reactive,
    computed,
    provide,
    watch,
    onMounted,
    onActivated,
    onDeactivated,
    onBeforeUnmount,
    defineComponent,
    h,
    defineOptions,
    type PropType
  } from 'vue'
  import { ElMessage } from 'element-plus'
  import type { UploadProps } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useUserStore } from '@/store/modules/user'
  import {
    getActiveHospitalPricingRule,
    listHospitalPricingRules
  } from '@/api/hospital/pricingRulesApi'
  import {
    importHospitalReconciliation,
    listHospitalReconciliations,
    getReconciliationRows,
    repriceReconciliationRow,
    updateHospitalReconciliationRows
  } from '@/api/hospital/reconciliationsApi'
  import {
    isPlaceholderHospitalName,
    isValidHospitalName,
    resolveHospitalBadgeName
  } from '@/utils/reconciliationHospitalName'
  import {
    isCustomerSpecificPricingRule,
    isGeneralPricingRule
  } from '@/utils/pricingRuleScope'
  import {
    filterRowsByAnomalyCategories,
    isReconciliationAnomalyRow,
    type ReconciliationAnomalyCategory
  } from '@/utils/reconciliationAnomaly'
  import ReconciliationEntryPanel from '@/components/business/reconciliation/ReconciliationEntryPanel.vue'
  import ReconciliationJobDialogs from '@/components/business/reconciliation/ReconciliationJobDialogs.vue'
  import { reconciliationJobActionsKey } from '@/composables/reconciliationJobActionsKey'
  import ReconciliationNoticeBell from '@/components/business/reconciliation/ReconciliationNoticeBell.vue'
  import PricingFlowDrawer from '@/components/business/reconciliation/PricingFlowDrawer.vue'
  import ReconciliationClerkRulesDrawer from '@/components/business/reconciliation/ReconciliationClerkRulesDrawer.vue'
  import BillingRoleBadge from '@/components/business/BillingRoleBadge.vue'
  import {
    buildReconciliationRowKey,
    useReconciliationEntryEditing
  } from '@/composables/useReconciliationEntryEditing'
  import { useBillingPermission } from '@/composables/useBillingPermission'
  import {
    buildEntryScopeKeys,
    useReconciliationHistory
  } from '@/composables/useReconciliationHistory'
  import {
    extractRowBillingFields,
    fieldConsistencyRowClass,
    parseReconciliationBillingContext
  } from '@/utils/reconciliationBillingNotes'
  import { applySingleRowCorrection } from '@/utils/reconciliationRowCorrection'

  defineOptions({ name: 'HospitalReconciliation' })

  const { t } = useI18n()

  function rowAsRecord(row: ProcessedRow): Record<string, unknown> {
    return row as unknown as Record<string, unknown>
  }

  const pricingFlowDrawerVisible = ref(false)
  const pricingFlowRow = ref<Record<string, unknown> | null>(null)

  function openPricingFlowDetail(row: Record<string, unknown>) {
    pricingFlowRow.value = row
    pricingFlowDrawerVisible.value = true
  }

  const clerkRulesDrawerVisible = ref(false)
  const clerkRulesJobId = ref<number | null>(null)
  const clerkRulesEntryId = ref<string | null>(null)
  const clerkRulesCanEdit = ref(true)

  function openClerkRulesDrawer(entry: UploadEntry) {
    if (!entry.savedJobId) return
    clerkRulesJobId.value = entry.savedJobId
    clerkRulesEntryId.value = entry.id
    clerkRulesCanEdit.value = canEditEntry(entry)
    clerkRulesDrawerVisible.value = true
  }

  function onClerkRulesRepriced(rows: Record<string, unknown>[]) {
    const entryId = clerkRulesEntryId.value
    if (!entryId) return
    const entry = uploadEntries.value.find((e) => e.id === entryId)
    if (entry) {
      applyRepricedRowsToEntry(entry, rows)
    }
  }

  function onClerkRulesPersisted(job: Api.Hospital.ReconciliationJob) {
    const entryId = clerkRulesEntryId.value
    if (!entryId) return
    const entry = uploadEntries.value.find((e) => e.id === entryId)
    if (!entry) return
    const editor = ensureEntryEditor(entry.id)
    editor.applySummaryToEntry(entry, job)
    editor.clearDirty()
    void refreshEntryHistory()
  }

  function mapApiRowToProcessedRow(row: Record<string, unknown>): ProcessedRow {
    const billingFields = extractRowBillingFields(row)
    const rowId = row['id'] ?? row['row_id']
    return {
      id: typeof rowId === 'number' ? rowId : rowId != null ? Number(rowId) : undefined,
      sheetName: row['sheetName'] as string,
      rowNumber: row['rowNumber'] as number,
      deliveryDateRaw: null,
      deliveryDate: row['deliveryDate'] as string,
      orderNo: row['orderNo'] as string,
      type: row['type'] as string,
      categoryNo: (row['categoryNo'] as string) ?? '',
      packName: row['packName'] as string,
      packageMaterial: row['packageMaterial'] as string,
      packCount: (row['packCount'] as number) ?? 0,
      instrumentCount: (row['instrumentCount'] as number) ?? 0,
      unitPrice: row['unitPrice'] as number | null,
      totalPrice: row['totalPrice'] as number | null,
      original: {},
      expectedUnitPrice: row['expectedUnitPrice'] as number | null,
      correctedTotalPrice: row['correctedTotalPrice'] as number | null,
      difference: row['difference'] as number | null,
      status: (row['status'] as ProcessedRow['status']) ?? 'unchanged',
      pricingRule: (row['pricingRule'] as string) ?? '',
      notes: (row['notes'] as string[]) ?? [],
      matchedRuleId: billingFields.matchedRuleId,
      matchedPriceOption: billingFields.matchedPriceOption,
      matchedProductId: toOptionalNumber(row['matchedProductId'] ?? row['matched_product_id']),
      matchedVariantId: toOptionalNumber(row['matchedVariantId'] ?? row['matched_variant_id']),
      pricingPath: (row['pricingPath'] ?? row['pricing_path']) as string | null | undefined,
      billingNotes: billingFields.billingNotes
    }
  }

  function toOptionalNumber(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value)) return value
    if (value == null || value === '') return null
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  const EntryStatusBadge = defineComponent({
    name: 'EntryStatusBadge',
    props: { status: { type: String as PropType<EntryStatus>, required: true } },
    setup(props) {
      type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
      const map: Record<string, { type: TagType; label: string }> = {
        pending: { type: 'info', label: '待解析' },
        parsing: { type: 'warning', label: '解析中' },
        parsed: { type: 'success', label: '已解析' },
        processing: { type: 'warning', label: '校对中' },
        processed: { type: 'success', label: '已校对' },
        saving: { type: 'warning', label: '保存中' },
        saved: { type: 'success', label: '已保存' },
        error: { type: 'danger', label: '解析失败' },
        process_error: { type: 'danger', label: '处理失败' }
      }
      return () => {
        const info = map[props.status] || { type: 'info', label: props.status }
        return h(ElTag, { type: info.type, size: 'small', effect: 'plain' }, () => info.label)
      }
    }
  })

  const uploadEntries = ref<UploadEntry[]>([])
  const entryEditors = reactive<
    Record<string, ReturnType<typeof useReconciliationEntryEditing>>
  >({})
  const activeRule = ref<Api.Hospital.PricingRuleRecord | null>(null)
  const isRuleLoading = ref(true)
  const { canEditReconciliationRows } = useBillingPermission()
  const reconciliationHistory = useReconciliationHistory()
  const {
    highlightedJobIds,
    isHistoryLoading,
    formatHistoryVersionLabel,
    setGroupSelectedVersion,
    patchHistoryItem,
    highlightJob,
    findGroupForEntry,
    getGroupSelectedVersion
  } = reconciliationHistory

  function findEntryVersionGroup(entry: UploadEntry) {
    return findGroupForEntry(entry.hospitalName, entry.file.name)
  }

  function findEntryVersionItem(entry: UploadEntry) {
    const group = findEntryVersionGroup(entry)
    if (!group) return null
    return getGroupSelectedVersion(group)
  }

  function ensureEntryEditor(entryId: string) {
    if (!entryEditors[entryId]) {
      entryEditors[entryId] = useReconciliationEntryEditing()
    }
    return entryEditors[entryId]
  }

  function canEditEntry(entry: UploadEntry): boolean {
    if (!canEditReconciliationRows.value || !entry.savedJobId) return false
    const versionItem = findEntryVersionItem(entry)
    if (versionItem && versionItem.reviewStatus !== 'pending') return false
    return true
  }

  function handleEntryRowFieldChange(
    entry: UploadEntry,
    row: Record<string, unknown>,
    field: string,
    value: unknown
  ) {
    ensureEntryEditor(entry.id).markDirty(row, field, value)
  }

  function applyRepricedRowsToEntry(entry: UploadEntry, rows: Record<string, unknown>[]) {
    const rowMap = new Map(rows.map((row) => [buildReconciliationRowKey(row), row]))
    const mapRow = (row: ProcessedRow) => {
      const updated = rowMap.get(buildReconciliationRowKey(rowAsRecord(row)))
      return updated ? mapApiRowToProcessedRow(updated) : row
    }
    if (entry.onlyShowAbnormal && entry.allAnomalyRows) {
      entry.allAnomalyRows = entry.allAnomalyRows.map(mapRow)
      syncEntryAnomalyDisplayTotal(entry)
    } else {
      entry.processedRows = entry.processedRows.map(mapRow)
    }
  }

  function syncEntryAnomalyDisplayTotal(entry: UploadEntry) {
    if (!entry.allAnomalyRows) {
      entry.displayTotal = 0
      return
    }
    entry.displayTotal = filterRowsByAnomalyCategories(
      entry.allAnomalyRows.map((row) => rowAsRecord(row)),
      entry.anomalyCategoryFilters ?? []
    ).length
  }

  async function loadEntryAnomalyRows(entry: UploadEntry) {
    if (!entry.savedJobId) return
    entry.anomalyLoading = true
    try {
      const allRows = await fetchAllRowsForExport(entry.savedJobId)
      let processed = allRows.map((row) => mapApiRowToProcessedRow(row))
      processed = processed.filter((row) => isReconciliationAnomalyRow(rowAsRecord(row)))
      if (entry.selectedSheetFilter) {
        processed = processed.filter((row) => row.sheetName === entry.selectedSheetFilter)
      }
      entry.allAnomalyRows = processed
      syncEntryAnomalyDisplayTotal(entry)
    } finally {
      entry.anomalyLoading = false
    }
  }

  async function reloadEntryView(entry: UploadEntry) {
    if (entry.onlyShowAbnormal) {
      await loadEntryAnomalyRows(entry)
      return
    }
    await loadEntryPage(entry, entry.displayPage)
  }

  async function handleSaveEntryChanges(entry: UploadEntry) {
    if (!entry.savedJobId) return
    const editor = ensureEntryEditor(entry.id)
    await editor.saveEntryRows(
      entry.savedJobId,
      () => fetchAllRowsForExport(entry.savedJobId!),
      {
        onJobUpdated: async (job) => {
          editor.applySummaryToEntry(entry, job)
          await refreshEntryHistory()
        },
        reloadCurrentPage: () => reloadEntryView(entry)
      }
    )
  }

  async function handleRepriceEntry(entry: UploadEntry) {
    if (!entry.savedJobId) return
    const editor = ensureEntryEditor(entry.id)
    await editor.repriceAndStage(entry.savedJobId, {
      onRepriced: (rows) => applyRepricedRowsToEntry(entry, rows),
      onJobUpdated: async (job) => {
        editor.applySummaryToEntry(entry, job)
        await refreshEntryHistory()
      }
    })
  }

  const entryRepricingRowIds = reactive<Record<string, number | null>>({})

  function entryRepricingRowId(entryId: string): number | null {
    return entryRepricingRowIds[entryId] ?? null
  }

  async function handleEntryFixSingleRow(entry: UploadEntry, row: Record<string, unknown>) {
    if (!entry.savedJobId) return
    applySingleRowCorrection(row)
    const editor = ensureEntryEditor(entry.id)
    try {
      const allRows = await fetchAllRowsForExport(entry.savedJobId)
      const key = buildReconciliationRowKey(row)
      const rowsToSave = allRows.map((item) =>
        buildReconciliationRowKey(item) === key ? { ...item, ...row } : item
      )
      const updated = await updateHospitalReconciliationRows(entry.savedJobId, rowsToSave)
      editor.clearDirty()
      editor.applySummaryToEntry(entry, updated)
      await refreshEntryHistory()
      ElMessage.success(t('reconciliation.inlineEdit.saveSuccess'))
    } catch (error) {
      editor.markDirty(row, 'status', row['status'])
      if (row['difference'] != null) {
        editor.markDirty(row, 'difference', row['difference'])
      }
      if (row['correctedTotalPrice'] != null) {
        editor.markDirty(row, 'correctedTotalPrice', row['correctedTotalPrice'])
      }
      ElMessage.error(
        error instanceof Error ? error.message : t('reconciliation.detail.saveFailed')
      )
    }
  }

  async function handleEntryRepriceRow(entry: UploadEntry, row: Record<string, unknown>) {
    if (!entry.savedJobId) return
    const rowId = row['id'] as number | undefined
    if (rowId == null) {
      ElMessage.error('该行缺少 ID，请先保存版本后再单行重算')
      return
    }
    entryRepricingRowIds[entry.id] = rowId
    try {
      const result = await repriceReconciliationRow(entry.savedJobId, rowId, {
        type: row['type'] != null ? String(row['type']) : undefined,
        packageMaterial:
          row['packageMaterial'] != null ? String(row['packageMaterial']) : undefined,
        instrumentCount: (row['instrumentCount'] as number | null) ?? undefined,
        packCount: (row['packCount'] as number | null) ?? undefined
      })
      const updated = mapApiRowToProcessedRow(result.row)
      const key = buildReconciliationRowKey(row)
      const mapRow = (r: ProcessedRow) =>
        buildReconciliationRowKey(rowAsRecord(r)) === key ? updated : r
      if (entry.onlyShowAbnormal && entry.allAnomalyRows) {
        entry.allAnomalyRows = entry.allAnomalyRows.map(mapRow)
      } else {
        entry.processedRows = entry.processedRows.map(mapRow)
      }
      ensureEntryEditor(entry.id).applySummaryToEntry(entry, result.job)
      await refreshEntryHistory()
      ElMessage.success('已保存并重算该行')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '单行重算失败')
    } finally {
      entryRepricingRowIds[entry.id] = null
    }
  }

  function entryHasDirty(entryId: string): boolean {
    return entryEditors[entryId]?.hasDirty.value ?? false
  }

  function hasUnsavedForJob(jobId: number | null | undefined): boolean {
    if (!jobId) return false
    return uploadEntries.value.some(
      (entry) => entry.savedJobId === jobId && entryHasDirty(entry.id)
    )
  }

  function entryIsSaving(entryId: string): boolean {
    return entryEditors[entryId]?.isSaving.value ?? false
  }

  function entryIsRepricing(entryId: string): boolean {
    return entryEditors[entryId]?.isRepricing.value ?? false
  }

  async function refreshEntryHistory() {
    const savedEntries = uploadEntries.value.filter((entry) => entry.savedJobId)
    if (savedEntries.length === 0) {
      reconciliationHistory.setScopeKeys(new Set())
      reconciliationHistory.historyItems.value = []
      return
    }
    reconciliationHistory.setScopeKeys(buildEntryScopeKeys(savedEntries))
    const hospitalNames = savedEntries.map((entry) => entry.hospitalName)
    await reconciliationHistory.loadHistoryForHospitals(hospitalNames)
  }


  const userStore = useUserStore()
  const operatorName = ref(userStore.info.userName || '')

  // ReconciliationJobDialogs 与条目卡片是平级节点，其内部 provide 的
  // reconciliationJobActionsKey 无法到达条目头部（inject 得 null，按钮静默失效），
  // 需由本页面（条目头部的祖先）转发 provide
  const jobDialogsRef = ref<InstanceType<typeof ReconciliationJobDialogs> | null>(null)
  provide(reconciliationJobActionsKey, {
    openDetail: (item) => jobDialogsRef.value?.openDetail(item),
    openReview: (item) => jobDialogsRef.value?.openReview(item),
    requestExport: (item, type, context) =>
      jobDialogsRef.value?.requestExport(item, type, {
        hasLocalUnsavedChanges: context?.hasLocalUnsavedChanges ?? hasUnsavedForJob(item.id)
      })
  })

  const exportAnomalyDialogVisible = ref(false)
  const exportAnomalyTarget = ref<UploadEntry | null>(null)
  const exportIncludeFieldConsistency = ref(false)
  const exportAnomalyLoading = ref(false)


  const effectiveRules = computed(() => activeRule.value?.rules ?? null)

  function computeSummary(rows: ProcessedRow[]) {
    return rows.reduce(
      (acc, row) => {
        acc.total += 1
        if (row.status === 'corrected') acc.corrected += 1
        if (row.status === 'unchanged') acc.unchanged += 1
        if (row.status === 'warning') acc.warning += 1
        if (row.status === 'skipped') acc.skipped += 1
        if (row.status === 'warning') acc.totalDifference += row.difference ?? 0
        acc.originalTotalPrice += row.totalPrice ?? 0
        acc.correctedTotalPrice += row.correctedTotalPrice ?? 0
        return acc
      },
      {
        total: 0,
        corrected: 0,
        unchanged: 0,
        warning: 0,
        skipped: 0,
        totalDifference: 0,
        originalTotalPrice: 0,
        correctedTotalPrice: 0
      }
    )
  }

  let isReparsing = false

  watch(
    [effectiveRules, isRuleLoading],
    async ([rules, loading]) => {
      if (loading || !rules || isReparsing) return
      isReparsing = true
      try {
        for (const entry of uploadEntries.value) {
          if (entry.status === 'pending' && entry.workbook === null) {
            await reparseEntry(entry)
            await resolveEntryRule(entry)
          }
        }
      } finally {
        isReparsing = false
      }
    },
    { immediate: false }
  )

  // 在离开页面前提醒用户未保存的数据。
  // 本路由 keepAlive=true，切走只是 deactivated 不会 unmount，监听必须随
  // onDeactivated 摘除，否则泄漏到整个会话：用户在任意页面按 F5/Cmd+R 都会被
  // 「离开此页面？」原生对话框拦截，表现为浏览器刷新完全失效。
  // 已 saved / error 的条目不阻断刷新——出错条目必须允许用户自由刷新重试。
  const UNSAVED_ENTRY_STATUSES: EntryStatus[] = [
    'pending',
    'parsing',
    'parsed',
    'processing',
    'saving'
  ]

  function hasUnsavedEntries(): boolean {
    return uploadEntries.value.some((entry) => UNSAVED_ENTRY_STATUSES.includes(entry.status))
  }

  function beforeUnloadHandler(e: BeforeUnloadEvent) {
    if (hasUnsavedEntries()) {
      e.preventDefault()
      e.returnValue = ''
    }
  }

  const addBeforeUnloadListener = () => window.addEventListener('beforeunload', beforeUnloadHandler)
  const removeBeforeUnloadListener = () =>
    window.removeEventListener('beforeunload', beforeUnloadHandler)

  onMounted(addBeforeUnloadListener)
  onActivated(addBeforeUnloadListener)
  onDeactivated(removeBeforeUnloadListener)
  onBeforeUnmount(removeBeforeUnloadListener)

  let skipNextActivatedHistoryLoad = false

  onMounted(async () => {
    if (!operatorName.value) operatorName.value = userStore.info.userName || ''
    try {
      isRuleLoading.value = true
      // 优先加载全局激活规则（兼容旧数据）
      activeRule.value = await getActiveHospitalPricingRule()
    } catch {
      // 没有激活规则时，降级加载"标准灭菌计费规则"或第一条规则作为默认
      try {
        const rules = await listHospitalPricingRules()
        activeRule.value = rules.find((r) => r.name === '标准灭菌计费规则') ?? rules[0] ?? null
      } catch {
        activeRule.value = null
      }
    } finally {
      isRuleLoading.value = false
    }
    skipNextActivatedHistoryLoad = true
    void refreshEntryHistory()
  })

  onActivated(() => {
    if (skipNextActivatedHistoryLoad) {
      skipNextActivatedHistoryLoad = false
      return
    }
    void refreshEntryHistory()
  })

  /** 添加一个上传条目 */
  async function addUploadEntry(file: File) {
    const entry = reactive<UploadEntry>({
      id: Date.now().toString() + '-' + Math.random().toString(36).slice(2, 8),
      file,
      workbook: null,
      processedRows: [],
      status: 'pending',
      errorMessage: '',
      hospitalName: '',
      rule: null,
      savedJobId: null,
      processingProgress: 0,
      displayPage: 1,
      displayPageSize: 200,
      displayTotal: 0,
      savedSummary: null,
      onlyShowAbnormal: false,
      anomalyCategoryFilters: [],
      allAnomalyRows: null,
      anomalyLoading: false,
      selectedSheetFilter: null,
      savedSheetRowCounts: null,
      savedSheetWarningCounts: null,
      sheetFilterLoading: false
    })
    uploadEntries.value.push(entry)
    if (!effectiveRules.value) {
      entry.status = 'pending'
      entry.errorMessage = isRuleLoading.value
        ? ''
        : '规则尚未加载成功，暂时不能解析 Excel'
      if (!isRuleLoading.value) {
        ElMessage.warning('规则尚未加载成功，暂时不能处理 Excel')
      }
      return
    }
    ensureEntryEditor(entry.id)
    await reparseEntry(entry)
    await resolveEntryRule(entry)
  }

  function entryHasClerkPricingHit(entry: UploadEntry) {
    return entry.processedRows.some((row) => {
      const notes = row.billingNotes
      if (!notes || typeof notes !== 'object') return false
      const layer = String(
        (notes as Record<string, unknown>).pricingLayer ??
          (notes as Record<string, unknown>).pricing_layer ??
          ''
      ).trim()
      const clerkRule = String(
        (notes as Record<string, unknown>).clerkRuleName ??
          (notes as Record<string, unknown>).clerk_rule_name ??
          ''
      ).trim()
      return layer === 'clerk' || clerkRule.length > 0
    })
  }

  function entryRuleDisplay(entry: UploadEntry) {
    if (entryHasClerkPricingHit(entry)) {
      return {
        label: '内勤计价',
        scope: 'special' as const,
        tooltip: '对账行已命中内勤账单价/包名特价规则（与顶栏客服规则名可并存）'
      }
    }
    const rule = entry.rule ?? activeRule.value
    const name = rule?.name ?? '标准灭菌计费规则'
    const special = rule ? isCustomerSpecificPricingRule(rule) : false
    const version = rule?.version ? `（${rule.version}）` : ''
    return {
      label: name,
      scope: special ? ('special' as const) : ('standard' as const),
      tooltip: special
        ? `特色计价规则：${name}${version}`
        : `标准计价规则：${name}${version}`
    }
  }

  /** 根据条目的医院名称解析匹配的计费规则 */
  async function resolveEntryRule(entry: UploadEntry) {
    const badgeName = resolveHospitalBadgeName({
      hospitalName: entry.hospitalName,
      fileName: entry.file.name,
      sheetHospitalDisplayNames:
        entry.workbook?.sheetMetas?.map((meta) => meta.hospitalDisplayName) ?? []
    })
    const keywords: string[] = []
    if (badgeName) keywords.push(badgeName)
    if (keywords.length === 0) return

    try {
      const rules = await listHospitalPricingRules()

      for (const keyword of keywords) {
        const matched = rules.find((r) => {
          if (!isCustomerSpecificPricingRule(r)) return false
          const baseName = r.name.replace(/灭菌计费规则|计费规则|灭菌规则|计费标准/g, '').trim()
          if (!baseName || baseName.length < 3) return false
          if (keyword === baseName || r.name === keyword) return true
          if (keyword.length >= 4 && (keyword.includes(baseName) || baseName.includes(keyword))) {
            return true
          }
          return false
        })
        if (matched) {
          entry.rule = matched
          return
        }
      }

      entry.rule =
        rules.find((r) => r.name === '标准灭菌计费规则' && isGeneralPricingRule(r)) ??
        rules.find((r) => isGeneralPricingRule(r)) ??
        null
    } catch {
      entry.rule = null
    }
  }

  async function handleRetryParseEntry(entry: UploadEntry) {
    await reparseEntry(entry)
    if (entry.workbook) {
      await resolveEntryRule(entry)
    }
  }

  /** 解析单个条目的 Excel */
  async function reparseEntry(entry: UploadEntry) {
    if (!effectiveRules.value) {
      entry.status = 'pending'
      entry.errorMessage = isRuleLoading.value
        ? ''
        : '规则尚未加载成功，暂时不能解析 Excel'
      return
    }
    ensureEntryEditor(entry.id)
    entry.status = 'parsing'
    entry.errorMessage = ''
    try {
      const workbook = await readHospitalWorkbook(entry.file, effectiveRules.value)
      entry.workbook = workbook
      entry.hospitalName = resolveHospitalBadgeName({
        fileName: entry.file.name,
        hospitalName: entry.hospitalName,
        sheetHospitalDisplayNames: workbook.sheetMetas.map((meta) => meta.hospitalDisplayName)
      })
      entry.status = 'parsed'
    } catch (error) {
      entry.status = 'error'
      entry.errorMessage = error instanceof Error ? error.message : '读取失败'
    }
  }

  /** 移除一个上传条目 */
  function removeUploadEntry(id: string) {
    const idx = uploadEntries.value.findIndex((e) => e.id === id)
    if (idx >= 0) uploadEntries.value.splice(idx, 1)
    delete entryEditors[id]
  }

  const MAX_FILE_SIZE = 20 * 1024 * 1024 // 20MB
  const handleUploadChange: UploadProps['onChange'] = (uploadFile) => {
    if (!uploadFile.raw) return
    if (uploadFile.raw.size > MAX_FILE_SIZE) {
      ElMessage.warning(`文件 "${uploadFile.name}" 超过 20MB 大小限制，请压缩后重新上传`)
      return
    }
    addUploadEntry(uploadFile.raw)
  }

  // 整个工作区卡片作为拖放区（重构后 ElUpload 不再带 drag，需原生事件补齐）
  const workspaceDragDepth = ref(0)
  const isDragOverWorkspace = ref(false)

  function onWorkspaceDragEnter(event: DragEvent) {
    if (!event.dataTransfer?.types?.includes('Files')) return
    workspaceDragDepth.value += 1
    isDragOverWorkspace.value = true
  }

  function onWorkspaceDragLeave() {
    workspaceDragDepth.value = Math.max(0, workspaceDragDepth.value - 1)
    if (workspaceDragDepth.value === 0) isDragOverWorkspace.value = false
  }

  function onWorkspaceDrop(event: DragEvent) {
    workspaceDragDepth.value = 0
    isDragOverWorkspace.value = false
    const files = Array.from(event.dataTransfer?.files ?? [])
    const excelFiles = files.filter((f) => /\.(xls|xlsx)$/i.test(f.name))
    if (excelFiles.length === 0) {
      if (files.length > 0) ElMessage.warning('仅支持 .xls / .xlsx 格式的账单文件')
      return
    }
    for (const file of excelFiles) {
      if (file.size > MAX_FILE_SIZE) {
        ElMessage.warning(`文件 "${file.name}" 超过 20MB 大小限制，请压缩后重新上传`)
        continue
      }
      addUploadEntry(file)
    }
  }

  /** 处理并保存：调用后端引擎，一步完成 Excel 读取 → 规则校对 → 保存 */
  async function handleProcessEntry(entry: UploadEntry) {
    const rule = entry.rule ?? activeRule.value
    if (!entry.file || !rule) return

    entry.status = 'processing'
    entry.processingProgress = 0
    entry.processedRows = []
    entry.errorMessage = ''
    try {
      const saved = await importHospitalReconciliation({
        file: entry.file,
        ruleId: rule.id,
        operatorName: operatorName.value.trim() || '未命名操作人',
        hospitalName: entry.hospitalName.trim() || undefined
      })
      await applySavedImportResult(entry, saved)
      ElMessage.success(`「${entry.file.name}」已保存，版本 V${saved.versionNo}`)
      highlightJob(saved.id)
    } catch (error) {
      // 大账单（如市五院 3 万+ 行）后端可能已落库，但客户端默认超时会先失败。
      // 超时后按「医院+文件名」找回最近成功 job，避免误报「解析失败」。
      const recovered = await tryRecoverRecentImportJob(entry)
      if (recovered) {
        await applySavedImportResult(entry, recovered)
        ElMessage.success(
          `「${entry.file.name}」后端已完成处理（客户端等待超时后已自动找回），版本 V${recovered.versionNo}`
        )
        highlightJob(recovered.id)
        return
      }
      entry.status = 'process_error'
      const raw = error instanceof Error ? error.message : '校对保存失败'
      entry.errorMessage = /timeout|timed out|ECONNABORTED|exceeded/i.test(raw)
        ? `处理超时：大账单可能仍在后端完成，请到历史版本中查看「${entry.file.name}」。原始错误：${raw}`
        : raw
    }
  }

  async function applySavedImportResult(
    entry: UploadEntry,
    saved: Api.Hospital.ReconciliationJob
  ) {
    entry.savedJobId = saved.id
    const savedName = (saved.hospitalName ?? '').trim()
    // 保存后以服务端解析名为准。表头 D9「南岗区」不能盖过已落库的「南岗院区」。
    entry.hospitalName = isValidHospitalName(savedName)
      ? savedName
      : resolveHospitalBadgeName({
          hospitalName: isPlaceholderHospitalName(saved.hospitalName)
            ? entry.hospitalName
            : saved.hospitalName || entry.hospitalName,
          fileName: entry.file.name,
          sheetHospitalDisplayNames:
            entry.workbook?.sheetMetas?.map((meta) => meta.hospitalDisplayName) ?? []
        })
    entry.savedSheetRowCounts = saved.sheetRowCounts ?? null
    entry.savedSheetWarningCounts = saved.sheetWarningCounts ?? null
    entry.selectedSheetFilter = null
    entry.savedSummary = {
      total: saved.totalRows ?? 0,
      corrected: saved.correctedRows ?? 0,
      unchanged: saved.unchangedRows ?? 0,
      warning: saved.warningRows ?? 0,
      skipped: saved.skippedRows ?? 0,
      totalDifference: saved.totalDifference ?? 0,
      originalTotalPrice: saved.originalTotalPrice ?? 0,
      correctedTotalPrice: saved.correctedTotalPrice ?? 0
    }
    entry.displayTotal = saved.totalRows ?? 0
    entry.displayPage = 1
    await Promise.all([loadEntryPage(entry, 1), refreshEntryHistory()])
    entry.status = 'saved'
    entry.errorMessage = ''
  }

  /** 超时后按医院名+源文件名找回最近 15 分钟内已落库的导入任务 */
  async function tryRecoverRecentImportJob(
    entry: UploadEntry
  ): Promise<Api.Hospital.ReconciliationJob | null> {
    try {
      const hospital = entry.hospitalName.trim()
      const jobs = await listHospitalReconciliations(hospital || undefined)
      const fileName = entry.file.name
      const now = Date.now()
      const candidates = jobs
        .filter(
          (job) =>
            job.sourceFileName === fileName ||
            job.sourceFileName?.endsWith(`_${fileName}`) ||
            job.sourceFileName?.endsWith(fileName)
        )
        .filter((job) => (job.totalRows ?? 0) > 0)
        .filter((job) => {
          const created = Date.parse(job.createdAt)
          return Number.isFinite(created) && now - created <= 15 * 60 * 1000
        })
        .sort((a, b) => b.id - a.id)
      return candidates[0] ?? null
    } catch {
      return null
    }
  }

  /** 加载条目指定页的行数据 */
  async function loadEntryPage(entry: UploadEntry, page: number) {
    if (!entry.savedJobId) return
    try {
      const result = await getReconciliationRows(
        entry.savedJobId,
        page,
        entry.displayPageSize,
        entry.selectedSheetFilter ?? undefined
      )
      const rows = (result.rows ?? []) as unknown as Record<string, unknown>[]
      entry.processedRows = rows.map((row) => mapApiRowToProcessedRow(row))
      entry.displayTotal = result.total
      entry.displayPage = page
    } catch {
      // ignore
    }
  }

  /** 保存后按科室筛选明细 */
  async function selectEntrySheet(entry: UploadEntry, sheetName: string | null) {
    if (!entry.savedJobId) return
    if (entry.selectedSheetFilter === sheetName) return
    entry.selectedSheetFilter = sheetName
    entry.displayPage = 1
    entry.sheetFilterLoading = true
    try {
      if (entry.onlyShowAbnormal) {
        await loadEntryAnomalyRows(entry)
      } else {
        await loadEntryPage(entry, 1)
      }
    } finally {
      entry.sheetFilterLoading = false
    }
  }

  /** 条目表格翻页 */
  async function onEntryPageChange(entry: UploadEntry, page: number) {
    await loadEntryPage(entry, page)
  }

  /** 切换"仅查看异常"模式：开启时全局加载全量数据再筛选 */
  async function toggleAnomalyMode(entry: UploadEntry) {
    if (entry.onlyShowAbnormal) {
      // 关闭异常模式
      entry.onlyShowAbnormal = false
      entry.anomalyCategoryFilters = []
      entry.allAnomalyRows = null
      entry.anomalyLoading = false
      await loadEntryPage(entry, 1)
      return
    }

    // 开启异常模式
    if (!entry.savedJobId) return

    entry.onlyShowAbnormal = true
    entry.anomalyCategoryFilters = []
    try {
      await loadEntryAnomalyRows(entry)
    } catch {
      entry.onlyShowAbnormal = false
      entry.anomalyCategoryFilters = []
      entry.allAnomalyRows = null
      entry.anomalyLoading = false
      ElMessage.warning('加载全量数据失败，无法使用异常筛选')
    }
  }

  function onAnomalyCategoryChange(
    entry: UploadEntry,
    filters: ReconciliationAnomalyCategory[]
  ) {
    entry.anomalyCategoryFilters = filters ?? []
    syncEntryAnomalyDisplayTotal(entry)
  }

  /** 导出异常明细：弹出选项后调用后端接口 */
  function openExportAnomalyDialog(entry: UploadEntry) {
    if (!entry.savedJobId) return
    exportAnomalyTarget.value = entry
    exportIncludeFieldConsistency.value = false
    exportAnomalyDialogVisible.value = true
  }

  async function confirmExportAnomalies() {
    const entry = exportAnomalyTarget.value
    if (!entry?.savedJobId) return
    exportAnomalyLoading.value = true
    try {
      const blob = await downloadBlob(
        `/api/hospital-reconciliations/${entry.savedJobId}/export-anomalies`,
        { includeFieldConsistency: exportIncludeFieldConsistency.value }
      )
      const hospitalName = entry.rule?.hospitalName || 'hospital'
      const fileName = buildExportFileName('异常明细_', hospitalName)
      triggerDownload(blob, fileName)
      exportAnomalyDialogVisible.value = false
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '导出异常失败')
    } finally {
      exportAnomalyLoading.value = false
    }
  }

  function hasFieldConsistencyIssues(row: Record<string, unknown>): boolean {
    const ctx = parseReconciliationBillingContext(row)
    return ctx.hasFieldConsistencyIssues || ctx.hasBlockingValidationIssues
  }

  function entryRowClassName({ row }: { row: ProcessedRow }): string {
    return fieldConsistencyRowClass(rowAsRecord(row))
  }

  function entryRowClassNameAsRecord({ row }: { row: Record<string, unknown> }): string {
    return fieldConsistencyRowClass(row)
  }

  function entryDisplayRowsAsRecords(entry: UploadEntry): Record<string, unknown>[] {
    return entryDisplayRows(entry).map((row) => rowAsRecord(row))
  }

  /** 导出单个条目的异常项 */
  /** 条目的摘要统计（优先使用后端汇总，回退到前端按页计算） */
  function entrySummary(entry: UploadEntry) {
    if (entry.selectedSheetFilter) {
      const partial = computeSummary(entryDisplayRows(entry))
      const sheet = entry.selectedSheetFilter
      return {
        ...partial,
        total: entry.savedSheetRowCounts?.[sheet] ?? entry.displayTotal ?? partial.total,
        warning: entry.savedSheetWarningCounts?.[sheet] ?? partial.warning
      }
    }
    if (entry.savedSummary) {
      return {
        total: entry.savedSummary.total,
        corrected: entry.savedSummary.corrected,
        unchanged: entry.savedSummary.unchanged,
        warning: entry.savedSummary.warning,
        skipped: entry.savedSummary.skipped,
        totalDifference: entry.savedSummary.totalDifference,
        originalTotalPrice: entry.savedSummary.originalTotalPrice,
        correctedTotalPrice: entry.savedSummary.correctedTotalPrice
      }
    }
    return computeSummary(entry.processedRows)
  }

  /** 当前页显示的行 */
  function entryDisplayRows(entry: UploadEntry): ProcessedRow[] {
    // 异常模式加载中：返回空数组，避免用当前页数据闪烁渲染
    if (entry.onlyShowAbnormal && entry.anomalyLoading) {
      return []
    }
    if (entry.onlyShowAbnormal && entry.allAnomalyRows) {
      return filterRowsByAnomalyCategories(
        entry.allAnomalyRows.map((row) => rowAsRecord(row)),
        entry.anomalyCategoryFilters ?? []
      ) as ProcessedRow[]
    }
    return entry.processedRows
  }

  /** 从历史记录查找版本号 */
  function findVersion(jobId: number | null): string {
    const found = reconciliationHistory.historyItems.value.find((h) => h.id === jobId)
    return found ? `V${found.versionNo}` : ''
  }

  async function fetchAllRowsForExport(jobId: number): Promise<Record<string, unknown>[]> {
    const firstPage = await getReconciliationRows(jobId, 1, 200)
    const total = firstPage.total
    if (total === 0) return []
    const pageSize = 200
    const rows0 = firstPage.rows as unknown as Record<string, unknown>[]
    if (total <= pageSize) return rows0

    const all: Record<string, unknown>[] = new Array(total)
    for (let i = 0; i < rows0.length; i++) all[i] = rows0[i]

    const totalPages = Math.ceil(total / pageSize)
    const MAX_CONCURRENT = 4
    for (let batchStart = 2; batchStart <= totalPages; batchStart += MAX_CONCURRENT) {
      const batchEnd = Math.min(batchStart + MAX_CONCURRENT, totalPages + 1)
      const tasks: Promise<void>[] = []
      for (let p = batchStart; p < batchEnd; p++) {
        tasks.push(
          getReconciliationRows(jobId, p, pageSize).then((result) => {
            const pageRows = result.rows as unknown as Record<string, unknown>[]
            const offset = (p - 1) * pageSize
            for (let i = 0; i < pageRows.length; i++) all[offset + i] = pageRows[i]
          })
        )
      }
      await Promise.all(tasks)
    }
    return all
  }

  async function downloadBlob(url: string, data: unknown): Promise<Blob> {
    const userStore = useUserStore()
    const response = await fetch(resolveApiRequestUrl(url), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(userStore.accessToken ? { Authorization: `Bearer ${userStore.accessToken}` } : {})
      },
      body: JSON.stringify(data)
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    return response.blob()
  }

  function resolveApiRequestUrl(url: string) {
    const baseURL = (import.meta.env.VITE_API_URL || '').trim()
    if (!baseURL || baseURL === '/') {
      return url
    }
    return new URL(url, `${baseURL.replace(/\/$/, '')}/`).toString()
  }

  function triggerDownload(blob: Blob, fileName: string) {
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
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

  .reconciliation-workspace :deep(.el-card__body) {
    padding: 14px 16px;
  }

  .workspace-header {
    display: flex;
    flex-wrap: wrap;
    gap: 12px 16px;
    align-items: flex-start;
    justify-content: space-between;
    padding-bottom: 12px;
    margin-bottom: 12px;
    border-bottom: 1px solid var(--el-border-color-extra-light, #f2f6fc);
  }

  .workspace-header__subtitle {
    margin-top: 2px;
    font-size: 13px;
    line-height: 1.4;
    color: var(--el-text-color-secondary, #909399);
  }

  .workspace-header__trailing {
    display: flex;
    flex-shrink: 0;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }

  :deep(.header-upload .el-upload) {
    display: inline-block;
  }

  .workspace-empty {
    padding: 28px 16px;
    text-align: center;
    background: var(--el-fill-color-lighter, #f5f7fa);
    border: 1px dashed var(--el-border-color-lighter, #ebeef5);
    border-radius: 8px;
  }

  .reconciliation-workspace.workspace-dragover {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9, #ecf5ff);
    transition:
      border-color 0.15s,
      background 0.15s;
  }

  .workspace-empty__text {
    margin: 0;
    font-size: 13px;
    color: var(--el-text-color-secondary, #909399);
  }

  .workspace-empty__note {
    margin: 6px 0 0;
    font-size: 12px;
    color: var(--el-text-color-placeholder, #a8abb2);
  }

  .entry-section {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--el-border-color-extra-light, #f2f6fc);
  }

  .entry-section-first {
    margin-top: 0;
    padding-top: 0;
    border-top: none;
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
