<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getItemList, getActivityList } from '@/api/content'
import { getProductList } from '@/api/product'

const currentSlide = ref(0)
const bannerImages = [
  { id: 1, title: '中国非物质文化遗产', subtitle: '传承千年匠心，守护文化瑰宝', color: '#8B2020' },
  { id: 2, title: '戏曲艺术之美', subtitle: '生旦净末丑，唱念做打舞', color: '#5D4037' },
  { id: 3, title: '传统手工技艺', subtitle: '指尖上的中国，匠心独运', color: '#2E4057' },
]

const announcements = ref([])
const shares = ref([])

const colorPool = ['#D4A847','#8B4513','#8B2020','#2E4057','#5D4037','#4A148C','#006064','#33691E']

const loadActivities = async () => {
  try {
    const res = await getActivityList({ pageNum: 1, pageSize: 5 })
    const list = res.data?.list || res.data?.records || []
    announcements.value = list.map(a => ({
      id: a.id,
      title: a.name,
      time: a.createTime ? new Date(a.createTime).toLocaleString('zh-CN') : ''
    }))
  } catch (e) { /* ignore */ }
}

const loadItems = async () => {
  try {
    const res = await getItemList({ pageNum: 1, pageSize: 5 })
    const list = res.data?.list || res.data?.records || []
    shares.value = list.map((item, i) => ({
      id: item.id,
      title: item.name,
      author: item.declarationUnit || item.regionName || '',
      time: item.createTime ? new Date(item.createTime).toLocaleDateString('zh-CN') : '',
      tag: item.categoryName || '',
      color: colorPool[i % colorPool.length],
      coverImage: item.coverImage || ''
    }))
  } catch (e) { /* ignore */ }
}

const nextSlide = () => { currentSlide.value = (currentSlide.value + 1) % bannerImages.length }
const prevSlide = () => { currentSlide.value = (currentSlide.value - 1 + bannerImages.length) % bannerImages.length }

let slideTimer = null
const startAutoSlide = () => { slideTimer = setInterval(nextSlide, 5000) }
const stopAutoSlide = () => { clearInterval(slideTimer) }

onMounted(() => {
  startAutoSlide()
  loadActivities()
  loadItems()
})
onUnmounted(() => { stopAutoSlide() })
</script>

