<template>
  <div class="rule-switch-field">
    <div class="rule-switch-field__label-row">
      <label class="rule-switch-field__label">{{ label }}</label>
      <ElTooltip v-if="tooltip" :content="tooltip" placement="top">
        <span class="rule-switch-field__help" tabindex="0">?</span>
      </ElTooltip>
    </div>
    <div class="rule-switch-field__control">
      <ElSwitch :model-value="modelValue" @update:model-value="onUpdate" @change="onChange" />
      <div class="rule-switch-field__placeholder" aria-hidden="true" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RuleSwitchField' })

defineProps<{
  modelValue: boolean
  label: string
  tooltip?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  change: [value: boolean]
}>()

function onUpdate(val: string | number | boolean) {
  emit('update:modelValue', Boolean(val))
}

function onChange(val: string | number | boolean) {
  emit('change', Boolean(val))
}
</script>

<style scoped>
.rule-switch-field {
  min-height: 88px;
}

.rule-switch-field__label-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 22px;
  margin-bottom: 8px;
}

.rule-switch-field__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.rule-switch-field__help {
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

.rule-switch-field__control {
  display: flex;
  flex-direction: column;
}

.rule-switch-field__placeholder {
  height: 28px;
  margin-top: 4px;
}
</style>
