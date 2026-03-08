<script setup>
import { ref, onMounted } from 'vue'
import { getItemList } from '@/api/content'

const items = ref([])
const searchText = ref('')
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const colorPool = ['#5D4037','#8B2020','#2E4057','#8B4513','#6B3A6B','#33691E','#BF360C','#4A148C']

const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : ''

const loadData = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (searchText.value) params.keyword = searchText.value
    const res = await getItemList(params)
    const pageData = res.data || {}
    items.value = (pageData.list || pageData.records || []).map((item, i) => ({
      ...item,
      color: colorPool[i % colorPool.length]
    }))
    total.value = pageData.total || 0
  } catch (e) { items.value = [] }
  loading.value = false
}

const doSearch = () => { pageNum.value = 1; loadData() }
const changePage = (p) => { pageNum.value = p; loadData() }
const totalPages = () => Math.ceil(total.value / pageSize.value)

onMounted(() => { loadData() })
</script>

<template>
  <div class="article-page">
    <div class="page-banner">
      <h1>文章资讯</h1>
      <p>了解非遗文化 · 传播传承故事</p>
    </div>

    <div class="container" style="margin-top: 24px;">
      <div class="search-box" style="max-width: 560px; margin: 0 auto;">
        <input v-model="searchText" placeholder="搜索文章..." @keyup.enter="doSearch" />
        <button class="search-btn" @click="doSearch">🔍</button>
      </div>
    </div>

    <div class="container" style="margin-top: 28px; padding-bottom: 40px;">
      <div v-if="loading" class="loading-state"><p>加载中...</p></div>
      <div v-else class="article-list">
        <div v-for="item in items" :key="item.id" class="article-card card">
          <div class="article-cover" :style="{ background: item.coverImage ? `url(${item.coverImage}) center/cover` : item.color }">
            <span v-if="!item.coverImage" class="cover-text">{{ item.name?.substring(0, 2) }}</span>
          </div>
          <div class="article-body">
            <div class="article-header">
              <span class="tag" v-if="item.categoryName">{{ item.categoryName }}</span>
              <h3 class="article-title">{{ item.name }}</h3>
            </div>
            <p v-if="item.description" class="article-desc">{{ item.description }}</p>
            <div class="article-meta">
              <span>🕐 {{ formatDate(item.createTime) }}</span>
              <span v-if="item.regionName">� {{ item.regionName }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!loading && items.length === 0" class="empty-state">
        <p>暂无匹配的文章</p>
      </div>

      <div v-if="totalPages() > 1" class="pagination">
        <button :disabled="pageNum <= 1" @click="changePage(pageNum - 1)">上一页</button>
        <span class="page-info">{{ pageNum }} / {{ totalPages() }}</span>
        <button :disabled="pageNum >= totalPages()" @click="changePage(pageNum + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.article-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.article-card {
  display: flex;
  overflow: hidden;
  cursor: pointer;
  transition: var(--ich-transition);
}

.article-card:hover {
  border-left: 3px solid var(--ich-primary);
}

.article-cover {
  width: 180px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-text {
  color: rgba(255,255,255,0.6);
  font-size: 32px;
  font-family: var(--ich-font-serif);
  letter-spacing: 6px;
}

.article-body {
  flex: 1;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.article-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.article-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ich-text-primary);
}

.article-desc {
  font-size: 13px;
  color: var(--ich-text-secondary);
  line-height: 1.7;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-meta {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: var(--ich-text-muted);
}

.empty-state {
  text-align: center;
  padding: 80px 0;
  color: var(--ich-text-muted);
}

@media (max-width: 640px) {
  .article-card { flex-direction: column; }
  .article-cover { width: 100%; height: 140px; }
}
</style>
