<script setup>
import { ref, onMounted } from 'vue'
import { getNotificationList } from '@/api/notification'

const items = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const readSet = ref(new Set())

const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : ''

const messageTypeText = (type) => {
  const map = { 1: '系统公告', 2: '活动通知', 3: '订单通知' }
  return map[type] || '通知'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getNotificationList({ pageNum: pageNum.value, pageSize: pageSize.value })
    const pageData = res.data || {}
    items.value = pageData.list || pageData.records || []
    total.value = pageData.total || 0
  } catch (e) { items.value = [] }
  loading.value = false
}

const markAsRead = (item) => { readSet.value.add(item.id) }
const changePage = (p) => { pageNum.value = p; loadData() }
const totalPages = () => Math.ceil(total.value / pageSize.value)

onMounted(() => { loadData() })
</script>

<template>
  <div class="notification-page">
    <div class="page-banner">
      <h1>系统通知</h1>
      <p>了解平台最新动态</p>
    </div>

    <div class="container" style="padding: 28px 20px 40px;">
      <div v-if="loading" class="loading-state"><p>加载中...</p></div>
      <div v-else class="notification-list">
        <div v-for="item in items" :key="item.id"
             :class="['notification-item card', { unread: !readSet.has(item.id) }]"
             @click="markAsRead(item)">
          <div class="notif-dot" v-if="!readSet.has(item.id)"></div>
          <div class="notif-body">
            <span class="notif-type tag">{{ messageTypeText(item.messageType) }}</span>
            <h3 class="notif-title">{{ item.title }}</h3>
            <p class="notif-content">{{ item.content }}</p>
            <span class="notif-time">{{ formatDate(item.publishTime || item.createTime) }}</span>
          </div>
        </div>
      </div>

      <div v-if="!loading && items.length === 0" class="empty-state"><p>暂无系统通知</p></div>

      <div v-if="totalPages() > 1" class="pagination">
        <button :disabled="pageNum <= 1" @click="changePage(pageNum - 1)">上一页</button>
        <span class="page-info">{{ pageNum }} / {{ totalPages() }}</span>
        <button :disabled="pageNum >= totalPages()" @click="changePage(pageNum + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.notification-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notification-item {
  padding: 20px 24px;
  cursor: pointer;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  transition: var(--ich-transition);
}

.notification-item.unread {
  border-left: 3px solid var(--ich-primary);
  background: var(--ich-primary-bg);
}

.notif-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ich-primary);
  margin-top: 8px;
  flex-shrink: 0;
}

.notif-type {
  margin-bottom: 6px;
}

.notif-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ich-text-primary);
  margin-bottom: 8px;
}

.notif-content {
  font-size: 14px;
  color: var(--ich-text-secondary);
  line-height: 1.7;
  margin-bottom: 8px;
}

.notif-time {
  font-size: 12px;
  color: var(--ich-text-muted);
}
</style>
