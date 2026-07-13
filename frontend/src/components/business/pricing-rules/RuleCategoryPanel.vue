<template>
  <section class="rule-category-panel" :class="`rule-category-panel--${theme}`">
    <header class="rule-category-panel__header">
      <div class="rule-category-panel__badge" aria-hidden="true">
        <span class="rule-category-panel__badge-text">{{ badge }}</span>
      </div>
      <div class="rule-category-panel__titles">
        <h2 class="rule-category-panel__title">{{ title }}</h2>
        <p v-if="subtitle" class="rule-category-panel__subtitle">{{ subtitle }}</p>
      </div>
      <span v-if="tag" class="rule-category-panel__tag">{{ tag }}</span>
    </header>
    <div class="rule-category-panel__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'RuleCategoryPanel' })

export type RuleCategoryTheme =
  | 'heat'
  | 'cold'
  | 'packaging'
  | 'needle'
  | 'cleaning'
  | 'logistics'
  | 'settlement'
  | 'export'

const props = defineProps<{
  category: string
  title: string
  subtitle?: string
  theme: RuleCategoryTheme
  badge?: string
  tag?: string
}>()

const badge = computed(() => props.badge ?? props.category.slice(0, 2))
</script>

<style scoped>
.rule-category-panel {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.rule-category-panel__header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
}

.rule-category-panel__badge {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rule-category-panel__badge-text {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.rule-category-panel__titles {
  flex: 1;
  min-width: 0;
}

.rule-category-panel__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
}

.rule-category-panel__subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
  opacity: 0.88;
}