<template>
  <div class="home-page">
    <!-- Banner + Announcements -->
    <div class="hero-section">
      <div class="hero-inner">
        <div class="banner-area" @mouseenter="stopAutoSlide" @mouseleave="startAutoSlide">
          <div class="banner-slides">
            <div v-for="(slide, idx) in bannerImages" :key="slide.id"
                 :class="['banner-slide', { active: currentSlide === idx }]"
                 :style="{ background: `linear-gradient(135deg, ${slide.color} 0%, ${slide.color}dd 100%)` }">
              <div class="slide-content">
                <h2>{{ slide.title }}</h2>
                <p>{{ slide.subtitle }}</p>
              </div>
              <div class="slide-pattern"></div>
            </div>
          </div>
          <div class="banner-dots">
            <span v-for="(_, idx) in bannerImages" :key="idx"
                  :class="['dot', { active: currentSlide === idx }]"
                  @click="currentSlide = idx"></span>
          </div>
          <button class="banner-arrow left" @click="prevSlide">‹</button>
          <button class="banner-arrow right" @click="nextSlide">›</button>
        </div>

        <div class="announce-panel">
          <div class="announce-header">
            <h3>系统公告</h3>
            <router-link to="/notification" class="more-link">查看更多</router-link>
          </div>
          <ul class="announce-list">
            <li v-for="item in announcements" :key="item.id" class="announce-item">
              <span class="announce-title">{{ item.title }}</span>
              <span class="announce-time">{{ item.time }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <!-- User Shares -->
    <div class="shares-section">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">🔴 网友分享</h2>
          <router-link to="/article" class="more-link">查看更多</router-link>
        </div>
        <div class="shares-grid">
          <div v-for="item in shares" :key="item.id" class="share-card card">
            <div class="share-img" :style="{ background: item.coverImage ? `url(${item.coverImage}) center/cover` : item.color }">
              <span v-if="!item.coverImage" class="share-img-text">{{ item.tag || item.title?.substring(0,2) }}</span>
            </div>
            <div class="share-info">
              <h4 class="share-title">{{ item.title }}</h4>
              <p class="share-meta">{{ item.author }} · {{ item.time }}</p>
              <span class="tag" v-if="item.tag">{{ item.tag }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Quick Entry -->
    <div class="quick-section">
      <div class="container">
        <h2 class="section-title">🏛️ 探索非遗</h2>
        <div class="quick-grid">
          <router-link to="/culture" class="quick-card">
            <span class="quick-icon">📜</span>
            <span class="quick-name">非遗文化</span>
            <span class="quick-desc">1000+项目</span>
          </router-link>
          <router-link to="/inheritor" class="quick-card">
            <span class="quick-icon">👨‍🎨</span>
            <span class="quick-name">传承人</span>
            <span class="quick-desc">500+匠人</span>
          </router-link>
          <router-link to="/video" class="quick-card">
            <span class="quick-icon">🎬</span>
            <span class="quick-name">非遗视频</span>
            <span class="quick-desc">影像记忆</span>
          </router-link>
          <router-link to="/shop" class="quick-card">
            <span class="quick-icon">🏪</span>
            <span class="quick-name">文创商城</span>
            <span class="quick-desc">匠心好物</span>
          </router-link>
          <router-link to="/activity" class="quick-card">
            <span class="quick-icon">�</span>
            <span class="quick-name">非遗活动</span>
            <span class="quick-desc">体验参与</span>
          </router-link>
          <router-link to="/article" class="quick-card">
            <span class="quick-icon">📰</span>
            <span class="quick-name">文章资讯</span>
            <span class="quick-desc">非遗故事</span>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page { padding-bottom: 40px; }

/* Hero Section */
.hero-section {
  background: var(--ich-white);
  padding: 20px 0;
}
.hero-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  gap: 20px;
}

/* Banner */
.banner-area {
  flex: 1;
  position: relative;
  border-radius: var(--ich-radius-lg);
  overflow: hidden;
  height: 320px;
}
.banner-slides { width: 100%; height: 100%; position: relative; }
.banner-slide {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  padding: 40px 50px;
  opacity: 0;
  transition: opacity 0.6s ease;
}
.banner-slide.active { opacity: 1; z-index: 1; }
.slide-content { position: relative; z-index: 2; color: #fff; }
.slide-content h2 {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 12px;
  font-family: var(--ich-font-serif);
  text-shadow: 0 2px 8px rgba(0,0,0,0.3);
}
.slide-content p {
  font-size: 16px;
  opacity: 0.85;
  text-shadow: 0 1px 4px rgba(0,0,0,0.2);
}
.slide-pattern {
  position: absolute;
  right: -20px;
  top: -20px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
}
.banner-dots {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 5;
}
.dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: rgba(255,255,255,0.4);
  cursor: pointer;
  transition: all 0.3s;
}
.dot.active { background: #fff; width: 24px; border-radius: 4px; }
.banner-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 5;
  width: 36px; height: 36px;
  border-radius: 50%;
  background: rgba(0,0,0,0.3);
  color: #fff;
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
}
.banner-arrow:hover { background: rgba(0,0,0,0.5); }
.banner-arrow.left { left: 12px; }
.banner-arrow.right { right: 12px; }

/* Announcements */
.announce-panel {
  width: 300px;
  flex-shrink: 0;
  background: var(--ich-bg-soft);
  border-radius: var(--ich-radius-lg);
  border: 1px solid var(--ich-border);
  padding: 20px;
  display: flex;
  flex-direction: column;
}
.announce-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ich-border);
}
.announce-header h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--ich-text-primary);
}
.more-link {
  font-size: 13px;
  color: var(--ich-text-muted);
  text-decoration: none;
}
.more-link:hover { color: var(--ich-primary); }
.announce-list { flex: 1; display: flex; flex-direction: column; gap: 12px; }
.announce-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 10px;
  border-bottom: 1px dashed var(--ich-border);
}
.announce-item:last-child { border-bottom: none; }
.announce-title {
  font-size: 14px;
  color: var(--ich-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.announce-time { font-size: 12px; color: var(--ich-text-muted); }

/* Shares Section */
.shares-section { padding: 40px 0; }
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.shares-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
.share-card { cursor: pointer; }
.share-img {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--ich-radius-lg) var(--ich-radius-lg) 0 0;
}
.share-img-text {
  color: rgba(255,255,255,0.7);
  font-size: 20px;
  font-family: var(--ich-font-serif);
  letter-spacing: 4px;
}
.share-info { padding: 14px; }
.share-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ich-text-primary);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.share-meta {
  font-size: 12px;
  color: var(--ich-text-muted);
  margin-bottom: 8px;
}

/* Quick Section */
.quick-section { padding: 0 0 40px; }
.quick-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-top: 24px;
}
.quick-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 16px;
  background: var(--ich-white);
  border-radius: var(--ich-radius-lg);
  box-shadow: var(--ich-shadow-card);
  text-decoration: none;
  transition: var(--ich-transition);
}
.quick-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--ich-shadow-md);
}
.quick-icon { font-size: 36px; }
.quick-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ich-text-primary);
}
.quick-desc {
  font-size: 12px;
  color: var(--ich-text-muted);
}

@media (max-width: 1024px) {
  .hero-inner { flex-direction: column; }
  .announce-panel { width: 100%; }
  .shares-grid { grid-template-columns: repeat(3, 1fr); }
  .quick-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 640px) {
  .shares-grid { grid-template-columns: repeat(2, 1fr); }
  .quick-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
