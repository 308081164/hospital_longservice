<template>
  <RuleFieldGrid :columns="3">
    <RuleSwitchField v-model="modelValue.enabled" label="启用物流费" @change="emitChange" />
    <RuleNumberField
      v-model="modelValue.feePerTrip"
      label="单次费用(元)"
      :quick-steps="[5, 10, 50]"
      @change="emitChange"
    />
    <RuleNumberField
      v-model="modelValue.defaultLogisticsFee"
      label="默认物流费(元)"
      :quick-steps="[5, 10, 50]"
      @change="emitChange"
    />
    <RuleNumberField
      v-model="modelValue.dayBoundaryHour"
      label="跨天时间点(时)"
      kind="integer"
      :min="0"
      :max="23"
      :precision="0"
      :step="1"
      @change="emitChange"
    />
    <RuleSwitchField v-model="modelValue.mergeAdjacentDays" label="合并相邻天数" @change="emitChange" />
    <RuleNumberField
      v-model="modelValue.mergeWindowDays"
      label="合并窗口(天)"
      kind="integer"
      :precision="0"
      :step="1"
      @change="emitChange"
    />
  </RuleFieldGrid>
</template>

<script setup lang="ts">
import RuleFieldGrid from './RuleFieldGrid.vue'
import RuleNumberField from './RuleNumberField.vue'
import RuleSwitchField from './RuleSwitchField.vue'

defineOptions({ name: 'RuleLogisticsPanel' })

defineProps<{
  modelValue: Api.Hospital.LogisticsRulesConfig
}>()

const emit = defineEmits<{ change: [] }>()

function emitChange() {
  emit('change')
}
</script>
