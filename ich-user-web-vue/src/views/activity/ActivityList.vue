<script setup>
import { ref, onMounted } from 'vue'
import { getActivityList, registerActivity } from '@/api/content'
import { getUserInfo as getStoredUser } from '@/utils/token'

const items = ref([])
const loading = ref(false)
const searchText = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const colorPool = ['#C62828','#5D4037','#1B5E20','#4A148C','#006064','#E65100','#1565C0','#8B2020']

const statusText = (item) => {
  if (item.status === 0) return '下架'
  const now = new Date()
  if (item.registrationDeadline && new Date(item.registrationDeadline) > now) return '报名中'
  if (item.startTime && new Date(item.startTime) > now) return '即将开始'
  if (item.endTime && new Date(item.endTime) > now) return '进行中'
  return '已结束'
}

const statusColor = (item) => {
  const s = statusText(item)
  const map = { '报名中': '#4CAF50', '即将开始': '#FF9800', '进行中': '#2196F3', '已结束': '#999', '下架': '#ccc' }
  return map[s] || '#999'
}

const formatDate = (d) => d ? new Date(d).toLocaleDateString('zh-CN') : ''

const loadData = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (searchText.value) params.keyword = searchText.value
    const res = await getActivityList(params)
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

const handleRegister = async (activityId) => {
  const user = getStoredUser()
  if (!user?.id) { alert('请先登录'); return }
  try {
    await registerActivity({ activityId, userId: user.id })
    alert('报名成功！')
  } catch (e) {
    alert(e.message || '报名失败')
  }
}

onMounted(() => { loadData() })
</script>

<template>
  <div class="activity-page">
    <div class="page-banner">
      <h1>非遗活动</h1>
      <p>参与体验 · 感受非遗魅力</p>
    </div>

    <div class="container" style="padding: 20px 20px 0;">
      <div class="search-box" style="max-width: 560px; margin: 0 auto;">
        <input v-model="searchText" placeholder="搜索活动..." @keyup.enter="doSearch" />
        <button class="search-btn" @click="doSearch">🔍</button>
      </div>
    </div>

    <div class="container" style="padding: 20px 20px 40px;">
      <div v-if="loading" class="loading-state"><p>加载中...</p></div>
      <div v-else class="activity-list">
        <div v-for="item in items" :key="item.id" class="activity-card card" style="cursor:pointer;" @click="$router.push(`/activity/${item.id}`)">
          <div class="activity-cover" :style="{ background: item.coverImage ? `url(${item.coverImage}) center/cover` : item.color }">
            <span v-if="!item.coverImage" class="cover-text">{{ item.name?.substring(0, 2) }}</span>
          </div>
          <div class="activity-body">
            <div class="activity-header">
              <h3>{{ item.name }}</h3>
              <span class="status-badge" :style="{ background: statusColor(item) }">{{ statusText(item) }}</span>
            </div>
            <p class="activity-desc">{{ item.description }}</p>
            <div class="activity-meta">
              <span>📅 {{ formatDate(item.startTime) }} ~ {{ formatDate(item.endTime) }}</span>
              <span v-if="item.location">📍 {{ item.location }}</span>
              <span v-if="item.maxParticipants">👥 {{ item.currentParticipants || 0 }}/{{ item.maxParticipants }}</span>
            </div>
            <div style="display:flex;align-items:center;gap:12px;margin-top:12px;">
              <button v-if="statusText(item) === '报名中'" class="btn-primary" @click.stop="handleRegister(item.id)">立即报名</button>
              <span class="detail-link">查看详情 ›</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!loading && items.length === 0" class="empty-state"><p>暂无活动</p></div>

      <div v-if="totalPages() > 1" class="pagination">
        <button :disabled="pageNum <= 1" @click="changePage(pageNum - 1)">上一页</button>
        <span class="page-info">{{ pageNum }} / {{ totalPages() }}</span>
        <button :disabled="pageNum >= totalPages()" @click="changePage(pageNum + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.activity-card {
  display: flex;
  overflow: hidden;
  cursor: pointer;
}

.activity-cover {
  width: 200px;
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

.activity-body {
  flex: 1;
  padding: 20px 24px;
}

.activity-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.activity-header h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--ich-text-primary);
}

.status-badge {
  padding: 2px 12px;
  border-radius: 12px;
  font-size: 12px;
  color: #fff;
}

.activity-desc {
  font-size: 14px;
  color: var(--ich-text-secondary);
  margin-bottom: 10px;
  line-height: 1.7;
}

.activity-meta {
  display: flex;
  gap: 24px;
  font-size: 13px;
  color: var(--ich-text-muted);
}

@media (max-width: 640px) {
  .activity-card { flex-direction: column; }
  .activity-cover { width: 100%; height: 150px; }
}
</style>
