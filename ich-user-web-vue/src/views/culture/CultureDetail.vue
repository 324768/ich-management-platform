<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getItem } from '@/api/content'
import { addBrowseHistory } from '@/utils/history'

const route = useRoute()
const router = useRouter()
const item = ref({})
const loading = ref(false)

const levelText = (l) => {
  const map = { 1: '国家级', 2: '省级', 3: '市级', 4: '县级', 5: '区级' }
  return map[l] || (l ? `${l}级` : '')
}

const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : ''

const detailImages = ref([])
const videos = ref([])

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getItem(route.params.id)
    item.value = res.data || {}
    // 解析图片和视频
    let imgs = item.value.detailImages
    if (typeof imgs === 'string') { try { imgs = JSON.parse(imgs) } catch { imgs = [] } }
    detailImages.value = Array.isArray(imgs) ? imgs : []
    let vids = item.value.videos
    if (typeof vids === 'string') { try { vids = JSON.parse(vids) } catch { vids = [] } }
    videos.value = Array.isArray(vids) ? vids : []
    addBrowseHistory('culture', { targetId: item.value.id, title: item.value.name, image: item.value.coverImage, desc: levelText(item.value.level) })
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
      <h1>非遗项目详情</h1>
      <p>了解非物质文化遗产的传承故事</p>
    </div>

    <div class="container" style="padding: 32px 20px 60px;">
      <button class="back-btn" @click="router.back()">← 返回列表</button>

      <div v-if="loading" class="loading-state"><p>加载中...</p></div>

      <div v-else-if="!item.id" class="empty-state"><p>未找到该项目</p></div>

      <div v-else class="detail-content">
        <!-- 头部 -->
        <div class="detail-header">
          <div class="detail-cover" :style="item.coverImage ? { backgroundImage: `url(${item.coverImage})` } : { background: '#5D4037' }">
            <span v-if="!item.coverImage" class="cover-text">{{ item.name?.substring(0, 2) }}</span>
          </div>
          <div class="detail-meta">
            <h2 class="detail-title">{{ item.name }}</h2>
            <div class="detail-tags">
              <span v-if="item.level" class="tag tag-level">{{ levelText(item.level) }}</span>
              <span v-if="item.categoryName" class="tag">{{ item.categoryName }}</span>
            </div>
            <p v-if="item.description" class="detail-desc">{{ item.description }}</p>
            <div class="detail-info-row">
              <span v-if="item.regionName">📍 {{ item.regionName }}</span>
              <span v-if="item.declarationUnit">🏛️ {{ item.declarationUnit }}</span>
              <span v-if="item.createTime">🕐 {{ formatDate(item.createTime) }}</span>
            </div>
          </div>
        </div>

        <!-- 详细内容 -->
        <div v-if="item.content" class="detail-section">
          <h3 class="section-title">详细介绍</h3>
          <div class="section-body">{{ item.content }}</div>
        </div>

        <!-- 详情图片 -->
        <div v-if="detailImages.length" class="detail-section">
          <h3 class="section-title">详情图片</h3>
          <div class="image-gallery">
            <img v-for="(img, idx) in detailImages" :key="idx" :src="img" class="gallery-img" @click="previewImage = img" />
          </div>
        </div>

        <!-- 介绍视频 -->
        <div v-if="videos.length" class="detail-section">
          <h3 class="section-title">介绍视频</h3>
          <div class="video-gallery">
            <video v-for="(v, idx) in videos" :key="idx" :src="v" controls preload="metadata" class="gallery-video" />
          </div>
        </div>
      </div>
    </div>

    <!-- 图片预览 -->
    <div v-if="previewImage" class="image-preview-overlay" @click="previewImage = null">
      <img :src="previewImage" class="preview-img" />
    </div>
  </div>
</template>

<script>
export default { data() { return { previewImage: null } } }
</script>

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
.back-btn:hover {
  border-color: var(--ich-primary);
  color: var(--ich-primary);
}

.detail-header {
  display: flex;
  gap: 28px;
  margin-bottom: 32px;
}

.detail-cover {
  width: 220px;
  height: 220px;
  border-radius: 12px;
  flex-shrink: 0;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--ich-shadow-sm);
}

.cover-text {
  color: rgba(255,255,255,0.5);
  font-size: 48px;
  font-family: var(--ich-font-serif);
  letter-spacing: 8px;
}

.detail-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.detail-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--ich-text-primary);
  margin: 0 0 12px;
  font-family: var(--ich-font-serif);
}

.detail-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.tag-level {
  background: var(--ich-primary) !important;
  color: #fff !important;
}

.detail-desc {
  font-size: 15px;
  color: var(--ich-text-secondary);
  line-height: 1.7;
  margin: 0 0 12px;
}

.detail-info-row {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: var(--ich-text-muted);
  flex-wrap: wrap;
}

.detail-section {
  margin-top: 32px;
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

.image-gallery {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.gallery-img {
  width: 180px;
  height: 180px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid var(--ich-border);
}
.gallery-img:hover {
  transform: scale(1.03);
  box-shadow: var(--ich-shadow-md);
}

.video-gallery {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.gallery-video {
  width: 360px;
  max-width: 100%;
  border-radius: 8px;
  border: 1px solid var(--ich-border);
}

.image-preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  cursor: pointer;
}

.preview-img {
  max-width: 90vw;
  max-height: 90vh;
  border-radius: 8px;
}

@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
  }
  .detail-cover {
    width: 100%;
    height: 200px;
  }
}
</style>
