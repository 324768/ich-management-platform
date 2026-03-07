<template>
  <nav class="ag-sub-pills" ref="pillsRef">
    <span class="ag-sub-pill-indicator" :style="indicatorStyle" />
    <router-link
      v-for="(tab, idx) in tabs"
      :key="tab.path"
      :ref="el => setPillRef(el, idx)"
      :to="tab.path"
      class="ag-sub-pill"
      :class="{ active: isActive(tab) }"
      draggable="false"
    >
      {{ tab.label }}
    </router-link>
  </nav>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps({
  tabs: { type: Array, required: true },
})

const route = useRoute()
const pillsRef = ref(null)
const pillRefs = ref([])
const indicatorStyle = ref({ opacity: 0 })

function setPillRef(el, idx) {
  if (el) pillRefs.value[idx] = el.$el || el
}

function isActive(tab) {
  return route.path.startsWith(tab.path)
}

function updateIndicator() {
  const activeIdx = props.tabs.findIndex(t => isActive(t))
  if (activeIdx < 0 || !pillRefs.value[activeIdx] || !pillsRef.value) {
    indicatorStyle.value = { opacity: 0 }
    return
  }
  const pillEl = pillRefs.value[activeIdx]
  const navEl = pillsRef.value
  const pillRect = pillEl.getBoundingClientRect()
  const navRect = navEl.getBoundingClientRect()
  indicatorStyle.value = {
    width: pillRect.width + 'px',
    transform: `translateX(${pillRect.left - navRect.left - 4}px)`,
    opacity: 1,
  }
}

watch(() => route.path, () => nextTick(updateIndicator))
watch(() => props.tabs, () => nextTick(() => setTimeout(updateIndicator, 50)), { deep: true })

onMounted(() => {
  nextTick(() => setTimeout(updateIndicator, 80))
  window.addEventListener('resize', updateIndicator)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateIndicator)
})
</script>

<style lang="scss" scoped>
.ag-sub-pills {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
  background: #f3f4f6;
  border-radius: 9999px;
  padding: 4px;
}

.ag-sub-pill-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  height: calc(100% - 8px);
  border-radius: 9999px;
  background: #e5e7eb;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1), width 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s;
  pointer-events: none;
  z-index: 0;
}

.ag-sub-pill {
  position: relative;
  z-index: 1;
  padding: 8px 24px;
  border-radius: 9999px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
  white-space: nowrap;
  text-decoration: none;
  color: #4b5563;
  cursor: pointer;

  &:hover {
    color: #111827;
    background: #e5e7eb;
  }

  &.active {
    background: transparent;
    color: #111827;
  }
}

// Dark mode
:global(html[data-theme="dark"]) {
  .ag-sub-pills { background: #191e24; }
  .ag-sub-pill-indicator { background: #2a323c; box-shadow: none; }
  .ag-sub-pill { color: #9ca3af;
    &:hover { color: #a6adbb; background: rgba(21, 25, 30, 0.5); }
    &.active { background: transparent; color: #a6adbb; }
  }
}
</style>
