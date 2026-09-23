<template>
  <div>
    <RuleSectionBlock title="结款函基础设置" subtitle="导出结款函时的全局格式与默认模板">
      <RuleFieldGrid :columns="3">
        <RuleTextField v-model="modelValue.companyName" label="公司名称" placeholder="公司名称" @change="emitChange" />
        <RuleSelectField
          v-model="modelValue.defaultTemplateId"
          label="默认模板"
          placeholder="选择默认模板"
          :options="settlementTemplateOptions"
          @change="emitChange"
        />
        <RuleNumberField
          v-model="modelValue.rowHeight"
          label="行高"
          kind="integer"
          :min="1"
          :precision="0"
          :step="1"
          @change="emitChange"
        />
        <RuleTextField
          v-model="modelValue.dateRangeTextTemplate"
          label="日期范围模板"
          placeholder="{start} 至 {end}"
          @change="emitChange"
        />
        <RuleTextField
          v-model="modelValue.uppercaseTotalLabel"
          label="大写金额标签"
          placeholder="大写金额"
          @change="emitChange"
        />
      </RuleFieldGrid>
    </RuleSectionBlock>

    <RuleSectionBlock title="模板配置" subtitle="按医院关键词匹配不同结款函样式">
      <RuleSettlementTemplateTable
        v-model="modelValue.templates"
        :available-templates="availableTemplates"
        @change="onTemplatesChange"
        @preview="previewSettlementTemplate"
      />
    </RuleSectionBlock>

    <RuleSectionBlock title="费用项配置" subtitle="结款函中列示的费用明细">
      <RuleFeeItemTable v-model="modelValue.feeItems" @change="emitChange" />
    </RuleSectionBlock>

    <ElDialog v-model="previewDialogVisible" title="结款函模板预览" width="800px" top="5vh">
      <div class="max-h-[70vh] overflow-auto rounded border bg-white p-4" v-html="sanitizeHtml(previewTemplateHtml)" />
      <template #footer>
        <ElButton @click="previewDialogVisible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { listSettlementTemplates, type BackendTemplateRef } from '@/api/hospital/reconciliationsApi'
import RuleSectionBlock from './RuleSectionBlock.vue'
import RuleFieldGrid from './RuleFieldGrid.vue'
import RuleTextField from './RuleTextField.vue'
import RuleSelectField from './RuleSelectField.vue'
import RuleNumberField from './RuleNumberField.vue'
import RuleSettlementTemplateTable from './RuleSettlementTemplateTable.vue'
import RuleFeeItemTable from './RuleFeeItemTable.vue'

defineOptions({ name: 'RuleSettlementLetterPanel' })

const props = defineProps<{
  modelValue: Api.Hospital.SettlementLetterConfig
}>()

const emit = defineEmits<{ change: [] }>()

const availableTemplates = ref<BackendTemplateRef[]>([])
const previewDialogVisible = ref(false)
const previewTemplateHtml = ref('')

const settlementTemplateOptions = computed(() =>
  props.modelValue.templates.map((item) => ({
    label: item.name || item.hospitalName || item.id,
    value: item.id,
  })),
)

function emitChange() {
  emit('change')
}

function onTemplatesChange() {
  const ids = props.modelValue.templates.map((t) => t.id)
  if (props.modelValue.defaultTemplateId && !ids.includes(props.modelValue.defaultTemplateId)) {
    props.modelValue.defaultTemplateId = props.modelValue.templates[0]?.id
  }
  if (!props.modelValue.defaultTemplateId && props.modelValue.templates.length) {
    props.modelValue.defaultTemplateId = props.modelValue.templates[0].id
  }
  emitChange()
}

async function previewSettlementTemplate(index: number) {
  const template = props.modelValue.templates[index]
  const templateId = template.templateRef || 'default'
  try {
    const token = useUserStore().accessToken
    const response = await fetch(
      resolveApiRequestUrl(
        `/api/hospital-reconciliations/templates/settlement/${encodeURIComponent(templateId)}/preview`,
      ),
      {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      },
    )
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    previewTemplateHtml.value = await response.text()
  } catch {
    previewTemplateHtml.value =
      '<p style="padding:40px;text-align:center;color:#999">无法加载模板预览，请确认后端模板文件存在。</p>'
  }
  previewDialogVisible.value = true
}

function resolveApiRequestUrl(url: string) {
  const baseURL = (import.meta.env.VITE_API_URL || '').trim()
  if (!baseURL || baseURL === '/') {
    return url
  }
  return new URL(url, `${baseURL.replace(/\/$/, '')}/`).toString()
}

function sanitizeHtml(html: string): string {
  return html
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '')
    .replace(/<script\b[^>]*\/>/gi, '')
    .replace(/\bon\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\bon\w+\s*=\s*'[^']*'/gi, '')
    .replace(/<iframe\b[^>]*>[\s\S]*?<\/iframe>/gi, '')
}

onMounted(async () => {
  try {
    availableTemplates.value = await listSettlementTemplates()
  } catch {
    // templates unavailable
  }
})
</script>
