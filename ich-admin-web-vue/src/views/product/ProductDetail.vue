<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <button class="ag-btn-secondary" @click="$router.back()">
          <el-icon :size="14"><ArrowLeft /></el-icon>
          <span>{{ t('common.back') }}</span>
        </button>
        <button v-if="hasChanges" class="ag-btn" @click="handleSave" :disabled="saving">
          <el-icon :size="14"><Check /></el-icon>
          <span>{{ saving ? '保存中...' : t('common.save') }}</span>
        </button>
      </div>

      <div class="ag-card detail-card" v-loading="loading">
        <div class="detail-header">
          <div class="detail-cover" :class="{ placeholder: !form.mainImage }" @click="triggerCoverInput">
            <el-image v-if="form.mainImage" :src="form.mainImage" fit="cover" />
            <el-icon v-else :size="32" color="#d1d5db"><ShoppingBag /></el-icon>
            <div class="cover-upload-overlay">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            </div>
          </div>
          <input ref="coverInputRef" type="file" accept="image/*" style="display:none" @change="handleCoverChange" />
          <div class="detail-meta">
            <h2 class="detail-title">{{ form.name || '-' }}</h2>
            <p class="detail-subtitle" v-if="form.subTitle">{{ form.subTitle }}</p>
            <div class="detail-tags">
              <el-tag :type="form.status === 1 ? 'success' : 'info'" size="small" round>{{ form.status === 1 ? t('product.item.onSale') : t('product.item.offShelf') }}</el-tag>
            </div>
            <div class="detail-price">
              <span class="price-current">¥{{ form.price || '0.00' }}</span>
              <span class="price-original" v-if="form.originalPrice">¥{{ form.originalPrice }}</span>
            </div>
          </div>
        </div>

        <el-divider />

        <el-descriptions :column="2" border class="detail-edit-field">
          <el-descriptions-item :label="t('common.id')">{{ form.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" :active-text="t('product.item.onSale')" :inactive-text="t('product.item.offShelf')" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('product.item.price')">
            <el-input-number v-model="form.price" :min="0" :precision="2" :controls="false" size="small" style="width: 120px" />
          </el-descriptions-item>
          <el-descriptions-item label="市场价">
            <el-input-number v-model="form.originalPrice" :min="0" :precision="2" :controls="false" size="small" style="width: 120px" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('product.item.stock')">
            <el-input-number v-model="form.stock" :min="0" :controls="false" size="small" style="width: 100px" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('product.item.sales')">{{ form.sale ?? 0 }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.belongCategory')">
            <el-select v-model="form.categoryId" :placeholder="t('common.selectCategory')" clearable size="small" style="width: 100%">
              <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.linkedHeritage')">
            <el-select v-model="form.heritageManId" :placeholder="t('common.selectHeritage')" clearable filterable size="small" style="width: 100%">
              <el-option v-for="h in heritageManOptions" :key="h.id" :label="h.name" :value="h.id" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item label="商品编码" :span="2">
            <el-input v-model="form.productSn" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.name')" :span="2">
            <el-input v-model="form.name" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('product.item.subtitle')" :span="2">
            <el-input v-model="form.subTitle" size="small" />
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <h3 class="section-title">{{ t('product.item.subImages') }}</h3>
          <div class="image-gallery">
            <div class="gallery-item-wrapper" v-for="(img, idx) in subImageList" :key="'img-'+idx">
              <el-image :src="img" fit="cover" :preview-src-list="subImageList" :initial-index="idx" preview-teleported class="gallery-item" />
              <div class="gallery-remove" @click="removeSubImage(idx)"><el-icon :size="14"><Close /></el-icon></div>
            </div>
            <div class="gallery-add" @click="$refs.subImageInput.click()">
              <el-icon :size="24" color="#9ca3af"><Plus /></el-icon>
            </div>
          </div>
          <input ref="subImageInput" type="file" accept="image/*" multiple style="display:none" @change="handleSubImageAdd" />
        </div>

        <div class="detail-section">
          <h3 class="section-title">{{ t('product.item.detail') }}</h3>
          <el-input v-model="form.detailDesc" type="textarea" :rows="4" class="detail-edit-field" />
        </div>

        <div class="detail-section">
          <h3 class="section-title">介绍视频</h3>
          <div class="video-gallery">
            <div class="video-item" v-for="(v, idx) in videoList" :key="'vid-'+idx">
              <video :src="v" controls preload="metadata" />
              <div class="gallery-remove" @click="removeVideo(idx)"><el-icon :size="14"><Close /></el-icon></div>
            </div>
            <div class="gallery-add video-add" @click="$refs.videoInput.click()">
              <el-icon :size="24" color="#9ca3af"><VideoCamera /></el-icon>
            </div>
          </div>
          <input ref="videoInput" type="file" accept="video/*" multiple style="display:none" @change="handleVideoAdd" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getProduct, updateProduct, getProductCategoryTree } from '@/api/product'
import { getHeritageManList } from '@/api/content'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const form = ref({})
const originalData = ref('')
const loading = ref(false)
const saving = ref(false)
const coverInputRef = ref(null)
const categoryOptions = ref([])
const heritageManOptions = ref([])

const hasChanges = computed(() => originalData.value && JSON.stringify(form.value) !== originalData.value)

const subImageList = computed(() => {
  if (!form.value.subImages) return []
  try { return JSON.parse(form.value.subImages) } catch { return [] }
})

const videoList = computed(() => form.value.videos || [])

const allImages = computed(() => {
  const imgs = []
  if (form.value.mainImage) imgs.push(form.value.mainImage)
  imgs.push(...subImageList.value)
  return imgs
})

function triggerCoverInput() { coverInputRef.value?.click() }

function handleCoverChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => { form.value.mainImage = ev.target.result }
  reader.readAsDataURL(file)
}

