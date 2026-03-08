<script setup>
import { ref, computed, onMounted } from 'vue'
import { getPostList, addPost } from '@/api/post'
import { uploadFile } from '@/api/upload'
import { getUserInfo } from '@/utils/token'
import PostDetailModal from './PostDetailModal.vue'

const posts = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const keyword = ref('')
const selectedType = ref(null)
const selectedPostId = ref(null)

const categories = [
  { label: '推荐', value: null },
  { label: '图文', value: 1 },
  { label: '视频', value: 2 },
]

const hasMore = computed(() => posts.value.length < total.value)

const loadPosts = async (append = false) => {
  loading.value = true
  try {
    const res = await getPostList({
      pageNum: pageNum.value,
      pageSize,
      keyword: keyword.value || undefined,
      type: selectedType.value || undefined
    })
    const list = res.data?.list || []
    if (append) {
      posts.value = [...posts.value, ...list]
    } else {
      posts.value = list
    }
    total.value = res.data?.total || 0
  } catch { if (!append) posts.value = [] }
  loading.value = false
}

const switchCategory = (val) => {
  selectedType.value = val
  pageNum.value = 1
  loadPosts()
}

const handleSearch = () => {
  pageNum.value = 1
  loadPosts()
}

const loadMore = () => {
  if (loading.value || !hasMore.value) return
  pageNum.value++
  loadPosts(true)
}

const openDetail = (post) => { selectedPostId.value = post.id }
const closeDetail = () => {
  selectedPostId.value = null
  pageNum.value = 1
  loadPosts()
}

const getCoverImage = (post) => {
  if (post.coverImage) return post.coverImage
  try {
    const imgs = JSON.parse(post.images || '[]')
    if (Array.isArray(imgs) && imgs.length > 0) return imgs[0]
  } catch { /* ignore */ }
  return ''
}

const isVideo = (post) => post.type === 2 || !!post.videoUrl

const formatCount = (n) => {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

// ========== 发布功能 ==========
const showPublish = ref(false)
const publishForm = ref({ title: '', content: '', tags: '', type: 1 })
const publishImages = ref([])
const publishVideo = ref(null)
const publishing = ref(false)

const openPublish = () => {
  const user = getUserInfo()
  if (!user?.id) { alert('请先登录'); return }
  publishForm.value = { title: '', content: '', tags: '', type: 1 }
  publishImages.value = []
  publishVideo.value = null
  showPublish.value = true
}

const handleImageSelect = async (e) => {
  const files = Array.from(e.target.files)
  if (!files.length) return
  for (const file of files) {
    try {
      const res = await uploadFile(file, 'post')
      if (res.data?.url) publishImages.value.push(res.data.url)
    } catch { alert('图片上传失败') }
  }
  e.target.value = ''
}

const removeImage = (i) => { publishImages.value.splice(i, 1) }

const handleVideoSelect = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  try {
    const res = await uploadFile(file, 'post')
    if (res.data?.url) publishVideo.value = res.data.url
  } catch { alert('视频上传失败') }
  e.target.value = ''
}

const submitPublish = async () => {
  if (!publishForm.value.title.trim()) { alert('请输入标题'); return }
  const user = getUserInfo()
  if (!user?.id) { alert('请先登录'); return }
  publishing.value = true
  try {
    const isVideoType = publishForm.value.type === 2
    await addPost({
      userId: user.id,
      userName: user.nickname || user.username,
      userAvatar: user.avatar || '',
      title: publishForm.value.title.trim(),
      content: publishForm.value.content.trim() || null,
      tags: publishForm.value.tags.trim() || null,
      type: publishForm.value.type,
      images: !isVideoType && publishImages.value.length ? JSON.stringify(publishImages.value) : null,
      coverImage: !isVideoType && publishImages.value.length ? publishImages.value[0] : null,
      videoUrl: isVideoType ? publishVideo.value : null,
    })
    showPublish.value = false
    pageNum.value = 1
    loadPosts()
    alert('发布成功！')
  } catch (e) {
    alert('发布失败: ' + (e.response?.data?.message || e.message))
  }
  publishing.value = false
}

onMounted(() => { loadPosts() })
</script>

