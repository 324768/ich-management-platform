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
          <div class="detail-cover" v-if="form.mainImage">
            <el-image :src="form.mainImage" fit="cover" :preview-src-list="allImages" preview-teleported />
          </div>
          <div class="detail-cover placeholder" v-else>
            <el-icon :size="32" color="#d1d5db"><ShoppingBag /></el-icon>
          </div>
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

        <div class="detail-section" v-if="subImageList.length">
          <h3 class="section-title">{{ t('product.item.subImages') }}</h3>
          <div class="image-gallery">
            <el-image v-for="(img, idx) in subImageList" :key="idx" :src="img" fit="cover" :preview-src-list="subImageList" :initial-index="idx" preview-teleported class="gallery-item" />
          </div>
        </div>

        <div class="detail-section">
          <h3 class="section-title">{{ t('product.item.detail') }}</h3>
          <el-input v-model="form.detailDesc" type="textarea" :rows="4" class="detail-edit-field" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getProduct, updateProduct } from '@/api/product'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const form = ref({})
const originalData = ref('')
const loading = ref(false)
const saving = ref(false)

const hasChanges = computed(() => originalData.value && JSON.stringify(form.value) !== originalData.value)

const subImageList = computed(() => {
  if (!form.value.subImages) return []
  try { return JSON.parse(form.value.subImages) } catch { return [] }
})

const allImages = computed(() => {
  const imgs = []
  if (form.value.mainImage) imgs.push(form.value.mainImage)
  imgs.push(...subImageList.value)
  return imgs
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getProduct(route.params.id)
    form.value = res.data || {}
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

  :deep(.el-image) {
    width: 100%;
    height: 100%;
  }

  &.placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
  }
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

.image-gallery {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.gallery-item {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f3f4f6;
  cursor: pointer;
}
</style>
