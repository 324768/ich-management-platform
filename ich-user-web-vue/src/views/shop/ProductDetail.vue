<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProduct } from '@/api/product'
import { addToCart } from '@/api/cart'
import { getUserInfo } from '@/utils/token'
import { addBrowseHistory } from '@/utils/history'

const route = useRoute()
const router = useRouter()
const item = ref({})
const loading = ref(false)
const previewImage = ref(null)
const quantity = ref(1)

const subImages = ref([])
const videos = ref([])

const allImages = computed(() => {
  const imgs = []
  if (item.value.mainImage) imgs.push(item.value.mainImage)
  imgs.push(...subImages.value)
  return imgs
})

const currentImage = ref('')

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getProduct(route.params.id)
    item.value = res.data || {}
    // 解析副图
    let subs = item.value.subImages
    if (typeof subs === 'string') { try { subs = JSON.parse(subs) } catch { subs = [] } }
    subImages.value = Array.isArray(subs) ? subs : []
    // 解析视频
    let vids = item.value.videos
    if (typeof vids === 'string') { try { vids = JSON.parse(vids) } catch { vids = [] } }
    videos.value = Array.isArray(vids) ? vids : []
    // 默认展示主图
    currentImage.value = item.value.mainImage || ''
    addBrowseHistory('product', { targetId: item.value.id, title: item.value.name, image: item.value.mainImage, desc: item.value.price ? '¥' + item.value.price : '' })
  } catch (e) {
    item.value = {}
  }
  loading.value = false
}

const handleAddToCart = async () => {
  const user = getUserInfo()
  if (!user?.id) {
    alert('请先登录')
    router.push('/login')
    return
  }
  try {
    await addToCart(user.id, item.value.id, quantity.value)
    alert('已加入购物车！')
  } catch (e) {
    alert('加入购物车失败: ' + (e.response?.data?.message || e.message))
  }
}

onMounted(loadDetail)
</script>

