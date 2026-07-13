<template>
  <div class="rule-select-field">
    <div class="rule-select-field__label-row">
      <label class="rule-select-field__label">{{ label }}</label>
      <ElTooltip v-if="tooltip" :content="tooltip" placement="top">
        <span class="rule-select-field__help" tabindex="0">?</span>
      </ElTooltip>
    </div>
    <ElSelect
      :model-value="modelValue"
      :placeholder="placeholder"
      class="rule-select-field__control"
      @update:model-value="onUpdate"
      @change="onChange"
    >
      <ElOption
        v-for="opt in options"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </ElSelect>
    <div class="rule-select-field__placeholder" aria-hidden="true" />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RuleSelectField' })

defineProps<{
  modelValue?: string
  label: string
  placeholder?: string
  tooltip?: string
  options: Array<{ label: string; value: string }>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: []
}>()

function onUpdate(val: string) {
  emit('update:modelValue', val)
}

function onChange() {
  emit('change')
}
</script>

<style scoped>
.rule-select-field {
  min-height: 88px;
}

.rule-select-field__label-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 22px;
  margin-bottom: 8px;
}

.rule-select-field__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.rule-select-field__help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  cursor: help;
}

.rule-select-field__control {
  width: 100%;
}

.rule-select-field__placeholder {
  height: 28px;
  margin-top: 4px;
}
</style>
