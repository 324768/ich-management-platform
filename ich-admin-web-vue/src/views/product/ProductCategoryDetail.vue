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
          <div class="detail-icon" @click="triggerIconInput">
            <el-image v-if="form.icon" :src="form.icon" fit="cover" />
            <el-icon v-else :size="32" color="#d1d5db"><Grid /></el-icon>
            <div class="icon-upload-overlay">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            </div>
          </div>
          <input ref="iconInputRef" type="file" accept="image/*" style="display:none" @change="handleIconFileChange" />
          <div class="detail-meta">
            <h2 class="detail-title">{{ form.name || '-' }}</h2>
            <div class="detail-tags">
              <el-tag v-if="form.level" size="small" round type="warning">{{ form.level === 1 ? t('common.topLevel') : `${t('common.level')} ${form.level}` }}</el-tag>
              <el-tag :type="form.status === 1 ? 'success' : 'info'" size="small" round>{{ form.status === 1 ? t('common.active') : t('common.disabled') }}</el-tag>
            </div>
            <p class="detail-desc">{{ form.description || '-' }}</p>
          </div>
        </div>

        <el-divider />

        <el-descriptions :column="2" border class="detail-edit-field">
          <el-descriptions-item :label="t('common.id')">{{ form.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" :active-text="t('common.active')" :inactive-text="t('common.disabled')" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.name')">
            <el-input v-model="form.name" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.sort')">
            <el-input-number v-model="form.sort" :min="0" :controls="false" size="small" style="width: 80px" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.parent')">
            <el-select v-model="form.parentId" :placeholder="t('common.topLevel')" size="small" style="width: 100%">
              <el-option :label="t('common.topLevel')" :value="0" />
              <el-option v-for="c in availableParents" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.level')">
            {{ form.level }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.belongCategory')">
            <el-select v-model="form.ichCategoryId" :placeholder="t('common.selectCategory')" clearable size="small" style="width: 100%">
              <el-option v-for="c in ichCategoryOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.description')" :span="2">
            <el-input v-model="form.description" size="small" />
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <h3 class="section-title">{{ t('product.category.detailImages') }}</h3>
          <div class="image-gallery" v-if="detailImageList.length">
            <div class="gallery-item-wrapper" v-for="(img, idx) in detailImageList" :key="idx">
              <el-image :src="img" fit="cover" :preview-src-list="detailImageList" :initial-index="idx" preview-teleported class="gallery-item" />
              <div class="gallery-remove" @click="removeDetailImage(idx)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </div>
            </div>
            <div class="gallery-add" @click="triggerDetailImageInput">
              <el-icon :size="24" color="#9ca3af"><Plus /></el-icon>
            </div>
          </div>
          <div v-else class="image-empty" @click="triggerDetailImageInput">
            <el-icon :size="32" color="#d1d5db"><Picture /></el-icon>
            <span>{{ t('common.add') }}{{ t('product.category.detailImages') }}</span>
          </div>
          <input ref="detailImageInputRef" type="file" accept="image/*" multiple style="display:none" @change="handleDetailImageFiles" />
        </div>

        <div class="detail-section">
          <h3 class="section-title">介绍视频</h3>
          <div class="video-gallery">
            <div class="video-item" v-for="(v, idx) in videoList" :key="'vid-'+idx">
              <video :src="v" controls preload="metadata" />
              <div class="gallery-remove video-remove" @click="removeVideo(idx)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </div>
            </div>
            <div class="gallery-add video-add" @click="$refs.videoInput.click()">
              <el-icon :size="24" color="#9ca3af"><VideoCamera /></el-icon>
            </div>
          </div>
          <input ref="videoInput" type="file" accept="video/*" multiple style="display:none" @change="handleVideoAdd" />
        </div>

        <div class="detail-section" v-if="childCategories.length">
          <h3 class="section-title">子分类</h3>
          <div class="child-list">
            <div class="child-item" v-for="child in childCategories" :key="child.id" @click="$router.push(`/product/category/${child.id}`)">
              <el-image v-if="child.icon" :src="child.icon" fit="cover" class="child-icon" />
              <div v-else class="child-icon placeholder"><el-icon :size="16" color="#d1d5db"><Grid /></el-icon></div>
              <div class="child-info">
                <span class="child-name">{{ child.name }}</span>
                <span class="child-desc">{{ child.description || '-' }}</span>
              </div>
              <el-tag :type="child.status === 1 ? 'success' : 'info'" size="small" round>{{ child.status === 1 ? t('common.active') : t('common.disabled') }}</el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getProductCategory, getProductCategoryTree, updateProductCategory } from '@/api/product'
import { getCategoryTree } from '@/api/content'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const form = ref({})
const originalData = ref('')
const loading = ref(false)
const saving = ref(false)
const iconInputRef = ref(null)
const detailImageInputRef = ref(null)
const allCategories = ref([])
const ichCategoryOptions = ref([])

const hasChanges = computed(() => originalData.value && JSON.stringify(form.value) !== originalData.value)

const parentName = computed(() => {
  if (!form.value.parentId || form.value.parentId === 0) return t('common.topLevel')
  const parent = findCategoryById(allCategories.value, form.value.parentId)
  return parent ? parent.name : '-'
})

const childCategories = computed(() => {
  return findChildrenById(allCategories.value, form.value.id) || []
})

const availableParents = computed(() => {
  const result = []
  const selfId = form.value.id
  function collectExcludingSelf(nodes) {
    for (const n of nodes) {
      if (n.id !== selfId) result.push({ id: n.id, name: n.name })
      if (n.children) collectExcludingSelf(n.children)
    }
  }
  collectExcludingSelf(allCategories.value)
  return result.filter(c => !isDescendant(allCategories.value, selfId, c.id))
})

function isDescendant(tree, parentId, childId) {
  const parent = findCategoryById(tree, parentId)
  if (!parent?.children) return false
  for (const c of parent.children) {
    if (c.id === childId) return true
    if (isDescendant([c], c.id, childId)) return true
  }
  return false
}

const detailImageList = computed(() => {
  if (!form.value.detailImages) return []
  return Array.isArray(form.value.detailImages) ? form.value.detailImages : []
})

const videoList = computed(() => form.value.videos || [])

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

function findCategoryById(tree, id) {
  for (const node of tree) {
    if (node.id === id) return node
    if (node.children) {
      const found = findCategoryById(node.children, id)
      if (found) return found
    }
  }
  return null
}

function findChildrenById(tree, id) {
  const node = findCategoryById(tree, id)
  return node?.children || []
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
    const [catRes, treeRes, ichTreeRes] = await Promise.all([
      getProductCategory(route.params.id),
      getProductCategoryTree(),
      getCategoryTree()
    ])
    form.value = catRes.data || {}
    allCategories.value = treeRes.data || []
    ichCategoryOptions.value = flattenCategoryTree(ichTreeRes.data || [])
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
    await updateProductCategory(form.value)
    originalData.value = JSON.stringify(form.value)
    ElMessage.success(t('common.updated'))
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function triggerIconInput() {
  iconInputRef.value?.click()
}

function handleIconFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    form.value.icon = ev.target.result
  }
  reader.readAsDataURL(file)
}

function triggerDetailImageInput() {
  detailImageInputRef.value?.click()
}

function handleDetailImageFiles(e) {
  const files = e.target.files
  if (!files || files.length === 0) return
  const promises = Array.from(files).map(file => {
    return new Promise((resolve) => {
      const reader = new FileReader()
      reader.onload = (ev) => resolve(ev.target.result)
      reader.readAsDataURL(file)
    })
  })
  Promise.all(promises).then(results => {
    if (!form.value.detailImages) form.value.detailImages = []
    form.value.detailImages = [...form.value.detailImages, ...results]
  })
  e.target.value = ''
}

function removeDetailImage(idx) {
  form.value.detailImages = form.value.detailImages.filter((_, i) => i !== idx)
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

.detail-icon {
  width: 100px;
  height: 100px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
  background: #f9fafb;
  border: 1px solid #f3f4f6;
  position: relative;
  cursor: pointer;
  transition: box-shadow 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;

  :deep(.el-image) {
    width: 100%;
    height: 100%;
  }

  &:hover {
    box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.2);
    .icon-upload-overlay { opacity: 1; }
  }
}

.icon-upload-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 28px;
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
  margin: 0 0 12px;
}

.detail-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-desc {
  font-size: 14px;
  color: #6b7280;
  line-height: 1.6;
  margin: 0;
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

.image-gallery {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.gallery-item-wrapper {
  position: relative;
}

.gallery-item {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f3f4f6;
  cursor: pointer;
}

.gallery-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #ef4444;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
  .gallery-item-wrapper:hover & { opacity: 1; }
}

.gallery-add {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  border: 2px dashed #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s;
  &:hover { border-color: #6366f1; }
}

.image-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  border: 2px dashed #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.2s;
  color: #9ca3af;
  font-size: 14px;
  &:hover { border-color: #6366f1; color: #6366f1; }
}

.video-gallery {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.video-item {
  position: relative;
  width: 240px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f3f4f6;
  &:hover .video-remove { opacity: 1; }
  video { width: 100%; display: block; }
}

.video-remove {
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

.video-add {
  width: 240px;
  height: 135px;
}

.child-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.child-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid #f3f4f6;
  cursor: pointer;
  transition: all 0.15s;
  &:hover { background: #f9fafb; border-color: #e5e7eb; }
}

.child-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
  &.placeholder {
    background: #f3f4f6;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.child-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.child-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.child-desc {
  font-size: 12px;
  color: #9ca3af;
}
</style>
