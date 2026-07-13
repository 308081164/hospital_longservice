<template>
  <div class="rule-field-grid" :class="`rule-field-grid--cols-${columns}`">
    <slot />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RuleFieldGrid' })

withDefaults(defineProps<{
  columns?: 1 | 2 | 3 | 4
}>(), {
  columns: 3,
})
</script>

<style scoped>
.rule-field-grid {
  display: grid;
  gap: 20px 24px;
  align-items: start;
}

.rule-field-grid--cols-1 {
  grid-template-columns: 1fr;
}

.rule-field-grid--cols-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.rule-field-grid--cols-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.rule-field-grid--cols-4 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

@media (max-width: 1100px) {
  .rule-field-grid--cols-3,
  .rule-field-grid--cols-4 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .rule-field-grid--cols-2,
  .rule-field-grid--cols-3,
  .rule-field-grid--cols-4 {
    grid-template-columns: 1fr;
  }
}
</style>
