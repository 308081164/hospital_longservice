<template>
  <div ref="panelRef" class="h-scroll-panel" :style="{ maxHeight: maxHeight ?? '500px' }">
    <div ref="contentRef" class="h-scroll-panel__content" @scroll="onContentScroll">
      <slot />
    </div>
    <div
      v-show="showRail"
      ref="railRef"
      class="h-scroll-panel__rail"
      aria-hidden="true"
      @scroll="onRailScroll"
    >
      <div class="h-scroll-panel__rail-track" :style="{ width: `${railWidth}px` }" />
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref } from 'vue'

  defineProps<{
    maxHeight?: string
  }>()

  type BodyEntry = {
    el: HTMLElement
    onScroll: () => void
    onResize: () => void
  }

  const contentRef = ref<HTMLElement | null>(null)
  const railRef = ref<HTMLElement | null>(null)
  const railWidth = ref(0)
  const railClientWidth = ref(0)

  const bodies: BodyEntry[] = []
  let activeBody: HTMLElement | null = null
  let syncing = false
  let bodyResizeObserver: ResizeObserver | null = null
  let railResizeObserver: ResizeObserver | null = null

  const showRail = computed(() => railWidth.value > railClientWidth.value + 1)

  function updateRailClientWidth() {
    railClientWidth.value = railRef.value?.clientWidth ?? contentRef.value?.clientWidth ?? 0
  }

  function syncRailFromBody() {
    if (syncing || !activeBody || !railRef.value) return
    syncing = true
    railRef.value.scrollLeft = activeBody.scrollLeft
    syncing = false
  }

  function syncBodyFromRail() {
    if (syncing || !activeBody || !railRef.value) return
    syncing = true
    activeBody.scrollLeft = railRef.value.scrollLeft
    syncing = false
  }

  function onRailScroll() {
    syncBodyFromRail()
  }

  function pickActiveBody() {
    const viewport = contentRef.value
    if (!viewport || bodies.length === 0) {
      activeBody = null
      railWidth.value = 0
      updateRailClientWidth()
      return
    }

    const viewportRect = viewport.getBoundingClientRect()
    let bestEl: HTMLElement | null = null
    let bestVisible = -1

    for (const entry of bodies) {
      const rect = entry.el.getBoundingClientRect()
      const visible = Math.min(rect.bottom, viewportRect.bottom) - Math.max(rect.top, viewportRect.top)
      if (visible > bestVisible) {
        bestVisible = visible
        bestEl = entry.el
      }
    }

    activeBody = bestEl
    if (activeBody) {
      railWidth.value = activeBody.scrollWidth
      syncRailFromBody()
    } else {
      railWidth.value = 0
    }
    updateRailClientWidth()
  }

  function onContentScroll() {
    pickActiveBody()
  }

  function registerBody(el: HTMLElement) {
    const onScroll = () => {
      activeBody = el
      railWidth.value = el.scrollWidth
      syncRailFromBody()
    }
    const onResize = () => {
      if (activeBody === el) {
        railWidth.value = el.scrollWidth
      }
      pickActiveBody()
    }

    const entry: BodyEntry = { el, onScroll, onResize }
    bodies.push(entry)
    el.addEventListener('scroll', onScroll, { passive: true })
    el.addEventListener('pointerdown', onScroll, { passive: true })

    if (!bodyResizeObserver) {
      bodyResizeObserver = new ResizeObserver(() => {
        for (const item of bodies) {
          item.onResize()
        }
      })
    }
    bodyResizeObserver.observe(el)

    nextTick(() => {
      pickActiveBody()
    })

    return () => {
      el.removeEventListener('scroll', onScroll)
      el.removeEventListener('pointerdown', onScroll)
      bodyResizeObserver?.unobserve(el)
      const index = bodies.indexOf(entry)
      if (index >= 0) bodies.splice(index, 1)
      if (activeBody === el) {
        pickActiveBody()
      }
    }
  }

  provide('horizontalScrollRegister', registerBody)

  onMounted(async () => {
    await nextTick()
    updateRailClientWidth()
    pickActiveBody()
    if (railRef.value) {
      railResizeObserver = new ResizeObserver(() => updateRailClientWidth())
      railResizeObserver.observe(railRef.value)
    }
  })

  onBeforeUnmount(() => {
    bodyResizeObserver?.disconnect()
    railResizeObserver?.disconnect()
    bodies.length = 0
  })
</script>

<style scoped>
  .h-scroll-panel {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: #fff;
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 6px;
  }

  .h-scroll-panel__content {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden auto;
  }

  .h-scroll-panel__rail {
    flex-shrink: 0;
    height: 14px;
    overflow: hidden auto;
    background: #fafafa;
    border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  }

  .h-scroll-panel__rail-track {
    height: 1px;
  }
</style>
