<template>
  <div ref="shellRef" class="h-scroll-shell" :style="shellStyle">
    <div ref="contentRef" class="h-scroll-shell__content">
      <div ref="trackRef" class="h-scroll-shell__track" @scroll="onTrackScroll">
        <div ref="innerRef" class="h-scroll-shell__inner">
          <slot />
        </div>
      </div>
    </div>
    <div v-if="$slots.footer" class="h-scroll-shell__footer">
      <slot name="footer" />
    </div>
    <div
      v-show="showRail"
      ref="railRef"
      class="h-scroll-shell__rail"
      aria-label="横向滚动"
    >
      <div
        ref="railTrackRef"
        class="h-scroll-shell__rail-track"
        @mousedown="onRailTrackMouseDown"
      >
        <div
          class="h-scroll-shell__rail-thumb"
          :style="thumbStyle"
          @mousedown.stop="onThumbMouseDown"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, nextTick, onBeforeUnmount, onMounted, onUpdated, ref, watch } from 'vue'

  const props = defineProps<{
    maxHeight?: string
  }>()

  const shellStyle = computed(() => ({
    maxHeight: props.maxHeight ?? '500px'
  }))

  const shellRef = ref<HTMLElement | null>(null)
  const trackRef = ref<HTMLElement | null>(null)
  const innerRef = ref<HTMLElement | null>(null)
  const railRef = ref<HTMLElement | null>(null)
  const railTrackRef = ref<HTMLElement | null>(null)
  const trackScrollWidth = ref(0)
  const trackClientWidth = ref(0)
  const trackScrollLeft = ref(0)

  let resizeObserver: ResizeObserver | null = null
  let dragState: { startX: number; startScrollLeft: number } | null = null
  let lastAppliedMinWidth = ''

  const showRail = computed(() => trackScrollWidth.value > trackClientWidth.value + 1)

  const maxScrollLeft = computed(() =>
    Math.max(0, trackScrollWidth.value - trackClientWidth.value)
  )

  const thumbWidth = computed(() => {
    if (!showRail.value || trackClientWidth.value <= 0) return 0
    const ratio = trackClientWidth.value / trackScrollWidth.value
    return Math.max(48, Math.round(trackClientWidth.value * ratio))
  })

  const thumbOffset = computed(() => {
    if (!showRail.value || maxScrollLeft.value <= 0) return 0
    const travel = trackClientWidth.value - thumbWidth.value
    return (trackScrollLeft.value / maxScrollLeft.value) * travel
  })

  const thumbStyle = computed(() => ({
    width: `${thumbWidth.value}px`,
    transform: `translateX(${thumbOffset.value}px)`
  }))

  function measureContentWidth(inner: HTMLElement): number {
    let width = inner.scrollWidth

    inner.querySelectorAll('.el-table').forEach((table) => {
      const tableEl = table as HTMLElement
      width = Math.max(width, tableEl.scrollWidth, tableEl.offsetWidth)

      tableEl
        .querySelectorAll('.el-table__header-wrapper table, .el-table__body-wrapper table')
        .forEach((nestedTable) => {
          const nested = nestedTable as HTMLElement
          width = Math.max(width, nested.scrollWidth, nested.offsetWidth)
        })
    })

    return width
  }

  function measureTrack() {
    const track = trackRef.value
    const inner = innerRef.value
    if (!track || !inner) {
      trackScrollWidth.value = 0
      trackClientWidth.value = 0
      trackScrollLeft.value = 0
      return
    }

    const contentWidth = Math.max(measureContentWidth(inner), track.scrollWidth)
    trackScrollWidth.value = contentWidth
    trackClientWidth.value = track.clientWidth
    trackScrollLeft.value = track.scrollLeft

    const nextMinWidth = contentWidth > track.clientWidth ? `${contentWidth}px` : '100%'
    if (lastAppliedMinWidth !== nextMinWidth) {
      inner.style.minWidth = nextMinWidth
      lastAppliedMinWidth = nextMinWidth
    }
  }

  function setScrollLeft(nextLeft: number) {
    const track = trackRef.value
    if (!track) return
    const clamped = Math.max(0, Math.min(maxScrollLeft.value, nextLeft))
    track.scrollLeft = clamped
    trackScrollLeft.value = clamped
  }

  function onTrackScroll() {
    trackScrollLeft.value = trackRef.value?.scrollLeft ?? 0
  }

  function onThumbMouseDown(event: MouseEvent) {
    if (!showRail.value) return
    dragState = {
      startX: event.clientX,
      startScrollLeft: trackScrollLeft.value
    }
    document.addEventListener('mousemove', onThumbMouseMove)
    document.addEventListener('mouseup', onThumbMouseUp)
    event.preventDefault()
  }

  function onThumbMouseMove(event: MouseEvent) {
    if (!dragState || maxScrollLeft.value <= 0) return
    const travel = trackClientWidth.value - thumbWidth.value
    if (travel <= 0) return
    const deltaX = event.clientX - dragState.startX
    const scrollDelta = (deltaX / travel) * maxScrollLeft.value
    setScrollLeft(dragState.startScrollLeft + scrollDelta)
  }

  function onThumbMouseUp() {
    dragState = null
    document.removeEventListener('mousemove', onThumbMouseMove)
    document.removeEventListener('mouseup', onThumbMouseUp)
  }

  function onRailTrackMouseDown(event: MouseEvent) {
    if (!showRail.value || !railTrackRef.value) return
    if ((event.target as HTMLElement).classList.contains('h-scroll-shell__rail-thumb')) return

    const rect = railTrackRef.value.getBoundingClientRect()
    const clickOffset = event.clientX - rect.left
    const targetScrollLeft =
      ((clickOffset - thumbWidth.value / 2) / Math.max(1, trackClientWidth.value - thumbWidth.value)) *
      maxScrollLeft.value
    setScrollLeft(targetScrollLeft)
  }

  function scheduleMeasure() {
    nextTick(() => {
      measureTrack()
    })
  }

  onMounted(() => {
    scheduleMeasure()
    const targets = [innerRef.value, trackRef.value, shellRef.value].filter(Boolean) as Element[]
    if (targets.length) {
      resizeObserver = new ResizeObserver(() => scheduleMeasure())
      targets.forEach((target) => resizeObserver?.observe(target))
    }
    if (innerRef.value) {
      innerRef.value.querySelectorAll('.el-table').forEach((table) => {
        resizeObserver?.observe(table)
      })
    }
    window.addEventListener('resize', scheduleMeasure, { passive: true })
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    window.removeEventListener('resize', scheduleMeasure)
    onThumbMouseUp()
  })

  onUpdated(() => {
    scheduleMeasure()
  })

  watch(
    () => props.maxHeight,
    () => scheduleMeasure()
  )

  defineExpose({ remeasure: scheduleMeasure })
