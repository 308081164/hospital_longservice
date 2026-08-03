<template>
  <div ref="bodyRef" class="h-scroll-body">
    <slot />
  </div>
</template>

<script setup lang="ts">
  import { inject, onBeforeUnmount, onMounted, ref } from 'vue'

  const bodyRef = ref<HTMLElement | null>(null)
  const register = inject<(el: HTMLElement) => (() => void) | undefined>('horizontalScrollRegister')

  let cleanup: (() => void) | undefined

  onMounted(() => {
    if (bodyRef.value && register) {
      cleanup = register(bodyRef.value)
    }
  })

  onBeforeUnmount(() => {
    cleanup?.()
  })
</script>

<style scoped>
  .h-scroll-body {
    overflow: auto visible;
    scrollbar-width: none;
  }

  .h-scroll-body::-webkit-scrollbar {
    display: none;
    height: 0;
  }
</style>
