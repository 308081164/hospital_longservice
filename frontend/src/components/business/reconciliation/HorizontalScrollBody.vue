<template>
  <div class="h-scroll-body">
    <slot />
  </div>
</template>

<script setup lang="ts">
  /**
   * 兼容旧用法：横向滚动已由 HorizontalScrollPanel 统一管理，
   * 此组件仅作语义分组，不再注册独立 scroll body。
   */
  defineOptions({ name: 'HorizontalScrollBody' })
</script>

<style scoped>
  .h-scroll-body {
    width: 100%;
  }
</style>
