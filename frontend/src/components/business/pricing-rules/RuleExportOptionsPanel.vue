<template>
  <RuleFieldGrid :columns="3">
    <RuleTextField v-model="modelValue.billFilePrefix" label="账单文件名前缀" placeholder="账单_" @change="emitChange" />
    <RuleTextField v-model="modelValue.warningFilePrefix" label="异常文件名前缀" placeholder="异常_" @change="emitChange" />
    <RuleTextField
      v-model="modelValue.settlementFilePrefix"
      label="结款函文件名前缀"
      placeholder="结款函_"
      @change="emitChange"
    />
    <RuleTextField v-model="modelValue.defaultPageMargin" label="默认页边距" placeholder="1cm" @change="emitChange" />
    <RuleSwitchField v-model="modelValue.includeWarningSheet" label="包含异常表" @change="emitChange" />
  </RuleFieldGrid>
</template>

<script setup lang="ts">
import RuleFieldGrid from './RuleFieldGrid.vue'
import RuleTextField from './RuleTextField.vue'
import RuleSwitchField from './RuleSwitchField.vue'

defineOptions({ name: 'RuleExportOptionsPanel' })

defineProps<{
  modelValue: Api.Hospital.ExportOptionsConfig
}>()

const emit = defineEmits<{ change: [] }>()

function emitChange() {
  emit('change')
}
</script>
