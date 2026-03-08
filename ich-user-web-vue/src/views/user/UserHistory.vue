<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getUserInfo } from '@/utils/token'

const router = useRouter()
const route = useRoute()
const user = getUserInfo()

const tabs = [
  { key: 'culture', label: '非遗文化', icon: '🎭' },
  { key: 'activity', label: '活动', icon: '📅' },
  { key: 'product', label: '商品', icon: '🛍️' },
  { key: 'inheritor', label: '传承人', icon: '👤' },
  { key: 'video', label: '非遗视频', icon: '🎬' },
  { key: 'article', label: '文章资讯', icon: '📰' },
]

const activeTab = ref(route.query.tab || 'culture')
const records = ref([])
const loading = ref(false)

const loadHistory = async () => {
  if (!user?.id) return
  loading.value = true
  records.value = []
  try {
    const historyKey = `ich_history_${user.id}_${activeTab.value}`
    const saved = localStorage.getItem(historyKey)
    if (saved) {
      records.value = JSON.parse(saved)
    }
  } catch { records.value = [] }
  loading.value = false
}

const switchTab = (key) => {
  activeTab.value = key
  router.replace({ path: '/user/history', query: { tab: key } })
  loadHistory()
}

const removeRecord = (index) => {
  records.value.splice(index, 1)
  const historyKey = `ich_history_${user.id}_${activeTab.value}`
  localStorage.setItem(historyKey, JSON.stringify(records.value))
}

const clearAll = () => {
  if (!confirm('确定清空该分类的全部浏览记录？')) return
  records.value = []
  const historyKey = `ich_history_${user.id}_${activeTab.value}`
  localStorage.removeItem(historyKey)
}

const goDetail = (record) => {
  const pathMap = {
    culture: '/culture/',
    activity: '/activity/',
    product: '/shop/product/',
    inheritor: '/inheritor/',
    video: '/video',
    article: '/article',
  }
  const base = pathMap[activeTab.value] || '/'
  if (record.targetId) {
    router.push(base + record.targetId)
  }
}

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return d.toLocaleDateString('zh-CN')
}

watch(() => route.query.tab, (val) => {
  if (val && val !== activeTab.value) {
    activeTab.value = val
    loadHistory()
  }
})

onMounted(loadHistory)
</script>

<template>
  <div class="page-container">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="router.push('/user')">← 返回</button>
        <h2>浏览历史</h2>
        <button v-if="records.length" class="clear-btn" @click="clearAll">清空</button>
      </div>

      <div class="history-tabs">
        <span v-for="tab in tabs" :key="tab.key"
              :class="['htab', { active: activeTab === tab.key }]"
              @click="switchTab(tab.key)">
          <span class="htab-icon">{{ tab.icon }}</span>
          <span>{{ tab.label }}</span>
        </span>
      </div>

      <div v-if="loading" class="empty-state">加载中...</div>
      <div v-else-if="records.length === 0" class="empty-state">
        <p class="empty-icon">📭</p>
        <p>暂无{{ tabs.find(t => t.key === activeTab)?.label || '' }}浏览记录</p>
        <p class="empty-hint">浏览相关内容后，记录会自动出现在这里</p>
      </div>
      <div v-else class="history-list">
        <div v-for="(record, idx) in records" :key="idx" class="history-card" @click="goDetail(record)">
          <div class="hc-img">
            <img v-if="record.image" :src="record.image" />
            <div v-else class="hc-placeholder">{{ record.title?.[0] || '?' }}</div>
          </div>
          <div class="hc-info">
            <p class="hc-title">{{ record.title }}</p>
            <p class="hc-desc" v-if="record.desc">{{ record.desc }}</p>
            <span class="hc-time">{{ formatTime(record.viewTime) }}</span>
          </div>
          <button class="hc-remove" @click.stop="removeRecord(idx)" title="删除">✕</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 24px 0 60px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
.page-header h2 { flex: 1; font-size: 20px; font-weight: 700; margin: 0; }
.back-btn { background: none; border: none; font-size: 15px; color: var(--ich-primary, #8B2020); cursor: pointer; }
.clear-btn { background: none; border: 1px solid #ddd; border-radius: 16px; padding: 4px 16px; font-size: 13px; color: #999; cursor: pointer; }
.clear-btn:hover { border-color: #ff4d4f; color: #ff4d4f; }

.history-tabs {
  display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap;
  background: #fff; border-radius: 12px; padding: 12px 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.htab {
  display: flex; align-items: center; gap: 4px;
  padding: 8px 16px; border-radius: 20px; font-size: 13px;
  cursor: pointer; background: #f5f5f5; color: #666; transition: 0.2s; user-select: none;
}
.htab:hover { background: #eee; }
.htab.active { background: var(--ich-primary, #8B2020); color: #fff; }
.htab-icon { font-size: 15px; }

.empty-state { text-align: center; padding: 60px 0; color: #bbb; }
.empty-icon { font-size: 48px; margin-bottom: 8px; }
.empty-state p { margin: 4px 0; font-size: 14px; }
.empty-hint { font-size: 12px; color: #ccc; }

.history-list { display: flex; flex-direction: column; gap: 10px; }
.history-card {
  display: flex; align-items: center; gap: 14px; padding: 14px 18px;
  background: #fff; border-radius: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  cursor: pointer; transition: 0.2s;
}
.history-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.1); transform: translateY(-1px); }
.hc-img { width: 64px; height: 64px; border-radius: 8px; overflow: hidden; flex-shrink: 0; }
.hc-img img { width: 100%; height: 100%; object-fit: cover; }
.hc-placeholder {
  width: 100%; height: 100%; background: linear-gradient(135deg, #f5e6d3, #e8d5c0);
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; font-weight: 700; color: rgba(139,32,32,0.3);
}
.hc-info { flex: 1; min-width: 0; }
.hc-title { font-size: 14px; font-weight: 600; color: #333; margin: 0 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hc-desc { font-size: 12px; color: #999; margin: 0 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hc-time { font-size: 11px; color: #ccc; }
.hc-remove {
  width: 28px; height: 28px; border-radius: 50%; border: none;
  background: transparent; color: #ccc; font-size: 14px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: 0.2s; flex-shrink: 0;
}
.hc-remove:hover { background: #fff0f0; color: #ff4d4f; }
</style>
