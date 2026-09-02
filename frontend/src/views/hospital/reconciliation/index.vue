<template>
  <div class="p-6">
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
            v-if="entry.workbook"
            :file-name="entry.file.name"
            :rule-label="entry.rule ? entry.rule.name : '默认规则'"
            :rule-tooltip="
              entry.rule
                ? `使用规则：${entry.rule.name}（${entry.rule.version}）`
                : '使用全局默认规则'
            "
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
            @remove="removeUploadEntry(entry.id)"
            @select-sheet="(sheet) => selectEntrySheet(entry, sheet)"
            @process="handleProcessEntry(entry)"
            @toggle-anomaly="toggleAnomalyMode(entry)"
            @save-changes="handleSaveEntryChanges(entry)"
            @reprice="handleRepriceEntry(entry)"
            @open-unmatched="openUnmatchedGuide(entry)"
            @export-anomaly="openExportAnomalyDialog(entry)"
            @page-change="(p) => onEntryPageChange(entry, p)"
            @open-pricing-flow="openPricingFlowDetail"
            @row-field-change="(row, field, value) => handleEntryRowFieldChange(entry, row, field, value)"
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
              </template>
              <template v-else-if="entry.status === 'error'">
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
    :active-rule="activeRule"
    :operator-name="operatorName"
    @patch-history="patchHistoryItem"
    @history-changed="refreshEntryHistory"
  />
  <PricingFlowDrawer
    v-model:visible="pricingFlowDrawerVisible"
    :row="pricingFlowRow"
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

  <ElDrawer v-model="unmatchedDrawerVisible" title="待建档产品引导" size="560px" destroy-on-close>
    <ElAlert
      v-if="unmatchedLoading"
      type="info"
      :closable="false"
      title="正在分析未命中产品..."
      class="mb-4"
    />
    <div v-else class="space-y-3">
      <p class="text-sm text-gray-500"
        >共 {{ unmatchedItems.length }} 项未在产品库命中，可快捷录入建档。</p
      >
      <div
        v-for="(item, idx) in unmatchedItems"
        :key="idx"
        class="rounded-lg border border-gray-200 p-3"
      >
        <div class="font-medium text-gray-800">{{ item.pack_name }}</div>
        <div class="mt-1 text-xs text-gray-500">
          {{ item.type }} · {{ item.package_material }} · {{ item.row_count }} 行
        </div>
        <div class="mt-1 text-xs text-blue-600">
          建议族：{{ item.suggested_family }}（{{ item.suggested_category_code }}）
        </div>
        <ElButton
          class="mt-2"
          size="small"
          type="primary"
          :loading="onboardingKey === `${item.pack_name}|${item.type}`"
          @click="quickOnboardFromUnmatched(item)"
        >
          快捷录入
        </ElButton>
      </div>
    </div>
  </ElDrawer>
</template>

