<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getVideoExhibition } from '@/api/video'

const route = useRoute()
const router = useRouter()
const item = ref({})
const loading = ref(false)

const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : ''
const formatDuration = (s) => {
  if (!s) return ''
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${m}分${sec > 0 ? sec + '秒' : ''}`
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getVideoExhibition(route.params.id)
    item.value = res.data || {}
  } catch (e) {
    item.value = {}
  }
  loading.value = false
}

onMounted(loadDetail)
</script>

<template>
  <div class="detail-page">
    <div class="page-banner">
      <h1>视频展览详情</h1>
      <p>光影记录 · 非遗之美</p>
    </div>

    <div class="container" style="padding: 32px 20px 60px;">
      <button class="back-btn" @click="router.back()">← 返回列表</button>

      <div v-if="loading" class="loading-state"><p>加载中...</p></div>

      <div v-else-if="!item.id" class="empty-state"><p>未找到该视频</p></div>

      <div v-else class="detail-content">
        <!-- 视频播放器 -->
        <div class="video-player-wrapper">
          <video v-if="item.videoUrl" :src="item.videoUrl" :poster="item.coverImage" controls preload="metadata" class="video-player"></video>
          <div v-else class="video-placeholder" :style="item.coverImage ? { backgroundImage: `url(${item.coverImage})` } : {}">
            <span class="placeholder-text">暂无视频</span>
          </div>
        </div>

        <!-- 视频信息 -->
        <div class="video-info">
          <h2 class="video-title">{{ item.title }}</h2>
          <div class="video-meta">
            <span v-if="item.duration">⏱️ {{ formatDuration(item.duration) }}</span>
            <span v-if="item.viewCount">👁️ {{ item.viewCount }} 次播放</span>
            <span>🕐 {{ formatDate(item.createTime) }}</span>
          </div>
        </div>

        <!-- 视频描述 -->
        <div v-if="item.description" class="detail-section">
          <h3 class="section-title">视频简介</h3>
          <div class="section-body">{{ item.description }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.back-btn {
  display: inline-block;
  margin-bottom: 24px;
  padding: 8px 20px;
  border: 1px solid var(--ich-border);
  border-radius: var(--ich-radius-md);
  background: var(--ich-white);
  color: var(--ich-text-secondary);
  cursor: pointer;
  font-size: 14px;
  transition: var(--ich-transition);
}
.back-btn:hover { border-color: var(--ich-primary); color: var(--ich-primary); }

.video-player-wrapper {
  width: 100%;
  max-width: 900px;
  margin: 0 auto 32px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--ich-shadow-md);
  background: #000;
}

.video-player {
  width: 100%;
  display: block;
  max-height: 520px;
}

.video-placeholder {
  width: 100%;
  aspect-ratio: 16/9;
  background-size: cover;
  background-position: center;
  background-color: #1a1a2e;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-text {
  color: rgba(255,255,255,0.5);
  font-size: 20px;
}

.video-info {
  max-width: 900px;
  margin: 0 auto;
}

.video-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--ich-text-primary);
  margin: 0 0 12px;
  font-family: var(--ich-font-serif);
}

.video-meta {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: var(--ich-text-muted);
  flex-wrap: wrap;
}

.detail-section {
  max-width: 900px;
  margin: 32px auto 0;
  padding-top: 24px;
  border-top: 1px solid var(--ich-border);
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--ich-text-primary);
  margin: 0 0 16px;
  font-family: var(--ich-font-serif);
}

.section-body {
  font-size: 15px;
  color: var(--ich-text-secondary);
  line-height: 1.9;
  white-space: pre-wrap;
}
</style>
