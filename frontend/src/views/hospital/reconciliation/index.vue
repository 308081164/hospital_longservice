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

    <ElCollapse class="mb-4">
      <ElCollapseItem :title="t('reconciliation.regression.title')" name="int03">
        <p class="text-xs text-gray-600 leading-relaxed">{{
          t('reconciliation.regression.desc')
        }}</p>
        <ul class="mt-2 text-xs list-disc pl-4 space-y-1 text-gray-600">
          <li>{{ t('reconciliation.regression.check1') }}</li>
          <li>{{ t('reconciliation.regression.check2') }}</li>
          <li>{{ t('reconciliation.regression.check3') }}</li>
        </ul>
      </ElCollapseItem>
    </ElCollapse>

    <div class="grid grid-cols-1 gap-6">
      <ElCard shadow="never" class="reconciliation-workspace">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div>
            <div class="flex items-center gap-2">
              <h3 class="text-base font-semibold text-gray-800">{{
                t('reconciliation.upload.title')
              }}</h3>
              <BillingRoleBadge />
            </div>
            <p class="mt-0.5 text-sm text-gray-500">{{ t('reconciliation.upload.subtitle') }}</p>
          </div>
          <span v-if="isRuleLoading" class="text-xs text-gray-400">
            {{ t('reconciliation.upload.ruleLoading') }}
          </span>
        </div>

        <ElUpload
          drag
          :auto-upload="false"
          accept=".xls,.xlsx"
          :show-file-list="false"
          :on-change="handleUploadChange"
          multiple
          class="compact-upload w-full"
        >
          <div class="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 px-4 py-2.5">
            <svg
              viewBox="0 0 24 24"
              class="h-4 w-4 shrink-0 text-gray-500"
              fill="none"
              stroke="currentColor"
            >
              <path
                d="M12 16V4m0 0-4 4m4-4 4 4M5 16v1a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3v-1"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <span class="text-sm text-gray-700">{{ t('reconciliation.upload.dropHint') }}</span>
            <span class="text-xs text-gray-400">{{ t('reconciliation.upload.dropNote') }}</span>
          </div>
        </ElUpload>

        <div
          v-if="uploadEntries.length === 0"
          class="mt-3 rounded-md border border-dashed border-gray-200 px-4 py-5 text-center text-sm text-gray-400"
        >
          {{ t('reconciliation.upload.empty') }}
        </div>

        <div
          v-for="(entry, entryIndex) in uploadEntries"
          :key="entry.id"
          class="entry-section"
          :class="{ 'entry-section-first': entryIndex === 0 }"
        >
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <span class="max-w-md truncate text-sm font-medium text-gray-800">{{
              entry.file.name
            }}</span>
            <EntryStatusBadge :status="entry.status" />
            <ElTooltip
              v-if="entry.workbook"
              :content="
                entry.rule
                  ? `使用规则：${entry.rule.name}（${entry.rule.version}）`
                  : '使用全局默认规则'
              "
              placement="top"
            >
              <span
                class="inline-flex items-center gap-1 rounded-md bg-gray-100 px-2 py-0.5 text-xs text-gray-600"
              >
                {{ entry.rule ? entry.rule.name : '默认规则' }}
              </span>
            </ElTooltip>
            <span class="min-w-0 flex-1 text-xs text-gray-500">
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
            </span>
            <ElButton
              size="small"
              type="danger"
              text
              :disabled="
                entry.status === 'saving' ||
                entry.status === 'processing' ||
                entry.status === 'parsing'
              "
              @click="removeUploadEntry(entry.id)"
            >
              {{ t('reconciliation.upload.remove') }}
            </ElButton>
          </div>

          <template v-if="entry.workbook">
            <div class="mb-3 flex flex-wrap gap-2">
              <div
                v-for="sheet in entry.workbook.previews"
                :key="sheet.name"
                class="inline-flex items-center gap-2 rounded-md border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs text-gray-600"
              >
                <span class="font-medium text-gray-800">{{ sheet.name }}</span>
                <span class="text-gray-400">{{ sheet.dataRows }} 行</span>
                <span class="text-gray-300">·</span>
                <span class="text-gray-400">表头 {{ sheet.headerRowIndex + 1 }}</span>
              </div>
            </div>

            <div class="mb-4 flex flex-wrap gap-2">
              <ElButton
                type="primary"
                size="small"
                :disabled="entry.status !== 'parsed' || !activeRule || isRuleLoading"
                :loading="entry.status === 'processing'"
                @click="handleProcessEntry(entry)"
              >
                {{
                  entry.status === 'processing'
                    ? '后端处理中...'
                    : entry.savedJobId
                      ? `已保存 ${findVersion(entry.savedJobId)}`
                      : '开始校对并保存'
                }}
              </ElButton>
            </div>

            <!-- Preview table -->
            <template v-if="entry.processedRows.length > 0">
              <div class="mb-3 flex items-center gap-3 text-sm">
                <div
                  class="flex items-center gap-3 rounded-md border border-gray-200 bg-gray-50 px-3 py-1.5"
                >
                  <span class="text-gray-500"
                    >总行
                    <strong class="text-gray-800">{{ entrySummary(entry).total }}</strong></span
                  >
                  <span class="text-gray-300">|</span>
                  <span class="text-gray-500"
                    >已修正
                    <strong class="text-primary">{{ entrySummary(entry).corrected }}</strong></span
                  >
                  <span class="text-gray-300">|</span>
                  <span class="text-gray-500"
                    >待复核
                    <strong class="text-warning">{{ entrySummary(entry).warning }}</strong></span
                  >
                </div>
                <div
                  class="flex items-center gap-3 rounded-md border border-gray-200 bg-gray-50 px-3 py-1.5"
                >
                  <span class="text-gray-500"
                    >原总价
                    <strong class="text-gray-800">{{
                      formatNumber(entrySummary(entry).originalTotalPrice)
                    }}</strong></span
                  >
                  <span class="text-gray-300">|</span>
                  <span class="text-gray-500"
                    >修正总价
                    <strong class="text-primary">{{
                      formatNumber(entrySummary(entry).correctedTotalPrice)
                    }}</strong></span
                  >
                  <span class="text-gray-300">|</span>
                  <span class="text-gray-500"
                    >{{ t('reconciliation.detail.pendingReviewDifference') }}
                    <strong
                      :class="
                        entrySummary(entry).totalDifference >= 0 ? 'text-green-600' : 'text-red-600'
                      "
                      >{{ formatSignedNumber(entrySummary(entry).totalDifference) }}</strong
                    ></span
                  >
                </div>
                <ElButton
                  size="small"
                  :type="entry.onlyShowAbnormal ? 'warning' : 'default'"
                  :plain="!entry.onlyShowAbnormal"
                  :loading="entry.anomalyLoading"
                  style="min-width: 120px"
                  @click="toggleAnomalyMode(entry)"
                >
                  {{ entry.anomalyLoading ? '正在加载全量数据...' : '仅查看异常' }}
                </ElButton>
                <ElButton
                  v-if="entry.savedJobId"
                  size="small"
                  type="primary"
                  plain
                  style="min-width: 120px"
                  @click="openUnmatchedGuide(entry)"
                >
                  待建档 {{ entry.unmatchedCount ?? '…' }}
                </ElButton>
                <ElButton
                  v-if="entry.savedJobId"
                  size="small"
                  type="danger"
                  plain
                  style="min-width: 120px"
                  :disabled="
                    entrySummary(entry).warning === 0 && entrySummary(entry).corrected === 0
                  "
                  @click="handleExportAnomalies(entry)"
                >
                  导出异常
                </ElButton>
                <span
                  v-if="entry.onlyShowAbnormal && !entry.anomalyLoading"
                  class="text-xs text-orange-500"
                  >当前仅显示异常行（全局筛选，共 {{ entry.displayTotal }} 条）</span
                >
                <span v-if="entry.anomalyLoading" class="text-xs text-blue-500"
                  >正在加载全量数据，请稍候...</span
                >
              </div>
              <div
                class="entry-group-scroll"
                :style="{ maxHeight: entry.onlyShowAbnormal ? '70vh' : '500px', overflowY: 'auto' }"
              >
                <div
                  v-if="entry.anomalyLoading"
                  class="flex items-center justify-center py-12 text-sm text-blue-500"
                >
                  正在从服务器加载全量数据，数据量较大时请耐心等待...
                </div>
                <template v-else>
                <div
                  v-for="group in entryGroupedRows(entry)"
                  :key="group.sheetName"
                  class="entry-sheet-group mb-6 last:mb-0"
                >
                  <div
                    class="entry-sheet-group-header mb-2 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md bg-gray-100 px-3 py-2"
                  >
                    <span class="shrink-0 text-sm font-semibold text-gray-700">{{
                      group.sheetName
                    }}</span>
                    <div
                      class="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-0.5 text-xs"
                    >
                      <span class="text-gray-500"
                        >行数
                        <strong class="text-gray-800">{{ group.summary.total }}</strong></span
                      >
                      <span class="text-gray-300">|</span>
                      <span class="text-gray-500"
                        >包数
                        <strong class="text-gray-800">{{ group.summary.packCount }}</strong></span
                      >
                      <span class="text-gray-300">|</span>
                      <span class="text-gray-500"
                        >原总价
                        <strong class="text-gray-800">{{
                          formatNumber(group.summary.originalTotalPrice)
                        }}</strong></span
                      >
                      <span class="text-gray-300">|</span>
                      <span class="text-gray-500"
                        >修正总价
                        <strong class="text-primary">{{
                          formatNumber(group.summary.correctedTotalPrice)
                        }}</strong></span
                      >
                      <span class="text-gray-300">|</span>
                      <span class="text-gray-500"
                        >{{ t('reconciliation.detail.pendingReviewDifference') }}
                        <strong
                          :class="
                            group.summary.totalDifference >= 0 ? 'text-green-600' : 'text-red-600'
                          "
                          >{{ formatSignedNumber(group.summary.totalDifference) }}</strong
                        ></span
                      >
                      <span class="text-gray-300">|</span>
                      <span class="text-gray-500"
                        >待复核
                        <strong class="text-warning">{{ group.summary.warning }}</strong></span
                      >
                    </div>
                  </div>
                  <div class="overflow-x-auto">
                  <ElTable
                    :data="group.rows"
                    border
                    stripe
                    size="small"
                    style="width: 100%"
                    :default-sort="{ prop: 'rowNumber', order: 'ascending' }"
                  >
                    <ElTableColumn prop="rowNumber" label="行号" width="65" sortable />
                    <ElTableColumn prop="deliveryDate" label="发货日期" width="110" sortable />
                    <ElTableColumn prop="type" label="类型" min-width="90" sortable />
                    <ElTableColumn
                      prop="packName"
                      label="包名"
                      min-width="160"
                      sortable
                      show-overflow-tooltip
                    />
                    <ElTableColumn
                      prop="packageMaterial"
                      label="包装材料"
                      min-width="110"
                      sortable
                    />
                    <ElTableColumn
                      prop="instrumentCount"
                      label="器械数"
                      width="80"
                      sortable
                      align="right"
                    />
                    <ElTableColumn
                      prop="packCount"
                      label="包数"
                      width="70"
                      sortable
                      align="right"
                    />
                    <ElTableColumn
                      label="原单价"
                      width="100"
                      sortable
                      prop="unitPrice"
                      align="right"
                    >
                      <template #default="{ row }">{{ formatNumber(row.unitPrice) }}</template>
                    </ElTableColumn>
                    <ElTableColumn
                      label="规则单价"
                      width="100"
                      sortable
                      prop="expectedUnitPrice"
                      align="right"
                    >
                      <template #default="{ row }">{{
                        formatNumber(row.expectedUnitPrice)
                      }}</template>
                    </ElTableColumn>
                    <ElTableColumn
                      label="原总价"
                      width="100"
                      sortable
                      prop="totalPrice"
                      align="right"
                    >
                      <template #default="{ row }">{{ formatNumber(row.totalPrice) }}</template>
                    </ElTableColumn>
                    <ElTableColumn
                      label="修正总价"
                      width="100"
                      sortable
                      prop="correctedTotalPrice"
                      align="right"
                    >
                      <template #default="{ row }">{{
                        formatNumber(row.correctedTotalPrice)
                      }}</template>
                    </ElTableColumn>
                    <ElTableColumn
                      label="差额"
                      width="100"
                      sortable
                      prop="difference"
                      align="right"
                    >
                      <template #default="{ row }">{{
                        formatSignedNumber(row.difference)
                      }}</template>
                    </ElTableColumn>
                    <ElTableColumn type="expand" width="42" :label="t('table.column.expand')">
                      <template #default="{ row }">
                        <ReconciliationBillingDetail
                          v-if="hasRowBillingDetail(row)"
                          :row="rowAsRecord(row)"
                          expanded
                        />
                      </template>
                    </ElTableColumn>
                    <ElTableColumn :label="t('reconciliation.detail.billingNotes')" min-width="160">
                      <template #default="{ row }">
                        <ReconciliationBillingDetail :row="rowAsRecord(row)" />
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="状态" width="90" sortable prop="status">
                      <template #default="{ row }">
                        <ElTag :type="statusTagType(row.status)" size="small" effect="plain">
                          {{ statusLabels[row.status] }}
                        </ElTag>
                      </template>
                    </ElTableColumn>
                  </ElTable>
                  </div>
                </div>
                </template>
              </div>
              <div class="mt-3 flex items-center justify-between">
                <span class="text-xs text-gray-400"
                  >{{ entry.onlyShowAbnormal ? '异常模式' : '当前显示' }}
                  {{ entryDisplayRows(entry).length }} 行{{
                    entry.onlyShowAbnormal
                      ? ''
                      : `，共 ${entry.displayTotal || entrySummary(entry).total} 行`
                  }}</span
                >
                <ElPagination
                  v-if="!entry.onlyShowAbnormal && entry.displayTotal > entry.displayPageSize"
                  :current-page="entry.displayPage"
                  :page-size="entry.displayPageSize"
                  :total="entry.displayTotal"
                  layout="prev, pager, next"
                  size="small"
                  background
                  @current-change="(p: number) => onEntryPageChange(entry, p)"
                />
              </div>
            </template>
          </template>
        </div>
      </ElCard>

      <ElCard shadow="never">
        <div class="mb-4 flex items-start justify-between gap-3">
          <div>
            <h3 class="text-base font-semibold text-gray-800">{{
              t('reconciliation.history.title')
            }}</h3>
            <p class="mt-1 text-sm text-gray-500">{{ t('reconciliation.history.subtitle') }}</p>
          </div>
          <ElButton size="small" :loading="isHistoryLoading" @click="loadHistory()">
            {{ t('reconciliation.history.refresh') }}
          </ElButton>
        </div>

        <div
          v-if="!isHistoryLoading && historyItems.length > 0"
          class="mb-4 flex flex-wrap items-center gap-3"
        >
          <ElInput
            v-model="historySearchDraft.keyword"
            :placeholder="t('reconciliation.history.filters.keywordPlaceholder')"
            clearable
            class="w-60"
            @keyup.enter="applyHistorySearch"
          />
          <ElSelect
            v-model="historySearchDraft.reviewStatus"
            clearable
            :placeholder="t('reconciliation.history.filters.reviewStatus')"
            class="w-36"
          >
            <ElOption value="pending" :label="t('reconciliation.history.reviewStatus.pending')" />
            <ElOption value="approved" :label="t('reconciliation.history.reviewStatus.approved')" />
            <ElOption value="rejected" :label="t('reconciliation.history.reviewStatus.rejected')" />
          </ElSelect>
          <ElInput
            v-model="historySearchDraft.operator"
            :placeholder="t('reconciliation.history.filters.operatorPlaceholder')"
            clearable
            class="w-36"
            @keyup.enter="applyHistorySearch"
          />
          <ElDatePicker
            v-model="historySearchDraft.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            :start-placeholder="t('reconciliation.history.filters.dateStart')"
            :end-placeholder="t('reconciliation.history.filters.dateEnd')"
            class="w-72"
          />
          <ElButton type="primary" size="small" @click="applyHistorySearch">
            {{ t('reconciliation.history.filters.search') }}
          </ElButton>
          <ElButton size="small" @click="resetHistorySearch">
            {{ t('reconciliation.history.filters.reset') }}
          </ElButton>
        </div>

        <div
          v-if="isHistoryLoading"
          class="rounded-lg border border-dashed border-gray-300 px-4 py-6 text-center text-sm text-gray-400"
        >
          {{ t('reconciliation.history.loading') }}
        </div>

        <div
          v-else-if="historyItems.length === 0"
          class="rounded-lg border border-dashed border-gray-300 px-4 py-6 text-center text-sm text-gray-400"
        >
          {{ t('reconciliation.history.empty') }}
        </div>

        <div
          v-else-if="filteredHistoryGroups.length === 0"
          class="rounded-lg border border-dashed border-gray-300 px-4 py-6 text-center text-sm text-gray-400"
        >
          {{ t('reconciliation.history.noMatch') }}
        </div>

        <div v-else>
          <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            <div
              v-for="group in paginatedHistoryCards"
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
                      group.hospitalName
                    }}</span>
                    <ElTag
                      :type="reviewTagType(group.item.reviewStatus)"
                      size="small"
                      effect="plain"
                    >
                      {{ reviewLabelMap[group.item.reviewStatus] ?? group.item.reviewStatus }}
                    </ElTag>
                    <span v-if="group.versions.length > 1" class="text-xs text-gray-400">
                      {{
                        t('reconciliation.history.versionCount', { count: group.versions.length })
                      }}
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
                      @change="(id: number) => setGroupSelectedVersion(group.key, id)"
                    >
                      <ElOption
                        v-for="version in group.versions"
                        :key="version.id"
                        :value="version.id"
                        :label="formatHistoryVersionLabel(version)"
                      />
                    </ElSelect>
                  </div>
                  <div v-else class="mt-2 text-xs text-gray-500">
                    V{{ group.item.versionNo }} · {{ formatDateTime(group.item.createdAt) }}
                  </div>
                  <div class="mt-2 flex flex-wrap gap-x-3 text-xs text-gray-500 leading-relaxed">
                    <span
                      >{{ t('reconciliation.history.operator') }}：{{
                        group.item.operatorName
                      }}</span
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
                <span
                  >{{ t('reconciliation.history.stats.totalRows') }}
                  {{ group.item.totalRows }}</span
                >
                <span
                  >{{ t('reconciliation.history.stats.correctedRows') }}
                  {{ group.item.correctedRows }}</span
                >
                <span
                  >{{ t('reconciliation.history.stats.warningRows') }}
                  {{ group.item.warningRows }}</span
                >
                <span>
                  {{ t('reconciliation.history.stats.difference') }}
                  {{ formatSignedNumber(group.item.totalDifference) }}
                </span>
              </div>

              <div class="mt-3 flex flex-wrap gap-2 border-t border-gray-200 pt-3">
                <ElButton size="small" @click="openDetail(group.item)">
                  {{ t('reconciliation.history.actions.detail') }}
                </ElButton>
                <ElButton
                  size="small"
                  type="primary"
                  :disabled="group.item.reviewStatus !== 'pending' || !canReviewReconciliation"
                  @click="openReview(group.item)"
                >
                  {{ t('reconciliation.history.actions.review') }}
                </ElButton>
                <ElDropdown
                  v-if="group.item.reviewStatus === 'approved' && canExport"
                  size="small"
                  @command="(cmd: string) => openExportWizard(group.item, cmd)"
                >
                  <ElButton size="small" type="success">
                    {{ t('reconciliation.history.actions.export') }}
                    <ElIcon class="el-icon--right"><ArrowDown /></ElIcon>
                  </ElButton>
                  <template #dropdown>
                    <ElDropdownMenu>
                      <ElDropdownItem command="bill">
                        {{ t('reconciliation.history.export.bill') }}
                      </ElDropdownItem>
                      <ElDropdownItem command="settlement">
                        {{ t('reconciliation.history.export.settlement') }}
                      </ElDropdownItem>
                      <ElDropdownItem command="departmentSummary">
                        {{ t('reconciliation.history.export.departmentSummary') }}
                      </ElDropdownItem>
                    </ElDropdownMenu>
                  </template>
                </ElDropdown>
              </div>
            </div>
          </div>
          <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
            <span class="text-sm text-gray-500">
              {{
                t('reconciliation.history.pagination.total', {
                  count: filteredHistoryGroups.length
                })
              }}
            </span>
            <div class="flex items-center gap-3">
              <ElSelect
                v-model="historyFilterPageSize"
                class="w-28"
                @change="historyFilterPage = 1"
              >
                <ElOption :value="6" :label="t('reconciliation.history.pagination.size6')" />
                <ElOption :value="9" :label="t('reconciliation.history.pagination.size9')" />
                <ElOption :value="12" :label="t('reconciliation.history.pagination.size12')" />
              </ElSelect>
              <ElPagination
                v-model:current-page="historyFilterPage"
                :page-size="historyFilterPageSize"
                :total="filteredHistoryGroups.length"
                layout="prev, pager, next"
                small
                background
              />
            </div>
          </div>
        </div>
      </ElCard>
    </div>
  </div>
  <ReconciliationExportWizard
    v-model="exportWizardVisible"
    :job-id="exportWizardJob?.id"
    :hospital-name="exportWizardJob?.hospitalName"
    :initial-export-type="exportWizardInitialType"
    :monthly-breakdown="exportWizardJob?.monthlyBreakdown ?? null"
    :logistics-fee="exportWizardJob?.logisticsFee ?? null"
    :settlement-adjustment="exportWizardJob?.settlementAdjustment ?? null"
    @exported="handleWizardExported"
  />
  <ElDialog v-model="detailVisible" title="校对详情" width="90%" top="3vh" class="max-h-[90vh]">
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
      <div
        v-if="detailLogisticsAllocation?.deptAllocations?.length"
        class="mb-4 rounded-lg border border-amber-200 bg-amber-50/60 p-4"
      >
        <div class="mb-2 flex items-center justify-between gap-2">
          <span class="text-sm font-semibold text-gray-800">
            {{ t('reconciliation.logisticsAllocation.title') }}
          </span>
          <span class="text-xs text-gray-500">
            {{ t('reconciliation.logisticsAllocation.total') }}：
            {{ formatNumber(detailLogisticsAllocation.totalLogisticsFee) }}
          </span>
        </div>
        <ElTable :data="detailLogisticsAllocation.deptAllocations" size="small" border stripe>
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
      </div>
      <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div class="grid grid-cols-2 gap-4 rounded-lg bg-gray-50 p-4 text-sm md:grid-cols-4 flex-1">
          <div>
            <span class="text-gray-500">医院：</span>
            <span class="font-medium">{{ detailData.hospitalName }}</span>
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

      <ElTable
        v-show="detailRowTab === 'regular'"
        :data="detailPaginatedRows"
        border
        stripe
        size="small"
        style="width: 100%"
        max-height="500"
        :default-sort="{ prop: 'rowNumber', order: 'ascending' }"
        :row-class-name="detailRowClassName"
        @selection-change="onDetailSelectionChange"
      >
        <ElTableColumn type="selection" width="42" :selectable="detailRowSelectable" />
        <ElTableColumn prop="rowNumber" label="行号" width="65" sortable />
        <ElTableColumn prop="sheetName" label="工作表" min-width="80" />
        <ElTableColumn prop="deliveryDate" label="发货日期" width="110" />
        <ElTableColumn prop="type" label="类型" min-width="90" />
        <ElTableColumn prop="packName" label="包名" min-width="140" show-overflow-tooltip />
        <ElTableColumn label="建议科室" min-width="120">
          <template #default="{ row }">
            <span
              v-if="detailRosterHintMap.get(row.rowNumber as number)"
              class="text-primary text-xs"
            >
              {{ detailRosterHintMap.get(row.rowNumber as number)?.suggestedDepartment }}
              <span class="text-gray-400">
                ({{ detailRosterHintMap.get(row.rowNumber as number)?.matchedDoctor }})
              </span>
            </span>
            <span v-else class="text-gray-300 text-xs">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="packageMaterial" label="包装材料" min-width="110" />
        <ElTableColumn prop="instrumentCount" label="器械数" width="70" align="right" />
        <ElTableColumn prop="packCount" label="包数" width="60" align="right" />
        <ElTableColumn label="原单价" width="80" align="right">
          <template #default="{ row }">{{
            formatNumber(row['unitPrice'] as number | null)
          }}</template>
        </ElTableColumn>
        <ElTableColumn label="规则单价" width="80" align="right">
          <template #default="{ row }">{{
            formatNumber(row['expectedUnitPrice'] as number | null)
          }}</template>
        </ElTableColumn>
        <ElTableColumn label="原总价" width="80" align="right">
          <template #default="{ row }">{{
            formatNumber(row['totalPrice'] as number | null)
          }}</template>
        </ElTableColumn>
        <ElTableColumn label="修正总价" width="120" align="right">
          <template #default="{ row }">
            <input
              v-if="detailData?.reviewStatus === 'pending'"
              :value="row['correctedTotalPrice']"
              type="number"
              step="0.01"
              min="0"
              class="detail-cell-input"
              @input="(e: Event) => onDetailRowEdit(row, (e.target as HTMLInputElement).value)"
            />
            <span v-else>{{ formatNumber(row['correctedTotalPrice'] as number | null) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="差额" width="100" align="right">
          <template #default="{ row }">
            <span
              v-if="detailData?.reviewStatus === 'pending'"
              :class="((row['difference'] as number) ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'"
            >
              {{ formatSignedNumber(row['difference'] as number | null) }}
            </span>
            <span v-else>{{ formatSignedNumber(row['difference'] as number | null) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn type="expand" width="42" :label="t('table.column.expand')">
          <template #default="{ row }">
            <ReconciliationBillingDetail v-if="hasRowBillingDetail(row)" :row="row" expanded />
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('reconciliation.detail.billingNotes')" min-width="180">
          <template #default="{ row }">
            <span v-if="row['isUrgent']" class="mr-1 text-xs text-warning">{{
              t('reconciliation.detail.urgentTag')
            }}</span>
            <ReconciliationBillingDetail :row="row" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <template v-if="detailData?.reviewStatus === 'pending'">
              <button
                v-if="hasDifference(row) && row['status'] !== 'corrected'"
                class="detail-cell-btn-warning"
                @click="handleFixSingleRow(row)"
              >
                修正
              </button>
              <span
                v-else-if="hasDifference(row) && row['status'] === 'corrected'"
                class="detail-cell-tag detail-cell-tag-success"
                >已修正</span
              >
              <select
                v-else
                v-model="row['status']"
                class="detail-cell-select"
                @change="onDetailRowChange(row)"
              >
                <option value="corrected">已修正</option>
                <option value="unchanged">无需修改</option>
                <option value="warning">人工复核</option>
                <option value="skipped">已跳过</option>
              </select>
            </template>
            <span v-else class="detail-cell-tag" :class="statusTagClass(row['status'] as string)">
              {{ statusLabels[row['status'] as string] ?? row['status'] }}
            </span>
          </template>
        </ElTableColumn>
      </ElTable>

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
              >{{ reviewTarget.hospitalName }} · {{ formatDateTime(reviewTarget.createdAt) }}</span
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
    const hospitalDisplayName =
      findRowText(
        matrix.slice(headerRowIndex, Math.min(matrix.length, headerRowIndex + 4)),
        '诊所'
      ) ||
      findRowText(
        matrix.slice(headerRowIndex, Math.min(matrix.length, headerRowIndex + 4)),
        '医院'
      ) ||
      findRowText(
        matrix.slice(headerRowIndex, Math.min(matrix.length, headerRowIndex + 4)),
        '门诊'
      ) ||
      sheetName
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
    return sheetMetas.map((item) => item.hospitalDisplayName.trim()).find(Boolean) || ''
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
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { ArrowDown, Select } from '@element-plus/icons-vue'
  import type { UploadProps } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useUserStore } from '@/store/modules/user'
  import {
    getActiveHospitalPricingRule,
    listHospitalPricingRules
  } from '@/api/hospital/pricingRulesApi'
  import {
    listHospitalReconciliations,
    importHospitalReconciliation,
    repriceReconciliation,
    updateHospitalReconciliationReview,
    createHospitalReconciliationExportLog,
    getReconciliationDetail,
    updateHospitalReconciliationRows,
    updateReconciliationRowsUrgent,
    getReconciliationRows,
    getUnmatchedProducts,
    type UnmatchedProductItem
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
  import { quickOnboardProduct } from '@/api/master-data/productsApi'
  import { buildReconciliationVersionGroupKey } from '@/utils/reconciliationVersionGroup'
  import ReconciliationBillingDetail from '@/components/business/reconciliation/ReconciliationBillingDetail.vue'
  import ReconciliationExportWizard from '@/components/business/reconciliation/ReconciliationExportWizard.vue'
  import ReconciliationAllocationPanel from '@/components/business/reconciliation/ReconciliationAllocationPanel.vue'
  import UatHelperPanel from '@/components/business/reconciliation/UatHelperPanel.vue'
  import BillingRoleBadge from '@/components/business/BillingRoleBadge.vue'
  import { useBillingPermission } from '@/composables/useBillingPermission'
  import { extractRowBillingFields, hasBillingDetail } from '@/utils/reconciliationBillingNotes'

  defineOptions({ name: 'HospitalReconciliation' })

  const { t } = useI18n()
  const { canEditReconciliationRows, canReviewReconciliation, canExport } = useBillingPermission()

  interface HistoryGroup {
    key: string
    hospitalName: string
    sourceFileName: string
    versions: Api.Hospital.ReconciliationJob[]
  }

  interface HistorySearchForm {
    keyword: string
    reviewStatus?: string
    operator: string
    dateRange: [string, string] | null
  }

  const createEmptyHistorySearch = (): HistorySearchForm => ({
    keyword: '',
    reviewStatus: undefined,
    operator: '',
    dateRange: null
  })

  function rowAsRecord(row: ProcessedRow): Record<string, unknown> {
    return row as unknown as Record<string, unknown>
  }

  function hasRowBillingDetail(row: ProcessedRow | Record<string, unknown>): boolean {
    return hasBillingDetail(row as Record<string, unknown>)
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
      billingNotes: billingFields.billingNotes
    }
  }

  const statusLabels: Record<string, string> = {
    corrected: '已修正',
    unchanged: '无需修改',
    skipped: '已跳过',
    warning: '人工复核'
  }

  const reviewLabelMap: Record<string, string> = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已驳回'
  }

  const statusTagType = (status: string): 'primary' | 'success' | 'info' | 'warning' => {
    switch (status) {
      case 'corrected':
        return 'primary'
      case 'unchanged':
        return 'success'
      case 'skipped':
        return 'info'
      case 'warning':
        return 'warning'
      default:
        return 'info'
    }
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

  const statusTagClass = (status: string): string => {
    switch (status) {
      case 'corrected':
        return 'detail-cell-tag-primary'
      case 'unchanged':
        return 'detail-cell-tag-success'
      case 'skipped':
        return 'detail-cell-tag-info'
      case 'warning':
        return 'detail-cell-tag-warning'
      default:
        return 'detail-cell-tag-info'
    }
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
  const activeRule = ref<Api.Hospital.PricingRuleRecord | null>(null)
  const isRuleLoading = ref(true)
  const historyItems = ref<Api.Hospital.ReconciliationJob[]>([])
  const isHistoryLoading = ref(false)
  const historySearchDraft = ref<HistorySearchForm>(createEmptyHistorySearch())
  const historySearchApplied = ref<HistorySearchForm>(createEmptyHistorySearch())
  const historyFilterPage = ref(1)
  const historyFilterPageSize = ref(9)
  const historyGroupSelectedVersion = ref<Map<string, number>>(new Map())

  function getHistoryGroupKey(item: Api.Hospital.ReconciliationJob): string {
    return buildReconciliationVersionGroupKey(item.hospitalName, item.sourceFileName)
  }

  const historyGroups = computed<HistoryGroup[]>(() => {
    const map = new Map<string, Api.Hospital.ReconciliationJob[]>()
    for (const item of historyItems.value) {
      const key = getHistoryGroupKey(item)
      if (!map.has(key)) map.set(key, [])
      map.get(key)!.push(item)
    }
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b, 'zh-CN'))
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
  })

  const filteredHistoryGroups = computed(() => {
    const { keyword, reviewStatus, operator, dateRange } = historySearchApplied.value
    const normalizedKeyword = keyword.trim().toLowerCase()
    const normalizedOperator = operator.trim().toLowerCase()

    return historyGroups.value.filter((group) => {
      if (normalizedKeyword) {
        const keywordMatched =
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

  const paginatedHistoryCards = computed(() =>
    paginatedHistoryGroups.value.map((group) => ({
      ...group,
      item: getGroupSelectedVersion(group)
    }))
  )

  function getGroupSelectedVersion(group: HistoryGroup): Api.Hospital.ReconciliationJob {
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
    return `V${version.versionNo} · ${formatDateTime(version.createdAt)} · ${version.sourceFileName}`
  }

  function applyHistorySearch() {
    historySearchApplied.value = {
      keyword: historySearchDraft.value.keyword,
      reviewStatus: historySearchDraft.value.reviewStatus,
      operator: historySearchDraft.value.operator,
      dateRange: historySearchDraft.value.dateRange ? [...historySearchDraft.value.dateRange] : null
    }
    historyFilterPage.value = 1
  }

  function resetHistorySearch() {
    const empty = createEmptyHistorySearch()
    historySearchDraft.value = { ...empty }
    historySearchApplied.value = { ...empty }
    historyFilterPage.value = 1
  }

  const highlightedJobIds = ref<Set<number>>(new Set())

  const userStore = useUserStore()
  const operatorName = ref(userStore.info.userName || '')

  const detailVisible = ref(false)
  const detailData = ref<Api.Hospital.ReconciliationJob | null>(null)
  const detailLoading = ref(false)
  /** 行数据缓存：page → rows，服务端分页后按需加载 */
  const detailRowsCache = ref(new Map<number, Record<string, unknown>[]>())
  const detailRowsTotal = ref(0)
  const detailPage = ref(1)
  const detailPageSize = ref(200)
  const detailLoadingRows = ref(false)
  const detailPaginatedRows = computed(() => {
    return detailRowsCache.value.get(detailPage.value) ?? []
  })
  const detailRosterHintMap = computed(() => {
    const map = new Map<number, RosterMatchHint>()
    for (const hint of detailRosterHints.value) {
      if (hint.rowNumber != null) {
        map.set(hint.rowNumber, hint)
      }
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
  const detailRosterHints = ref<RosterMatchHint[]>([])
  const detailAllocation = ref<AllocationResult | null>(null)
  const detailLogisticsAllocation = ref<LogisticsAllocationPreview | null>(null)
  const isRunningAllocation = ref(false)
  const isExportingOrchestrated = ref(false)

  const reviewVisible = ref(false)
  const reviewTarget = ref<Api.Hospital.ReconciliationJob | null>(null)
  const reviewForm = ref({ status: 'approved', comment: '' })
  const isReviewing = ref(false)

  const exportWizardVisible = ref(false)
  const exportWizardJob = ref<Api.Hospital.ReconciliationJob | null>(null)
  const exportWizardInitialType = ref<'bill' | 'settlement' | 'dept_summary'>('bill')

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

  /** 科室分组摘要（单次遍历，在分组时与 entryGroupedRows 一并计算） */
  function computeGroupSummary(rows: ProcessedRow[]) {
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
        acc.packCount += row.packCount ?? 0
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
        correctedTotalPrice: 0,
        packCount: 0
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
    void loadHistory()
  })

  onActivated(() => {
    if (skipNextActivatedHistoryLoad) {
      skipNextActivatedHistoryLoad = false
      return
    }
    void loadHistory()
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
      hospitalName: file.name.replace(/\.[^.]+$/, '').replace(/^\d{4}[\s_-]?/, ''),
      rule: null,
      savedJobId: null,
      processingProgress: 0,
      displayPage: 1,
      displayPageSize: 200,
      displayTotal: 0,
      savedSummary: null,
      onlyShowAbnormal: false,
      allAnomalyRows: null,
      anomalyLoading: false
    })
    uploadEntries.value.push(entry)
    await reparseEntry(entry)
    await resolveEntryRule(entry)
  }

  /** 根据条目的医院名称解析匹配的计费规则 */
  async function resolveEntryRule(entry: UploadEntry) {
    // 收集所有可能用于匹配的关键词
    const keywords: string[] = []
    if (entry.hospitalName) keywords.push(entry.hospitalName)
    // 同时尝试从文件名提取的原始名称（reparseEntry 中可能被 Excel 内容覆盖）
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
      entry.hospitalName = resolveSettlementHospitalName(workbook.sheetMetas) || entry.hospitalName
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
      await Promise.all([loadEntryPage(entry, 1), loadHistory(), refreshUnmatchedCount(entry)])

      entry.status = 'saved'
      ElMessage.success(`「${entry.file.name}」已保存，版本 V${saved.versionNo}`)
      highlightedJobIds.value.add(saved.id)
      highlightedJobIds.value = new Set(highlightedJobIds.value)
    } catch (error) {
      entry.status = 'error'
      entry.errorMessage = error instanceof Error ? error.message : '校对保存失败'
    }
  }

  /** 加载条目指定页的行数据 */
  async function loadEntryPage(entry: UploadEntry, page: number) {
    if (!entry.savedJobId) return
    try {
      const result = await getReconciliationRows(entry.savedJobId, page, entry.displayPageSize)
      const rows = (result.rows ?? []) as unknown as Record<string, unknown>[]
      entry.processedRows = rows.map((row) => mapApiRowToProcessedRow(row))
      entry.displayTotal = result.total
      entry.displayPage = page
    } catch {
      // ignore
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
          const processed = allRows.map((row) => mapApiRowToProcessedRow(row))
          entry.allAnomalyRows = processed.filter((row) => row.status !== 'unchanged')
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

  /** 导出异常明细：调用后端接口，下载差额不为0的异常行 Excel */
  async function handleExportAnomalies(entry: UploadEntry) {
    if (!entry.savedJobId) return
    try {
      const blob = await downloadBlob(
        `/api/hospital-reconciliations/${entry.savedJobId}/export-anomalies`,
        {}
      )
      const hospitalName = entry.rule?.hospitalName || 'hospital'
      const fileName = buildExportFileName('异常明细_', hospitalName)
      triggerDownload(blob, fileName)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '导出异常失败')
    }
  }

  /** 导出单个条目的异常项 */
  /** 条目的摘要统计（优先使用后端汇总，回退到前端按页计算） */
  function entrySummary(entry: UploadEntry) {
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

  /** 按科室分组条目行 */
  function entryGroupedRows(
    entry: UploadEntry
  ): { sheetName: string; rows: ProcessedRow[]; summary: ReturnType<typeof computeGroupSummary> }[] {
    const rows = entryDisplayRows(entry)
    const map = new Map<string, ProcessedRow[]>()
    for (const row of rows) {
      const key = row.sheetName || '(默认)'
      if (!map.has(key)) map.set(key, [])
      map.get(key)!.push(row)
    }
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b, 'zh-CN'))
      .map(([sheetName, groupRows]) => ({
        sheetName,
        rows: groupRows,
        summary: computeGroupSummary(groupRows)
      }))
  }

  /** 从历史记录查找版本号 */
  function findVersion(jobId: number | null): string {
    const found = historyItems.value.find((h) => h.id === jobId)
    return found ? `V${found.versionNo}` : ''
  }

  const openDetail = async (item: Api.Hospital.ReconciliationJob) => {
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
      detailAllocation.value = await getJobAllocationResult(jobId)
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

  /** 加载指定页的行数据（如未缓存则请求服务端） */
  async function loadDetailPage(page: number) {
    if (detailRowsCache.value.has(page)) return
    if (!detailData.value) return
    detailLoadingRows.value = true
    try {
      const result = await getReconciliationRows(detailData.value.id, page, detailPageSize.value)
      detailRowsCache.value.set(page, (result.rows ?? []) as unknown as Record<string, unknown>[])
      detailRowsTotal.value = result.total
      // 触发响应式更新
      detailRowsCache.value = new Map(detailRowsCache.value)
    } catch {
      // ignore
    } finally {
      detailLoadingRows.value = false
    }
  }

  /** 翻页时检查缓存，未命中则加载 */
  function onDetailPageChange(p: number) {
    detailPage.value = p
    loadDetailPage(p)
  }

  /** 详情行修正总价编辑后重算差额 */
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

  /** 详情行其他字段修改后触发（如状态变更） */
  function onDetailRowChange(row: Record<string, unknown>) {
    updateDetailSummary()
  }

  /** 判断行是否有差额 */
  function hasDifference(row: Record<string, unknown>): boolean {
    const diff = row['difference'] as number | null | undefined
    return diff !== null && diff !== undefined && diff !== 0
  }

  function detailRowClassName({ row }: { row: Record<string, unknown> }): string {
    const classes: string[] = []
    if (row['isUrgent']) classes.push('detail-row-urgent')
    const diff = row['difference'] as number | null | undefined
    if (diff === null || diff === undefined) return classes.join(' ')
    classes.push(diff !== 0 ? 'detail-row-diff' : 'detail-row-ok')
    return classes.join(' ')
  }

  function detailRowSelectable(row: Record<string, unknown>) {
    return detailData.value?.reviewStatus === 'pending'
  }

  function onDetailSelectionChange(rows: Record<string, unknown>[]) {
    detailSelectedRows.value = rows
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

  /** 获取全部行数据（按需加载所有未缓存页，返回扁平数组） */
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

  /** 更新详情弹窗中的摘要数据（已修正/待复核/总差额），需加载全量行再统计 */
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

  /** 单行修正：保留用户填写的修正总价，仅标记为已修正 */
  function applySingleRowCorrection(row: Record<string, unknown>) {
    const ctp = row['correctedTotalPrice'] as number | null
    const totalPrice = row['totalPrice'] as number | null
    if (ctp != null && totalPrice != null) {
      row['difference'] = Math.round((ctp - totalPrice) * 100) / 100
    }
    row['status'] = 'corrected'
  }

  /** 单行修正：重算价格并标记为已修正 */
  async function handleFixSingleRow(row: Record<string, unknown>) {
    applySingleRowCorrection(row)
    await updateDetailSummary()
  }

  /** 一键修正：二次确认后调用后端定价引擎重新计算 */
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
      // 将后端返回的全部行按页大小重新分配到缓存中
      const allRows = result.rows
      const newCache = new Map<number, Record<string, unknown>[]>()
      const pageSize = detailPageSize.value
      for (let i = 0; i < allRows.length; i += pageSize) {
        const page = Math.floor(i / pageSize) + 1
        newCache.set(page, allRows.slice(i, i + pageSize))
      }
      detailRowsTotal.value = allRows.length
      detailRowsCache.value = newCache
      // 如果当前页码超出新的总页数，回到第一页
      const maxPage = Math.ceil(allRows.length / detailPageSize.value) || 1
      if (detailPage.value > maxPage) detailPage.value = 1
      // 更新详情摘要（使用后端计算的汇总数据，字段名做映射）
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

  /** 保存修改：有变化时后端自动升级版本号 */
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

      const idx = historyItems.value.findIndex((h) => h.id === previousJobId)
      if (idx >= 0 && versionUpgraded) {
        historyItems.value[idx] = { ...historyItems.value[idx], ...updated }
      }
      await loadHistory()

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

  const openReview = (item: Api.Hospital.ReconciliationJob) => {
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
        reviewerName: operatorName.value.trim() || '未命名审核人'
      })
      ElMessage.success(`已${reviewForm.value.status === 'approved' ? '通过' : '驳回'}审核`)
      reviewVisible.value = false
      const idx = historyItems.value.findIndex((item) => item.id === reviewTarget.value!.id)
      if (idx >= 0) {
        historyItems.value[idx] = { ...historyItems.value[idx], ...updated }
      }
      if (detailData.value?.id === reviewTarget.value.id) {
        detailData.value = { ...detailData.value, ...updated }
      }
      await loadHistory()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '审核失败')
    } finally {
      isReviewing.value = false
    }
  }

  const openExportWizard = (item: Api.Hospital.ReconciliationJob, type: string) => {
    exportWizardJob.value = item
    exportWizardInitialType.value =
      type === 'settlement' ? 'settlement' : type === 'departmentSummary' ? 'dept_summary' : 'bill'
    exportWizardVisible.value = true
  }

  async function handleWizardExported(payload: { exportType: string; fileName: string }) {
    if (!exportWizardJob.value) return
    await logExportFromJob(exportWizardJob.value.id, payload.exportType, payload.fileName)
  }

  /** 导出/全量筛选前从分页接口加载全部行数据（限制并发，避免阻塞 UI） */
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

  async function logExportFromJob(jobId: number, exportType: string, fileName: string) {
    try {
      await createHospitalReconciliationExportLog(jobId, {
        exportType,
        fileName,
        operatorName: operatorName.value.trim() || '未命名操作人'
      })
    } catch {
      // ignore logging errors for history exports
    }
  }

  async function loadHistory(nextHospitalName?: string) {
    try {
      isHistoryLoading.value = true
      historyItems.value = await listHospitalReconciliations(nextHospitalName)
    } catch (error) {
      ElMessage.error(
        error instanceof Error ? error.message : t('reconciliation.history.loadFailed')
      )
    } finally {
      isHistoryLoading.value = false
    }
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
  :deep(.compact-upload .el-upload) {
    width: 100%;
  }

  :deep(.compact-upload .el-upload-dragger) {
    width: 100%;
    height: auto;
    min-height: 48px;
    padding: 0;
    border-radius: 6px;
  }

  :deep(.compact-upload .el-upload-dragger:hover) {
    border-color: var(--el-color-primary);
  }

  .entry-section {
    margin-top: 1rem;
    padding-top: 1rem;
    border-top: 1px solid #f0f0f0;
  }

  .entry-section-first {
    margin-top: 0.75rem;
  }

  :deep(.detail-row-diff td) {
    background-color: #fef0f0 !important;
  }

  :deep(.detail-row-diff:hover td) {
    background-color: #fde2e2 !important;
  }

  :deep(.detail-row-ok td) {
    background-color: #f0f9eb !important;
  }

  :deep(.detail-row-ok:hover td) {
    background-color: #e1f3d8 !important;
  }

  /* 详情表格原生交互元素（替代 ElInput/ElSelect/ElButton，避免每行创建 Vue 组件导致渲染卡顿） */
  .detail-cell-input {
    width: 100px;
    padding: 4px 8px;
    font-size: 12px;
    text-align: right;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    outline: none;
    transition: border-color 0.2s;
  }

  .detail-cell-input:focus {
    border-color: #409eff;
  }

  .detail-cell-input::-webkit-inner-spin-button,
  .detail-cell-input::-webkit-outer-spin-button {
    height: 24px;
    opacity: 1;
  }

  .detail-cell-select {
    width: 95px;
    padding: 4px;
    font-size: 12px;
    background: #fff;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    outline: none;
  }

  .detail-cell-select:focus {
    border-color: #409eff;
  }

  .detail-cell-btn-warning {
    display: inline-block;
    padding: 4px 8px;
    font-size: 12px;
    color: #e6a23c;
    white-space: nowrap;
    cursor: pointer;
    background: #fdf6ec;
    border: 1px solid #e6a23c;
    border-radius: 4px;
  }

  .detail-cell-btn-warning:hover {
    background: #f5dab1;
  }

  .detail-cell-tag {
    display: inline-block;
    padding: 2px 6px;
    font-size: 12px;
    white-space: nowrap;
    border-radius: 4px;
  }

  .detail-cell-tag-primary {
    color: #409eff;
    background: #ecf5ff;
    border: 1px solid #409eff;
  }

  .detail-cell-tag-success {
    color: #67c23a;
    background: #f0f9eb;
    border: 1px solid #67c23a;
  }

  .detail-cell-tag-info {
    color: #909399;
    background: #f4f4f5;
    border: 1px solid #909399;
  }

  .detail-cell-tag-warning {
    color: #e6a23c;
    background: #fdf6ec;
    border: 1px solid #e6a23c;
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
