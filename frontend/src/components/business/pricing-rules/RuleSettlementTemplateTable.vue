<template>
  <div class="rule-settlement-template-table">
    <div class="rule-settlement-template-table__toolbar">
      <span class="rule-settlement-template-table__hint">按医院名称关键词匹配导出模板</span>
      <ElButton type="primary" link @click="addRow">+ 添加结款函模板</ElButton>
    </div>
    <ElTable :data="modelValue" border size="small" class="rule-settlement-template-table__table">
      <ElTableColumn label="模板名称" min-width="120">
        <template #default="{ row }">
          <ElInput v-model="row.name" placeholder="如：方南南口腔" @input="emitChange" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="医院名称" min-width="120">
        <template #default="{ row }">
          <ElInput v-model="row.hospitalName" placeholder="导出显示医院名" @input="emitChange" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="模板表名" width="110">
        <template #default="{ row }">
          <ElInput v-model="row.templateSheetName" placeholder="结款函" @input="emitChange" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="标题文字" min-width="120">
        <template #default="{ row }">
          <ElInput v-model="row.titleText" placeholder="货款结算单" @input="emitChange" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="结款函模板" min-width="160">
        <template #default="{ row }">
          <ElSelect v-model="row.templateRef" placeholder="选择模板" class="w-full" @change="emitChange">
            <ElOption
              v-for="tpl in availableTemplates"
              :key="tpl.id"
              :label="tpl.name"
              :value="tpl.id"
            />
          </ElSelect>
        </template>
      </ElTableColumn>
      <ElTableColumn label="匹配关键词" min-width="160">
        <template #default="{ row, $index }">
          <ElInput
            :model-value="row.matchKeywords.join(', ')"
            placeholder="逗号分隔"
            @input="updateKeywords($index, $event)"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="120" align="center" fixed="right">
        <template #default="{ $index }">
          <ElButton link @click="emitPreview($index)">预览</ElButton>
          <ElButton
            type="danger"
            link
            :disabled="modelValue.length <= 1"
            @click="removeRow($index)"
          >
            删除
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RuleSettlementTemplateTable' })

const props = defineProps<{
  modelValue: Api.Hospital.SettlementLetterTemplate[]
  availableTemplates: Array<{ id: string; name: string; description?: string }>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Api.Hospital.SettlementLetterTemplate[]]
  change: []
  preview: [index: number]
}>()

function emitChange() {
  emit('update:modelValue', props.modelValue)
  emit('change')
}

function updateKeywords(index: number, val: string | number) {
  const row = props.modelValue[index]
  if (!row) return
  row.matchKeywords = String(val).split(',').map((s) => s.trim()).filter(Boolean)
  emitChange()
}

function buildId(seed: string): string {
  const normalized = seed.trim().toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '_').replace(/^_+|_+$/g, '')
  return normalized || `template_${Date.now()}`
}

function addRow() {
  const nextIndex = props.modelValue.length + 1
  props.modelValue.push({
    id: buildId(`template_${nextIndex}_${Date.now()}`),
    name: `模板${nextIndex}`,
    hospitalName: '',
    templateSheetName: '结款函',
    titleText: '货款结算单',
    matchKeywords: [],
    templateRef: 'default',
  })
  emitChange()
}

function removeRow(index: number) {
  props.modelValue.splice(index, 1)
  emitChange()
}

function emitPreview(index: number) {
  emit('preview', index)
}
</script>

<style scoped>
.rule-settlement-template-table__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.rule-settlement-template-table__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rule-settlement-template-table__table :deep(.el-table__cell) {
  vertical-align: middle;
}
</style>
