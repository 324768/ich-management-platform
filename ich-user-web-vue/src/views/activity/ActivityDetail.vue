<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getActivity, registerActivity, getActivityComments, addActivityComment } from '@/api/content'
import { getUserInfo } from '@/utils/token'
import { addBrowseHistory } from '@/utils/history'

const route = useRoute()
const router = useRouter()
const item = ref({})
const loading = ref(false)
const previewImage = ref(null)

const detailImages = ref([])
const videos = ref([])
const timelineData = ref({})

// 评论
const comments = ref([])
const commentLoading = ref(false)
const commentText = ref('')
const commentPageNum = ref(1)
const commentTotal = ref(0)

const activityTypeText = (t) => {
  const map = { 1: '演出展示', 2: '研学体验', 3: '讲座论坛', 4: '展览展陈', 5: '其他' }
  return map[t] || '活动'
}

const statusText = (s) => {
  const map = { 0: '草稿', 1: '报名中', 2: '进行中', 3: '已结束', 4: '已取消' }
  return map[s] || ''
}

const statusClass = (s) => {
  const map = { 0: 'draft', 1: 'open', 2: 'ongoing', 3: 'ended', 4: 'cancelled' }
  return map[s] || ''
}

const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : ''

const isRegistrationFull = computed(() => {
  return item.value.maxParticipants > 0 && (item.value.currentParticipants || 0) >= item.value.maxParticipants
})

const canRegister = computed(() => {
  return item.value.status === 1 && !isRegistrationFull.value
})

const timelinePhases = [
  { key: 'apply', label: '报名阶段' },
  { key: 'prepare', label: '筹备阶段' },
  { key: 'ongoing', label: '进行阶段' },
  { key: 'end', label: '结束阶段' },
]

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getActivity(route.params.id)
    const data = res.data || {}
    // 解析图片和视频
    let imgs = data.detailImages
    if (typeof imgs === 'string') { try { imgs = JSON.parse(imgs) } catch { imgs = [] } }
    detailImages.value = Array.isArray(imgs) ? imgs : []
    let vids = data.videos
    if (typeof vids === 'string') { try { vids = JSON.parse(vids) } catch { vids = [] } }
    videos.value = Array.isArray(vids) ? vids : []
    // 解析时间线
    let tl = data.timelineData
    if (typeof tl === 'string') { try { tl = JSON.parse(tl) } catch { tl = {} } }
    timelineData.value = tl || {}
    item.value = data
    addBrowseHistory('activity', { targetId: data.id, title: data.title, image: data.coverImage, desc: data.location || activityTypeText(data.activityType) })
  } catch (e) {
    item.value = {}
  }
  loading.value = false
}

const handleRegister = async () => {
  const user = getUserInfo()
  if (!user?.id) {
    alert('请先登录')
    router.push('/login')
    return
  }
  try {
    await registerActivity({ activityId: item.value.id, userId: user.id, userName: user.nickname || user.username, userPhone: user.phone || '' })
    alert('报名成功！')
    loadDetail()
  } catch (e) {
    alert('报名失败: ' + (e.response?.data?.message || e.message))
  }
}

const loadComments = async () => {
  commentLoading.value = true
  try {
    const res = await getActivityComments(route.params.id, { pageNum: commentPageNum.value, pageSize: 20 })
    comments.value = res.data?.list || res.data?.records || []
    commentTotal.value = res.data?.total || 0
  } catch { comments.value = [] }
  commentLoading.value = false
}

const submitComment = async () => {
  if (!commentText.value.trim()) return
  const user = getUserInfo()
  if (!user?.id) { alert('请先登录'); router.push('/login'); return }
  try {
    await addActivityComment({
      activityId: item.value.id,
      userId: user.id,
      userName: user.nickname || user.username,
      content: commentText.value.trim()
    })
    commentText.value = ''
    loadComments()
  } catch (e) {
    alert('评论失败: ' + (e.response?.data?.message || e.message))
  }
}

const hasTimeline = computed(() => {
  return timelinePhases.some(p => timelineData.value[p.key]?.time)
})

let commentTimer = null

onMounted(() => {
  loadDetail()
  loadComments()
  commentTimer = setInterval(loadComments, 15000)
})

onUnmounted(() => {
  if (commentTimer) clearInterval(commentTimer)
})
</script>

