<template>
  <div class="rule-packaging-table">
    <RuleFieldGrid :columns="2">
      <RuleSwitchField v-model="modelValue.enabled" label="启用包装收费" @change="emitChange" />
      <RuleKeywordField
        v-model="modelValue.selfPackedKeywords"
        label="医院自行打包关键词"
        hint="命中后不计包装费"
        :rows="1"
        @change="emitChange"
      />
    </RuleFieldGrid>

    <div class="rule-packaging-table__toolbar">
      <span class="rule-packaging-table__hint">按项目名称与关键词匹配，支持多规格选项定价</span>
      <ElButton type="primary" link @click="addItem">+ 添加包装收费项目</ElButton>
    </div>

    <div v-for="(item, itemIndex) in modelValue.items" :key="`pkg-${itemIndex}`" class="rule-packaging-table__item">
      <div class="rule-packaging-table__item-header">
        <span class="rule-packaging-table__item-title">{{ item.name || `项目 ${itemIndex + 1}` }}</span>
        <div class="rule-packaging-table__item-actions">
          <ElButton type="primary" link @click="addOption(item.options)">+ 添加选项</ElButton>
          <ElButton type="danger" link @click="removeItem(itemIndex)">删除项目</ElButton>
        </div>
      </div>

      <RuleFieldGrid :columns="3" class="rule-packaging-table__item-fields">
        <RuleTextField v-model="item.name" label="项目名称" placeholder="如：纱布棉球" @change="emitChange" />
        <RuleKeywordField
          v-model="item.keywords"
          label="匹配关键词"
          :rows="1"
          @change="emitChange"
        />
        <RuleSwitchField v-model="item.chargePerPack" label="按包收费" @change="emitChange" />
      </RuleFieldGrid>

      <ElTable
        v-if="item.options.length"
        :data="item.options"
        border
        size="small"
        class="rule-packaging-table__options"
      >
        <ElTableColumn label="选项名称" min-width="180" align="left" header-align="left">
          <template #default="{ row }">
            <div class="rule-pricing-table__cell rule-pricing-table__cell--text">
              <ElInput
                v-model="row.label"
                placeholder="如：大（20cm*20cm*15cm）"
                class="rule-pricing-table__text-input"
                @input="emitChange"
              />
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="价格(元)" width="300" align="left" header-align="left">
          <template #default="{ row }">
            <div class="rule-pricing-table__cell rule-pricing-table__cell--price">
              <PriceStepper
                v-model="row.price"
                compact
                :min="0"
                :precision="2"
                :quick-steps="[0.5, 1, 5]"
                @change="emitChange"
              />
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="匹配关键词" min-width="240" align="left" header-align="left">
          <template #default="{ row, $index }">
            <div class="rule-pricing-table__cell rule-pricing-table__cell--text">
              <ElInput
                :model-value="row.keywords.join(', ')"
                placeholder="逗号分隔"
                class="rule-pricing-table__text-input"
                @input="updateOptionKeywords(item.options, $index, $event)"
              />
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="76" align="center" header-align="center" fixed="right">
          <template #default="{ $index }">
            <div class="rule-pricing-table__cell rule-pricing-table__cell--action">
              <ElButton type="danger" link @click="removeOption(item.options, $index)">删除</ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
      <p v-else class="rule-packaging-table__empty">暂无规格选项，可点击「添加选项」</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import PriceStepper from '@/components/business/PriceStepper.vue'
import RuleFieldGrid from './RuleFieldGrid.vue'
import RuleSwitchField from './RuleSwitchField.vue'
import RuleKeywordField from './RuleKeywordField.vue'
import RuleTextField from './RuleTextField.vue'

defineOptions({ name: 'RulePackagingTable' })

const props = defineProps<{
  modelValue: Api.Hospital.PackagingRulesConfig
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Api.Hospital.PackagingRulesConfig]
  change: []
}>()

function emitChange() {
  emit('update:modelValue', props.modelValue)
  emit('change')
}

function updateOptionKeywords(target: Api.Hospital.PackagingOptionConfig[], index: number, val: string | number) {
  target[index].keywords = String(val).split(',').map((s) => s.trim()).filter(Boolean)
  emitChange()
}

function addItem() {
  props.modelValue.items.push({ name: '', keywords: [], chargePerPack: true, options: [] })
  emitChange()
}

function removeItem(index: number) {
  props.modelValue.items.splice(index, 1)
  emitChange()
}

function addOption(target: Api.Hospital.PackagingOptionConfig[]) {
  target.push({ label: '', price: 0, keywords: [] })
  emitChange()
}

function removeOption(target: Api.Hospital.PackagingOptionConfig[], index: number) {
  target.splice(index, 1)
  emitChange()
}
</script>

<style scoped>
.rule-packaging-table__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 12px;
}

.rule-packaging-table__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rule-packaging-table__item {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.rule-packaging-table__item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.rule-packaging-table__item-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.rule-packaging-table__item-actions {
  display: flex;
  gap: 4px;
}

.rule-packaging-table__item-fields {
  margin-bottom: 12px;
}

.rule-packaging-table__options :deep(.el-table__header th.el-table__cell) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.rule-packaging-table__options :deep(.el-table__body td.el-table__cell) {
  vertical-align: top;
  padding: 10px 12px;
}

.rule-packaging-table__options :deep(.el-table__body .cell) {
  overflow: visible;
  padding: 0;
  line-height: normal;
}

.rule-pricing-table__cell {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.rule-pricing-table__cell--text {
  padding-top: 2px;
}

.rule-pricing-table__cell--action {
  padding-top: 6px;
}

.rule-pricing-table__text-input :deep(.el-input__wrapper) {
  min-height: 28px;
}

.rule-packaging-table__empty {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  text-align: center;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}
</style>