</script>

<style scoped>
  .h-scroll-shell {
    display: grid;
    grid-template-rows: minmax(0, 1fr) auto auto;
    overflow: hidden;
    background: #fff;
    border: 1px solid var(--el-border-color-lighter, #ebeef5);
    border-radius: 6px;
  }

  .h-scroll-shell__content {
    min-height: 0;
    overflow: hidden;
  }

  .h-scroll-shell__track {
    height: 100%;
    max-height: 100%;
    overflow: auto;
    overscroll-behavior: contain;
    scrollbar-width: none;
    -ms-overflow-style: none;
  }

  .h-scroll-shell__track::-webkit-scrollbar {
    display: none;
    width: 0;
    height: 0;
  }

  .h-scroll-shell__inner {
    width: max-content;
    min-width: 100%;
  }

  .h-scroll-shell__footer {
    grid-row: 2;
    flex-shrink: 0;
    padding: 8px 12px 6px;
    background: #fff;
    border-top: 1px solid var(--el-border-color-extra-light, #f2f6fc);
  }

  .h-scroll-shell__rail {
    grid-row: 3;
    padding: 4px 8px 6px;
    background: #f5f7fa;
    border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  }

  .h-scroll-shell__rail-track {
    position: relative;
    height: 10px;
    overflow: hidden;
    cursor: pointer;
    background: #e4e7ed;
    border-radius: 5px;
  }

  .h-scroll-shell__rail-thumb {
    position: absolute;
    top: 0;
    left: 0;
    height: 10px;
    background: rgb(144 147 153 / 0.85);
    border-radius: 5px;
    cursor: grab;
    transition: background-color 0.15s ease;
  }

  .h-scroll-shell__rail-thumb:hover {
    background: rgb(96 98 102 / 0.9);
  }

  .h-scroll-shell__rail-thumb:active {
    cursor: grabbing;
    background: rgb(64 66 70 / 0.95);
  }

  .h-scroll-shell__inner :deep(.el-table) {
    width: max-content;
    min-width: 100%;
  }

  .h-scroll-shell__inner :deep(.el-table table) {
    width: max-content !important;
    min-width: 100%;
    table-layout: auto !important;
  }

  .h-scroll-shell__inner :deep(.el-table__inner-wrapper),
  .h-scroll-shell__inner :deep(.el-table__header-wrapper),
  .h-scroll-shell__inner :deep(.el-table__body-wrapper),
  .h-scroll-shell__inner :deep(.el-table__footer-wrapper) {
    overflow: visible !important;
  }

  .h-scroll-shell__inner :deep(.el-table .el-scrollbar__wrap) {
    overflow: visible !important;
  }

  .h-scroll-shell__inner :deep(.el-table .el-scrollbar__bar.is-horizontal) {
    display: none !important;
    height: 0 !important;
    opacity: 0 !important;
    pointer-events: none !important;
  }
</style>
