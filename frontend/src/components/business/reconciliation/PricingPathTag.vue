<template>
  <div class="pricing-path-tag flex flex-wrap items-center gap-1">
    <ElTag
      :type="classification.tagType"
      size="small"
      effect="plain"
      :class="{ 'cursor-pointer': clickable }"
      @click.stop="emitOpenDetail"
    >
      {{ t(classification.label) }}
    </ElTag>
    <ElTooltip v-if="classification.summary && classification.summary !== '—'" placement="top" :show-after="200">
      <template #content>
        <div class="max-w-xs text-xs">{{ localizedSummary }}</div>
      </template>
      <ElTag
        size="small"
        type="info"
        effect="plain"
        class="max-w-[120px] truncate"
        :class="{ 'cursor-pointer': clickable }"
        @click.stop="emitOpenDetail"
      >
        {{ localizedSummary }}
      </ElTag>
    </ElTooltip>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { classifyPricingPath } from '@/utils/reconciliationPricingPath'
  import { localizeReconciliationDisplayText } from '@/utils/reconciliationDisplayText'

  defineOptions({ name: 'PricingPathTag' })

  const props = withDefaults(
    defineProps<{
      row: Record<string, unknown>
      clickable?: boolean
    }>(),
    {
      clickable: true
    }
  )

  const emit = defineEmits<{
    'open-detail': [row: Record<string, unknown>]
  }>()

  const { t } = useI18n()

  const classification = computed(() => classifyPricingPath(props.row))

  const localizedSummary = computed(() =>
    localizeReconciliationDisplayText(classification.value.summary)
  )

  function emitOpenDetail() {
    if (!props.clickable) return
    emit('open-detail', props.row)
  }
</script>
