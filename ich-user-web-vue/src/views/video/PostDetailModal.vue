<script setup>
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
import { getPost, getPostComments, addPostComment, likePost, unlikePost, favoritePost, unfavoritePost, hasLikedPost, hasFavoritedPost } from '@/api/post'
import { getUserInfo } from '@/utils/token'
import { addBrowseHistory } from '@/utils/history'

const props = defineProps({ postId: { type: Number, default: null } })
const emit = defineEmits(['close', 'liked'])

const post = ref(null)
const loading = ref(false)
const comments = ref([])
const commentTotal = ref(0)
const commentText = ref('')
const commentSending = ref(false)
const currentImageIndex = ref(0)
const liked = ref(false)
const favorited = ref(false)

const imageList = computed(() => {
  if (!post.value) return []
  try {
    const imgs = JSON.parse(post.value.images || '[]')
    return Array.isArray(imgs) ? imgs : []
  } catch { return [] }
})

const tagList = computed(() => {
  if (!post.value?.tags) return []
  return post.value.tags.split(',').filter(t => t.trim())
})

const loadPost = async () => {
  if (!props.postId) return
  loading.value = true
  try {
    const res = await getPost(props.postId)
    post.value = res.data
    if (post.value) {
      const coverImg = (() => { try { const imgs = JSON.parse(post.value.images || '[]'); return Array.isArray(imgs) && imgs.length ? imgs[0] : '' } catch { return '' } })()
      addBrowseHistory('video', { targetId: post.value.id, title: post.value.title, image: post.value.coverImage || coverImg, desc: post.value.userName || '' })
    }
  } catch { /* ignore */ }
  loading.value = false
}

const loadComments = async () => {
  if (!props.postId) return
  try {
    const res = await getPostComments(props.postId, { pageNum: 1, pageSize: 100 })
    comments.value = res.data?.list || []
    commentTotal.value = res.data?.total || 0
  } catch { comments.value = [] }
}

const checkUserStatus = async () => {
  const user = getUserInfo()
  if (!user?.id || !props.postId) return
  try {
    const [likeRes, favRes] = await Promise.all([
      hasLikedPost(props.postId, user.id),
      hasFavoritedPost(props.postId, user.id)
    ])
    liked.value = !!likeRes.data
    favorited.value = !!favRes.data
  } catch { /* ignore */ }
}

const handleLike = async () => {
  const user = getUserInfo()
  if (!user?.id) { alert('请先登录'); return }
  try {
    if (liked.value) {
      await unlikePost(props.postId, user.id)
      liked.value = false
    } else {
      await likePost(props.postId, user.id)
      liked.value = true
    }
    await loadPost()
    emit('liked', props.postId)
  } catch { /* ignore */ }
}

const handleFavorite = async () => {
  const user = getUserInfo()
  if (!user?.id) { alert('请先登录'); return }
  try {
    if (favorited.value) {
      await unfavoritePost(props.postId, user.id)
      favorited.value = false
    } else {
      await favoritePost(props.postId, user.id)
      favorited.value = true
    }
    await loadPost()
  } catch { /* ignore */ }
}

const submitComment = async () => {
  if (!commentText.value.trim()) return
  const user = getUserInfo()
  if (!user?.id) { alert('请先登录'); return }
  commentSending.value = true
  try {
    await addPostComment({
      postId: props.postId,
      userId: user.id,
      userName: user.nickname || user.username,
      userAvatar: user.avatar || '',
      content: commentText.value.trim()
    })
    commentText.value = ''
    await loadComments()
    await loadPost()
  } catch (e) {
    alert('评论失败: ' + (e.response?.data?.message || e.message))
  }
  commentSending.value = false
}

const prevImage = () => { if (currentImageIndex.value > 0) currentImageIndex.value-- }
const nextImage = () => { if (currentImageIndex.value < imageList.value.length - 1) currentImageIndex.value++ }

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const diff = Date.now() - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 2592000000) return Math.floor(diff / 86400000) + '天前'
  return d.toLocaleDateString('zh-CN')
}

const handleKeydown = (e) => {
  if (e.key === 'Escape') emit('close')
  if (e.key === 'ArrowLeft') prevImage()
  if (e.key === 'ArrowRight') nextImage()
}

let commentTimer = null