<template>
  <div class="detail-page">
    <div class="page-banner">
      <h1>商品详情</h1>
      <p>非遗文创 · 匠心之作</p>
    </div>

    <div class="container" style="padding: 32px 20px 60px;">
      <button class="back-btn" @click="router.back()">← 返回商城</button>

      <div v-if="loading" class="loading-state"><p>加载中...</p></div>

      <div v-else-if="!item.id" class="empty-state"><p>未找到该商品</p></div>

      <div v-else class="detail-content">
        <!-- 商品主体 -->
        <div class="product-main">
          <!-- 图片区 -->
          <div class="product-gallery">
            <div class="main-image" :style="currentImage ? { backgroundImage: `url(${currentImage})` } : { background: '#f5f0eb' }">
              <span v-if="!currentImage" class="no-image-text">暂无图片</span>
            </div>
            <div v-if="allImages.length > 1" class="thumb-list">
              <div v-for="(img, idx) in allImages" :key="idx"
                   :class="['thumb-item', { active: currentImage === img }]"
                   :style="{ backgroundImage: `url(${img})` }"
                   @click="currentImage = img">
              </div>
            </div>
          </div>

          <!-- 信息区 -->
          <div class="product-info">
            <h2 class="product-title">{{ item.name }}</h2>
            <p v-if="item.subTitle" class="product-subtitle">{{ item.subTitle }}</p>

            <div class="price-area">
              <span class="price-current">¥{{ item.price || '0.00' }}</span>
              <span v-if="item.originalPrice" class="price-original">¥{{ item.originalPrice }}</span>
            </div>

            <div class="product-meta-list">
              <div class="meta-row" v-if="item.productSn">
                <span class="meta-label">商品编码</span>
                <span class="meta-value">{{ item.productSn }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">库存</span>
                <span class="meta-value" :class="{ 'out-of-stock': item.stock <= 0 }">{{ item.stock > 0 ? item.stock : '暂无库存' }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">销量</span>
                <span class="meta-value">{{ item.sale || 0 }}</span>
              </div>
              <div class="meta-row" v-if="item.categoryName">
                <span class="meta-label">分类</span>
                <span class="meta-value">{{ item.categoryName }}</span>
              </div>
            </div>

            <!-- 购买操作 -->
            <div class="buy-area" v-if="item.status === 1 && item.stock > 0">
              <div class="quantity-picker">
                <button @click="quantity > 1 && quantity--">−</button>
                <span>{{ quantity }}</span>
                <button @click="quantity < item.stock && quantity++">+</button>
              </div>
              <button class="add-cart-btn" @click="handleAddToCart">🛒 加入购物车</button>
            </div>
            <div v-else class="sold-out-tip">
              {{ item.status !== 1 ? '商品已下架' : '暂时缺货' }}
            </div>
          </div>
        </div>

        <!-- 商品详情 -->
        <div v-if="item.detailDesc" class="detail-section">
          <h3 class="section-title">商品详情</h3>
          <div class="section-body">{{ item.detailDesc }}</div>
        </div>

        <!-- 更多图片 -->
        <div v-if="subImages.length" class="detail-section">
          <h3 class="section-title">商品图片</h3>
          <div class="image-gallery">
            <img v-for="(img, idx) in allImages" :key="idx" :src="img" class="gallery-img" @click="previewImage = img" />
          </div>
        </div>

        <!-- 视频 -->
        <div v-if="videos.length" class="detail-section">
          <h3 class="section-title">商品视频</h3>
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

.product-main {
  display: flex;
  gap: 40px;
  margin-bottom: 32px;
}

.product-gallery {
  width: 420px;
  flex-shrink: 0;
}

.main-image {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 12px;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--ich-border);
}

.no-image-text {
  color: var(--ich-text-muted);
  font-size: 16px;
}

.thumb-list {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.thumb-item {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  background-size: cover;
  background-position: center;
  cursor: pointer;
  border: 2px solid transparent;
  transition: var(--ich-transition);
}
.thumb-item.active, .thumb-item:hover {
  border-color: var(--ich-primary);
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--ich-text-primary);
  margin: 0 0 8px;
  font-family: var(--ich-font-serif);
}

.product-subtitle {
  font-size: 14px;
  color: var(--ich-text-muted);
  margin: 0 0 20px;
}

.price-area {
  background: #fef7f0;
  padding: 16px 20px;
  border-radius: var(--ich-radius-md);
  margin-bottom: 24px;
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.price-current {
  font-size: 32px;
  font-weight: 700;
  color: #c62828;
}

.price-original {
  font-size: 16px;
  color: var(--ich-text-muted);
  text-decoration: line-through;
}

.product-meta-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.meta-row {
  display: flex;
  gap: 12px;
  font-size: 14px;
}

.meta-label {
  color: var(--ich-text-muted);
  width: 70px;
  flex-shrink: 0;
}

.meta-value {
  color: var(--ich-text-primary);
}
.meta-value.out-of-stock { color: #c62828; }

.buy-area {
  display: flex;
  align-items: center;
  gap: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--ich-border);
}

.quantity-picker {
  display: flex;
  align-items: center;
  border: 1px solid var(--ich-border);
  border-radius: var(--ich-radius-md);
  overflow: hidden;
}

.quantity-picker button {
  width: 36px;
  height: 36px;
  border: none;
  background: var(--ich-bg-light);
  cursor: pointer;
  font-size: 18px;
  color: var(--ich-text-secondary);
  transition: var(--ich-transition);
}
.quantity-picker button:hover { background: var(--ich-border); }

.quantity-picker span {
  width: 48px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  border-left: 1px solid var(--ich-border);
  border-right: 1px solid var(--ich-border);
}

.add-cart-btn {
  padding: 10px 32px;
  background: var(--ich-primary);
  color: #fff;
  border: none;
  border-radius: var(--ich-radius-md);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: var(--ich-transition);
}
.add-cart-btn:hover { opacity: 0.9; transform: translateY(-1px); }

.sold-out-tip {
  padding: 16px 0;
  color: var(--ich-text-muted);
  font-size: 15px;
  border-top: 1px solid var(--ich-border);
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

@media (max-width: 768px) {
  .product-main { flex-direction: column; }
  .product-gallery { width: 100%; }
}
</style>