.rule-category-panel__tag {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.rule-category-panel__body {
  padding: 4px 20px 20px;
  overflow: visible;
}

/* 高温：暖色左条 + 浅橙底 */
.rule-category-panel--heat {
  border-color: #fecaca;
}

.rule-category-panel--heat .rule-category-panel__header {
  background: linear-gradient(90deg, #fff7ed 0%, #ffedd5 100%);
  border-bottom: 2px solid #fdba74;
  border-left: 6px solid #ea580c;
}

.rule-category-panel--heat .rule-category-panel__badge {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: #ea580c;
  color: #fff;
}

.rule-category-panel--heat .rule-category-panel__title {
  color: #9a3412;
}

.rule-category-panel--heat .rule-category-panel__subtitle {
  color: #c2410c;
}

.rule-category-panel--heat .rule-category-panel__tag {
  background: #fed7aa;
  color: #9a3412;
}

/* 低温：冷色圆角胶囊头 */
.rule-category-panel--cold {
  border-color: #bae6fd;
}

.rule-category-panel--cold .rule-category-panel__header {
  margin: 12px 12px 0;
  padding: 14px 18px;
  border-radius: 10px;
  background: linear-gradient(135deg, #e0f2fe 0%, #dbeafe 100%);
  border: 1px solid #7dd3fc;
}

.rule-category-panel--cold .rule-category-panel__badge {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #0284c7;
  color: #fff;
  box-shadow: 0 0 0 3px rgba(2, 132, 199, 0.15);
}

.rule-category-panel--cold .rule-category-panel__title {
  color: #075985;
}

.rule-category-panel--cold .rule-category-panel__subtitle {
  color: #0369a1;
}

.rule-category-panel--cold .rule-category-panel__tag {
  background: #bae6fd;
  color: #0c4a6e;
}

/* 包装：绿色方框图标 + 虚线底 */
.rule-category-panel--packaging {
  border-color: #86efac;
  border-style: dashed;
}

.rule-category-panel--packaging .rule-category-panel__header {
  background: #f0fdf4;
  border-bottom: 1px dashed #4ade80;
}

.rule-category-panel--packaging .rule-category-panel__badge {
  width: 42px;
  height: 42px;
  border-radius: 6px;
  border: 2px solid #16a34a;
  background: #dcfce7;
  color: #15803d;
}

.rule-category-panel--packaging .rule-category-panel__title {
  color: #14532d;
}

.rule-category-panel--packaging .rule-category-panel__subtitle {
  color: #166534;
}

.rule-category-panel--packaging .rule-category-panel__tag {
  background: #bbf7d0;
  color: #14532d;
}

/* 小件识别：紫色居中强调条 */
.rule-category-panel--needle {
  border-color: #d8b4fe;
}

.rule-category-panel--needle .rule-category-panel__header {
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  background: #faf5ff;
  border-top: 4px solid #9333ea;
  position: relative;
}

.rule-category-panel--needle .rule-category-panel__badge {
  position: absolute;
  top: 14px;
  right: 20px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #9333ea;
  color: #fff;
}

.rule-category-panel--needle .rule-category-panel__titles {
  padding-right: 48px;
}

.rule-category-panel--needle .rule-category-panel__title {
  color: #581c87;
}

.rule-category-panel--needle .rule-category-panel__subtitle {
  color: #7e22ce;
}

.rule-category-panel--needle .rule-category-panel__tag {
  background: #e9d5ff;
  color: #6b21a8;
}

/* 数据清洗：灰蓝简约下划线 */
.rule-category-panel--cleaning {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.rule-category-panel--cleaning .rule-category-panel__header {
  padding-bottom: 12px;
  background: transparent;
  border-bottom: 3px double #94a3b8;
}

.rule-category-panel--cleaning .rule-category-panel__badge {
  width: auto;
  height: auto;
  padding: 6px 10px;
  border-radius: 4px;
  background: #475569;
  color: #f8fafc;
  font-size: 11px;
}

.rule-category-panel--cleaning .rule-category-panel__title {
  color: #1e293b;
}

.rule-category-panel--cleaning .rule-category-panel__subtitle {
  color: #64748b;
}

.rule-category-panel--cleaning .rule-category-panel__tag {
  background: #e2e8f0;
  color: #334155;
}

/* 物流：琥珀斜纹底 */
.rule-category-panel--logistics {
  border-color: #fcd34d;
}

.rule-category-panel--logistics .rule-category-panel__header {
  background: repeating-linear-gradient(
    -45deg,
    #fffbeb,
    #fffbeb 8px,
    #fef3c7 8px,
    #fef3c7 16px
  );
  border-bottom: 2px solid #f59e0b;
}

.rule-category-panel--logistics .rule-category-panel__badge {
  width: 44px;
  height: 44px;
  border-radius: 8px 8px 8px 0;
  background: #d97706;
  color: #fff;
}

.rule-category-panel--logistics .rule-category-panel__title {
  color: #92400e;
}

.rule-category-panel--logistics .rule-category-panel__subtitle {
  color: #b45309;
}

.rule-category-panel--logistics .rule-category-panel__tag {
  background: #fde68a;
  color: #78350f;
}

/* 结款函：靛蓝双线框 */
.rule-category-panel--settlement {
  border: 2px solid #818cf8;
}

.rule-category-panel--settlement .rule-category-panel__header {
  background: #eef2ff;
  border-bottom: 1px solid #a5b4fc;
  box-shadow: inset 0 -1px 0 #c7d2fe;
}

.rule-category-panel--settlement .rule-category-panel__badge {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  background: #4f46e5;
  color: #fff;
}

.rule-category-panel--settlement .rule-category-panel__title {
  color: #312e81;
}

.rule-category-panel--settlement .rule-category-panel__subtitle {
  color: #4338ca;
}

.rule-category-panel--settlement .rule-category-panel__tag {
  background: #c7d2fe;
  color: #3730a3;
}

/* 导出：翠绿紧凑标签式 */
.rule-category-panel--export {
  border-color: #6ee7b7;
}

.rule-category-panel--export .rule-category-panel__header {
  padding: 12px 16px;
  background: #ecfdf5;
  border-left: 8px solid #10b981;
}

.rule-category-panel--export .rule-category-panel__badge {
  width: auto;
  padding: 4px 12px;
  border-radius: 999px;
  background: #059669;
  color: #fff;
}

.rule-category-panel--export .rule-category-panel__title {
  font-size: 16px;
  color: #065f46;
}

.rule-category-panel--export .rule-category-panel__subtitle {
  color: #047857;
}

.rule-category-panel--export .rule-category-panel__tag {
  background: #a7f3d0;
  color: #064e3b;
}
</style>
