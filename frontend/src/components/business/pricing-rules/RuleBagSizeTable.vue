<template>
  <div class="rule-bag-size-table">
    <div class="rule-bag-size-table__toolbar">
      <span class="rule-bag-size-table__hint">{{ hint }}</span>
      <ElButton type="primary" link @click="addRow">+ {{ addLabel }}</ElButton>
    </div>
    <ElTable :data="modelValue" border size="small" class="rule-bag-size-table__table rule-pricing-table">
      <ElTableColumn :label="sizeLabel" width="132" align="left" header-align="left">
        <template #default="{ row }">
          <div class="rule-pricing-table__cell rule-pricing-table__cell--size">
            <ElInputNumber
              v-model="row.size"
              :min="0"
              :precision="0"
              controls-position="right"
              class="rule-pricing-table__input-number"
              @change="emitChange"
            />
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="priceLabel" width="300" align="left" header-align="left">
        <template #default="{ row }">
          <div class="rule-pricing-table__cell rule-pricing-table__cell--price">
            <PriceStepper
              v-model="row.price"
              compact
              :min="0"
              :precision="2"
              :quick-steps="priceQuickSteps"
              @change="emitChange"
            />
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="匹配关键词" min-width="260" align="left" header-align="left">
        <template #default="{ row, $index }">
          <div class="rule-pricing-table__cell rule-pricing-table__cell--text">
            <ElInput
              :model-value="row.keywords.join(', ')"
              placeholder="逗号分隔，如 20cm, 20, 大"
              class="rule-pricing-table__text-input"
              @input="updateKeywords($index, $event)"
            />
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="76" align="center" header-align="center" fixed="right">
        <template #default="{ $index }">
          <div class="rule-pricing-table__cell rule-pricing-table__cell--action">
            <ElButton type="danger" link @click="removeRow($index)">删除</ElButton>
          </div>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<script setup lang="ts">
import PriceStepper from '@/components/business/PriceStepper.vue'

defineOptions({ name: 'RuleBagSizeTable' })

const props = withDefaults(defineProps<{
  modelValue: Api.Hospital.BagSizeConfig[]
  sizeLabel?: string
  priceLabel?: string
  addLabel?: string
  hint?: string
  priceQuickSteps?: number[]
}>(), {
  sizeLabel: '尺寸(cm)',
  priceLabel: '袋费(元)',
  addLabel: '添加袋型',
  hint: '按尺寸匹配包装材料关键词，命中后采用对应袋费',
  priceQuickSteps: () => [0.5, 1, 5],
})

const emit = defineEmits<{
  'update:modelValue': [value: Api.Hospital.BagSizeConfig[]]
  change: []
}>()

function emitChange() {
  emit('update:modelValue', props.modelValue)
  emit('change')
}

function updateKeywords(index: number, val: string | number) {
  const row = props.modelValue[index]
  if (!row) return
  row.keywords = String(val).split(',').map((s) => s.trim()).filter(Boolean)
  emitChange()
}

function addRow() {
  props.modelValue.push({ size: 0, price: 0, keywords: [] })
  emitChange()
}

function removeRow(index: number) {
  props.modelValue.splice(index, 1)
  emitChange()
}
</script>

<style scoped>
.rule-bag-size-table__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding-top: 2px;
}

.rule-bag-size-table__hint {
  flex: 1;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.rule-bag-size-table__table :deep(.el-table__header th.el-table__cell) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.rule-bag-size-table__table :deep(.el-table__body td.el-table__cell) {
  vertical-align: top;
  padding: 10px 12px;
}

.rule-bag-size-table__table :deep(.el-table__body .cell) {
  overflow: visible;
  padding: 0;
  line-height: normal;
}

.rule-pricing-table__cell {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.rule-pricing-table__cell--size,
.rule-pricing-table__cell--text {
  padding-top: 2px;
}

.rule-pricing-table__cell--action {
  padding-top: 6px;
}

.rule-pricing-table__input-number {
  width: 100%;
}

.rule-pricing-table__input-number :deep(.el-input__wrapper) {
  min-height: 28px;
}

.rule-pricing-table__text-input :deep(.el-input__wrapper) {
  min-height: 28px;
}
</style>
