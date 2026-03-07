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
          <div class="detail-avatar" :class="{ clickable: true }" @click="triggerAvatarInput">
            <el-image v-if="form.avatar" :src="form.avatar" fit="cover" />
            <template v-else>
              <span class="avatar-letter">{{ (form.name || '?').charAt(0) }}</span>
            </template>
            <div class="avatar-upload-overlay">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            </div>
          </div>
          <input ref="avatarInputRef" type="file" accept="image/*" style="display:none" @change="handleAvatarChange" />
          <div class="detail-meta">
            <h2 class="detail-title">{{ form.name || '-' }}</h2>
            <div class="detail-tags">
              <el-tag size="small" round>{{ form.gender === 1 ? t('content.heritage.male') : t('content.heritage.female') }}</el-tag>
              <el-tag v-if="form.level" size="small" round type="warning">{{ {1:t('content.heritage.national'),2:t('content.heritage.provincial'),3:t('content.heritage.municipal')}[form.level] || form.level }}</el-tag>
              <el-tag :type="form.status === 1 ? 'success' : 'info'" size="small" round>{{ form.status === 1 ? t('content.heritage.active') : t('content.heritage.inactive') }}</el-tag>
            </div>
            <p class="detail-desc">{{ form.title || '-' }}</p>
          </div>
        </div>

        <el-divider />

        <el-descriptions :column="2" border class="detail-edit-field">
          <el-descriptions-item :label="t('common.id')">{{ form.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" :active-text="t('content.heritage.active')" :inactive-text="t('content.heritage.inactive')" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.name')">
            <el-input v-model="form.name" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.gender')">
            <el-radio-group v-model="form.gender">
              <el-radio :value="1">{{ t('content.heritage.male') }}</el-radio>
              <el-radio :value="2">{{ t('content.heritage.female') }}</el-radio>
            </el-radio-group>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.level')">
            <el-select v-model="form.level" :placeholder="t('content.heritage.selectLevel')" size="small" style="width:100%">
              <el-option :value="1" :label="t('content.heritage.national')" />
              <el-option :value="2" :label="t('content.heritage.provincial')" />
              <el-option :value="3" :label="t('content.heritage.municipal')" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.belongCategory')">
            <el-select v-model="form.categoryId" :placeholder="t('common.selectCategory')" clearable size="small" style="width: 100%">
              <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.belongItem')">
            <el-select v-model="form.itemId" :placeholder="t('common.selectItem')" clearable filterable size="small" style="width: 100%">
              <el-option v-for="item in itemOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.title')">
            <el-input v-model="form.title" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.phone')">
            <el-input v-model="form.phone" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.email')">
            <el-input v-model="form.email" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.idCard')" :span="2">
            <el-input v-model="form.idCard" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.address')" :span="2">
            <el-input v-model="form.address" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.skill')" :span="2">
            <el-input v-model="form.skill" size="small" />
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <h3 class="section-title">{{ t('content.heritage.introduction') }}</h3>
          <el-input v-model="form.introduction" type="textarea" :rows="3" class="detail-edit-field" />
        </div>

        <div class="detail-section">
          <h3 class="section-title">{{ t('content.heritage.achievement') }}</h3>
          <el-input v-model="form.achievement" type="textarea" :rows="3" class="detail-edit-field" />
        </div>

        <div class="detail-section">
          <h3 class="section-title">详情图片</h3>
          <div class="image-gallery">
            <div class="gallery-item-wrapper" v-for="(img, idx) in detailImageList" :key="'img-'+idx">
              <el-image :src="img" fit="cover" :preview-src-list="detailImageList" :initial-index="idx" preview-teleported class="gallery-item" />
              <div class="gallery-remove" @click="removeDetailImage(idx)"><el-icon :size="14"><Close /></el-icon></div>
            </div>
            <div class="gallery-add" @click="$refs.detailImageInput.click()">
              <el-icon :size="24" color="#9ca3af"><Plus /></el-icon>
            </div>
          </div>
          <input ref="detailImageInput" type="file" accept="image/*" multiple style="display:none" @change="handleDetailImageAdd" />
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
import { getHeritageMan, updateHeritageMan, getCategoryTree, getItemList } from '@/api/content'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const form = ref({})
const originalData = ref('')
const loading = ref(false)
const saving = ref(false)
const avatarInputRef = ref(null)
const categoryOptions = ref([])
const itemOptions = ref([])

const hasChanges = computed(() => originalData.value && JSON.stringify(form.value) !== originalData.value)

const detailImageList = computed(() => form.value.detailImages || [])
const videoList = computed(() => form.value.videos || [])

function removeDetailImage(idx) {
  if (!form.value.detailImages) return
  form.value.detailImages.splice(idx, 1)
}

function removeVideo(idx) {
  if (!form.value.videos) return
  form.value.videos.splice(idx, 1)
}

function handleDetailImageAdd(e) {
  const files = e.target.files
  if (!files) return
  if (!form.value.detailImages) form.value.detailImages = []
  for (const file of files) {
    const reader = new FileReader()
    reader.onload = (ev) => { form.value.detailImages.push(ev.target.result) }
    reader.readAsDataURL(file)
  }
  e.target.value = ''
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
    const [detailRes, catRes, itemRes] = await Promise.all([
      getHeritageMan(route.params.id),
      getCategoryTree(),
      getItemList({ pageNum: 1, pageSize: 999 })
    ])
    form.value = detailRes.data || {}
    categoryOptions.value = flattenCategoryTree(catRes.data || [])
    itemOptions.value = (itemRes.data?.list || []).map(i => ({ id: i.id, name: i.name }))
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
    await updateHeritageMan(form.value)
    originalData.value = JSON.stringify(form.value)
    ElMessage.success(t('common.updated'))
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function triggerAvatarInput() {
  avatarInputRef.value?.click()
}

function handleAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    form.value.avatar = ev.target.result
  }
  reader.readAsDataURL(file)
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

.detail-avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border: 2px solid #f3f4f6;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;

  :deep(.el-image) {
    width: 100%;
    height: 100%;
  }

  &.clickable {
    cursor: pointer;
    transition: box-shadow 0.2s;
    &:hover {
      box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.2);
      .avatar-upload-overlay { opacity: 1; }
    }
  }
}

.avatar-upload-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 32px;
  background: rgba(0,0,0,0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-letter {
  font-size: 40px;
  font-weight: 700;
  color: #fff;
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