watch(() => props.postId, (val) => {
  if (val) {
    currentImageIndex.value = 0
    liked.value = false
    favorited.value = false
    loadPost()
    loadComments()
    checkUserStatus()
  }
}, { immediate: true })

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  document.body.style.overflow = 'hidden'
  commentTimer = setInterval(loadComments, 15000)
})
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
  if (commentTimer) clearInterval(commentTimer)
})
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-container" v-if="post">
      <button class="close-btn" @click="emit('close')">✕</button>

      <!-- 左侧：图片/视频 -->
      <div class="modal-left">
        <div class="carousel" v-if="imageList.length > 0">
          <img :src="imageList[currentImageIndex]" class="carousel-img" />
          <button v-if="currentImageIndex > 0" class="arrow arrow-left" @click="prevImage">‹</button>
          <button v-if="currentImageIndex < imageList.length - 1" class="arrow arrow-right" @click="nextImage">›</button>
          <div class="dots" v-if="imageList.length > 1">
            <span v-for="(_, i) in imageList" :key="i"
                  :class="['dot', { active: i === currentImageIndex }]"
                  @click="currentImageIndex = i"></span>
          </div>
        </div>
        <div class="carousel" v-else-if="post.videoUrl">
          <video :src="post.videoUrl" controls class="carousel-video"></video>
        </div>
        <div class="carousel no-media" v-else>
          <span>暂无媒体</span>
        </div>
      </div>

      <!-- 右侧：信息+评论 -->
      <div class="modal-right">
        <div class="right-top">
          <!-- 作者 -->
          <div class="author-bar">
            <div class="author-left">
              <div class="avatar" v-if="post.userAvatar" :style="{ backgroundImage: `url(${post.userAvatar})` }"></div>
              <div class="avatar avatar-letter" v-else>{{ (post.userName || '?')[0] }}</div>
              <span class="author-name">{{ post.userName }}</span>
            </div>
          </div>
          <!-- 内容 -->
          <div class="post-body">
            <h2 class="post-title">{{ post.title }}</h2>
            <div class="post-tags" v-if="tagList.length">
              <span v-for="tag in tagList" :key="tag" class="tag">#{{ tag }}</span>
            </div>
            <p class="post-text" v-if="post.content">{{ post.content }}</p>
            <div class="post-date">{{ formatTime(post.createTime) }}</div>
          </div>
          <!-- 评论 -->
          <div class="comments-section">
            <div class="comments-header">共 {{ commentTotal }} 条评论</div>
            <div class="comments-list">
              <div v-for="c in comments" :key="c.id" class="comment-item">
                <div class="c-avatar" v-if="c.userAvatar" :style="{ backgroundImage: `url(${c.userAvatar})` }"></div>
                <div class="c-avatar c-avatar-letter" v-else>{{ (c.userName || '?')[0] }}</div>
                <div class="c-body">
                  <span class="c-name">{{ c.userName }}</span>
                  <p class="c-text">{{ c.content }}</p>
                  <div class="c-meta">
                    <span>{{ formatTime(c.createTime) }}</span>
                    <span v-if="c.location" class="c-loc">{{ c.location }}</span>
                  </div>
                </div>
              </div>
              <div v-if="comments.length === 0" class="no-comments">暂无评论，快来抢沙发~</div>
            </div>
          </div>
        </div>
        <!-- 底部栏 -->
        <div class="bottom-bar">
          <div class="input-wrap">
            <input v-model="commentText" placeholder="说点什么..." maxlength="500"
                   @keyup.enter="submitComment" :disabled="commentSending" />
          </div>
          <div class="actions">
            <span :class="['act', { liked }]" @click="handleLike">♥ {{ post.likeCount || 0 }}</span>
            <span :class="['act', { favorited }]" @click="handleFavorite">☆ {{ post.favoriteCount || 0 }}</span>
            <span class="act">💬 {{ commentTotal }}</span>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="modal-loading">加载中...</div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.65);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999; padding: 30px;
}
.modal-container {
  display: flex; background: #fff; border-radius: 12px; overflow: hidden;
  max-width: 960px; width: 100%; max-height: 88vh; position: relative;
  box-shadow: 0 20px 60px rgba(0,0,0,0.35);
}
.close-btn {
  position: absolute; top: 12px; right: 12px; z-index: 10;
  width: 32px; height: 32px; border-radius: 50%; border: none;
  background: rgba(0,0,0,0.4); color: #fff; font-size: 16px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: background 0.2s;
}
.close-btn:hover { background: rgba(0,0,0,0.7); }

