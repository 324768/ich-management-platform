<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <button class="ag-btn-secondary" @click="$router.back()">
          <el-icon :size="14"><ArrowLeft /></el-icon>
          <span>{{ t('common.back') }}</span>
        </button>
        <el-tag type="info" size="large" round>{{ t('activity.readonlyHint') }}</el-tag>
      </div>

      <div class="ag-card detail-card" v-loading="loading">
        <div class="detail-header">
          <div class="detail-cover" :class="{ placeholder: !form.coverImage }">
            <el-image v-if="form.coverImage" :src="form.coverImage" fit="cover" />
            <el-icon v-else :size="32" color="#d1d5db"><Picture /></el-icon>
          </div>
          <div class="detail-meta">
            <h2 class="detail-title">{{ form.name || '-' }}</h2>
            <div class="detail-tags">
              <el-tag size="small" round>{{ activityTypeLabel(form.activityType) }}</el-tag>
              <el-tag size="small" round>{{ t('activity.statusEnded') }}</el-tag>
            </div>
            <p class="detail-desc">{{ form.description || '-' }}</p>
          </div>
        </div>

        <el-divider />

        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('common.id')">{{ form.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-tag size="small" round>{{ t('activity.statusEnded') }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.name')" :span="2">{{ form.name }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.type')">{{ activityTypeLabel(form.activityType) }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.location')">{{ form.location || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.startTime')">{{ formatDateTime(form.startTime) }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.endTime')">{{ formatDateTime(form.endTime) }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.maxParticipants')">{{ form.maxParticipants || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.participants')">{{ form.currentParticipants || 0 }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.deadline')">{{ formatDateTime(form.registrationDeadline) }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.contactPerson')">{{ form.contactPerson || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.contactPhone')">{{ form.contactPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.organizer')">{{ form.organizer || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.requester')">{{ form.requester || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('activity.requestUnit')" :span="2">{{ form.requestUnit || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.description')" :span="2">{{ form.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section" v-if="form.content">
          <h3 class="section-title">{{ t('activity.content') }}</h3>
          <div class="section-body">{{ form.content }}</div>
        </div>

        <div class="detail-section" v-if="detailImageList.length">
          <h3 class="section-title">{{ t('activity.detailImages') }}</h3>
          <div class="image-gallery">
            <div class="gallery-item-wrapper" v-for="(img, idx) in detailImageList" :key="'img-'+idx">
              <el-image :src="img" fit="cover" :preview-src-list="detailImageList" :initial-index="idx" preview-teleported class="gallery-item" />
            </div>
          </div>
        </div>

        <div class="detail-section" v-if="videoList.length">
          <h3 class="section-title">{{ t('activity.videos') }}</h3>
          <div class="video-gallery">
            <div class="video-item" v-for="(v, idx) in videoList" :key="'vid-'+idx">
              <video :src="v" controls preload="metadata" />
            </div>
          </div>
        </div>

        <el-divider />

        <!-- ========== 只读时间线 ========== -->
        <div class="detail-section" v-if="hasTimeline">
          <h3 class="section-title">{{ t('activity.timeline') }}</h3>
          <div class="activity-timeline">
            <div class="tl-item" v-for="(phase, pi) in timelinePhases" :key="phase.key">
              <div class="tl-dot" :class="{ active: timeline[phase.key]?.time }"></div>
              <div class="tl-line" v-if="pi < timelinePhases.length - 1"></div>
              <div class="tl-content">
                <div class="tl-header">
                  <span class="tl-label">{{ phase.label }}</span>
                  <span class="tl-time" v-if="timeline[phase.key]?.time">{{ formatDateTime(timeline[phase.key].time) }}</span>
                </div>
                <div class="tl-desc" v-if="timeline[phase.key]?.description">{{ timeline[phase.key].description }}</div>
                <div class="tl-media" v-if="(timeline[phase.key]?.images?.length) || (timeline[phase.key]?.videos?.length)">
                  <div class="image-gallery" v-if="timeline[phase.key]?.images?.length" style="margin-bottom: 8px;">
                    <div class="gallery-item-wrapper" v-for="(img, idx) in timeline[phase.key].images" :key="'tl-img-'+pi+'-'+idx">
                      <el-image :src="img" fit="cover" :preview-src-list="timeline[phase.key].images" :initial-index="idx" preview-teleported class="gallery-item" />
                    </div>
                  </div>
                  <div class="video-gallery" v-if="timeline[phase.key]?.videos?.length">
                    <div class="video-item video-item-sm" v-for="(v, idx) in timeline[phase.key].videos" :key="'tl-vid-'+pi+'-'+idx">
                      <video :src="v" controls preload="metadata" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <el-divider />

        <div class="detail-section">
          <div class="section-title-bar">
            <h3 class="section-title" style="margin-bottom:0; border-bottom:none; padding-bottom:0;">{{ t('activity.registrations') }}</h3>
            <el-tag size="small" round type="info">{{ registrationTotal }} {{ t('activity.registrationUnit') }}</el-tag>
          </div>
          <el-table :data="registrationData" v-loading="regLoading" style="margin-top: 12px;" size="small">
            <el-table-column prop="id" label="ID" width="60" align="center" />
            <el-table-column prop="userName" :label="t('activity.record.userName')" width="120" align="center" />
            <el-table-column prop="userPhone" :label="t('activity.record.userPhone')" width="140" align="center" />
            <el-table-column :label="t('activity.record.registrationTime')" width="160" align="center">
              <template #default="{ row }">{{ formatDate(row.registrationTime) }}</template>
            </el-table-column>
            <el-table-column :label="t('activity.record.checkInTime')" width="160" align="center">
              <template #default="{ row }">{{ row.checkInTime ? formatDate(row.checkInTime) : '-' }}</template>
            </el-table-column>
            <el-table-column :label="t('common.status')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="recordStatusType(row.status)" size="small" round>{{ recordStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="t('activity.record.remark')" min-width="100" show-overflow-tooltip />
          </el-table>
          <div class="ag-pagination" v-if="registrationTotal > regPageSize">
            <el-pagination v-model:current-page="regPageNum" v-model:page-size="regPageSize"
              :total="registrationTotal" :page-sizes="[5, 10, 20]" layout="total, prev, pager, next" small @change="loadRegistrations" />
          </div>
        </div>

        <el-divider />

        <!-- ========== 浏览记录按钮 ========== -->
        <div class="detail-section">
          <div class="section-title-bar">
            <h3 class="section-title" style="margin-bottom:0; border-bottom:none; padding-bottom:0;">{{ t('activity.viewLogs') }}</h3>
            <el-button size="small" type="primary" @click="showViewLogDialog = true">{{ t('activity.viewLogBtn') }}</el-button>
          </div>
        </div>

        <el-divider />

        <!-- ========== 实时评论 ========== -->
        <div class="detail-section">
          <div class="section-title-bar">
            <h3 class="section-title" style="margin-bottom:0; border-bottom:none; padding-bottom:0;">{{ t('activity.comments') }}</h3>
            <el-tag size="small" round type="info">{{ commentTotal }}</el-tag>
            <el-button size="small" @click="loadComments" :icon="Refresh" circle style="margin-left: auto;" />
          </div>
          <div class="comment-list" v-loading="commentLoading">
            <div v-if="commentList.length === 0 && !commentLoading" class="comment-empty">{{ t('activity.noComments') }}</div>
            <div class="comment-item" v-for="c in commentList" :key="c.id">
              <div class="comment-avatar">{{ (c.userName || '?')[0] }}</div>
              <div class="comment-body">
                <div class="comment-header">
                  <span class="comment-user">{{ c.userName || '-' }}</span>
                  <span class="comment-time">{{ formatDate(c.createTime) }}</span>
                </div>
                <div class="comment-text">{{ c.content }}</div>
              </div>
            </div>
          </div>
          <div class="ag-pagination" v-if="commentTotal > commentPageSize">
            <el-pagination v-model:current-page="commentPageNum" v-model:page-size="commentPageSize"
              :total="commentTotal" :page-sizes="[10, 20, 50]" layout="total, prev, pager, next" small @change="loadComments" />
          </div>
        </div>
      </div>

      <!-- ========== 浏览记录弹窗 ========== -->
      <el-dialog v-model="showViewLogDialog" :title="t('activity.viewLogs')" width="600px" destroy-on-close>
        <el-table :data="viewLogList" v-loading="viewLogLoading" size="small">
          <el-table-column prop="userName" :label="t('activity.viewLogUser')" width="150" align="center" />
          <el-table-column :label="t('activity.viewLogTime')" align="center">
            <template #default="{ row }">{{ formatDateTime(row.viewTime) }}</template>
          </el-table-column>
        </el-table>
        <div class="ag-pagination" v-if="viewLogTotal > viewLogPageSize" style="margin-top: 12px;">
          <el-pagination v-model:current-page="viewLogPageNum" v-model:page-size="viewLogPageSize"
            :total="viewLogTotal" layout="total, prev, pager, next" small @change="loadViewLogs" />
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getActivity, getActivityRecordsByActivityId, getActivityComments, getActivityViewLogs } from '@/api/activity'
import { Refresh } from '@element-plus/icons-vue'

const { t } = useI18n()
const route = useRoute()

const form = ref({})
const loading = ref(false)

const registrationData = ref([])
const regLoading = ref(false)
const regPageNum = ref(1)
const regPageSize = ref(5)
const registrationTotal = ref(0)

const commentList = ref([])
const commentLoading = ref(false)
const commentPageNum = ref(1)
const commentPageSize = ref(10)
const commentTotal = ref(0)
let commentTimer = null

const showViewLogDialog = ref(false)
const viewLogList = ref([])
const viewLogLoading = ref(false)
const viewLogPageNum = ref(1)
const viewLogPageSize = ref(20)
const viewLogTotal = ref(0)

const defaultPhase = () => ({ time: null, description: '', images: [], videos: [] })
const timeline = reactive({
  apply: defaultPhase(),
  prepare: defaultPhase(),
  ongoing: defaultPhase(),
  end: defaultPhase(),
})

const timelinePhases = computed(() => [
  { key: 'apply', label: t('activity.timelineApply') },
  { key: 'prepare', label: t('activity.timelinePrepare') },
  { key: 'ongoing', label: t('activity.timelineOngoing') },
  { key: 'end', label: t('activity.timelineEnd') },
])

const hasTimeline = computed(() => {
  return ['apply', 'prepare', 'ongoing', 'end'].some(k =>
    timeline[k]?.time || timeline[k]?.description || timeline[k]?.images?.length || timeline[k]?.videos?.length
  )
})

const detailImageList = computed(() => form.value.detailImages || [])
const videoList = computed(() => form.value.videos || [])

function activityTypeLabel(type) {
  const map = { 1: t('activity.typeShow'), 2: t('activity.typeWorkshop'), 3: t('activity.typeLecture'), 4: t('activity.typeExhibition'), 5: t('activity.typeOther') }
  return map[type] || '-'
}

function recordStatusLabel(s) {
  const map = { 0: t('activity.record.pending'), 1: t('activity.record.confirmed'), 2: t('activity.record.checkedIn'), 3: t('activity.record.cancelled') }
  return map[s] || '-'
}

function recordStatusType(s) {
  return { 0: 'warning', 1: 'success', 2: '', 3: 'danger' }[s] || 'info'
}

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function formatDateTime(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getActivity(route.params.id)
    const data = res.data || {}
    if (typeof data.detailImages === 'string') { try { data.detailImages = JSON.parse(data.detailImages) } catch { data.detailImages = [] } }
    if (typeof data.videos === 'string') { try { data.videos = JSON.parse(data.videos) } catch { data.videos = [] } }
    if (!Array.isArray(data.detailImages)) data.detailImages = []
    if (!Array.isArray(data.videos)) data.videos = []
    // 解析时间线
    let tlData = {}
    if (typeof data.timelineData === 'string') { try { tlData = JSON.parse(data.timelineData) } catch { tlData = {} } }
    for (const k of ['apply', 'prepare', 'ongoing', 'end']) {
      const src = tlData[k] || {}
      timeline[k].time = src.time || null
      timeline[k].description = src.description || ''
      timeline[k].images = Array.isArray(src.images) ? [...src.images] : []
      timeline[k].videos = Array.isArray(src.videos) ? [...src.videos] : []
    }
    form.value = data
  } catch {
    form.value = {}
  } finally {
    loading.value = false
  }
}

async function loadRegistrations() {
  regLoading.value = true
  try {
    const res = await getActivityRecordsByActivityId(route.params.id, { pageNum: regPageNum.value, pageSize: regPageSize.value })
    registrationData.value = res.data?.list || []
    registrationTotal.value = res.data?.total || 0
  } catch {
    registrationData.value = []
  } finally {
    regLoading.value = false
  }
}

async function loadComments() {
  commentLoading.value = true
  try {
    const res = await getActivityComments(route.params.id, { pageNum: commentPageNum.value, pageSize: commentPageSize.value })
    commentList.value = res.data?.list || []
    commentTotal.value = res.data?.total || 0
  } catch {
    commentList.value = []
  } finally {
    commentLoading.value = false
  }
}

async function loadViewLogs() {
  viewLogLoading.value = true
  try {
    const res = await getActivityViewLogs(route.params.id, { pageNum: viewLogPageNum.value, pageSize: viewLogPageSize.value })
    viewLogList.value = res.data?.list || []
    viewLogTotal.value = res.data?.total || 0
  } catch {
    viewLogList.value = []
  } finally {
    viewLogLoading.value = false
  }
}

watch(showViewLogDialog, (val) => {
  if (val) loadViewLogs()
})

onMounted(() => {
  loadDetail()
  loadRegistrations()
  loadComments()
  commentTimer = setInterval(loadComments, 15000)
})

onUnmounted(() => {
  if (commentTimer) clearInterval(commentTimer)
})
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
  position: relative;

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

.section-title-bar {
  display: flex;
  align-items: center;
  gap: 12px;
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
}

.gallery-item {
  width: 120px;
  height: 120px;
  cursor: pointer;
}

.video-item {
  position: relative;
  width: 240px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f3f4f6;
  video { width: 100%; display: block; }
}

// ========== 时间线 ==========
.activity-timeline {
  position: relative;
  padding-left: 28px;
}

.tl-item {
  position: relative;
  padding-bottom: 28px;
  &:last-child { padding-bottom: 0; }
}

.tl-dot {
  position: absolute;
  left: -28px;
  top: 6px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #d1d5db;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px #e5e7eb;
  z-index: 1;
  &.active { background: #6366f1; box-shadow: 0 0 0 2px #c7d2fe; }
}

.tl-line {
  position: absolute;
  left: -23px;
  top: 20px;
  bottom: 0;
  width: 2px;
  background: #e5e7eb;
}

.tl-content {
  background: #f9fafb;
  border-radius: 10px;
  padding: 14px 18px;
  border: 1px solid #f3f4f6;
}

.tl-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tl-label {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.tl-time {
  font-size: 13px;
  color: #6b7280;
}

.tl-desc {
  font-size: 14px;
  color: #374151;
  margin-top: 6px;
  line-height: 1.6;
}

.tl-media {
  margin-top: 10px;
}

.video-item-sm {
  width: 180px;
  video { width: 100%; }
}

// ========== 评论 ==========
.comment-list {
  margin-top: 12px;
  min-height: 60px;
}

.comment-empty {
  text-align: center;
  color: #9ca3af;
  padding: 24px 0;
  font-size: 14px;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
  &:last-child { border-bottom: none; }
}

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e0e7ff;
  color: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-user {
  font-weight: 600;
  font-size: 13px;
  color: #111827;
}

.comment-time {
  font-size: 12px;
  color: #9ca3af;
}

.comment-text {
  font-size: 14px;
  color: #374151;
  line-height: 1.6;
  margin-top: 4px;
}
</style>
