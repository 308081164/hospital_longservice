<template>
  <div class="rule-batch-import">
    <div class="rule-batch-import__header">
      <div>
        <div class="text-sm font-semibold">{{ t('billingRules.import.title') }}</div>
        <div class="text-xs text-gray-500">{{ t('billingRules.import.subtitle') }}</div>
      </div>
      <ElButton
        size="small"
        type="primary"
        :disabled="!customerId || !canImport"
        @click="openWizard"
      >
        {{ t('billingRules.import.openWizard') }}
      </ElButton>
    </div>

    <ElDialog
      v-model="visible"
      :title="t('billingRules.import.wizardTitle')"
      width="820px"
      destroy-on-close
      @closed="resetWizard"
    >
      <ElSteps :active="step" finish-status="success" align-center class="mb-4">
        <ElStep :title="t('billingRules.import.stepUpload')" />
        <ElStep :title="t('billingRules.import.stepMapping')" />
        <ElStep :title="t('billingRules.import.stepPreview')" />
      </ElSteps>

      <div v-if="step === 0" class="py-2">
        <ElUpload
          drag
          :auto-upload="false"
          accept=".xlsx,.xls,.csv"
          :show-file-list="false"
          @change="handleFileChange"
        >
          <div class="py-6 text-center text-sm text-gray-500">
            {{ t('billingRules.import.dropHint') }}
          </div>
        </ElUpload>
        <div v-if="fileName" class="mt-2 text-xs text-gray-600">
          {{ t('billingRules.import.selectedFile') }}：{{ fileName }}（{{ rawRows.length }}
          {{ t('billingRules.import.rows') }}）
        </div>
      </div>

      <div v-else-if="step === 1" class="py-2">
        <ElAlert
          type="info"
          :closable="false"
          class="mb-3"
          :title="t('billingRules.import.mappingHint')"
        />
        <ElTable :data="fieldMappings" size="small" border>
          <ElTableColumn :label="t('billingRules.import.field')" prop="label" width="160" />
          <ElTableColumn :label="t('billingRules.import.required')" width="80" align="center">
            <template #default="{ row }">
              <ElTag v-if="row.required" size="small" type="danger">*</ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn :label="t('billingRules.import.excelColumn')">
            <template #default="{ row }">
              <ElSelect v-model="columnMap[row.key]" clearable class="w-full" size="small">
                <ElOption v-for="col in excelHeaders" :key="col" :label="col" :value="col" />
              </ElSelect>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>

      <div v-else v-loading="previewLoading" class="py-2">
        <ElAlert
          v-if="previewErrors.length"
          type="error"
          :closable="false"
          class="mb-3"
          :title="t('billingRules.import.errorsFound', { count: previewErrors.length })"
        >
          <ul class="text-xs max-h-24 overflow-auto">
            <li v-for="(err, idx) in previewErrors" :key="idx">{{ err }}</li>
          </ul>
        </ElAlert>
        <ElAlert
          v-if="hasConflicts"
          type="warning"
          :closable="false"
          class="mb-3"
          :title="t('billingRules.import.conflictsFound')"
        />
        <div class="mb-2 text-sm">
          {{ t('billingRules.import.validCount', { count: previewRules.length }) }}
        </div>
        <ElTable :data="previewRules.slice(0, 50)" size="small" border max-height="320">
          <ElTableColumn
            prop="ruleType"
            :label="t('billingRules.import.colRuleType')"
            width="100"
          />
          <ElTableColumn prop="name" :label="t('billingRules.import.colName')" min-width="120" />
          <ElTableColumn
            prop="keywords"
            :label="t('billingRules.import.colKeywords')"
            min-width="140"
          />
          <ElTableColumn prop="price" :label="t('billingRules.import.colPrice')" width="90" />
          <ElTableColumn prop="priority" :label="t('billingRules.import.colPriority')" width="80" />
        </ElTable>
        <div v-if="previewRules.length > 50" class="mt-2 text-xs text-gray-400">
          {{ t('billingRules.import.previewTruncated', { total: previewRules.length }) }}
        </div>
      </div>

      <template #footer>
        <ElButton @click="visible = false">{{ t('common.cancel') }}</ElButton>
        <ElButton v-if="step > 0" @click="step -= 1">{{ t('billingRules.import.back') }}</ElButton>
        <ElButton v-if="step < 2" type="primary" :disabled="!canGoNext" @click="goNext">
          {{ t('billingRules.import.next') }}
        </ElButton>
        <ElButton
          v-else
          type="success"
          :loading="confirming"
          :disabled="previewRules.length === 0 || previewErrors.length > 0"
          @click="handleConfirm"
        >
          {{ t('billingRules.import.confirmImport', { count: previewRules.length }) }}
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, reactive, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import type { UploadFile } from 'element-plus'
  import * as XLSX from 'xlsx'
  import { confirmRuleImport, previewRuleImport } from '@/api/billing/billingRulesApi'
  import { useUserStore } from '@/store/modules/user'

  const props = defineProps<{
    customerId?: number | null
    canImport?: boolean
  }>()

  const emit = defineEmits<{
    imported: [count: number]
  }>()

  const { t } = useI18n()
  const userStore = useUserStore()

  const visible = ref(false)
  const step = ref(0)
  const fileName = ref('')
  const excelHeaders = ref<string[]>([])
  const rawRows = ref<Record<string, unknown>[]>([])
  const columnMap = reactive<Record<string, string>>({})
  const previewLoading = ref(false)
  const confirming = ref(false)
  const previewRules = ref<Record<string, unknown>[]>([])
  const previewErrors = ref<string[]>([])
  const hasConflicts = ref(false)

  const FIELD_DEFS = [
    {
      key: 'ruleType',
      labelKey: 'billingRules.import.colRuleType',
      required: true,
      aliases: ['ruleType', '规则类型', '类型', 'rule_type']
    },
    {
      key: 'name',
      labelKey: 'billingRules.import.colName',
      required: false,
      aliases: ['name', '规则名称', '名称', 'ruleName']
    },
    {
      key: 'productId',
      labelKey: 'billingRules.import.colProductId',
      required: false,
      aliases: ['productId', '商品ID', 'product_id']
    },
    {
      key: 'keywords',
      labelKey: 'billingRules.import.colKeywords',
      required: false,
      aliases: ['keywords', '关键词', '关键字']
    },
    {
      key: 'price',
      labelKey: 'billingRules.import.colPrice',
      required: false,
      aliases: ['price', '单价', '固定价']
    },
    {
      key: 'fee',
      labelKey: 'billingRules.import.colFee',
      required: false,
      aliases: ['fee', '加收', '费用']
    },
    {
      key: 'temperature',
      labelKey: 'billingRules.import.colTemperature',
      required: false,
      aliases: ['temperature', '温度', 'HT/LT']
    },
    {
      key: 'priority',
      labelKey: 'billingRules.import.colPriority',
      required: false,
      aliases: ['priority', '优先级']
    }
  ] as const

  const fieldMappings = computed(() =>
    FIELD_DEFS.map((f) => ({
      key: f.key,
      label: t(f.labelKey),
      required: f.required
    }))
  )

  const canGoNext = computed(() => {
    if (step.value === 0) return rawRows.value.length > 0
    if (step.value === 1) return Boolean(columnMap.ruleType)
    return true
  })

  function openWizard() {
    if (!props.customerId) {
      ElMessage.warning(t('billingRules.import.saveCustomerFirst'))
      return
    }
    visible.value = true
  }

  function resetWizard() {
    step.value = 0
    fileName.value = ''
    excelHeaders.value = []
    rawRows.value = []
    previewRules.value = []
    previewErrors.value = []
    hasConflicts.value = false
    for (const key of Object.keys(columnMap)) {
      delete columnMap[key]
    }
  }

  async function handleFileChange(uploadFile: UploadFile) {
    const file = uploadFile.raw
    if (!file) return
    fileName.value = file.name
    const buffer = await file.arrayBuffer()
    const workbook = XLSX.read(buffer, { type: 'array' })
    const sheet = workbook.Sheets[workbook.SheetNames[0]]
    const matrix = XLSX.utils.sheet_to_json<(string | number)[]>(sheet, {
      header: 1,
      defval: '',
      blankrows: false
    })
    if (matrix.length < 2) {
      ElMessage.warning(t('billingRules.import.emptyFile'))
      return
    }
    const headers = (matrix[0] ?? []).map((h) => String(h).trim()).filter(Boolean)
    excelHeaders.value = headers
    rawRows.value = matrix.slice(1).map((row) => {
      const record: Record<string, unknown> = {}
      headers.forEach((header, idx) => {
        record[header] = row[idx] ?? ''
      })
      return record
    })
    autoMapColumns(headers)
  }

  function autoMapColumns(headers: string[]) {
    for (const def of FIELD_DEFS) {
      const hit = headers.find((h) =>
        def.aliases.some((alias) => alias.toLowerCase() === h.toLowerCase() || h.includes(alias))
      )
      if (hit) columnMap[def.key] = hit
    }
  }

  function mapRowsToPayload(): Record<string, unknown>[] {
    return rawRows.value.map((row) => {
      const mapped: Record<string, unknown> = {}
      for (const def of FIELD_DEFS) {
        const col = columnMap[def.key]
        if (!col) continue
        const val = row[col]
        if (val !== '' && val != null) mapped[def.key] = val
      }
      return mapped
    })
  }

  async function goNext() {
    if (step.value === 0) {
      step.value = 1
      return
    }
    if (step.value === 1) {
      await loadPreview()
      step.value = 2
    }
  }

  async function loadPreview() {
    if (!props.customerId) return
    previewLoading.value = true
    previewErrors.value = []
    previewRules.value = []
    hasConflicts.value = false
    try {
      const rows = mapRowsToPayload()
      const res = await previewRuleImport({ customerId: props.customerId, rows })
      previewRules.value = (res.previewRules as Record<string, unknown>[]) ?? []
      previewErrors.value = (res.errors as string[]) ?? []
      hasConflicts.value = Boolean(res.hasConflicts)
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : t('billingRules.import.previewFailed'))
      throw e
    } finally {
      previewLoading.value = false
    }
  }

  async function handleConfirm() {
    if (!props.customerId || previewRules.value.length === 0) return
    confirming.value = true
    try {
      const operatorName = userStore.info?.userName ?? userStore.info?.username
      const res = await confirmRuleImport({
        customerId: props.customerId,
        rows: mapRowsToPayload(),
        operatorName
      })
      ElMessage.success(t('billingRules.import.success', { count: res.importedCount }))
      visible.value = false
      emit('imported', res.importedCount)
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : t('billingRules.import.confirmFailed'))
    } finally {
      confirming.value = false
    }
  }
</script>

<style scoped>
  .rule-batch-import__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 8px;
  }
</style>