<script lang="ts">
  import * as XLSX from 'xlsx'
  import { isLikelyHospitalName } from '@/utils/reconciliationHospitalName'

  type SheetTemplateMeta = {
    sheetName: string
    titleText: string
    dateRangeText: string
    hospitalDisplayName: string
  }

  type SheetPreview = {
    name: string
    totalRows: number
    dataRows: number
    headerRowIndex: number
  }

  type RawWorkbook = {
    fileName: string
    sheetNames: string[]
    previews: SheetPreview[]
    sheetMetas: SheetTemplateMeta[]
    rows: HospitalRow[]
  }

  type HospitalRow = {
    sheetName: string
    rowNumber: number
    deliveryDateRaw: string | number | null
    deliveryDate: string
    orderNo: string
    type: string
    categoryNo: string
    packName: string
    packageMaterial: string
    packCount: number
    instrumentCount: number
    unitPrice: number | null
    totalPrice: number | null
    original: Record<string, unknown>
  }

  type ProcessedRow = HospitalRow & {
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

  function findRowText(rows: unknown[][], keyword: string): string {
    for (const row of rows) {
      for (const cell of row) {
        const text = String(cell ?? '').trim()
        if (text && text.includes(keyword)) return text
      }
    }
    return ''
  }

  /** 在表头区域查找日期范围文本（兼容不同格式的日期前缀） */
  function findDateRangeText(rows: unknown[][]): string {
    // 尝试常见前缀
    const prefixes = ['从:', '从：', '时间:', '时间：', '日期:', '日期：']
    for (const prefix of prefixes) {
      const found = findRowText(rows, prefix)
      if (found) return found
    }
    // 回退：查找包含年份且含"至"/"到"的单元格
    for (const row of rows) {
      for (const cell of row) {
        const text = String(cell ?? '').trim()
        if (text && /\d{4}.*(?:至|到).*\d{4}/.test(text)) return text
      }
    }
    return ''
  }

  type EntryStatus = 'pending' | 'parsing' | 'parsed' | 'processing' | 'saving' | 'saved' | 'error'

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
    /** 仅查看异常模式：全量筛选结果缓存 */
    allAnomalyRows: ProcessedRow[] | null
    /** 异常模式加载中 */
    anomalyLoading: boolean
    /** 未命中产品数量 */
    unmatchedCount?: number | null
    /** 保存后按科室筛选（sheetName） */
    selectedSheetFilter: string | null
    /** 各科室行数（保存后来自后端） */
    savedSheetRowCounts: Record<string, number> | null
    /** 各科室待复核行数（保存后来自后端） */
    savedSheetWarningCounts: Record<string, number> | null
    /** 科室筛选切换中 */
    sheetFilterLoading: boolean
  }

  function sanitizeCellText(value: unknown, normalizeFormatting = false): string {
    const text = String(value ?? '').trim()
    return normalizeFormatting
      ? text
          .replace(/[\r\n\t]+/g, ' ')
          .replace(/[：:]\s*$/g, '')
          .replace(/\s+/g, ' ')
          .trim()
      : text
  }

  function normalizeText(value: unknown): string {
    return String(value ?? '')
      .replace(/\s+/g, '')
      .trim()
  }

  function toNumber(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value)) return value
    const normalized = String(value ?? '')
      .replace(/,/g, '')
      .replace(/￥/g, '')
      .trim()
    if (!normalized) return null
    const parsed = Number(normalized)
    return Number.isFinite(parsed) ? parsed : null
  }

  function isExcelDateNumber(value: unknown): boolean {
    return typeof value === 'number' && value > 40000 && value < 60000
  }

  function formatExcelDate(value: unknown): string {
    if (isExcelDateNumber(value)) {
      const parsed = XLSX.SSF.parse_date_code(value as number)
      if (!parsed) return String(value)
      return `${parsed.y}-${String(parsed.m).padStart(2, '0')}-${String(parsed.d).padStart(2, '0')}`
    }
    return String(value ?? '').trim()
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

  function getCell(row: unknown[], headerMap: Map<string, number>, headerName: string): unknown {
    const index = headerMap.get(normalizeText(headerName))
    return index === undefined ? null : (row[index] ?? null)
  }

  function findHeaderRowIndex(matrix: unknown[][]): number {
    return matrix.findIndex((row) => {
      const normalized = row.map((cell) => normalizeText(cell))
      return (
        normalized.includes(normalizeText('发货日期')) &&
        normalized.includes(normalizeText('包名')) &&
        normalized.includes(normalizeText('包装材料')) &&
        normalized.includes(normalizeText('器械数')) &&
        normalized.includes(normalizeText('单价')) &&
        normalized.includes(normalizeText('总价'))
      )
    })
  }

  function createHeaderMap(headerRow: unknown[]): Map<string, number> {
    const map = new Map<string, number>()
    headerRow.forEach((cell, index) => {
      const key = normalizeText(cell)
      if (key && !map.has(key)) map.set(key, index)
    })
    return map
  }

  function extractSheetTemplateMeta(
    sheetName: string,
    matrix: unknown[][],
    headerRowIndex: number
  ): SheetTemplateMeta {
    const titleText =
      findRowText(matrix.slice(0, headerRowIndex), '发货单汇总表') || '发货单汇总表-显示包装材料'
    const dateRangeText = findDateRangeText(matrix.slice(0, headerRowIndex))
    const headerScanEnd = Math.min(matrix.length, headerRowIndex + 8)
    const headerArea = matrix.slice(0, headerScanEnd)
    const hospitalDisplayName =
      ['医院', '诊所']
        .map((keyword) => findRowText(headerArea, keyword))
        .find((text) => text && isLikelyHospitalName(text)) || ''
    return { sheetName, titleText, dateRangeText, hospitalDisplayName }
  }

  function isDetailRow(
    row: {
      deliveryDateRaw: unknown
      orderNo: string
      type: string
      packName: string
      packageMaterial: string
    },
    rules: Api.Hospital.PricingRules
  ): boolean {
    const combinedText = [row.orderNo, row.type, row.packName, row.packageMaterial].join(' ')
    const hasDate =
      isExcelDateNumber(row.deliveryDateRaw) ||
      /\d{4}[/-]\d{1,2}[/-]\d{1,2}/.test(String(row.deliveryDateRaw ?? ''))
    const hasKeyFields = Boolean(row.type && row.packName)
    const looksLikeSummary =
      rules.cleaning.dropSummaryRows &&
      rules.cleaning.summaryKeywords.some((kw) => combinedText.includes(kw))
    const looksInvalid = !row.type || !row.packName
    return hasDate && hasKeyFields && !looksLikeSummary && !looksInvalid
  }

  function extractHospitalRows(
    sheetName: string,
    matrix: unknown[][],
    headerRowIndex: number,
    headerMap: Map<string, number>,
    rules: Api.Hospital.PricingRules
  ): HospitalRow[] {
    const rows: HospitalRow[] = []
    for (let i = headerRowIndex + 1; i < matrix.length; i += 1) {
      const row = matrix[i] ?? []
      const deliveryDateRaw = getCell(row, headerMap, '发货日期') as string | number | null
      const orderNo = sanitizeCellText(getCell(row, headerMap, '发货单号'))
      const type = sanitizeCellText(getCell(row, headerMap, '类型'))
      const categoryNo = sanitizeCellText(getCell(row, headerMap, '包类别号'))
      const packName = sanitizeCellText(
        getCell(row, headerMap, '包名'),
        rules.cleaning.clearInstrumentColumnFormatting
      )
      const packageMaterial = sanitizeCellText(
        getCell(row, headerMap, '包装材料'),
        rules.cleaning.trimPackagingMaterial || rules.cleaning.clearInstrumentColumnFormatting
      )
      const packCount = toNumber(getCell(row, headerMap, '包数')) ?? 0
      const instrumentCount = toNumber(getCell(row, headerMap, '器械数')) ?? 0
      const unitPrice = toNumber(getCell(row, headerMap, '单价'))
      const totalPrice = toNumber(getCell(row, headerMap, '总价'))

      if (!isDetailRow({ deliveryDateRaw, orderNo, type, packName, packageMaterial }, rules))
        continue

      rows.push({
        sheetName,
        rowNumber: i + 1,
        deliveryDateRaw,
        deliveryDate: formatExcelDate(deliveryDateRaw),
        orderNo,
        type,
        categoryNo,
        packName,
        packageMaterial,
        packCount,
        instrumentCount,
        unitPrice,
        totalPrice,
        original: {
          deliveryDateRaw,
          orderNo,
          type,
          categoryNo,
          packName,
          packageMaterial,
          packCount,
          instrumentCount,
          unitPrice,
          totalPrice
        }
      })
    }
    return rows
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

  async function readHospitalWorkbook(
    file: File,
    rules: Api.Hospital.PricingRules
  ): Promise<RawWorkbook> {
    const buffer = await file.arrayBuffer()
    const workbook = XLSX.read(buffer, { type: 'array', cellDates: false })
    const sheetNames = workbook.SheetNames
    const previews: SheetPreview[] = []
    const sheetMetas: SheetTemplateMeta[] = []
    const rows: HospitalRow[] = []

    for (const sheetName of sheetNames) {
      const worksheet = workbook.Sheets[sheetName]
      const rawMatrix = XLSX.utils.sheet_to_json<(string | number)[]>(worksheet, {
        header: 1,
        defval: '',
        blankrows: false,
        raw: true
      })
      const matrix = rules.cleaning.removeFirstRow ? rawMatrix.slice(1) : rawMatrix

      const headerRowIndex = findHeaderRowIndex(matrix)
      if (headerRowIndex < 0) continue

      const headerMap = createHeaderMap(matrix[headerRowIndex] as unknown[])
      const sheetRows = extractHospitalRows(sheetName, matrix, headerRowIndex, headerMap, rules)
      sheetMetas.push(extractSheetTemplateMeta(sheetName, matrix, headerRowIndex))

      previews.push({
        name: sheetName,
        totalRows: matrix.length,
        dataRows: sheetRows.length,
        headerRowIndex
      })
      rows.push(...sheetRows)
    }

    if (rows.length === 0) {
      throw new Error('没有识别到有效明细行，请确认 Excel 格式与示例一致。')
    }

    return { fileName: file.name, sheetNames, previews, sheetMetas, rows }
  }
</script>

<script setup lang="ts">
  import {
    ref,
    reactive,
    computed,
    watch,
    onMounted,
    onActivated,
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
    getReconciliationRows,
    getUnmatchedProducts,
    type UnmatchedProductItem
  } from '@/api/hospital/reconciliationsApi'
  import { quickOnboardProduct } from '@/api/master-data/productsApi'
  import {
    inferHospitalNameFromFileName,
    resolveReconciliationHospitalName
  } from '@/utils/reconciliationHospitalName'
  import ReconciliationEntryPanel from '@/components/business/reconciliation/ReconciliationEntryPanel.vue'
  import ReconciliationJobDialogs from '@/components/business/reconciliation/ReconciliationJobDialogs.vue'
  import ReconciliationNoticeBell from '@/components/business/reconciliation/ReconciliationNoticeBell.vue'
  import PricingFlowDrawer from '@/components/business/reconciliation/PricingFlowDrawer.vue'
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

  function mapApiRowToProcessedRow(row: Record<string, unknown>): ProcessedRow {
    const billingFields = extractRowBillingFields(row)
    return {
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
        error: { type: 'danger', label: '解析失败' }
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
    } else {
      entry.processedRows = entry.processedRows.map(mapRow)
    }
  }

  async function reloadEntryView(entry: UploadEntry) {
    if (entry.onlyShowAbnormal) {
      entry.anomalyLoading = true
      try {
        const allRows = await fetchAllRowsForExport(entry.savedJobId!)
        let processed = allRows.map((row) => mapApiRowToProcessedRow(row))
        processed = processed.filter((row) => row.status !== 'unchanged')
        if (entry.selectedSheetFilter) {
          processed = processed.filter((row) => row.sheetName === entry.selectedSheetFilter)
        }
        entry.allAnomalyRows = processed
        entry.displayTotal = processed.length
      } finally {
        entry.anomalyLoading = false
      }
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
      onRepriced: (rows) => applyRepricedRowsToEntry(entry, rows)
    })
  }

  function entryHasDirty(entryId: string): boolean {
    return entryEditors[entryId]?.hasDirty.value ?? false
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

  const exportAnomalyDialogVisible = ref(false)
  const exportAnomalyTarget = ref<UploadEntry | null>(null)
  const exportIncludeFieldConsistency = ref(false)
  const exportAnomalyLoading = ref(false)

  const unmatchedDrawerVisible = ref(false)
  const unmatchedLoading = ref(false)
  const unmatchedItems = ref<UnmatchedProductItem[]>([])
  const unmatchedJobId = ref<number | null>(null)
  const onboardingKey = ref('')

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

  // 在离开页面前提醒用户未保存的数据
  function beforeUnloadHandler(e: BeforeUnloadEvent) {
    if (uploadEntries.value.length > 0) {
      e.preventDefault()
      e.returnValue = ''
    }
  }
  onMounted(() => window.addEventListener('beforeunload', beforeUnloadHandler))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnloadHandler))

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
    if (!effectiveRules.value) {
      ElMessage.warning('规则尚未加载成功，暂时不能处理 Excel')
      return
    }
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
      allAnomalyRows: null,
      anomalyLoading: false,
      unmatchedCount: null,
      selectedSheetFilter: null,
      savedSheetRowCounts: null,
      savedSheetWarningCounts: null,
      sheetFilterLoading: false
    })
    uploadEntries.value.push(entry)
    ensureEntryEditor(entry.id)
    await reparseEntry(entry)
    await resolveEntryRule(entry)
  }

  /** 根据条目的医院名称解析匹配的计费规则 */
  async function resolveEntryRule(entry: UploadEntry) {
    // 收集所有可能用于匹配的关键词
    const keywords: string[] = []
    if (entry.hospitalName && !keywords.includes(entry.hospitalName)) {
      keywords.push(entry.hospitalName)
    }
    const fileNameHospital = inferHospitalNameFromFileName(entry.file.name)
    if (fileNameHospital && !keywords.includes(fileNameHospital)) {
      keywords.push(fileNameHospital)
    }
    const fileNameBase = entry.file.name.replace(/\.[^.]+$/, '').replace(/^\d{4}[\s_-]?/, '')
    if (fileNameBase && !keywords.includes(fileNameBase)) {
      keywords.push(fileNameBase)
    }
    if (keywords.length === 0) return

    // 统一从全量规则列表中按名称模糊匹配（无需调用 /active 接口，已无激活规则概念）
    try {
      const rules = await listHospitalPricingRules()

      for (const keyword of keywords) {
        const matched = rules.find((r) => {
          if (r.name.includes(keyword)) return true
          const baseName = r.name.replace(/灭菌计费规则|计费规则|灭菌规则|计费标准/g, '').trim()
          return keyword.includes(baseName) || baseName.includes(keyword)
        })
        if (matched) {
          entry.rule = matched
          return
        }
      }

      // 最终降级：查找名为"标准灭菌计费规则"的默认规则
      const fallback = rules.find((r) => r.name === '标准灭菌计费规则')
      entry.rule = fallback ?? null
    } catch {
      entry.rule = null
    }
  }

  /** 解析单个条目的 Excel */
  async function reparseEntry(entry: UploadEntry) {
    if (!effectiveRules.value) return
    entry.status = 'parsing'
    entry.errorMessage = ''
    try {
      const workbook = await readHospitalWorkbook(entry.file, effectiveRules.value)
      entry.workbook = workbook
      entry.hospitalName =
        resolveReconciliationHospitalName({
          fileName: entry.file.name,
          currentName: entry.hospitalName,
          sheetHospitalDisplayNames: workbook.sheetMetas.map((meta) => meta.hospitalDisplayName),
          ruleHospitalName: entry.rule?.hospitalName,
          ruleName: entry.rule?.name
        }) || entry.hospitalName
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
    try {
      const saved = await importHospitalReconciliation({
        file: entry.file,
        ruleId: rule.id,
        operatorName: operatorName.value.trim() || '未命名操作人',
        hospitalName: entry.hospitalName.trim() || undefined
      })

      entry.savedJobId = saved.id
      entry.hospitalName = saved.hospitalName || entry.hospitalName
      entry.savedSheetRowCounts = saved.sheetRowCounts ?? null
      entry.savedSheetWarningCounts = saved.sheetWarningCounts ?? null
      entry.selectedSheetFilter = null
      // 使用后端预计算的汇总数据
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
      // 分页加载第一页行数据（不依赖 saved.rows，避免巨大 JSON）
      // 和历史列表并行请求，互不依赖
      await Promise.all([loadEntryPage(entry, 1), refreshEntryHistory(), refreshUnmatchedCount(entry)])

      entry.status = 'saved'
      ElMessage.success(`「${entry.file.name}」已保存，版本 V${saved.versionNo}`)
      highlightJob(saved.id)
    } catch (error) {
      entry.status = 'error'
      entry.errorMessage = error instanceof Error ? error.message : '校对保存失败'
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
        entry.anomalyLoading = true
        const allRows = await fetchAllRowsForExport(entry.savedJobId)
        let processed = allRows
          .map((row) => mapApiRowToProcessedRow(row))
          .filter((row) => row.status !== 'unchanged')
        if (sheetName) {
          processed = processed.filter((row) => row.sheetName === sheetName)
        }
        entry.allAnomalyRows = processed
        entry.displayTotal = processed.length
        entry.anomalyLoading = false
      } else {
        await loadEntryPage(entry, 1)
      }
    } finally {
      entry.sheetFilterLoading = false
    }
  }

  async function refreshUnmatchedCount(entry: UploadEntry) {
    if (!entry.savedJobId) return
    try {
      const result = await getUnmatchedProducts(entry.savedJobId)
      entry.unmatchedCount = result.unmatched_count
    } catch {
      entry.unmatchedCount = null
    }
  }

  async function openUnmatchedGuide(entry: UploadEntry) {
    if (!entry.savedJobId) return
    unmatchedJobId.value = entry.savedJobId
    unmatchedDrawerVisible.value = true
    unmatchedLoading.value = true
    try {
      const result = await getUnmatchedProducts(entry.savedJobId)
      unmatchedItems.value = result.items ?? []
      entry.unmatchedCount = result.unmatched_count
    } catch {
      unmatchedItems.value = []
      ElMessage.error('加载未命中产品失败')
    } finally {
      unmatchedLoading.value = false
    }
  }

  async function quickOnboardFromUnmatched(item: UnmatchedProductItem) {
    const key = `${item.pack_name}|${item.type ?? ''}`
    onboardingKey.value = key
    try {
      await quickOnboardProduct({
        familyName: item.suggested_family || item.pack_name,
        packName: item.pack_name,
        type: item.type,
        packageMaterial: item.package_material,
        categoryCode: item.suggested_category_code
      })
      ElMessage.success(`已建档：${item.suggested_family || item.pack_name}`)
      if (unmatchedJobId.value) {
        const result = await getUnmatchedProducts(unmatchedJobId.value)
        unmatchedItems.value = result.items ?? []
        const entry = uploadEntries.value.find((e) => e.savedJobId === unmatchedJobId.value)
        if (entry) entry.unmatchedCount = result.unmatched_count
      }
    } catch {
      ElMessage.error('快捷录入失败')
    } finally {
      onboardingKey.value = ''
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
      entry.allAnomalyRows = null
      entry.anomalyLoading = false
      await loadEntryPage(entry, 1)
      return
    }

    // 开启异常模式
    if (!entry.savedJobId) return

    entry.onlyShowAbnormal = true
    entry.anomalyLoading = true

    try {
      const allRows = await fetchAllRowsForExport(entry.savedJobId)
      // 用 requestAnimationFrame 延迟同步处理，避免阻塞 UI
      await new Promise<void>((resolve) => {
        requestAnimationFrame(() => {
          let processed = allRows.map((row) => mapApiRowToProcessedRow(row))
          processed = processed.filter((row) => row.status !== 'unchanged')
          if (entry.selectedSheetFilter) {
            processed = processed.filter((row) => row.sheetName === entry.selectedSheetFilter)
          }
          entry.allAnomalyRows = processed
          entry.displayTotal = entry.allAnomalyRows.length
          entry.anomalyLoading = false
          resolve()
        })
      })
    } catch {
      entry.onlyShowAbnormal = false
      entry.allAnomalyRows = null
      entry.anomalyLoading = false
      ElMessage.warning('加载全量数据失败，无法使用异常筛选')
    }
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
      return entry.allAnomalyRows
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
