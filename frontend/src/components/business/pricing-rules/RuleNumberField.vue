<template>
  <div class="rule-number-field">
    <div class="rule-number-field__label-row">
      <label class="rule-number-field__label">{{ label }}</label>
      <ElTooltip v-if="tooltip" :content="tooltip" placement="top">
        <span class="rule-number-field__help" tabindex="0">?</span>
      </ElTooltip>
    </div>
    <div class="rule-number-field__control">
      <PriceStepper
        v-if="kind === 'price'"
        :model-value="modelValue"
        :min="min"
        :max="max"
        :step="step"
        :precision="precision"
        :quick-steps="quickSteps"
        @update:model-value="emitValue"
        @change="emitChange"
      />
      <ElInputNumber
        v-else
        :model-value="modelValue"
        :min="min"
        :max="max"
        :step="step ?? 1"
        :precision="precision"
        :clearable="optional"
        class="rule-number-field__input"
        controls-position="right"
        @update:model-value="emitValue"
        @change="emitChange"
      />
      <div v-if="kind !== 'price'" class="rule-number-field__quick-placeholder" aria-hidden="true" />
    </div>
  </div>
</template>

<script setup lang="ts">
import PriceStepper from '@/components/business/PriceStepper.vue'

defineOptions({ name: 'RuleNumberField' })

const props = withDefaults(defineProps<{
  modelValue?: number
  label: string
  tooltip?: string
  kind?: 'price' | 'integer' | 'decimal'
  min?: number
  max?: number
  step?: number
  precision?: number
  quickSteps?: number[]
  /** 允许留空；清空时不写入 0，而是 undefined */
  optional?: boolean
}>(), {
  modelValue: undefined,
  kind: 'price',
  min: 0,
  max: undefined,
  step: 0.5,
  precision: 2,
  quickSteps: () => [0.5, 1, 5],
  optional: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: number]
  change: [value: number]
}>()

function emitValue(val: number | undefined | null) {
  if (props.optional && (val == null || Number.isNaN(val))) {
    emit('update:modelValue', undefined)
    return
  }
  emit('update:modelValue', val ?? (props.optional ? undefined : 0))
}

function emitChange(val: number | undefined | null) {
  if (props.optional && (val == null || Number.isNaN(val))) {
    emit('change', undefined)
    return
  }
  const v = val ?? (props.optional ? undefined : (props.modelValue ?? 0))
  emit('change', v)
}
</script>

<style scoped>
.rule-number-field {
  display: flex;
  flex-direction: column;
  min-height: 88px;
}

.rule-number-field__label-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 22px;
  margin-bottom: 8px;
}

.rule-number-field__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  line-height: 1.4;
}

.rule-number-field__help {
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

.rule-number-field__control {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.rule-number-field__input {
  width: 100%;
}

.rule-number-field__input :deep(.el-input__wrapper) {
  min-height: 32px;
}

.rule-number-field__quick-placeholder {
  height: 28px;
  margin-top: 4px;
}
</style>
