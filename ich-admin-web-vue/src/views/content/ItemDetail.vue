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
          <div class="detail-cover" v-if="form.coverImage">
            <el-image :src="form.coverImage" fit="cover" :preview-src-list="[form.coverImage]" preview-teleported />
          </div>
          <div class="detail-cover placeholder" v-else>
            <el-icon :size="32" color="#d1d5db"><Picture /></el-icon>
          </div>
          <div class="detail-meta">
            <h2 class="detail-title">{{ form.name || '-' }}</h2>
            <div class="detail-tags">
              <el-tag v-if="form.level" size="small" round type="warning">{{ t('common.level') }} {{ form.level }}</el-tag>
              <el-tag :type="form.status === 1 ? 'success' : 'info'" size="small" round>{{ form.status === 1 ? t('content.item.published') : t('content.item.draft') }}</el-tag>
            </div>
            <p class="detail-desc">{{ form.description || '-' }}</p>
          </div>
        </div>

        <el-divider />

        <el-descriptions :column="2" border class="detail-edit-field">
          <el-descriptions-item :label="t('common.id')">{{ form.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" :active-text="t('content.item.published')" :inactive-text="t('content.item.draft')" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.name')" :span="2">
            <el-input v-model="form.name" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.level')">
            <el-input-number v-model="form.level" :min="1" :max="5" :controls="false" size="small" style="width: 80px" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.item.regionName')">
            <el-input v-model="form.regionName" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.item.regionCode')">
            <el-input v-model="form.regionCode" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.item.declarationUnit')">
            <el-input v-model="form.declarationUnit" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.description')" :span="2">
            <el-input v-model="form.description" size="small" />
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <h3 class="section-title">{{ t('content.item.content') }}</h3>
          <el-input v-model="form.content" type="textarea" :rows="5" class="detail-edit-field" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getItem, updateItem } from '@/api/content'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const form = ref({})
const originalData = ref('')
const loading = ref(false)
const saving = ref(false)

const hasChanges = computed(() => originalData.value && JSON.stringify(form.value) !== originalData.value)

async function loadDetail() {
  loading.value = true
  try {
    const res = await getItem(route.params.id)
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
    await updateItem(form.value)
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
  width: 160px;
  height: 160px;
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
</style>
