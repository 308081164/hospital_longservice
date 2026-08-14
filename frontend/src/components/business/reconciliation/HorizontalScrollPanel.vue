<template>
  <div ref="panelRef" class="h-scroll-panel" :style="{ maxHeight: maxHeight ?? '500px' }">
    <div ref="contentRef" class="h-scroll-panel__content">
      <slot />
    </div>
    <div
      ref="railRef"
      class="h-scroll-panel__rail"
      :class="{ 'is-inactive': !showRail }"
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
  }

  const contentRef = ref<HTMLElement | null>(null)
  const railRef = ref<HTMLElement | null>(null)
  const railWidth = ref(0)
  const railClientWidth = ref(0)

  const bodies: BodyEntry[] = []
  const visibilityMap = new Map<HTMLElement, number>()
  let activeBody: HTMLElement | null = null
  let syncing = false
  let bodyResizeObserver: ResizeObserver | null = null
  let railResizeObserver: ResizeObserver | null = null
  let bodyIntersectionObserver: IntersectionObserver | null = null
  let pickRaf = 0
  let resizeRaf = 0

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

  function applyActiveBody(nextBody: HTMLElement | null) {
    const nextRailWidth = nextBody ? nextBody.scrollWidth : 0
    if (activeBody === nextBody && railWidth.value === nextRailWidth) {
      return
    }

    activeBody = nextBody
    railWidth.value = nextRailWidth
    if (activeBody) {
      syncRailFromBody()
    }
    updateRailClientWidth()
  }

  function pickActiveBody() {
    const viewport = contentRef.value
    if (!viewport || bodies.length === 0) {
      if (activeBody !== null) {
        applyActiveBody(null)
      }
      return
    }

    let bestEl: HTMLElement | null = null
    let bestRatio = -1

    for (const entry of bodies) {
      const ratio = visibilityMap.get(entry.el) ?? 0
      if (ratio > bestRatio) {
        bestRatio = ratio
        bestEl = entry.el
      }
    }

    // 滞后：当前 body 仍占视口 25% 以上且领先不明显时，避免边界来回切换
    if (activeBody && bestEl && activeBody !== bestEl) {
      const currentRatio = visibilityMap.get(activeBody) ?? 0
      if (currentRatio > 0.25 && bestRatio - currentRatio < 0.12) {
        bestEl = activeBody
      }
    }

    applyActiveBody(bestEl)
  }

  function schedulePickActiveBody() {
    if (pickRaf) return
    pickRaf = window.requestAnimationFrame(() => {
      pickRaf = 0
      pickActiveBody()
    })
  }

  function scheduleResizeUpdate() {
    if (resizeRaf) return
    resizeRaf = window.requestAnimationFrame(() => {
      resizeRaf = 0
      if (activeBody) {
        const nextRailWidth = activeBody.scrollWidth
        if (railWidth.value !== nextRailWidth) {
          railWidth.value = nextRailWidth
          updateRailClientWidth()
        }
      }
    })
  }

  function ensureIntersectionObserver() {
    const root = contentRef.value
    if (!root) return

    if (!bodyIntersectionObserver) {
      bodyIntersectionObserver = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            visibilityMap.set(entry.target as HTMLElement, entry.intersectionRatio)
          }
          schedulePickActiveBody()
        },
        {
          root,
          threshold: [0, 0.1, 0.25, 0.5, 0.75, 1]
        }
      )
    }

    for (const entry of bodies) {
      bodyIntersectionObserver.observe(entry.el)
    }
  }

  function registerBody(el: HTMLElement) {
    const onScroll = () => {
      if (activeBody !== el) {
        applyActiveBody(el)
        return
      }
      syncRailFromBody()
    }

    const entry: BodyEntry = { el, onScroll }
    bodies.push(entry)
    visibilityMap.set(el, 0)
    el.addEventListener('scroll', onScroll, { passive: true })

    if (!bodyResizeObserver) {
      bodyResizeObserver = new ResizeObserver(() => {
        scheduleResizeUpdate()
      })
    }
    bodyResizeObserver.observe(el)

    nextTick(() => {
      ensureIntersectionObserver()
      schedulePickActiveBody()
    })

    return () => {
      el.removeEventListener('scroll', onScroll)
      bodyResizeObserver?.unobserve(el)
      bodyIntersectionObserver?.unobserve(el)
      visibilityMap.delete(el)
      const index = bodies.indexOf(entry)
      if (index >= 0) bodies.splice(index, 1)
      if (activeBody === el) {
        schedulePickActiveBody()
      }
    }
  }

  provide('horizontalScrollRegister', registerBody)

  onMounted(async () => {
    await nextTick()
    updateRailClientWidth()
    ensureIntersectionObserver()
    schedulePickActiveBody()
    if (railRef.value) {
      railResizeObserver = new ResizeObserver(() => updateRailClientWidth())
      railResizeObserver.observe(railRef.value)
    }
  })

  onBeforeUnmount(() => {
    if (pickRaf) {
      window.cancelAnimationFrame(pickRaf)
    }
    if (resizeRaf) {
      window.cancelAnimationFrame(resizeRaf)
    }
    bodyResizeObserver?.disconnect()
    bodyIntersectionObserver?.disconnect()
    railResizeObserver?.disconnect()
    bodies.length = 0
    visibilityMap.clear()
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
    overscroll-behavior: contain;
  }

  .h-scroll-panel__rail {
    flex-shrink: 0;
    height: 14px;
    overflow: hidden auto;
    background: #fafafa;
    border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  }

  .h-scroll-panel__rail.is-inactive {
    visibility: hidden;
    pointer-events: none;
  }

  .h-scroll-panel__rail-track {
    height: 1px;
  }
</style>
