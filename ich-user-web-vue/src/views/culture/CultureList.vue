<script setup>
import { ref, onMounted } from 'vue'
import { getCategoryTree, getItemList } from '@/api/content'

const activeCategory = ref(null)
const searchText = ref('')
const categories = ref([])
const items = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)

const colorPool = ['#5D4037','#8B2020','#2E4057','#4A6741','#8B4513','#6B3A6B','#33691E','#BF360C','#1565C0','#6A1B9A']

const loadCategories = async () => {
  try {
    const res = await getCategoryTree()
    categories.value = res.data || []
  } catch (e) { /* ignore */ }
}

const loadItems = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (activeCategory.value) params.categoryId = activeCategory.value
    if (searchText.value) params.keyword = searchText.value
    const res = await getItemList(params)
    const pageData = res.data || {}
    items.value = (pageData.list || pageData.records || []).map((item, i) => ({
      ...item,
      color: colorPool[i % colorPool.length]
    }))
    total.value = pageData.total || 0
  } catch (e) {
    items.value = []
  }
  loading.value = false
}

const filterByCategory = (catId) => {
  activeCategory.value = catId
  pageNum.value = 1
  loadItems()
}

const doSearch = () => {
  pageNum.value = 1
  loadItems()
}

const changePage = (p) => {
  pageNum.value = p
  loadItems()
}

const totalPages = () => Math.ceil(total.value / pageSize.value)

onMounted(() => {
  loadCategories()
  loadItems()
})
</script>

<template>
  <div class="culture-page">
    <!-- Search -->
    <div class="search-section">
      <div class="container">
        <div class="search-box" style="max-width: 600px;">
          <input v-model="searchText" placeholder="请输入文化项目搜索关键词" @keyup.enter="doSearch" />
          <button class="search-btn" @click="doSearch">🔍</button>
        </div>
      </div>
    </div>

    <!-- Categories -->
    <div class="container">
      <div class="category-tabs">
        <span :class="['tab-item', { active: !activeCategory }]"
              @click="filterByCategory(null)">全部</span>
        <span v-for="cat in categories" :key="cat.id"
              :class="['tab-item', { active: activeCategory === cat.id }]"
              @click="filterByCategory(cat.id)">{{ cat.name }}</span>
      </div>
    </div>

    <!-- Items Grid -->
    <div class="container">
      <div v-if="loading" class="loading-state"><p>加载中...</p></div>
      <div v-else class="items-grid">
        <div v-for="item in items" :key="item.id" class="culture-card card" @click="$router.push(`/culture/${item.id}`)" style="cursor:pointer;">
          <div class="card-img" :style="{ background: item.coverImage ? `url(${item.coverImage}) center/cover` : item.color }">
            <span v-if="!item.coverImage" class="card-img-text">{{ item.name }}</span>
          </div>
          <div class="card-body">
            <h3 class="card-title">{{ item.name }}</h3>
            <div class="card-meta">
              <span v-if="item.categoryName">类型: {{ item.categoryName }}</span>
              <span v-if="item.level">级别: {{ ['','国家级','省级','市级','县级'][item.level] || '' }}</span>
            </div>
            <div class="card-meta">
              <span v-if="item.regionName">地区: {{ item.regionName }}</span>
              <span v-if="item.declarationUnit">申报单位: {{ item.declarationUnit }}</span>
            </div>
            <p class="card-desc">{{ item.description }}</p>
            <span class="detail-link">查看详情 ›</span>
          </div>
        </div>
      </div>

      <div v-if="!loading && items.length === 0" class="empty-state">
        <p>暂无匹配的非遗文化项目</p>
      </div>

      <!-- 分页 -->
      <div v-if="totalPages() > 1" class="pagination">
        <button :disabled="pageNum <= 1" @click="changePage(pageNum - 1)">上一页</button>
        <span class="page-info">{{ pageNum }} / {{ totalPages() }}</span>
        <button :disabled="pageNum >= totalPages()" @click="changePage(pageNum + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.culture-page { padding-bottom: 40px; }

.search-section {
  background: var(--ich-white);
  padding: 24px 0;
  margin-bottom: 8px;
}

.items-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 8px;
}

.culture-card { cursor: pointer; }

.card-img {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.card-img::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 50%, rgba(0,0,0,0.3) 100%);
}

.card-img-text {
  color: rgba(255,255,255,0.8);
  font-size: 28px;
  font-family: var(--ich-font-serif);
  letter-spacing: 6px;
  z-index: 1;
  text-shadow: 0 2px 8px rgba(0,0,0,0.3);
}

.card-body { padding: 16px; }

.card-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--ich-text-primary);
  margin-bottom: 8px;
}

.card-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--ich-text-muted);
  margin-bottom: 6px;
}

.card-desc {
  font-size: 13px;
  color: var(--ich-text-secondary);
  line-height: 1.7;
  margin-top: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-state {
  text-align: center;
  padding: 80px 0;
  color: var(--ich-text-muted);
  font-size: 16px;
}

@media (max-width: 900px) {
  .items-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 600px) {
  .items-grid { grid-template-columns: 1fr; }
}
</style>
