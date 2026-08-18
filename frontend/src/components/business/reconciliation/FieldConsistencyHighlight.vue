<template>
  <span :class="cellClass">
    <slot />
  </span>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import {
    fieldConsistencyCellClass,
    type FieldConsistencyHighlightField
  } from '@/utils/reconciliationBillingNotes'

  defineOptions({ name: 'FieldConsistencyHighlight' })

  const props = defineProps<{
    row: Record<string, unknown>
    field: FieldConsistencyHighlightField
  }>()

  const cellClass = computed(() => fieldConsistencyCellClass(props.row, props.field))
</script>

<style scoped>
  .field-consistency-cell {
    display: inline-block;
    max-width: 100%;
    padding: 1px 4px;
    font-weight: 600;
    border-radius: 4px;
  }

  .field-consistency-cell--red {
    color: #c45656;
    background: #fef0f0;
    box-shadow: inset 0 0 0 1px #fab6b6;
  }

  .field-consistency-cell--amber {
    color: #b88230;
    background: #fdf6ec;
    box-shadow: inset 0 0 0 1px #f5dab1;
  }
</style>
