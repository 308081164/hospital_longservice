<template>
  <div class="rule-text-field">
    <div class="rule-text-field__label-row">
      <label class="rule-text-field__label">{{ label }}</label>
      <ElTooltip v-if="tooltip" :content="tooltip" placement="top">
        <span class="rule-text-field__help" tabindex="0">?</span>
      </ElTooltip>
    </div>
    <ElInput
      :model-value="modelValue"
      :placeholder="placeholder"
      @update:model-value="onUpdate"
      @input="onInput"
    />
    <div class="rule-text-field__placeholder" aria-hidden="true" />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RuleTextField' })

withDefaults(defineProps<{
  modelValue?: string
  label: string
  placeholder?: string
  tooltip?: string
}>(), {
  modelValue: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: []
}>()

function onUpdate(val: string) {
  emit('update:modelValue', val)
  emit('change')
}

function onInput() {
  emit('change')
}
</script>

<style scoped>
.rule-text-field {
  min-height: 88px;
}

.rule-text-field__label-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 22px;
  margin-bottom: 8px;
}

.rule-text-field__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.rule-text-field__help {
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

.rule-text-field__placeholder {
  height: 28px;
  margin-top: 4px;
}
</style>
