<script setup>
import { ref, onMounted } from 'vue'
import { getHeritageManList } from '@/api/content'

const searchText = ref('')
const items = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const colorPool = ['#8B4513','#6B3A6B','#2E4057','#5D4037','#C62828','#33691E','#1565C0','#4A6741','#BF360C','#6A1B9A']
const levelMap = { 1: '国家级', 2: '省级', 3: '市级', 4: '县级' }

const loadData = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (searchText.value) params.keyword = searchText.value
    const res = await getHeritageManList(params)
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
  <div class="inheritor-page">
    <!-- Banner -->
    <div class="page-banner">
      <h1>非物质文化遗产传承人</h1>
      <p>守护传统 · 传承匠心</p>
    </div>

    <!-- Search -->
    <div class="container" style="margin-top: 24px;">
      <div class="search-box" style="max-width: 560px; margin: 0 auto;">
        <input v-model="searchText" placeholder="请输入关键词搜索传承人" @keyup.enter="doSearch" />
        <button class="search-btn" @click="doSearch">🔍</button>
      </div>
    </div>

    <!-- Grid -->
    <div class="container" style="margin-top: 28px; padding-bottom: 40px;">
      <div v-if="loading" class="loading-state"><p>加载中...</p></div>
      <div v-else class="inheritor-grid">
        <div v-for="person in items" :key="person.id" class="inheritor-card card" @click="$router.push(`/inheritor/${person.id}`)" style="cursor:pointer;">
          <div class="card-avatar" :style="{ background: person.avatar ? `url(${person.avatar}) center/cover` : person.color }">
            <span v-if="!person.avatar" class="avatar-text">{{ person.name?.[0] }}</span>
          </div>
          <div class="card-body">
            <h3 class="card-name">{{ person.name }}</h3>
            <div class="card-info">
              <span>👤 {{ person.gender === 1 ? '男' : '女' }}</span>
              <span v-if="person.level">🏷️ {{ levelMap[person.level] || '' }}传承人</span>
            </div>
            <p v-if="person.categoryName || person.itemName" class="card-location">{{ person.categoryName }} · {{ person.itemName }}</p>
            <p class="card-desc">{{ person.introduction || person.skill || '' }}</p>
            <div class="card-action">
              <span class="detail-link">查看详情 ›</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!loading && items.length === 0" class="empty-state">
        <p>暂无匹配的传承人信息</p>
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
.inheritor-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.inheritor-card { cursor: pointer; }

.card-avatar {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.avatar-text {
  font-size: 48px;
  color: rgba(255,255,255,0.7);
  font-family: var(--ich-font-serif);
  font-weight: 700;
}

.card-code {
  position: absolute;
  bottom: 8px;
  right: 8px;
  font-size: 11px;
  color: rgba(255,255,255,0.8);
  background: rgba(0,0,0,0.3);
  padding: 2px 8px;
  border-radius: 3px;
}

.card-body { padding: 14px; }

.card-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--ich-text-primary);
  margin-bottom: 8px;
}

.card-info {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: var(--ich-text-secondary);
  margin-bottom: 6px;
}

.card-location {
  font-size: 12px;
  color: var(--ich-text-muted);
  margin-bottom: 8px;
}

.card-desc {
  font-size: 13px;
  color: var(--ich-text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
}

.card-action { text-align: right; }

.detail-link {
  font-size: 13px;
  color: var(--ich-primary);
  cursor: pointer;
}
.detail-link:hover { text-decoration: underline; }

.empty-state {
  text-align: center;
  padding: 80px 0;
  color: var(--ich-text-muted);
}

@media (max-width: 1100px) { .inheritor-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 640px) { .inheritor-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
