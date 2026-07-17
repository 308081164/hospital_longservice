<template>
  <div class="rule-keyword-field" :class="{ 'rule-keyword-field--large': size === 'large' }">
    <div class="rule-keyword-field__label-row">
      <label class="rule-keyword-field__label">{{ label }}</label>
      <ElTooltip v-if="tooltip" :content="tooltip" placement="top">
        <span class="rule-keyword-field__help" tabindex="0">?</span>
      </ElTooltip>
    </div>
    <ElInput
      :model-value="textValue"
      :placeholder="placeholder"
      type="textarea"
      :autosize="autosizeConfig"
      :class="{ 'rule-keyword-field__textarea--large': size === 'large' }"
      @focus="focused = true"
      @blur="handleBlur"
      @input="onInput"
    />
    <p v-if="hint" class="rule-keyword-field__hint">
      {{ hint }}
      <span v-if="showCount" class="rule-keyword-field__count">（共 {{ modelValue.length }} 个）</span>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

defineOptions({ name: 'RuleKeywordField' })

const props = withDefaults(defineProps<{
  modelValue: string[]
  label: string
  placeholder?: string
  tooltip?: string
  hint?: string
  rows?: number
  maxRows?: number
  size?: 'default' | 'large'
  showCount?: boolean
}>(), {
  placeholder: '多个关键词用逗号分隔',
  rows: 2,
  maxRows: undefined,
  size: 'default',
  showCount: false,
})

const autosizeConfig = computed(() => {
  const minRows = props.rows
  const maxRows = props.maxRows ?? (props.size === 'large' ? 12 : minRows + 1)
  return { minRows, maxRows }
})

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
  change: [value: string[]]
}>()

const focused = ref(false)
const textValue = ref('')

function formatKeywords(keywords: string[]): string {
  return keywords.join(', ')
}

function parseKeywords(raw: string): string[] {
  return String(raw)
    .replace(/，/g, ',')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

function emitKeywords(keywords: string[]) {
  emit('update:modelValue', keywords)
  emit('change', keywords)
}

watch(
  () => props.modelValue,
  (keywords) => {
    if (focused.value) return
    textValue.value = formatKeywords(keywords)
  },
  { immediate: true, deep: true },
)

function onInput(val: string | number) {
  textValue.value = String(val)
  emitKeywords(parseKeywords(textValue.value))
}

function handleBlur() {
  focused.value = false
  const keywords = parseKeywords(textValue.value)
  textValue.value = formatKeywords(keywords)
  emitKeywords(keywords)
}
</script>

<style scoped>
.rule-keyword-field {
  display: flex;
  flex-direction: column;
  min-height: 88px;
}

.rule-keyword-field__label-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 22px;
  margin-bottom: 8px;
}

.rule-keyword-field__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.rule-keyword-field__help {
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

.rule-keyword-field__hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rule-keyword-field__count {
  color: var(--el-text-color-placeholder);
}

.rule-keyword-field--large {
  min-height: auto;
}

.rule-keyword-field--large .rule-keyword-field__textarea--large :deep(.el-textarea__inner) {
  min-height: 140px;
  line-height: 1.6;
  font-size: 13px;
}
</style>
