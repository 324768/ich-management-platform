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
          <div class="detail-cover" :class="{ placeholder: !form.coverImage }" @click="triggerCoverInput">
            <el-image v-if="form.coverImage" :src="form.coverImage" fit="cover" />
            <el-icon v-else :size="32" color="#d1d5db"><Picture /></el-icon>
            <div class="cover-upload-overlay">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            </div>
          </div>
          <input ref="coverInputRef" type="file" accept="image/*" style="display:none" @change="handleCoverChange" />
          <div class="detail-meta">
            <h2 class="detail-title">{{ form.name || '-' }}</h2>
            <div class="detail-tags">
              <el-tag size="small" round>{{ activityTypeLabel(form.activityType) }}</el-tag>
              <el-tag :type="statusTagType(form.status)" size="small" round>{{ statusLabel(form.status) }}</el-tag>
            </div>
            <p class="detail-desc">{{ form.description || '-' }}</p>
          </div>
        </div>

        <el-divider />

        <el-descriptions :column="2" border class="detail-edit-field">
          <el-descriptions-item :label="t('common.id')">{{ form.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-select v-model="form.status" size="small" style="width: 120px">
              <el-option v-for="s in [0,1,2,3,4]" :key="s" :label="statusLabel(s)" :value="s" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.name')" :span="2">
            <el-input v-model="form.name" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.type')">
            <el-select v-model="form.activityType" size="small" style="width: 100%">
              <el-option :label="t('activity.typeShow')" :value="1" />
              <el-option :label="t('activity.typeWorkshop')" :value="2" />
              <el-option :label="t('activity.typeLecture')" :value="3" />
              <el-option :label="t('activity.typeExhibition')" :value="4" />
              <el-option :label="t('activity.typeOther')" :value="5" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.location')">
            <el-input v-model="form.location" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.startTime')">
            <el-date-picker v-model="form.startTime" type="datetime" size="small" class="fixed-date-picker" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.endTime')">
            <el-date-picker v-model="form.endTime" type="datetime" size="small" class="fixed-date-picker" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.maxParticipants')">
            <el-input-number v-model="form.maxParticipants" :min="0" :controls="false" size="small" style="width: 100px" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.participants')">
            <span :class="{ 'text-danger': isRegistrationFull }">{{ form.currentParticipants || 0 }} / {{ form.maxParticipants || '∞' }}</span>
            <el-tag v-if="isRegistrationFull" type="danger" size="small" round style="margin-left: 8px;">已满</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.deadline')">
            <el-date-picker v-model="form.registrationDeadline" type="datetime" size="small" class="fixed-date-picker" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.contactPerson')">
            <el-input v-model="form.contactPerson" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.contactPhone')">
            <el-input v-model="form.contactPhone" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.organizer')">
            <el-input v-model="form.organizer" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.requester')">
            <el-input v-model="form.requester" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('activity.requestUnit')" :span="2">
            <el-input v-model="form.requestUnit" size="small" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.description')" :span="2">
            <el-input v-model="form.description" size="small" />
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <h3 class="section-title">{{ t('activity.content') }}</h3>
          <el-input v-model="form.content" type="textarea" :rows="5" class="detail-edit-field" />
        </div>

        <div class="detail-section">
          <h3 class="section-title">{{ t('activity.detailImages') }}</h3>
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
          <h3 class="section-title">{{ t('activity.videos') }}</h3>
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

        <el-divider />

        <!-- ========== 时间线 & 报名用户 — 按钮入口 ========== -->
        <div class="detail-section">
          <div class="action-btn-row">
            <el-button type="primary" plain @click="timelineDialogVisible = true">
              <el-icon><Clock /></el-icon>
              <span>{{ t('activity.timeline') }}</span>
            </el-button>
            <el-button type="primary" plain @click="registrationDialogVisible = true">
              <el-icon><User /></el-icon>
              <span>{{ t('activity.registrations') }}</span>
              <el-tag size="small" round type="info" style="margin-left: 8px;">{{ registrationTotal }}</el-tag>
            </el-button>
          </div>
        </div>

        <!-- ========== 时间线弹窗 ========== -->
        <el-dialog v-model="timelineDialogVisible" :title="t('activity.timeline')" width="780px" destroy-on-close>
          <div class="activity-timeline">
            <div class="tl-item" v-for="(phase, pi) in timelinePhases" :key="phase.key">
              <div class="tl-dot" :class="{ active: timeline[phase.key]?.time }"></div>
              <div class="tl-line" v-if="pi < timelinePhases.length - 1"></div>
              <div class="tl-content">
                <div class="tl-header">
                  <span class="tl-label">{{ phase.label }}</span>
                  <el-date-picker v-model="timeline[phase.key].time" type="datetime" size="small" :placeholder="t('activity.timelineTime')" style="width: 200px" />
                </div>
                <el-input v-model="timeline[phase.key].description" :placeholder="t('activity.timelineDesc')" size="small" style="margin: 8px 0;" />
                <div class="tl-media">
                  <div class="image-gallery" style="margin-bottom: 8px;">
                    <div class="gallery-item-wrapper" v-for="(img, idx) in (timeline[phase.key].images || [])" :key="'tl-img-'+pi+'-'+idx">
                      <el-image :src="img" fit="cover" :preview-src-list="timeline[phase.key].images" :initial-index="idx" preview-teleported class="gallery-item" />
                      <div class="gallery-remove" @click="timeline[phase.key].images.splice(idx, 1)"><el-icon :size="14"><Close /></el-icon></div>
                    </div>
                    <div class="gallery-add gallery-add-sm" @click="triggerTimelineFileInput(phase.key, 'image')">
                      <el-icon :size="18" color="#9ca3af"><Plus /></el-icon>
                    </div>
                  </div>
                  <div class="video-gallery">
                    <div class="video-item video-item-sm" v-for="(v, idx) in (timeline[phase.key].videos || [])" :key="'tl-vid-'+pi+'-'+idx">
                      <video :src="v" controls preload="metadata" />
                      <div class="gallery-remove" @click="timeline[phase.key].videos.splice(idx, 1)"><el-icon :size="14"><Close /></el-icon></div>
                    </div>
                    <div class="gallery-add gallery-add-sm video-add-sm" @click="triggerTimelineFileInput(phase.key, 'video')">
                      <el-icon :size="18" color="#9ca3af"><VideoCamera /></el-icon>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <input ref="tlFileInput" type="file" style="display:none" @change="handleTimelineFileChange" />
        </el-dialog>

        <!-- ========== 报名用户弹窗 ========== -->
        <el-dialog v-model="registrationDialogVisible" :title="t('activity.registrations')" width="780px" destroy-on-close @open="loadRegistrations">
          <el-table :data="registrationData" v-loading="regLoading" size="small">
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
        </el-dialog>

        <el-divider />

        <!-- ========== 用户评论 ========== -->
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
                  <el-popconfirm :title="t('activity.commentDeleteConfirm')" @confirm="handleDeleteComment(c.id)">
                    <template #reference><el-button link type="danger" size="small" style="margin-left: auto;"><el-icon><Delete /></el-icon></el-button></template>
                  </el-popconfirm>
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
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getActivity, updateActivity, updateActivityStatus, getActivityRecordsByActivityId, getActivityComments, deleteActivityComment } from '@/api/activity'
import { ElMessage } from 'element-plus'
import { Refresh, Clock, User } from '@element-plus/icons-vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const form = ref({})
const originalData = ref('')
const loading = ref(false)
const saving = ref(false)
const coverInputRef = ref(null)

const registrationData = ref([])
const regLoading = ref(false)
const regPageNum = ref(1)
const regPageSize = ref(5)
const registrationTotal = ref(0)

const timelineDialogVisible = ref(false)
const registrationDialogVisible = ref(false)

const commentList = ref([])
const commentLoading = ref(false)
const commentPageNum = ref(1)
const commentPageSize = ref(10)
const commentTotal = ref(0)
let commentTimer = null

const defaultPhase = () => ({ time: null, description: '', images: [], videos: [] })
const timeline = reactive({
  apply: defaultPhase(),
  prepare: defaultPhase(),
  ongoing: defaultPhase(),
  end: defaultPhase(),
})
const tlFileInput = ref(null)
let tlFileCtx = { phase: '', type: '' }

const timelinePhases = computed(() => [
  { key: 'apply', label: t('activity.timelineApply') },
  { key: 'prepare', label: t('activity.timelinePrepare') },
  { key: 'ongoing', label: t('activity.timelineOngoing') },
  { key: 'end', label: t('activity.timelineEnd') },
])

const isRegistrationFull = computed(() => {
  return form.value.maxParticipants > 0 && (form.value.currentParticipants || 0) >= form.value.maxParticipants
})

const hasChanges = computed(() => originalData.value && JSON.stringify({ ...form.value, _tl: timeline }) !== originalData.value)

const detailImageList = computed(() => form.value.detailImages || [])
const videoList = computed(() => form.value.videos || [])

function activityTypeLabel(type) {
  const map = { 1: t('activity.typeShow'), 2: t('activity.typeWorkshop'), 3: t('activity.typeLecture'), 4: t('activity.typeExhibition'), 5: t('activity.typeOther') }
  return map[type] || '-'
}

function statusLabel(s) {
  const map = { 0: t('activity.statusDraft'), 1: t('activity.statusOpen'), 2: t('activity.statusOngoing'), 3: t('activity.statusEnded'), 4: t('activity.statusCancelled') }
  return map[s] || '-'
}

function statusTagType(s) {
  return { 0: 'info', 1: 'success', 2: 'warning', 3: '', 4: 'danger' }[s] || 'info'
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

function triggerTimelineFileInput(phaseKey, type) {
  tlFileCtx = { phase: phaseKey, type }
  const input = tlFileInput.value
  if (input) {
    input.accept = type === 'image' ? 'image/*' : 'video/*'
    input.multiple = true
    input.click()
  }
}

function handleTimelineFileChange(e) {
  const files = e.target.files
  if (!files) return
  const phase = timeline[tlFileCtx.phase]
  if (!phase) return
  const arr = tlFileCtx.type === 'image' ? phase.images : phase.videos
  for (const file of files) {
    const reader = new FileReader()
    reader.onload = (ev) => { arr.push(ev.target.result) }
    reader.readAsDataURL(file)
  }
  e.target.value = ''
}

function triggerCoverInput() {
  coverInputRef.value?.click()
}

function handleCoverChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => { form.value.coverImage = ev.target.result }
  reader.readAsDataURL(file)
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
    originalData.value = JSON.stringify({ ...form.value, _tl: timeline })
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

async function handleSave() {
  saving.value = true
  try {
    const payload = { ...form.value }
    if (Array.isArray(payload.detailImages)) payload.detailImages = JSON.stringify(payload.detailImages)
    if (Array.isArray(payload.videos)) payload.videos = JSON.stringify(payload.videos)
    payload.timelineData = JSON.stringify(timeline)
    await updateActivity(payload)
    if (form.value.status !== JSON.parse(originalData.value).status) {
      await updateActivityStatus(form.value.id, form.value.status)
    }
    originalData.value = JSON.stringify(form.value)
    ElMessage.success(t('common.updated'))
  } catch {
    ElMessage.error(t('common.saveFailed') || '保存失败')
  } finally {
    saving.value = false
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

async function handleDeleteComment(id) {
  await deleteActivityComment(id)
  ElMessage.success(t('common.deleted'))
  loadComments()
}

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

// ========== 日期选择器固定宽度防跳动 ==========
.fixed-date-picker {
  width: 100% !important;
  :deep(.el-input) {
    width: 100% !important;
  }
  :deep(.el-input__wrapper) {
    width: 100% !important;
    box-sizing: border-box;
  }
  :deep(.el-input__suffix) {
    position: absolute;
    right: 8px;
  }
}

.action-btn-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.text-danger {
  color: #ef4444;
  font-weight: 600;
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

.gallery-add-sm {
  width: 80px;
  height: 80px;
}

.gallery-item-wrapper {
  width: 80px;
  height: 80px;
  .gallery-item { width: 80px; height: 80px; }
}

.video-item-sm {
  width: 180px;
  video { width: 100%; }
}

.video-add-sm {
  width: 180px;
  height: 100px;
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
