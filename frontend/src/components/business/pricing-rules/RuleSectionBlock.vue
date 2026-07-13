<template>
  <section class="rule-section-block">
    <header class="rule-section-block__header">
      <div>
        <h3 class="rule-section-block__title">{{ title }}</h3>
        <p v-if="subtitle" class="rule-section-block__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="rule-section-block__actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="rule-section-block__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
defineOptions({ name: 'RuleSectionBlock' })

defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<style scoped>
.rule-section-block {
  margin-bottom: 28px;
  padding: 20px 22px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.rule-section-block__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.rule-section-block__title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  text-align: left;
}

.rule-section-block__subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  text-align: left;
}

.rule-section-block__actions {
  flex-shrink: 0;
}

.rule-section-block__body {
  overflow: visible;
}
</style>