<template>
  <div class="video-page">
    <!-- Banner -->
    <div class="page-banner">
      <h1>非遗视频与动态</h1>
      <p>影像记忆 · 活态传承</p>
    </div>

    <div class="explore-page">
      <!-- 吸顶区域：搜索 + 分类标签 -->
      <div class="sticky-bar">
        <div class="explore-header">
          <div class="search-box">
            <input v-model="keyword" placeholder="搜索非遗内容..." @keyup.enter="handleSearch" />
            <button class="search-btn" @click="handleSearch">🔍</button>
          </div>
          <button class="publish-btn" @click="openPublish">✚ 发布</button>
        </div>
        <div class="category-tabs">
          <span v-for="cat in categories" :key="cat.label"
                :class="['tab', { active: selectedType === cat.value }]"
                @click="switchCategory(cat.value)">{{ cat.label }}</span>
        </div>
      </div>

      <!-- 瀑布流 -->
      <div v-if="loading && posts.length === 0" class="loading-state"><p>加载中...</p></div>
      <div v-else class="waterfall">
        <div v-for="post in posts" :key="post.id" class="wf-card" @click="openDetail(post)">
          <div class="card-cover">
            <img v-if="getCoverImage(post)" :src="getCoverImage(post)" loading="lazy" />
            <div v-else class="cover-placeholder">
              <span>{{ post.title?.substring(0, 2) || '非遗' }}</span>
            </div>
            <span v-if="isVideo(post)" class="video-badge">▶</span>
          </div>
          <div class="card-body">
            <p class="card-title">{{ post.title }}</p>
            <div class="card-footer">
              <div class="card-author">
                <div class="mini-avatar" v-if="post.userAvatar" :style="{ backgroundImage: `url(${post.userAvatar})` }"></div>
                <div class="mini-avatar mini-avatar-letter" v-else>{{ (post.userName || '?')[0] }}</div>
                <span class="author-text">{{ post.userName }}</span>
              </div>
              <span class="like-count">♥ {{ formatCount(post.likeCount) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载更多 -->
      <div v-if="posts.length > 0" class="load-more">
        <button v-if="hasMore" class="more-btn" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
        <span v-else class="no-more">— 没有更多了 —</span>
      </div>

      <div v-if="!loading && posts.length === 0" class="empty-state"><p>暂无内容，快来发布第一条非遗笔记~</p></div>
    </div>

    <!-- 详情弹窗 -->
    <PostDetailModal v-if="selectedPostId" :postId="selectedPostId" @close="closeDetail" />

    <!-- 发布弹窗 -->
    <div v-if="showPublish" class="pub-overlay" @click.self="showPublish = false">
      <div class="pub-box">
        <div class="pub-header">
          <h3>发布非遗笔记</h3>
          <span class="pub-close" @click="showPublish = false">&times;</span>
        </div>
        <div class="pub-body">
          <div class="pub-field">
            <label>类型</label>
            <div class="type-switch">
              <span :class="['ts', { active: publishForm.type === 1 }]" @click="publishForm.type = 1">📷 图文</span>
              <span :class="['ts', { active: publishForm.type === 2 }]" @click="publishForm.type = 2">🎬 视频</span>
            </div>
          </div>
          <div class="pub-field">
            <label>标题 *</label>
            <input v-model="publishForm.title" placeholder="请输入标题" maxlength="100" />
          </div>
          <div class="pub-field">
            <label>内容</label>
            <textarea v-model="publishForm.content" placeholder="分享你的非遗故事..." rows="3" maxlength="2000"></textarea>
          </div>
          <div class="pub-field">
            <label>标签</label>
            <input v-model="publishForm.tags" placeholder="多个标签用逗号分隔，如：苏绣,刺绣" />
          </div>
          <!-- 图片上传 -->
          <div v-if="publishForm.type === 1" class="pub-field">
            <label>图片</label>
            <div class="img-preview-list">
              <div v-for="(url, i) in publishImages" :key="i" class="img-preview-item">
                <img :src="url" />
                <span class="img-remove" @click="removeImage(i)">✕</span>
              </div>
              <label class="img-add-btn">
                <span>+</span>
                <input type="file" accept="image/*" multiple @change="handleImageSelect" style="display:none" />
              </label>
            </div>
          </div>
          <!-- 视频上传 -->
          <div v-if="publishForm.type === 2" class="pub-field">
            <label>视频</label>
            <div v-if="publishVideo" class="video-preview">
              <video :src="publishVideo" controls style="max-width:100%;max-height:200px;border-radius:8px;"></video>
              <span class="img-remove" @click="publishVideo = null" style="top:4px;right:4px;">✕</span>
            </div>
            <label v-else class="img-add-btn" style="width:120px;height:80px;">
              <span>+ 上传视频</span>
              <input type="file" accept="video/*" @change="handleVideoSelect" style="display:none" />
            </label>
          </div>
        </div>
        <div class="pub-footer">
          <button class="pub-btn cancel" @click="showPublish = false">取消</button>
          <button class="pub-btn confirm" :disabled="publishing" @click="submitPublish">
            {{ publishing ? '发布中...' : '发布' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.video-page { padding-bottom: 40px; }

.explore-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 16px 20px;
}

/* 吸顶区域 */
.sticky-bar {
  position: sticky;
  top: var(--ich-header-height, 64px);
  z-index: 100;
  background: #F5F5F5;
  padding: 16px 0 0;
  margin: 0 auto;
  max-width: 1200px;
}

/* 搜索 + 发布 */
.explore-header {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}
.search-box {
  display: flex;
  align-items: center;
  width: 400px;
  max-width: 100%;
  border: 1px solid #e8e8e8;
  border-radius: 24px;
  overflow: hidden;
  background: #fff;
  transition: 0.2s;
}
.search-box:focus-within {
  border-color: var(--ich-primary, #8B2020);
  box-shadow: 0 0 0 3px rgba(139, 32, 32, 0.08);
}
.search-box input {
  flex: 1; border: none; outline: none;
  padding: 10px 16px; font-size: 14px; background: transparent;
}
.search-btn {
  border: none; background: none; padding: 10px 16px; cursor: pointer; font-size: 16px;
}
.publish-btn {
  padding: 9px 22px; border-radius: 24px; border: none;
  background: var(--ich-primary, #8B2020); color: #fff;
  font-size: 14px; font-weight: 600; cursor: pointer;
  white-space: nowrap; transition: 0.2s;
}
.publish-btn:hover { opacity: 0.85; }

/* 分类标签 */
.category-tabs {
  display: flex; justify-content: center; gap: 8px;
  padding-bottom: 14px; flex-wrap: wrap;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 0;
}
.tab {
  padding: 7px 20px; border-radius: 20px; font-size: 14px;
  color: #666; cursor: pointer; transition: 0.2s; user-select: none; background: #f5f5f5;
}
.tab:hover { color: #333; background: #eee; }
.tab.active {
  background: var(--ich-primary, #8B2020); color: #fff; font-weight: 600;
}

/* 瀑布流 */
.waterfall {
  columns: 5;
  column-gap: 16px;
  padding-top: 18px;
}
.wf-card {
  break-inside: avoid;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 1px 6px rgba(0,0,0,0.06);
  transition: transform 0.25s, box-shadow 0.25s;
  display: inline-block;
  width: 100%;
}
.wf-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(0,0,0,0.12);
}

.card-cover { position: relative; overflow: hidden; line-height: 0; }
.card-cover img {
  width: 100%; display: block;
}
.cover-placeholder {
  width: 100%; height: 200px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #f5e6d3, #e8d5c0);
  font-size: 28px; font-weight: 700;
  color: rgba(139, 32, 32, 0.3);
  font-family: var(--ich-font-serif, serif);
}
.video-badge {
  position: absolute; top: 10px; right: 10px;
  width: 28px; height: 28px; border-radius: 50%;
  background: rgba(0,0,0,0.55); color: #fff; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
}

.card-body { padding: 10px 12px 12px; }
.card-title {
  font-size: 14px; font-weight: 600; color: #222;
  line-height: 1.45; margin: 0 0 8px;
  display: -webkit-box; -webkit-line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden;
}
.card-footer {
  display: flex; align-items: center; justify-content: space-between;
}
.card-author {
  display: flex; align-items: center; gap: 6px; min-width: 0;
}
.mini-avatar {
  width: 20px; height: 20px; border-radius: 50%; flex-shrink: 0;
  background-size: cover; background-position: center;
}
.mini-avatar-letter {
  background: linear-gradient(135deg, #8B2020, #6B3A6B);
  color: #fff; font-size: 10px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.author-text {
  font-size: 12px; color: #999;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.like-count { font-size: 12px; color: #999; flex-shrink: 0; }

/* 加载更多 */
.load-more { text-align: center; padding: 30px 0; }
.more-btn {
  padding: 10px 40px; border: 1px solid #ddd; border-radius: 24px;
  background: #fff; color: #666; font-size: 14px; cursor: pointer; transition: 0.2s;
}
.more-btn:hover:not(:disabled) { border-color: var(--ich-primary, #8B2020); color: var(--ich-primary, #8B2020); }
.more-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.no-more { font-size: 13px; color: #ccc; }

.loading-state, .empty-state {
  text-align: center; padding: 60px 0; color: #999; font-size: 15px;
}

/* ===== 发布弹窗 ===== */
.pub-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 9999;
}
.pub-box {
  background: #fff; border-radius: 14px; width: 520px; max-width: 94vw;
  max-height: 90vh; overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
}
.pub-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 18px 24px; border-bottom: 1px solid #f0f0f0;
}
.pub-header h3 { font-size: 18px; font-weight: 600; color: #222; margin: 0; }
.pub-close { font-size: 24px; color: #999; cursor: pointer; line-height: 1; }
.pub-close:hover { color: #333; }
.pub-body { padding: 20px 24px; }
.pub-field { margin-bottom: 18px; }
.pub-field label {
  display: block; font-size: 13px; font-weight: 500; color: #666; margin-bottom: 6px;
}
.pub-field input, .pub-field textarea {
  width: 100%; padding: 10px 14px; border: 1px solid #e8e8e8;
  border-radius: 8px; font-size: 14px; color: #333; box-sizing: border-box;
  outline: none; transition: 0.2s; font-family: inherit;
}
.pub-field input:focus, .pub-field textarea:focus {
  border-color: var(--ich-primary, #8B2020);
}
.type-switch { display: flex; gap: 10px; }
.ts {
  padding: 6px 18px; border-radius: 18px; font-size: 13px;
  cursor: pointer; background: #f5f5f5; color: #666; transition: 0.2s; user-select: none;
}
.ts.active { background: var(--ich-primary, #8B2020); color: #fff; }
.img-preview-list {
  display: flex; flex-wrap: wrap; gap: 10px;
}
.img-preview-item {
  position: relative; width: 80px; height: 80px; border-radius: 8px; overflow: hidden;
}
.img-preview-item img {
  width: 100%; height: 100%; object-fit: cover;
}
.img-remove {
  position: absolute; top: 2px; right: 2px;
  width: 20px; height: 20px; border-radius: 50%;
  background: rgba(0,0,0,0.6); color: #fff; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
}
.img-add-btn {
  width: 80px; height: 80px; border: 2px dashed #ddd; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; color: #ccc; font-size: 14px; transition: 0.2s; text-align: center;
}
.img-add-btn:hover { border-color: var(--ich-primary, #8B2020); color: var(--ich-primary, #8B2020); }
.video-preview { position: relative; display: inline-block; }
.pub-footer {
  display: flex; justify-content: flex-end; gap: 12px;
  padding: 14px 24px; border-top: 1px solid #f0f0f0;
}
.pub-btn {
  padding: 8px 28px; border-radius: 8px; font-size: 14px; cursor: pointer; transition: 0.2s;
}
.pub-btn.cancel { background: #fff; border: 1px solid #ddd; color: #666; }
.pub-btn.cancel:hover { border-color: #999; }
.pub-btn.confirm { background: var(--ich-primary, #8B2020); border: none; color: #fff; }
.pub-btn.confirm:hover:not(:disabled) { opacity: 0.9; }
.pub-btn.confirm:disabled { opacity: 0.5; cursor: not-allowed; }

@media (max-width: 1200px) { .waterfall { columns: 4; } }
@media (max-width: 960px) { .waterfall { columns: 3; } }
@media (max-width: 640px) { .waterfall { columns: 2; column-gap: 10px; } .wf-card { margin-bottom: 10px; } }
</style>
