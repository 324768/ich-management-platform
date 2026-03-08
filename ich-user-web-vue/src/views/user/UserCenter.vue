<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo as getStoredUser, removeToken, setUserInfo } from '@/utils/token'
import { getUserInfo, updateUser } from '@/api/user'
import { uploadFile } from '@/api/upload'
import { getUserPosts, getUserLikedPosts, getUserFavoritedPosts } from '@/api/post'
import PostDetailModal from '@/views/video/PostDetailModal.vue'

const router = useRouter()
const user = ref(getStoredUser() || {})

const refreshUserInfo = async () => {
  if (user.value?.id) {
    try {
      const res = await getUserInfo(user.value.id)
      user.value = res.data
      localStorage.setItem('ich_user', JSON.stringify(res.data))
    } catch (e) { /* ignore */ }
  }
}

const handleAvatarUpload = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  try {
    const res = await uploadFile(file, 'avatar')
    if (res.data?.url) {
      await updateUser({ id: user.value.id, avatar: res.data.url })
      user.value.avatar = res.data.url
      localStorage.setItem('ich_user', JSON.stringify(user.value))
      alert('头像更新成功')
    }
  } catch (e) {
    alert('上传失败: ' + (e.message || '未知错误'))
  }
}

const handleLogout = () => {
  removeToken()
  router.push('/login')
}

// ========== 编辑资料弹窗 ==========
const showEditModal = ref(false)
const editForm = ref({ nickname: '', phone: '', email: '' })
const editSaving = ref(false)

const openEditModal = () => {
  editForm.value = {
    nickname: user.value?.nickname || '',
    phone: user.value?.phone || '',
    email: user.value?.email || ''
  }
  showEditModal.value = true
}

const saveEditForm = async () => {
  if (!editForm.value.nickname?.trim()) {
    alert('昵称不能为空')
    return
  }
  editSaving.value = true
  try {
    await updateUser({
      id: user.value.id,
      nickname: editForm.value.nickname.trim(),
      phone: editForm.value.phone?.trim() || null,
      email: editForm.value.email?.trim() || null
    })
    user.value.nickname = editForm.value.nickname.trim()
    user.value.phone = editForm.value.phone?.trim() || null
    user.value.email = editForm.value.email?.trim() || null
    setUserInfo(user.value)
    showEditModal.value = false
    alert('资料更新成功')
  } catch (e) {
    alert('保存失败: ' + (e.response?.data?.message || e.message))
  }
  editSaving.value = false
}

// ========== 笔记/收藏/点赞 Tab ==========
const activeTab = ref('posts')
const tabPosts = ref([])
const tabLoading = ref(false)
const tabTotal = ref(0)
const tabPage = ref(1)
const pageSize = 20

const tabs = [
  { key: 'posts', label: '笔记' },
  { key: 'favorited', label: '收藏' },
  { key: 'liked', label: '点赞' }
]

const loadTabData = async () => {
  if (!user.value?.id) return
  tabLoading.value = true
  try {
    let res
    const params = { pageNum: tabPage.value, pageSize }
    if (activeTab.value === 'posts') {
      res = await getUserPosts(user.value.id, params)
    } else if (activeTab.value === 'favorited') {
      res = await getUserFavoritedPosts(user.value.id, params)
    } else {
      res = await getUserLikedPosts(user.value.id, params)
    }
    if (tabPage.value === 1) {
      tabPosts.value = res.data?.list || []
    } else {
      tabPosts.value = [...tabPosts.value, ...(res.data?.list || [])]
    }
    tabTotal.value = res.data?.total || 0
  } catch { tabPosts.value = [] }
  tabLoading.value = false
}

const switchTab = (key) => {
  activeTab.value = key
  tabPage.value = 1
  loadTabData()
}

const loadMoreTab = () => {
  tabPage.value++
  loadTabData()
}

const hasMoreTab = computed(() => tabPosts.value.length < tabTotal.value)

const getCoverImage = (post) => {
  if (post.coverImage) return post.coverImage
  try {
    const imgs = JSON.parse(post.images || '[]')
    if (Array.isArray(imgs) && imgs.length) return imgs[0]
  } catch {}
  return ''
}

const isVideo = (post) => post.type === 2 || !!post.videoUrl

