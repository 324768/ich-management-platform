<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/utils/token'
import { getActivityList } from '@/api/content'

const router = useRouter()
const user = getUserInfo()
const records = ref([])
const loading = ref(false)

const loadRecords = async () => {
  if (!user?.id) return
  loading.value = true
  try {
    const res = await getActivityList({ pageNum: 1, pageSize: 50 })
    records.value = res.data?.list || []
  } catch { records.value = [] }
  loading.value = false
}

const formatTime = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

const statusText = (s) => {
  const map = { 0: '未开始', 1: '进行中', 2: '已结束' }
  return map[s] || '未知'
}

onMounted(loadRecords)
</script>

<template>
  <div class="page-container">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="router.push('/user')">← 返回</button>
        <h2>活动记录</h2>
      </div>

      <div v-if="loading" class="empty-state">加载中...</div>
      <div v-else-if="records.length === 0" class="empty-state">暂无活动记录</div>
      <div v-else class="record-list">
        <div v-for="item in records" :key="item.id" class="record-card" @click="router.push('/activity/' + item.id)">
          <div class="rc-img">
            <img v-if="item.coverImage" :src="item.coverImage" />
            <div v-else class="rc-placeholder">活动</div>
          </div>
          <div class="rc-info">
            <p class="rc-title">{{ item.title }}</p>
            <p class="rc-desc">{{ item.location || '线上活动' }}</p>
            <div class="rc-meta">
              <span class="rc-time">{{ formatTime(item.startTime) }}</span>
              <span :class="['rc-status', 'st-' + item.status]">{{ statusText(item.status) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 24px 0 60px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.page-header h2 { flex: 1; font-size: 20px; font-weight: 700; margin: 0; }
.back-btn { background: none; border: none; font-size: 15px; color: var(--ich-primary, #8B2020); cursor: pointer; }

.empty-state { text-align: center; padding: 80px 0; color: #bbb; font-size: 15px; }

.record-list { display: flex; flex-direction: column; gap: 12px; }
.record-card {
  display: flex; gap: 16px; padding: 16px 20px; background: #fff;
  border-radius: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); cursor: pointer; transition: 0.2s;
}
.record-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.1); transform: translateY(-2px); }
.rc-img { width: 100px; height: 80px; border-radius: 8px; overflow: hidden; flex-shrink: 0; }
.rc-img img { width: 100%; height: 100%; object-fit: cover; }
.rc-placeholder { width: 100%; height: 100%; background: #f5f5f5; display: flex; align-items: center; justify-content: center; color: #ccc; font-size: 13px; }
.rc-info { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }
.rc-title { font-size: 15px; font-weight: 600; color: #333; margin: 0 0 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rc-desc { font-size: 13px; color: #999; margin: 0 0 8px; }
.rc-meta { display: flex; align-items: center; gap: 12px; }
.rc-time { font-size: 12px; color: #bbb; }
.rc-status { font-size: 12px; padding: 2px 10px; border-radius: 10px; }
.st-0 { background: #fff7e6; color: #fa8c16; }
.st-1 { background: #e6f7ff; color: #1890ff; }
.st-2 { background: #f5f5f5; color: #999; }
</style>
