<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from './AppHeader.vue'
import OmnitrixFloat from '../OmnitrixFloat.vue'

const route = useRoute()

const breadcrumbMap = {
  '/': '首页',
  '/culture': '非遗文化',
  '/activity': '非遗活动',
  '/shop': '周边商城',
  '/inheritor': '传承人',
  '/video': '非遗视频',
  '/article': '文章资讯',
  '/notification': '系统通知',
  '/user': '个人中心',
  '/omnitrix/chat': 'AI助手',
  '/omnitrix/analysis': '智能分析',
}

const breadcrumb = computed(() => {
  const path = route.path
  if (path === '/') return []
  const matched = Object.entries(breadcrumbMap).find(([k]) => k !== '/' && path.startsWith(k))
  return matched ? [{ name: matched[1], path: matched[0] }] : []
})
</script>

<template>
  <div class="main-layout">
    <AppHeader />
    <div class="breadcrumb-bar" v-if="breadcrumb.length > 0">
      <div class="breadcrumb-inner">
        <span class="breadcrumb-label">您当前的位置：</span>
        <router-link to="/" class="breadcrumb-link">首页</router-link>
        <template v-for="item in breadcrumb" :key="item.path">
          <span class="breadcrumb-sep">&gt;</span>
          <span class="breadcrumb-current">{{ item.name }}</span>
        </template>
      </div>
    </div>
    <main class="main-content">
      <router-view />
    </main>
    <footer class="app-footer">
      <div class="footer-inner">
        <p>© 2025 非物质文化遗产管理平台 — 传承千年匠心 · 守护文化瑰宝</p>
      </div>
    </footer>
    <OmnitrixFloat />
  </div>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--ich-bg);
}

.breadcrumb-bar {
  background: var(--ich-white);
  border-bottom: 1px solid var(--ich-border);
  padding: 10px 0;
}

.breadcrumb-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 20px;
  font-size: 13px;
  color: var(--ich-text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.breadcrumb-label { color: var(--ich-text-secondary); }
.breadcrumb-link {
  color: var(--ich-text-secondary);
  text-decoration: none;
}
.breadcrumb-link:hover { color: var(--ich-primary); }
.breadcrumb-sep { color: var(--ich-text-muted); margin: 0 2px; }
.breadcrumb-current { color: var(--ich-text-primary); }

.main-content {
  flex: 1;
}

.app-footer {
  background: var(--ich-white);
  border-top: 1px solid var(--ich-border);
  padding: 20px 0;
  margin-top: auto;
}

.footer-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 20px;
  text-align: center;
  font-size: 13px;
  color: var(--ich-text-muted);
}
</style>
