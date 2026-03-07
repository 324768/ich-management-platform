<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <button class="ag-btn-secondary" @click="$router.back()">
          <el-icon :size="14"><ArrowLeft /></el-icon>
          <span>{{ t('common.back') }}</span>
        </button>
        <button class="ag-btn" @click="handleEdit">
          <el-icon :size="14"><Edit /></el-icon>
          <span>{{ t('common.edit') }}</span>
        </button>
      </div>

      <div class="ag-card detail-card" v-loading="loading">
        <div class="detail-header">
          <div class="detail-avatar" v-if="detail.avatar">
            <el-image :src="detail.avatar" fit="cover" :preview-src-list="[detail.avatar]" preview-teleported />
          </div>
          <div class="detail-avatar placeholder" v-else>
            <span class="avatar-letter">{{ (detail.name || '?').charAt(0) }}</span>
          </div>
          <div class="detail-meta">
            <h2 class="detail-title">{{ detail.name || '-' }}</h2>
            <div class="detail-tags">
              <el-tag size="small" round>{{ detail.gender === 1 ? t('content.heritage.male') : t('content.heritage.female') }}</el-tag>
              <el-tag v-if="detail.level" size="small" round type="warning">{{ {1:t('content.heritage.national'),2:t('content.heritage.provincial'),3:t('content.heritage.municipal')}[detail.level] || detail.level }}</el-tag>
              <el-tag :type="detail.status === 1 ? 'success' : 'info'" size="small" round>{{ detail.status === 1 ? t('content.heritage.active') : t('content.heritage.inactive') }}</el-tag>
            </div>
            <p class="detail-desc">{{ detail.title || '-' }}</p>
          </div>
        </div>

        <el-divider />

        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('common.id')">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.gender')">{{ detail.gender === 1 ? t('content.heritage.male') : t('content.heritage.female') }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.level')">{{ {1:t('content.heritage.national'),2:t('content.heritage.provincial'),3:t('content.heritage.municipal')}[detail.level] || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.title')">{{ detail.title || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.phone')">{{ detail.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.email')">{{ detail.email || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.idCard')" :span="2">{{ detail.idCard || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.address')" :span="2">{{ detail.address || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('content.heritage.skill')" :span="2">{{ detail.skill || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section" v-if="detail.introduction">
          <h3 class="section-title">{{ t('content.heritage.introduction') }}</h3>
          <div class="section-body">{{ detail.introduction }}</div>
        </div>

        <div class="detail-section" v-if="detail.achievement">
          <h3 class="section-title">{{ t('content.heritage.achievement') }}</h3>
          <div class="section-body">{{ detail.achievement }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getHeritageMan } from '@/api/content'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const detail = ref({})
const loading = ref(false)

async function loadDetail() {
  loading.value = true
  try {
    const res = await getHeritageMan(route.params.id)
    detail.value = res.data || {}
  } catch {
    detail.value = {}
  } finally {
    loading.value = false
  }
}

function handleEdit() {
  router.push({ path: '/content/heritage', query: { editId: detail.value.id } })
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
  background: #f9fafb;
  border: 2px solid #f3f4f6;

  :deep(.el-image) {
    width: 100%;
    height: 100%;
  }

  &.placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
  }
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
</style>
