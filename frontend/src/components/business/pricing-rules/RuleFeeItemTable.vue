<template>
  <div class="rule-fee-item-table">
    <div class="rule-fee-item-table__toolbar">
      <span class="rule-fee-item-table__hint">结款函中展示的费用明细行</span>
      <ElButton type="primary" link @click="addRow">+ 添加费用项</ElButton>
    </div>
    <ElTable :data="modelValue" border size="small" class="rule-fee-item-table__table">
      <ElTableColumn label="名称" min-width="140">
        <template #default="{ row }">
          <ElInput v-model="row.label" placeholder="费用名称" @input="emitChange" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="备注" min-width="140">
        <template #default="{ row }">
          <ElInput v-model="row.remark" placeholder="可选备注" @input="emitChange" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="排序" width="100" align="center">
        <template #default="{ row }">
          <ElInputNumber
            v-model="row.sortOrder"
            :min="0"
            :precision="0"
            controls-position="right"
            class="w-full"
            @change="emitChange"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="启用" width="80" align="center">
        <template #default="{ row }">
          <ElSwitch v-model="row.enabled" @change="emitChange" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="72" align="center" fixed="right">
        <template #default="{ $index }">
          <ElButton type="danger" link @click="removeRow($index)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RuleFeeItemTable' })

const props = defineProps<{
  modelValue: Api.Hospital.SettlementLetterFeeItem[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Api.Hospital.SettlementLetterFeeItem[]]
  change: []
}>()

function emitChange() {
  emit('update:modelValue', props.modelValue)
  emit('change')
}

function addRow() {
  const maxSort = props.modelValue.reduce((max, f) => Math.max(max, f.sortOrder), 0)
  props.modelValue.push({
    key: `fee_${Date.now()}`,
    label: '',
    remark: '',
    enabled: true,
    sortOrder: maxSort + 1,
  })
  emitChange()
}

function removeRow(index: number) {
  props.modelValue.splice(index, 1)
  emitChange()
}
</script>

<style scoped>
.rule-fee-item-table__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.rule-fee-item-table__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
