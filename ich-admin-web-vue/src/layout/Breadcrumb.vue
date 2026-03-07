<template>
  <div class="breadcrumb-container" v-if="breadcrumbs.length > 0">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">
        <el-icon :size="14" style="vertical-align: -2px; margin-right: 4px;"><HomeFilled /></el-icon>
        Home
      </el-breadcrumb-item>
      <el-breadcrumb-item v-if="parentTitle">{{ parentTitle }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const currentTitle = computed(() => route.meta?.title || '')
const parentTitle = computed(() => route.meta?.parent || '')
const breadcrumbs = computed(() => {
  const items = []
  if (parentTitle.value) items.push(parentTitle.value)
  if (currentTitle.value) items.push(currentTitle.value)
  return items
})
</script>

<style lang="scss" scoped>
.breadcrumb-container {
  margin-bottom: 24px;

  :deep(.el-breadcrumb__inner) {
    font-size: 13px;
    color: #94A3B8;
    font-weight: 400;
    transition: color 0.15s ease;
  }

  :deep(.el-breadcrumb__inner a) {
    font-weight: 400;
    color: #94A3B8;

    &:hover {
      color: #2563EB;
    }
  }

  :deep(.el-breadcrumb__separator) {
    color: #CBD5E1;
    font-weight: 300;
  }

  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: #334155;
    font-weight: 600;
  }
}
</style>