const formatCount = (n) => {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

// ========== 详情弹窗 ==========
const detailPostId = ref(null)
const openDetail = (post) => { detailPostId.value = post.id }
const closeDetail = () => { detailPostId.value = null; loadTabData() }

const sections = [
  {
    title: '我的订单',
    items: [
      { icon: '📦', name: '购物订单', path: '/user/orders' },
      { icon: '🛒', name: '购物车', path: '/user/cart' },
      { icon: '⭐', name: '活动记录', path: '/user/activity-records' },
    ]
  },
  {
    title: '浏览记录',
    items: [
      { icon: '📋', name: '浏览历史', path: '/user/history' },
    ]
  },
  {
    title: '设置',
    items: [
      { icon: '📍', name: '收货地址', path: '/user/address' },
      { icon: '🔒', name: '修改密码', path: '/user/password' },
      { icon: '🚪', name: '退出登录', path: 'logout' },
    ]
  }
]

const handleClick = (item) => {
  if (item.path === 'logout') {
    handleLogout()
  } else if (item.path !== '#') {
    router.push(item.path)
  }
}

onMounted(() => {
  refreshUserInfo()
  loadTabData()
})
</script>

<template>
  <div class="user-center">
    <div class="container">
      <!-- Profile Header - 小红书风格居中 -->
      <div class="profile-header">
        <div class="avatar-wrapper" @click="$refs.avatarInput.click()">
          <div v-if="user?.avatar" class="avatar" :style="{ backgroundImage: `url(${user.avatar})` }"></div>
          <div v-else class="avatar avatar-default">
            <span class="avatar-text">{{ user?.nickname?.[0] || user?.username?.[0] || '?' }}</span>
          </div>
          <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="handleAvatarUpload" />
        </div>
        <div class="profile-info">
          <h2 class="nickname">{{ user?.nickname || user?.username || '未登录' }}</h2>
          <p class="user-id">非遗号：{{ user?.id || '-' }}</p>
          <button class="edit-btn" @click="openEditModal">编辑资料</button>
        </div>
      </div>

      <!-- 笔记/收藏/点赞 Tab -->
      <div class="content-tabs">
        <span v-for="tab in tabs" :key="tab.key"
              :class="['ctab', { active: activeTab === tab.key }]"
              @click="switchTab(tab.key)">{{ tab.label }}</span>
      </div>

      <!-- Tab 内容瀑布流 -->
      <div v-if="tabLoading && tabPosts.length === 0" class="tab-loading">加载中...</div>
      <div v-else-if="tabPosts.length === 0" class="tab-empty">还没有内容哦~</div>
      <div v-else class="tab-waterfall">
        <div v-for="post in tabPosts" :key="post.id" class="wf-card" @click="openDetail(post)">
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
      <div v-if="tabPosts.length > 0" class="load-more">
        <button v-if="hasMoreTab" class="more-btn" :disabled="tabLoading" @click="loadMoreTab">
          {{ tabLoading ? '加载中...' : '加载更多' }}
        </button>
        <span v-else class="no-more">— 没有更多了 —</span>
      </div>

      <!-- 功能区 -->
      <div v-for="section in sections" :key="section.title" class="user-section">
        <h3 class="section-title">{{ section.title }}</h3>
        <div class="section-grid">
          <div v-for="item in section.items" :key="item.name"
               class="section-item"
               @click="handleClick(item)">
            <span class="item-icon">{{ item.icon }}</span>
            <span class="item-name">{{ item.name }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <PostDetailModal v-if="detailPostId" :postId="detailPostId" @close="closeDetail" @liked="loadTabData" />

    <!-- 编辑资料弹窗 -->
    <div v-if="showEditModal" class="modal-overlay" @click.self="showEditModal = false">
      <div class="modal-box">
        <div class="modal-header">
          <h3>编辑个人资料</h3>
          <span class="modal-close" @click="showEditModal = false">&times;</span>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>昵称</label>
            <input v-model="editForm.nickname" placeholder="请输入昵称" maxlength="20" />
          </div>
          <div class="form-group">
            <label>手机号</label>
            <input v-model="editForm.phone" placeholder="请输入手机号" maxlength="11" />
          </div>
          <div class="form-group">
            <label>邮箱</label>
            <input v-model="editForm.email" placeholder="请输入邮箱" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="modal-btn cancel" @click="showEditModal = false">取消</button>
          <button class="modal-btn confirm" :disabled="editSaving" @click="saveEditForm">
            {{ editSaving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-center {
  padding: 30px 0 60px;
}

/* 小红书风格 Profile Header - 居中 */
.profile-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px 40px 30px;
  background: var(--ich-white);
  border-radius: var(--ich-radius-lg);
  box-shadow: var(--ich-shadow-card);
  margin-bottom: 0;
}

.avatar-wrapper {
  position: relative;
  cursor: pointer;
  flex-shrink: 0;
}

.avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background-size: cover;
  background-position: center;
  border: 3px solid #f0f0f0;
}

.avatar-default {
  background: linear-gradient(135deg, var(--ich-primary, #8B2020), #6B3A6B);
}

.avatar-text {
  color: #fff;
  font-size: 38px;
  font-weight: 700;
}

.profile-info {
  text-align: center;
}

.nickname {
  font-size: 24px;
  font-weight: 700;
  color: var(--ich-text-primary);
  margin: 0 0 6px;
}

.user-id {
  font-size: 13px;
  color: var(--ich-text-muted);
  margin: 0 0 14px;
}

.edit-btn {
  padding: 6px 24px;
  background: transparent;
  color: var(--ich-text-secondary);
  border: 1px solid #ddd;
  border-radius: var(--ich-radius-xl);
  font-size: 13px;
  cursor: pointer;
  transition: var(--ich-transition);
}
.edit-btn:hover { border-color: var(--ich-primary); color: var(--ich-primary); }

/* 笔记/收藏/点赞 Tab */
.content-tabs {
  display: flex;
  justify-content: center;
  gap: 32px;
  padding: 18px 0;
  background: var(--ich-white);
  border-radius: 0 0 var(--ich-radius-lg) var(--ich-radius-lg);
  box-shadow: var(--ich-shadow-card);
  margin-bottom: 24px;
}
.ctab {
  font-size: 15px;
  color: var(--ich-text-muted);
  cursor: pointer;
  padding: 6px 0;
  border-bottom: 2px solid transparent;
  transition: 0.2s;
  user-select: none;
}
.ctab:hover { color: var(--ich-text-primary); }
.ctab.active {
  color: var(--ich-text-primary);
  font-weight: 600;
  border-bottom-color: var(--ich-primary, #8B2020);
}

/* Tab 瀑布流 */
.tab-waterfall {
  columns: 5;
  column-gap: 16px;
  margin-bottom: 24px;
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
.card-cover img { width: 100%; display: block; }
.cover-placeholder {
  width: 100%; height: 180px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #f5e6d3, #e8d5c0);
  font-size: 24px; font-weight: 700;
  color: rgba(139, 32, 32, 0.3);
  font-family: var(--ich-font-serif, serif);
}
.video-badge {
  position: absolute; top: 10px; right: 10px;
  width: 28px; height: 28px; border-radius: 50%;
  background: rgba(0,0,0,0.55); color: #fff; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
}
.card-body { padding: 10px 12px; }
.card-title {
  font-size: 14px; font-weight: 600; color: #333;
  margin: 0 0 8px; line-height: 1.4;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.card-footer { display: flex; justify-content: space-between; align-items: center; }
.card-author { display: flex; align-items: center; gap: 6px; min-width: 0; flex: 1; }
.mini-avatar {
  width: 20px; height: 20px; border-radius: 50%; flex-shrink: 0;
  background-size: cover; background-position: center;
}
.mini-avatar-letter {
  background: linear-gradient(135deg, #8B2020, #6B3A6B);
  color: #fff; font-size: 10px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.author-text { font-size: 12px; color: #999; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.like-count { font-size: 12px; color: #ff2442; white-space: nowrap; flex-shrink: 0; }

.tab-loading, .tab-empty {
  text-align: center; padding: 60px 0; color: #bbb; font-size: 14px;
}

.load-more { text-align: center; padding: 16px 0; margin-bottom: 24px; }
.more-btn {
  padding: 10px 40px; border: 1px solid #ddd; border-radius: 24px;
  background: #fff; color: #666; font-size: 14px; cursor: pointer; transition: 0.2s;
}
.more-btn:hover { border-color: var(--ich-primary, #8B2020); color: var(--ich-primary, #8B2020); }
.no-more { font-size: 13px; color: #ccc; }

/* 功能区 */
.user-section {
  background: var(--ich-white);
  border-radius: var(--ich-radius-lg);
  box-shadow: var(--ich-shadow-card);
  padding: 24px 30px;
  margin-bottom: 20px;
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-top: 20px;
}

.section-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 12px;
  border-radius: var(--ich-radius-md);
  cursor: pointer;
  transition: var(--ich-transition);
}
.section-item:hover {
  background: var(--ich-primary-bg);
}

.item-icon { font-size: 32px; }

.item-name {
  font-size: 13px;
  color: var(--ich-text-secondary);
  text-align: center;
}

@media (max-width: 768px) {
  .section-grid { grid-template-columns: repeat(3, 1fr); }
  .tab-waterfall { columns: 2; }
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.modal-box {
  background: var(--ich-white);
  border-radius: var(--ich-radius-lg);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  width: 440px;
  max-width: 92vw;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--ich-border);
}

.modal-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--ich-text-primary);
  margin: 0;
}

.modal-close {
  font-size: 24px;
  color: var(--ich-text-muted);
  cursor: pointer;
  line-height: 1;
  transition: var(--ich-transition);
}
.modal-close:hover { color: var(--ich-text-primary); }

.modal-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--ich-text-secondary);
  margin-bottom: 6px;
}

.form-group input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--ich-border);
  border-radius: var(--ich-radius-md);
  font-size: 14px;
  color: var(--ich-text-primary);
  transition: var(--ich-transition);
  box-sizing: border-box;
}
.form-group input:focus {
  outline: none;
  border-color: var(--ich-primary);
  box-shadow: 0 0 0 3px rgba(139, 32, 32, 0.1);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--ich-border);
}

.modal-btn {
  padding: 8px 24px;
  border-radius: var(--ich-radius-md);
  font-size: 14px;
  cursor: pointer;
  transition: var(--ich-transition);
}

.modal-btn.cancel {
  background: var(--ich-white);
  border: 1px solid var(--ich-border);
  color: var(--ich-text-secondary);
}
.modal-btn.cancel:hover { border-color: var(--ich-text-muted); }

.modal-btn.confirm {
  background: var(--ich-primary);
  border: none;
  color: #fff;
}
.modal-btn.confirm:hover:not(:disabled) { opacity: 0.9; }
.modal-btn.confirm:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
