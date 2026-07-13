<template>
  <div class="rule-tier-price-table">
    <div class="rule-tier-price-table__toolbar">
      <span class="rule-tier-price-table__hint">{{ hint }}</span>
      <ElButton type="primary" link @click="addRow">+ {{ addLabel }}</ElButton>
    </div>
    <ElTable :data="modelValue" border size="small" class="rule-tier-price-table__table rule-pricing-table">
      <ElTableColumn label="件数" width="140" align="left" header-align="left">
        <template #default="{ row }">
          <div class="rule-pricing-table__cell rule-pricing-table__cell--size">
            <ElInputNumber
              v-model="row.count"
              :min="1"
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

defineOptions({ name: 'RuleTierPriceTable' })

const props = withDefaults(defineProps<{
  modelValue: Api.Hospital.TierPriceConfig[]
  priceLabel?: string
  addLabel?: string
  hint?: string
  priceQuickSteps?: number[]
}>(), {
  priceLabel: '阶梯总价(元)',
  addLabel: '添加阶梯',
  hint: '按件数区间采用对应总价，超出部分按余数单价计算',
  priceQuickSteps: () => [5, 10, 50],
})

const emit = defineEmits<{
  'update:modelValue': [value: Api.Hospital.TierPriceConfig[]]
  change: []
}>()

function emitChange() {
  emit('update:modelValue', props.modelValue)
  emit('change')
}

function addRow() {
  props.modelValue.push({ count: 1, price: 0 })
  emitChange()
}

function removeRow(index: number) {
  props.modelValue.splice(index, 1)
  emitChange()
}
</script>

<style scoped>
.rule-tier-price-table__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding-top: 2px;
}

.rule-tier-price-table__hint {
  flex: 1;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.rule-tier-price-table__table :deep(.el-table__header th.el-table__cell) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.rule-tier-price-table__table :deep(.el-table__body td.el-table__cell) {
  vertical-align: top;
  padding: 10px 12px;
}

.rule-tier-price-table__table :deep(.el-table__body .cell) {
  overflow: visible;
  padding: 0;
  line-height: normal;
}

.rule-pricing-table__cell {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.rule-pricing-table__cell--size {
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
</style>