function removeSubImage(idx) {
  const list = subImageList.value
  list.splice(idx, 1)
  form.value.subImages = JSON.stringify(list)
}

function handleSubImageAdd(e) {
  const files = e.target.files
  if (!files) return
  const list = [...subImageList.value]
  let loaded = 0
  for (const file of files) {
    const reader = new FileReader()
    reader.onload = (ev) => {
      list.push(ev.target.result)
      loaded++
      if (loaded === files.length) form.value.subImages = JSON.stringify(list)
    }
    reader.readAsDataURL(file)
  }
  e.target.value = ''
}

function removeVideo(idx) {
  if (!form.value.videos) return
  form.value.videos.splice(idx, 1)
}

function handleVideoAdd(e) {
  const files = e.target.files
  if (!files) return
  if (!form.value.videos) form.value.videos = []
  for (const file of files) {
    const reader = new FileReader()
    reader.onload = (ev) => { form.value.videos.push(ev.target.result) }
    reader.readAsDataURL(file)
  }
  e.target.value = ''
}

function flattenCategoryTree(nodes, result = [], prefix = '') {
  nodes.forEach(n => {
    result.push({ id: n.id, name: prefix + n.name })
    if (n.children?.length) flattenCategoryTree(n.children, result, prefix + n.name + ' / ')
  })
  return result
}

async function loadDetail() {
  loading.value = true
  try {
    const [prodRes, catRes, hRes] = await Promise.all([
      getProduct(route.params.id),
      getProductCategoryTree(),
      getHeritageManList({ pageNum: 1, pageSize: 999 })
    ])
    form.value = prodRes.data || {}
    categoryOptions.value = flattenCategoryTree(catRes.data || [])
    heritageManOptions.value = (hRes.data?.list || []).map(h => ({ id: h.id, name: h.name }))
    originalData.value = JSON.stringify(form.value)
  } catch {
    form.value = {}
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateProduct(form.value)
    originalData.value = JSON.stringify(form.value)
    ElMessage.success(t('common.updated'))
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadDetail)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';

.detail-card {
  padding: 32px;
}

.detail-header {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.detail-cover {
  width: 200px;
  height: 200px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
  background: #f9fafb;
  border: 1px solid #f3f4f6;
  position: relative;
  cursor: pointer;
  transition: box-shadow 0.2s;

  :deep(.el-image) {
    width: 100%;
    height: 100%;
  }

  &.placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &:hover {
    box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.2);
    .cover-upload-overlay { opacity: 1; }
  }
}

.cover-upload-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 36px;
  background: rgba(0,0,0,0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s;
  border-radius: 0 0 12px 12px;
}

.detail-meta {
  flex: 1;
  min-width: 0;
}

.detail-title {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 8px;
}

.detail-subtitle {
  font-size: 14px;
  color: #9ca3af;
  margin: 0 0 12px;
}

.detail-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-price {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.price-current {
  font-size: 28px;
  font-weight: 700;
  color: #ef4444;
}

.price-original {
  font-size: 16px;
  color: #9ca3af;
  text-decoration: line-through;
}

.detail-section {
  margin-top: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f3f4f6;
}

.section-body {
  font-size: 14px;
  color: #374151;
  line-height: 1.8;
  white-space: pre-wrap;
}

.image-gallery, .video-gallery {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.gallery-item-wrapper {
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f3f4f6;
  &:hover .gallery-remove { opacity: 1; }
}

.gallery-item {
  width: 120px;
  height: 120px;
  cursor: pointer;
}

.gallery-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0,0,0,0.55);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 2;
}

.gallery-add {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  border: 2px dashed #d1d5db;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s;
  &:hover { border-color: #6366f1; }
}

.video-item {
  position: relative;
  width: 240px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f3f4f6;
  &:hover .gallery-remove { opacity: 1; }
  video { width: 100%; display: block; }
}

.video-add {
  width: 240px;
  height: 135px;
}
</style>
