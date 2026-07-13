<template>
  <div class="price-stepper" :class="{ 'price-stepper--compact': compact }">
    <div class="stepper-main">
      <ElButton
        class="step-btn step-btn-minus"
        :disabled="(modelValue ?? 0) <= min"
        @click="adjust(-step)"
      >
        <span class="step-icon">−</span>
      </ElButton>
      <ElInputNumber
        :model-value="modelValue"
        :min="min"
        :max="max"
        :precision="precision"
        :step="step"
        class="stepper-input"
        controls-position="right"
        @update:model-value="emitValue"
      />
      <ElButton
        class="step-btn step-btn-plus"
        :disabled="max !== undefined && (modelValue ?? 0) >= max"
        @click="adjust(step)"
      >
        <span class="step-icon">+</span>
      </ElButton>
    </div>
    <div v-if="quickSteps.length" class="quick-steps">
      <span class="quick-label">快捷</span>
      <ElButton
        v-for="qs in quickSteps"
        :key="qs"
        size="small"
        text
        class="quick-chip"
        :disabled="max !== undefined && (modelValue ?? 0) + qs > max"
        @click="adjust(qs)"
      >
        +{{ formatStep(qs) }}
      </ElButton>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'PriceStepper' })

const props = withDefaults(defineProps<{
  modelValue?: number
  min?: number
  max?: number
  step?: number
  precision?: number
  quickSteps?: number[]
  /** 表格等窄容器内使用，缩小按钮并防止横向溢出 */
  compact?: boolean
}>(), {
  modelValue: 0,
  min: 0,
  max: undefined,
  step: 0.5,
  precision: 2,
  quickSteps: () => [0.5, 1, 5],
  compact: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: number]
  'change': [value: number]
}>()

function emitValue(val: number | undefined) {
  const v = val ?? 0
  emit('update:modelValue', v)
  emit('change', v)
}

function adjust(delta: number) {
  const raw = (props.modelValue ?? 0) + delta
  const clamped = Math.min(
    Math.max(raw, props.min),
    props.max ?? Infinity,
  )
  const rounded = parseFloat(clamped.toFixed(props.precision))
  emit('update:modelValue', rounded)
  emit('change', rounded)
}

function formatStep(v: number): string {
  return v % 1 === 0 ? String(v) : v.toFixed(1)
}
</script>

<style scoped>
.price-stepper {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.stepper-main {
  display: flex;
  align-items: center;
  gap: 0;
  width: 100%;
  max-width: 100%;
}

.step-btn {
  height: 32px;
  width: 32px;
  flex-shrink: 0;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px !important;
  font-size: 18px;
  font-weight: 600;
  z-index: 1;
}

.step-btn-minus {
  margin-right: -1px;
  border-top-right-radius: 0 !important;
  border-bottom-right-radius: 0 !important;
}

.step-btn-plus {
  margin-left: -1px;
  border-top-left-radius: 0 !important;
  border-bottom-left-radius: 0 !important;
}

.step-icon {
  line-height: 1;
}

.stepper-input {
  flex: 1;
  min-width: 0;
  width: 0;
}

.stepper-input :deep(.el-input__wrapper) {
  border-radius: 0;
}

.stepper-input :deep(.el-input-number__decrease),
.stepper-input :deep(.el-input-number__increase) {
  display: none;
}

.quick-steps {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
  width: 100%;
  max-width: 100%;
}

.quick-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.quick-chip {
  height: 24px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: 4px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
}

.quick-chip:hover {
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
}

/* 表格内紧凑模式 */
.price-stepper--compact .step-btn {
  width: 28px;
  height: 28px;
  font-size: 16px;
}

.price-stepper--compact .stepper-input :deep(.el-input__wrapper) {
  min-height: 28px;
  padding-left: 8px;
  padding-right: 8px;
}

.price-stepper--compact .quick-steps {
  margin-top: 4px;
  gap: 3px;
}

.price-stepper--compact .quick-chip {
  height: 22px;
  padding: 0 6px;
  font-size: 11px;
}
</style>
