<script setup>
import { ref, onMounted } from 'vue'
import { getProductCategoryTree, getProductList } from '@/api/product'
import { addToCart } from '@/api/cart'
import { getUserInfo as getStoredUser } from '@/utils/token'

const searchText = ref('')
const activeCategory = ref(null)
const categories = ref([])
const products = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)

const colorPool = ['#8B4513','#2E4057','#5D4037','#6B3A6B','#BF360C','#C62828','#1565C0','#4E342E','#33691E','#6A1B9A']

const loadCategories = async () => {
  try {
    const res = await getProductCategoryTree()
    categories.value = res.data || []
  } catch (e) { /* ignore */ }
}

const loadProducts = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (activeCategory.value) params.categoryId = activeCategory.value
    if (searchText.value) params.keyword = searchText.value
    const res = await getProductList(params)
    const pageData = res.data || {}
    products.value = (pageData.list || pageData.records || []).map((item, i) => ({
      ...item,
      color: colorPool[i % colorPool.length]
    }))
    total.value = pageData.total || 0
  } catch (e) { products.value = [] }
  loading.value = false
}

const selectCategory = (catId) => {
  activeCategory.value = activeCategory.value === catId ? null : catId
  pageNum.value = 1
  loadProducts()
}

const doSearch = () => { pageNum.value = 1; loadProducts() }
const changePage = (p) => { pageNum.value = p; loadProducts() }
const totalPages = () => Math.ceil(total.value / pageSize.value)

const handleAddCart = async (productId) => {
  const user = getStoredUser()
  if (!user?.id) { alert('请先登录'); return }
  try {
    await addToCart(user.id, productId, 1)
    alert('已加入购物车')
  } catch (e) {
    alert(e.message || '加入购物车失败')
  }
}

onMounted(() => {
  loadCategories()
  loadProducts()
})
</script>

<template>
  <div class="shop-page">
    <!-- Search Bar -->
    <div class="shop-search-bar">
      <div class="container">
        <div class="search-box" style="max-width: 700px;">
          <span style="padding-left:12px; color: var(--ich-text-muted);">🔍</span>
          <input v-model="searchText" placeholder="请输入你喜欢的文创品" @keyup.enter="doSearch" />
          <button class="search-btn" @click="doSearch">搜 索</button>
        </div>
      </div>
    </div>

    <div class="shop-body">
      <div class="container">
        <div class="shop-layout">
          <!-- Sidebar Categories -->
          <aside class="shop-sidebar">
            <h3 class="sidebar-title">文创品分类</h3>
            <ul class="cat-list">
              <li :class="['cat-item', { active: !activeCategory }]"
                  @click="selectCategory(null)">全部商品</li>
              <li v-for="cat in categories" :key="cat.id"
                  :class="['cat-item', { active: activeCategory === cat.id }]"
                  @click="selectCategory(cat.id)">{{ cat.name }}</li>
            </ul>
          </aside>

          <!-- Main Content -->
          <div class="shop-main">
            <!-- Featured Banner -->
            <div class="featured-banner" style="background: linear-gradient(135deg, #D4A847 0%, #B8860B 100%);">
              <div class="banner-text">
                <h2>匠心文创</h2>
                <p>传承非遗之美，品味文化精粹</p>
              </div>
            </div>

            <!-- Products -->
            <div class="products-section">
              <h3 class="section-title">🏷️ 文创商品 <span style="font-size:13px; color: var(--ich-text-muted); font-weight:400;">共 {{ total }} 件</span></h3>
              <div v-if="loading" class="loading-state"><p>加载中...</p></div>
              <div v-else class="product-grid">
                <div v-for="product in products" :key="product.id" class="product-card card" style="cursor:pointer;" @click="$router.push(`/shop/product/${product.id}`)">
                  <div class="product-img" :style="{ background: product.mainImage ? `url(${product.mainImage}) center/cover` : product.color }">
                    <span v-if="!product.mainImage" class="product-img-text">{{ product.name?.substring(0, 2) }}</span>
                  </div>
                  <div class="product-info">
                    <h4 class="product-name">{{ product.name }}</h4>
                    <span class="product-cat">{{ product.categoryName || '' }}</span>
                    <div class="product-price">¥{{ product.price }}</div>
                    <button class="add-cart-btn" @click.stop="handleAddCart(product.id)">加入购物车</button>
                  </div>
                </div>
              </div>

              <div v-if="!loading && products.length === 0" class="empty-state"><p>暂无商品</p></div>

              <div v-if="totalPages() > 1" class="pagination">
                <button :disabled="pageNum <= 1" @click="changePage(pageNum - 1)">上一页</button>
                <span class="page-info">{{ pageNum }} / {{ totalPages() }}</span>
                <button :disabled="pageNum >= totalPages()" @click="changePage(pageNum + 1)">下一页</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shop-page { padding-bottom: 40px; }

.shop-search-bar {
  background: var(--ich-white);
  padding: 16px 0;
  border-bottom: 1px solid var(--ich-border);
}

.shop-body { margin-top: 20px; }

.shop-layout {
  display: flex;
  gap: 24px;
}

.shop-sidebar {
  width: 200px;
  flex-shrink: 0;
  background: var(--ich-white);
  border-radius: var(--ich-radius-lg);
  padding: 20px;
  box-shadow: var(--ich-shadow-card);
  height: fit-content;
  position: sticky;
  top: 80px;
}

.sidebar-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--ich-primary);
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid var(--ich-primary);
}

.cat-list { display: flex; flex-direction: column; gap: 4px; }

.cat-item {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--ich-text-secondary);
  cursor: pointer;
  border-radius: var(--ich-radius-sm);
  transition: var(--ich-transition);
}
.cat-item:hover {
  color: var(--ich-primary);
  background: var(--ich-primary-bg);
}
.cat-item.active {
  color: var(--ich-primary);
  font-weight: 600;
  background: var(--ich-primary-bg);
}

.shop-main { flex: 1; min-width: 0; }

.featured-banner {
  border-radius: var(--ich-radius-lg);
  padding: 48px 40px;
  margin-bottom: 28px;
  position: relative;
  overflow: hidden;
}
.banner-text { position: relative; z-index: 1; color: #fff; }
.banner-text h2 {
  font-size: 28px;
  font-weight: 700;
  font-family: var(--ich-font-serif);
  margin-bottom: 8px;
}
.banner-text p { font-size: 15px; opacity: 0.85; }

.products-section { margin-top: 8px; }

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 20px;
}

.product-card { cursor: pointer; }

.product-img {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.product-img-text {
  color: rgba(255,255,255,0.6);
  font-size: 28px;
  font-family: var(--ich-font-serif);
  letter-spacing: 6px;
}

.product-info { padding: 14px; }

.product-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ich-text-primary);
  margin-bottom: 4px;
}
.product-cat {
  font-size: 12px;
  color: var(--ich-text-muted);
  display: block;
  margin-bottom: 8px;
}
.product-price {
  font-size: 18px;
  font-weight: 700;
  color: var(--ich-primary);
  margin-bottom: 10px;
}
.add-cart-btn {
  width: 100%;
  padding: 8px 0;
  background: var(--ich-primary-bg);
  color: var(--ich-primary);
  border: 1px solid var(--ich-primary);
  border-radius: var(--ich-radius-md);
  font-size: 13px;
  cursor: pointer;
  transition: var(--ich-transition);
}
.add-cart-btn:hover {
  background: var(--ich-primary);
  color: #fff;
}

@media (max-width: 900px) {
  .shop-layout { flex-direction: column; }
  .shop-sidebar { width: 100%; position: static; }
  .product-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
