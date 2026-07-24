<template>
  <div class="export-templates-page p-4">
    <ElCard shadow="never">
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <span class="text-lg font-semibold">{{ t('exportTemplates.title') }}</span>
          <ElButton type="primary" @click="openCreate">{{ t('exportTemplates.create') }}</ElButton>
        </div>
      </template>

      <ElForm :inline="true" class="mb-4 flex flex-wrap gap-y-2">
        <ElFormItem :label="t('exportTemplates.filters.type')">
          <ElSelect
            v-model="filterType"
            clearable
            :placeholder="t('exportTemplates.filters.typeAll')"
            style="width: 160px"
            @change="loadData"
          >
            <ElOption
              v-for="opt in typeOptions"
              :key="opt.value"
              :label="t(opt.labelKey)"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('exportTemplates.filters.scope')">
          <ElSelect v-model="filterScope" style="width: 140px" @change="loadData">
            <ElOption :label="t('exportTemplates.filters.scopeAll')" value="all" />
            <ElOption :label="t('exportTemplates.filters.scopeGlobal')" value="global" />
            <ElOption :label="t('exportTemplates.filters.scopeCustomer')" value="customer" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton @click="loadData">{{ t('table.searchBar.search') }}</ElButton>
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="filteredTemplates" stripe border>
        <ElTableColumn prop="name" :label="t('exportTemplates.columns.name')" min-width="160" />
        <ElTableColumn :label="t('exportTemplates.columns.type')" width="120">
          <template #default="{ row }">
            {{ t(typeLabelKey(row.templateType)) }}
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('exportTemplates.columns.scope')" width="110" align="center">
          <template #default="{ row }">
            <ElTag size="small" :type="row.customerId ? 'warning' : 'info'">
              {{
                row.customerId
                  ? t('exportTemplates.scopeCustomer')
                  : t('exportTemplates.scopeGlobal')
              }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="strategyKey"
          :label="t('exportTemplates.columns.strategy')"
          width="150"
        />
        <ElTableColumn :label="t('exportTemplates.columns.columnMapping')" min-width="200">
          <template #default="{ row }">
            <span class="text-xs text-gray-600">{{ columnMappingSummary(row.columnMapping) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('exportTemplates.columns.active')" width="80" align="center">
          <template #default="{ row }">
            <ElTag size="small" :type="row.isActive !== false ? 'success' : 'info'">
              {{
                row.isActive !== false ? t('exportTemplates.active') : t('exportTemplates.inactive')
              }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          :label="t('exportTemplates.columns.actions')"
          width="140"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <ElButton type="primary" link @click="openEdit(row)">{{ t('common.edit') }}</ElButton>
            <ElButton type="danger" link @click="handleDelete(row)">{{
              t('common.delete')
            }}</ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <span class="text-gray-400">{{ t('exportTemplates.empty') }}</span>
        </template>
      </ElTable>
    </ElCard>

    <ElDialog
      v-model="dialogVisible"
      :title="editingId ? t('exportTemplates.editTitle') : t('exportTemplates.createTitle')"
      width="720px"
      destroy-on-close
    >
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="120px">
        <ElFormItem :label="t('exportTemplates.form.name')" prop="name">
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem :label="t('exportTemplates.form.type')" prop="templateType">
          <ElSelect v-model="form.templateType" class="w-full">
            <ElOption
              v-for="opt in typeOptions"
              :key="opt.value"
              :label="t(opt.labelKey)"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('exportTemplates.form.strategy')" prop="strategyKey">
          <ElSelect v-model="form.strategyKey" class="w-full" filterable allow-create>
            <ElOption
              v-for="opt in strategyOptions"
              :key="opt.value"
              :label="t(opt.labelKey)"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('exportTemplates.form.storagePath')">
          <ElInput
            v-model="form.storagePath"
            :placeholder="t('exportTemplates.form.storagePathHint')"
          />
        </ElFormItem>
        <ElFormItem :label="t('exportTemplates.form.customerId')">
          <ElInputNumber
            v-model="form.customerId"
            :min="1"
            class="w-full"
            :controls="false"
            clearable
          />
          <div class="mt-1 text-xs text-gray-500">{{
            t('exportTemplates.form.customerIdHint')
          }}</div>
        </ElFormItem>
        <ElFormItem :label="t('exportTemplates.form.active')">
          <ElSwitch v-model="form.isActive" />
        </ElFormItem>

        <ElDivider>{{ t('exportTemplates.form.columnMappingTitle') }}</ElDivider>
        <ElFormItem :label="t('exportTemplates.form.mode')">
          <ElRadioGroup v-model="mappingMode">
            <ElRadio value="remove">{{ t('exportTemplates.form.modeRemove') }}</ElRadio>
            <ElRadio value="keep">{{ t('exportTemplates.form.modeKeep') }}</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem :label="t('exportTemplates.form.columns')">
          <div class="column-tag-editor w-full">
            <div class="mb-2 flex flex-wrap gap-1">
              <ElTag
                v-for="col in mappingColumns"
                :key="col"
                closable
                @close="removeMappingColumn(col)"
              >
                {{ col }}
              </ElTag>
              <span v-if="mappingColumns.length === 0" class="text-xs text-gray-400">
                {{ t('exportTemplates.form.columnsEmpty') }}
              </span>
            </div>
            <div class="flex flex-wrap gap-2">
              <ElSelect
                v-model="columnPicker"
                filterable
                allow-create
                clearable
                :placeholder="t('exportTemplates.form.addColumn')"
                style="width: 220px"
                @change="addMappingColumn"
              >
                <ElOption
                  v-for="col in DEFAULT_BILL_COLUMNS"
                  :key="col"
                  :label="col"
                  :value="col"
                />
              </ElSelect>
              <ElButton @click="fillDefaultBillColumns">{{
                t('exportTemplates.form.fillDefault')
              }}</ElButton>
            </div>
          </div>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">{{ t('common.cancel') }}</ElButton>
        <ElButton type="primary" :loading="saving" @click="submitForm">{{
          t('common.save')
        }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import type { FormInstance, FormRules } from 'element-plus'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import {
    DEFAULT_BILL_COLUMNS,
    EXPORT_STRATEGY_OPTIONS,
    buildColumnMappingJson,
    buildSheetConfigJson,
    createExportTemplate,
    deleteExportTemplate,
    listExportTemplates,
    parseColumnMapping,
    parseSheetConfig,
    updateExportTemplate,
    type ExportTemplateRecord
  } from '@/api/hospital/exportTemplatesApi'

  const { t } = useI18n()

  const loading = ref(false)
  const saving = ref(false)
  const dialogVisible = ref(false)
  const editingId = ref<number | null>(null)
  const templates = ref<ExportTemplateRecord[]>([])
  const filterType = ref('')
  const filterScope = ref<'all' | 'global' | 'customer'>('all')
  const formRef = ref<FormInstance>()
  const mappingMode = ref<'remove' | 'keep'>('remove')
  const mappingColumns = ref<string[]>([])
  const columnPicker = ref<string>('')

  const typeOptions = [
    { value: 'bill', labelKey: 'exportTemplates.types.bill' },
    { value: 'settlement', labelKey: 'exportTemplates.types.settlement' },
    { value: 'dept_summary', labelKey: 'exportTemplates.types.deptSummary' },
    { value: 'price_summary', labelKey: 'exportTemplates.types.priceSummary' },
    { value: 'instrument_audit', labelKey: 'exportTemplates.types.instrumentAudit' },
    { value: 'logistics_allocation', labelKey: 'exportTemplates.types.logisticsAllocation' },
    { value: 'grand_summary', labelKey: 'exportTemplates.types.grandSummary' }
  ]

  const strategyOptions = EXPORT_STRATEGY_OPTIONS

  const form = reactive({
    name: '',
    templateType: 'bill',
    strategyKey: 'standard_bill',
    storagePath: '',
    customerId: undefined as number | undefined,
    isActive: true
  })

  const rules: FormRules = {
    name: [
      { required: true, message: () => t('exportTemplates.validation.name'), trigger: 'blur' }
    ],
    templateType: [
      { required: true, message: () => t('exportTemplates.validation.type'), trigger: 'change' }
    ],
    strategyKey: [
      { required: true, message: () => t('exportTemplates.validation.strategy'), trigger: 'change' }
    ]
  }

  const filteredTemplates = computed(() => {
    let data = templates.value
    if (filterScope.value === 'global') {
      data = data.filter((row) => !row.customerId)
    } else if (filterScope.value === 'customer') {
      data = data.filter((row) => !!row.customerId)
    }
    return data
  })

  function typeLabelKey(type: string) {
    const found = typeOptions.find((o) => o.value === type)
    return found?.labelKey ?? 'exportTemplates.types.bill'
  }

  function columnMappingSummary(raw?: string | null) {
    const parsed = parseColumnMapping(raw)
    if (parsed.keepColumns?.length) {
      return t('exportTemplates.summaryKeep', { count: parsed.keepColumns.length })
    }
    if (parsed.removeColumns?.length) {
      return t('exportTemplates.summaryRemove', { cols: parsed.removeColumns.join('、') })
    }
    return t('exportTemplates.summaryNone')
  }

  function resetForm() {
    form.name = ''
    form.templateType = 'bill'
    form.strategyKey = 'standard_bill'
    form.storagePath = ''
    form.customerId = undefined
    form.isActive = true
    mappingMode.value = 'remove'
    mappingColumns.value = []
    columnPicker.value = ''
  }

  function loadFormFromRecord(row: ExportTemplateRecord) {
    form.name = row.name
    form.templateType = row.templateType
    form.storagePath = row.storagePath ?? ''
    form.customerId = row.customerId ?? undefined
    form.isActive = row.isActive !== false
    const sheet = parseSheetConfig(row.sheetConfig)
    form.strategyKey = sheet.strategyKey ?? row.strategyKey ?? 'standard_bill'
    const mapping = parseColumnMapping(row.columnMapping)
    if (mapping.keepColumns?.length) {
      mappingMode.value = 'keep'
      mappingColumns.value = [...mapping.keepColumns]
    } else {
      mappingMode.value = 'remove'
      mappingColumns.value = [...(mapping.removeColumns ?? [])]
    }
  }

  function addMappingColumn(val?: string) {
    const col = (val ?? columnPicker.value)?.trim()
    if (!col) return
    if (!mappingColumns.value.includes(col)) {
      mappingColumns.value.push(col)
    }
    columnPicker.value = ''
  }

  function removeMappingColumn(col: string) {
    mappingColumns.value = mappingColumns.value.filter((c) => c !== col)
  }

  function fillDefaultBillColumns() {
    mappingMode.value = 'keep'
    mappingColumns.value = [...DEFAULT_BILL_COLUMNS]
  }

  async function loadData() {
    loading.value = true
    try {
      templates.value = await listExportTemplates({
        templateType: filterType.value || undefined
      })
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : t('exportTemplates.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    editingId.value = null
    resetForm()
    dialogVisible.value = true
  }

  function openEdit(row: ExportTemplateRecord) {
    editingId.value = row.id
    resetForm()
    loadFormFromRecord(row)
    dialogVisible.value = true
  }

  async function submitForm() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      const columnMapping = buildColumnMappingJson(
        mappingMode.value === 'keep'
          ? { keepColumns: mappingColumns.value }
          : { removeColumns: mappingColumns.value }
      )
      const payload = {
        name: form.name.trim(),
        templateType: form.templateType,
        storagePath: form.storagePath.trim(),
        customerId: form.customerId ?? null,
        isActive: form.isActive,
        columnMapping,
        sheetConfig: buildSheetConfigJson(form.strategyKey)
      }
      if (editingId.value) {
        await updateExportTemplate(editingId.value, payload)
        ElMessage.success(t('exportTemplates.updated'))
      } else {
        await createExportTemplate(payload)
        ElMessage.success(t('exportTemplates.created'))
      }
      dialogVisible.value = false
      await loadData()
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : t('exportTemplates.saveFailed'))
    } finally {
      saving.value = false
    }
  }

  async function handleDelete(row: ExportTemplateRecord) {
    try {
      await ElMessageBox.confirm(
        t('exportTemplates.deleteConfirm', { name: row.name }),
        t('common.tips'),
        { type: 'warning' }
      )
      await deleteExportTemplate(row.id)
      ElMessage.success(t('exportTemplates.deleted'))
      await loadData()
    } catch {
      // cancelled
    }
  }

  onMounted(() => {
    void loadData()
  })
</script>

<style scoped>
  .column-tag-editor {
    border: 1px dashed var(--el-border-color);
    border-radius: 6px;
    padding: 10px;
  }
</style>
