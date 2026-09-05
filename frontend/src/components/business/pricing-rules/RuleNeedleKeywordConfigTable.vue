<template>
  <div class="rule-needle-config-table">
    <div class="rule-needle-config-table__toolbar">
      <span class="rule-needle-config-table__hint">{{ hint }}</span>
      <ElButton type="primary" link @click="addRow">+ {{ addLabel }}</ElButton>
    </div>
    <ElTable
      v-if="modelValue.length"
      :data="modelValue"
      border
      size="small"
      class="rule-needle-config-table__table"
    >
      <ElTableColumn label="关键词" min-width="150" align="left" header-align="left">
        <template #default="{ row, $index }">
          <ElInput
            :model-value="row.keyword"
            placeholder="如 车针"
            @update:model-value="(val: string) => updateRow($index, { keyword: val })"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="匹配模式" width="150" align="left" header-align="left">
        <template #default="{ row, $index }">
          <ElSelect
            :model-value="row.matchMode ?? ''"
            @update:model-value="(val: string) => updateRow($index, { matchMode: (val || undefined) as 'exact_token' | 'contains' | undefined })"
          >
            <ElOption value="" :label="`默认（${defaultMatchModeLabel}）`" />
            <ElOption value="contains" label="含词即触发" />
            <ElOption value="exact_token" label="严格对齐" />
          </ElSelect>
        </template>
      </ElTableColumn>
      <ElTableColumn label="触发件数" width="132" align="left" header-align="left">
        <template #default="{ row, $index }">
          <ElInputNumber
            :model-value="row.threshold"
            :min="0"
            :precision="0"
            controls-position="right"
            :placeholder="`默认 ${defaultThreshold}`"
            class="rule-needle-config-table__input-number"
            @update:model-value="(val: number | undefined) => updateRow($index, { threshold: val ?? undefined })"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="折算比例(N:1)" width="132" align="left" header-align="left">
        <template #default="{ row, $index }">
          <ElInputNumber
            :model-value="row.foldRatio"
            :min="1"
            :precision="0"
            controls-position="right"
            :placeholder="`默认 ${defaultFoldRatio}`"
            class="rule-needle-config-table__input-number"
            @update:model-value="(val: number | undefined) => updateRow($index, { foldRatio: val ?? undefined })"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="70" align="center" header-align="center" fixed="right">
        <template #default="{ $index }">
          <ElButton type="danger" link @click="removeRow($index)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <div v-else class="rule-needle-config-table__empty">
      暂无独立配置，上方识别关键词统一使用默认触发件数 / 折算比例 / 匹配模式
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'RuleNeedleKeywordConfigTable' })

const props = withDefaults(defineProps<{
  modelValue: Api.Hospital.NeedleKeywordConfig[]
  defaultThreshold?: number
  defaultFoldRatio?: number
  defaultMatchMode?: string
  addLabel?: string
  hint?: string
}>(), {
  defaultThreshold: 5,
  defaultFoldRatio: 5,
  defaultMatchMode: 'exact_token',
  addLabel: '添加关键词独立配置',
  hint: '为单个关键词单独设置匹配模式 / 触发件数 / 折算比例，多条配置共存并独立生效；留空字段沿用上方默认值',
})

const emit = defineEmits<{
  'update:modelValue': [value: Api.Hospital.NeedleKeywordConfig[]]
  change: []
}>()

const defaultMatchModeLabel = computed(() =>
  props.defaultMatchMode === 'contains' ? '含词即触发' : '严格对齐',
)

function updateRow(index: number, patch: Partial<Api.Hospital.NeedleKeywordConfig>) {
  const next = props.modelValue.map((row, i) => (i === index ? { ...row, ...patch } : row))
  emit('update:modelValue', next)
  emit('change')
}

function addRow() {
  emit('update:modelValue', [...props.modelValue, { keyword: '' }])
  emit('change')
}

function removeRow(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== index),
  )
  emit('change')
}
</script>

<style scoped>
.rule-needle-config-table__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding-top: 2px;
}

.rule-needle-config-table__hint {
  flex: 1;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.rule-needle-config-table__table :deep(.el-table__header th.el-table__cell) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.rule-needle-config-table__table :deep(.el-table__body td.el-table__cell) {
  vertical-align: middle;
  padding: 6px 12px;
}

.rule-needle-config-table__table :deep(.el-table__body .cell) {
  overflow: visible;
  padding: 0;
  line-height: normal;
}

.rule-needle-config-table__input-number {
  width: 100%;
}

.rule-needle-config-table__input-number :deep(.el-input__wrapper) {
  min-height: 28px;
}

.rule-needle-config-table__empty {
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
}
</style>