<template>
  <div class="detail-page">
    <div class="page-banner">
      <h1>活动详情</h1>
      <p>参与非遗活动 · 感受文化魅力</p>
    </div>

    <div class="container" style="padding: 32px 20px 60px;">
      <button class="back-btn" @click="router.back()">← 返回列表</button>

      <div v-if="loading" class="loading-state"><p>加载中...</p></div>

      <div v-else-if="!item.id" class="empty-state"><p>未找到该活动</p></div>

      <div v-else class="detail-content">
        <!-- 头部 -->
        <div class="detail-header">
          <div class="detail-cover" :style="item.coverImage ? { backgroundImage: `url(${item.coverImage})` } : { background: '#2E4057' }">
            <span v-if="!item.coverImage" class="cover-text">{{ item.name?.substring(0, 2) }}</span>
          </div>
          <div class="detail-meta">
            <h2 class="detail-title">{{ item.name }}</h2>
            <div class="detail-tags">
              <span class="tag">{{ activityTypeText(item.activityType) }}</span>
              <span :class="['tag', 'tag-status', statusClass(item.status)]">{{ statusText(item.status) }}</span>
            </div>
            <p v-if="item.description" class="detail-desc">{{ item.description }}</p>
            <div class="detail-info-row">
              <span v-if="item.location">📍 {{ item.location }}</span>
              <span v-if="item.organizer">🏛️ {{ item.organizer }}</span>
            </div>
          </div>
        </div>

        <!-- 关键信息 -->
        <div class="info-grid">
          <div class="info-card">
            <span class="info-label">活动时间</span>
            <span class="info-value">{{ formatDate(item.startTime) }}</span>
            <span class="info-sub" v-if="item.endTime">至 {{ formatDate(item.endTime) }}</span>
          </div>
          <div class="info-card">
            <span class="info-label">报名人数</span>
            <span class="info-value" :class="{ full: isRegistrationFull }">{{ item.currentParticipants || 0 }} / {{ item.maxParticipants || '不限' }}</span>
            <span v-if="isRegistrationFull" class="info-sub full">已满</span>
          </div>
          <div class="info-card" v-if="item.registrationDeadline">
            <span class="info-label">报名截止</span>
            <span class="info-value">{{ formatDate(item.registrationDeadline) }}</span>
          </div>
          <div class="info-card" v-if="item.contactPerson">
            <span class="info-label">联系人</span>
            <span class="info-value">{{ item.contactPerson }}</span>
            <span class="info-sub" v-if="item.contactPhone">{{ item.contactPhone }}</span>
          </div>
        </div>

        <!-- 报名按钮 -->
        <div v-if="item.status === 1" class="register-bar">
          <button :class="['register-btn', { disabled: !canRegister }]" :disabled="!canRegister" @click="handleRegister">
            {{ isRegistrationFull ? '名额已满' : '立即报名' }}
          </button>
        </div>

        <!-- 活动内容 -->
        <div v-if="item.content" class="detail-section">
          <h3 class="section-title">活动内容</h3>
          <div class="section-body">{{ item.content }}</div>
        </div>

        <!-- 活动时间线 -->
        <div v-if="hasTimeline" class="detail-section">
          <h3 class="section-title">活动时间线</h3>
          <div class="timeline">
            <div v-for="phase in timelinePhases" :key="phase.key" class="tl-item" v-show="timelineData[phase.key]?.time">
              <div class="tl-dot"></div>
              <div class="tl-content">
                <div class="tl-label">{{ phase.label }}</div>
                <div class="tl-time">{{ formatDate(timelineData[phase.key]?.time) }}</div>
                <div v-if="timelineData[phase.key]?.description" class="tl-desc">{{ timelineData[phase.key].description }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 详情图片 -->
        <div v-if="detailImages.length" class="detail-section">
          <h3 class="section-title">活动图片</h3>
          <div class="image-gallery">
            <img v-for="(img, idx) in detailImages" :key="idx" :src="img" class="gallery-img" @click="previewImage = img" />
          </div>
        </div>

        <!-- 介绍视频 -->
        <div v-if="videos.length" class="detail-section">
          <h3 class="section-title">活动视频</h3>
          <div class="video-gallery">
            <video v-for="(v, idx) in videos" :key="idx" :src="v" controls preload="metadata" class="gallery-video" />
          </div>
        </div>

        <!-- 评论区 -->
        <div class="detail-section">
          <h3 class="section-title">用户评论 ({{ commentTotal }})</h3>
          <div class="comment-input-area">
            <textarea v-model="commentText" placeholder="分享你的想法..." rows="3"></textarea>
            <button class="comment-submit-btn" @click="submitComment" :disabled="!commentText.trim()">发表评论</button>
          </div>
          <div v-if="commentLoading" class="loading-state" style="padding: 24px 0;"><p>加载中...</p></div>
          <div v-else-if="comments.length === 0" class="comment-empty">暂无评论，快来抢沙发！</div>
          <div v-else class="comment-list">
            <div v-for="c in comments" :key="c.id" class="comment-item">
              <div class="comment-avatar">{{ (c.userName || '?')[0] }}</div>
              <div class="comment-body">
                <div class="comment-header">
                  <span class="comment-user">{{ c.userName || '匿名' }}</span>
                  <span class="comment-time">{{ formatDate(c.createTime) }}</span>
                </div>
                <div class="comment-text">{{ c.content }}</div>
              </div>
            </div>
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

.detail-header {
  display: flex;
  gap: 28px;
  margin-bottom: 32px;
}

.detail-cover {
  width: 240px;
  height: 180px;
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
  font-size: 42px;
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

.detail-tags { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }

.tag-status.open { background: #e8f5e9 !important; color: #2e7d32 !important; }
.tag-status.ongoing { background: #fff3e0 !important; color: #e65100 !important; }
.tag-status.ended { background: #f5f5f5 !important; color: #757575 !important; }
.tag-status.cancelled { background: #ffebee !important; color: #c62828 !important; }
.tag-status.draft { background: #f5f5f5 !important; color: #9e9e9e !important; }

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

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.info-card {
  background: var(--ich-primary-bg);
  border-radius: var(--ich-radius-md);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: var(--ich-text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.info-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--ich-text-primary);
}
.info-value.full { color: #c62828; }

.info-sub {
  font-size: 13px;
  color: var(--ich-text-muted);
}
.info-sub.full { color: #c62828; }

.register-bar {
  text-align: center;
  margin-bottom: 24px;
}

.register-btn {
  padding: 14px 48px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: var(--ich-primary);
  border: none;
  border-radius: var(--ich-radius-md);
  cursor: pointer;
  transition: var(--ich-transition);
}
.register-btn:hover:not(.disabled) { opacity: 0.9; transform: translateY(-1px); }
.register-btn.disabled { background: #ccc; cursor: not-allowed; }

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

/* 时间线 */
.timeline { padding-left: 24px; position: relative; }

.tl-item {
  position: relative;
  padding-bottom: 24px;
  padding-left: 20px;
  border-left: 2px solid var(--ich-border);
}
.tl-item:last-child { border-left-color: transparent; }

.tl-dot {
  position: absolute;
  left: -7px;
  top: 4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--ich-primary);
  border: 2px solid var(--ich-white);
  box-shadow: 0 0 0 2px var(--ich-border);
}

.tl-label { font-weight: 600; color: var(--ich-text-primary); font-size: 15px; }
.tl-time { font-size: 13px; color: var(--ich-text-muted); margin: 4px 0; }
.tl-desc { font-size: 14px; color: var(--ich-text-secondary); line-height: 1.6; }

/* 图片/视频 */
.image-gallery { display: flex; gap: 12px; flex-wrap: wrap; }

.gallery-img {
  width: 180px;
  height: 180px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid var(--ich-border);
}
.gallery-img:hover { transform: scale(1.03); box-shadow: var(--ich-shadow-md); }

.video-gallery { display: flex; gap: 16px; flex-wrap: wrap; }

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
.preview-img { max-width: 90vw; max-height: 90vh; border-radius: 8px; }

/* 评论区 */
.comment-input-area {
  margin-bottom: 24px;
}

.comment-input-area textarea {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--ich-border);
  border-radius: var(--ich-radius-md);
  font-size: 14px;
  resize: vertical;
  font-family: inherit;
  transition: var(--ich-transition);
  box-sizing: border-box;
}
.comment-input-area textarea:focus {
  outline: none;
  border-color: var(--ich-primary);
}

.comment-submit-btn {
  margin-top: 8px;
  padding: 8px 24px;
  background: var(--ich-primary);
  color: #fff;
  border: none;
  border-radius: var(--ich-radius-md);
  cursor: pointer;
  font-size: 14px;
  transition: var(--ich-transition);
}
.comment-submit-btn:hover:not(:disabled) { opacity: 0.9; }
.comment-submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.comment-empty {
  text-align: center;
  color: var(--ich-text-muted);
  padding: 32px 0;
  font-size: 14px;
}

.comment-list { display: flex; flex-direction: column; gap: 0; }

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--ich-border);
}
.comment-item:last-child { border-bottom: none; }

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--ich-primary-bg);
  color: var(--ich-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.comment-body { flex: 1; min-width: 0; }

.comment-header { display: flex; align-items: center; gap: 8px; }

.comment-user { font-weight: 600; font-size: 13px; color: var(--ich-text-primary); }
.comment-time { font-size: 12px; color: var(--ich-text-muted); }
.comment-text { font-size: 14px; color: var(--ich-text-secondary); line-height: 1.6; margin-top: 4px; }

@media (max-width: 768px) {
  .detail-header { flex-direction: column; }
  .detail-cover { width: 100%; height: 200px; }
  .info-grid { grid-template-columns: 1fr 1fr; }
}
</style>