/* 左侧 */
.modal-left {
  flex: 1.1; min-width: 0; background: #000;
  display: flex; align-items: center; justify-content: center;
}
.carousel {
  position: relative; width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
}
.carousel-img {
  max-width: 100%; max-height: 88vh; object-fit: contain;
  user-select: none;
}
.carousel-video {
  width: 100%; max-height: 88vh; object-fit: contain; background: #000;
}
.no-media { color: #888; font-size: 16px; }
.arrow {
  position: absolute; top: 50%; transform: translateY(-50%);
  width: 36px; height: 36px; border-radius: 50%; border: none;
  background: rgba(255,255,255,0.85); font-size: 22px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15); transition: 0.2s;
  color: #333;
}
.arrow:hover { background: #fff; box-shadow: 0 2px 12px rgba(0,0,0,0.25); }
.arrow-left { left: 12px; }
.arrow-right { right: 12px; }
.dots {
  position: absolute; bottom: 16px; left: 50%; transform: translateX(-50%);
  display: flex; gap: 6px;
}
.dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: rgba(255,255,255,0.5); cursor: pointer; transition: 0.2s;
}
.dot.active { background: #fff; transform: scale(1.2); }

/* 右侧 */
.modal-right {
  width: 380px; flex-shrink: 0; display: flex; flex-direction: column;
  border-left: 1px solid #f0f0f0;
}
.right-top { flex: 1; overflow-y: auto; }
.author-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid #f5f5f5;
}
.author-left { display: flex; align-items: center; gap: 10px; }
.avatar {
  width: 40px; height: 40px; border-radius: 50%; flex-shrink: 0;
  background-size: cover; background-position: center;
}
.avatar-letter {
  background: linear-gradient(135deg, var(--ich-primary, #8B2020), #6B3A6B);
  color: #fff; font-size: 16px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.author-name { font-size: 15px; font-weight: 600; color: #333; }

.post-body { padding: 16px 20px; border-bottom: 1px solid #f5f5f5; }
.post-title { font-size: 17px; font-weight: 700; color: #222; margin: 0 0 10px; line-height: 1.4; }
.post-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.tag {
  font-size: 13px; color: #3378b5; cursor: pointer;
}
.tag:hover { text-decoration: underline; }
.post-text { font-size: 14px; color: #555; line-height: 1.7; margin: 0 0 10px; white-space: pre-wrap; }
.post-date { font-size: 12px; color: #bbb; }

/* 评论 */
.comments-section { padding: 16px 20px; }
.comments-header { font-size: 14px; color: #999; margin-bottom: 14px; }
.comments-list { display: flex; flex-direction: column; gap: 16px; }
.comment-item { display: flex; gap: 10px; }
.c-avatar {
  width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0;
  background-size: cover; background-position: center;
}
.c-avatar-letter {
  background: linear-gradient(135deg, #8B2020, #6B3A6B);
  color: #fff; font-size: 13px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.c-body { flex: 1; min-width: 0; }
.c-name { font-size: 13px; font-weight: 600; color: #666; }
.c-text { font-size: 14px; color: #333; margin: 4px 0; line-height: 1.5; }
.c-meta { display: flex; gap: 12px; font-size: 12px; color: #bbb; }
.no-comments { text-align: center; color: #ccc; font-size: 14px; padding: 30px 0; }

/* 底部栏 */
.bottom-bar {
  border-top: 1px solid #f0f0f0; padding: 12px 20px;
  display: flex; align-items: center; gap: 12px; flex-shrink: 0;
}
.input-wrap {
  flex: 1; min-width: 0;
}
.input-wrap input {
  width: 100%; padding: 8px 14px; border: 1px solid #e8e8e8;
  border-radius: 20px; font-size: 13px; outline: none;
  background: #f7f7f7; box-sizing: border-box; transition: 0.2s;
}
.input-wrap input:focus { border-color: var(--ich-primary, #8B2020); background: #fff; }
.actions { display: flex; gap: 14px; flex-shrink: 0; }
.act {
  font-size: 13px; color: #666; cursor: pointer; white-space: nowrap;
  transition: 0.2s; user-select: none;
}
.act:hover { color: #333; }
.act.liked { color: #ff2442; }
.act.favorited { color: #ffaa00; }

.modal-loading { color: #fff; font-size: 16px; }

@media (max-width: 768px) {
  .modal-container { flex-direction: column; max-height: 95vh; }
  .modal-left { max-height: 45vh; }
  .modal-right { width: 100%; }
}
</style>
