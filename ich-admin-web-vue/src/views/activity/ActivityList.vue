<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <nav class="ag-sub-pills">
          <router-link to="/activity/list" class="ag-sub-pill" :class="{ active: $route.path === '/activity/list' }">{{ t('activity.tabs.list') }}</router-link>
          <router-link to="/activity/record" class="ag-sub-pill" :class="{ active: $route.path === '/activity/record' }">{{ t('activity.tabs.records') }}</router-link>
          <router-link to="/activity/approval" class="ag-sub-pill" :class="{ active: $route.path === '/activity/approval' }">{{ t('activity.tabs.approval') }}</router-link>
        </nav>
        <div class="toolbar-actions">
          <button v-if="selectedIds.length" class="ag-btn-danger" @click="handleBatchDelete">
            <el-icon :size="14"><Delete /></el-icon>
            <span>{{ t('common.delete') }} ({{ selectedIds.length }})</span>
          </button>
          <button class="ag-btn" @click="openDialog()">
            <el-icon :size="14"><Plus /></el-icon>
            <span>{{ t('common.add') }}</span>
          </button>
        </div>
      </div>

      <div class="ag-card">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-input v-model="keyword" :placeholder="t('activity.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="statusFilter" :placeholder="t('common.status')" clearable style="width: 140px" @change="loadData">
              <el-option :label="t('activity.statusDraft')" :value="0" />
              <el-option :label="t('activity.statusOpen')" :value="1" />
              <el-option :label="t('activity.statusOngoing')" :value="2" />
              <el-option :label="t('activity.statusEnded')" :value="3" />
              <el-option :label="t('activity.statusCancelled')" :value="4" />
            </el-select>
            <el-select v-model="typeFilter" :placeholder="t('activity.type')" clearable style="width: 140px" @change="loadData">
              <el-option :label="t('activity.typeShow')" :value="1" />
              <el-option :label="t('activity.typeWorkshop')" :value="2" />
              <el-option :label="t('activity.typeLecture')" :value="3" />
              <el-option :label="t('activity.typeExhibition')" :value="4" />
              <el-option :label="t('activity.typeOther')" :value="5" />
            </el-select>
          </div>
        </div>

        <el-table :data="tableData" v-loading="loading" @selection-change="handleSelectionChange" @row-click="row => router.push('/activity/detail/' + row.id)" style="cursor: pointer;">
          <el-table-column type="selection" width="45" align="center" />
          <el-table-column prop="id" :label="t('common.id')" width="70" align="center" />
          <el-table-column :label="t('activity.cover')" width="80" align="center">
            <template #default="{ row }">
              <el-image v-if="row.coverImage" :src="row.coverImage" fit="cover" style="width: 48px; height: 48px; border-radius: 6px;" :preview-src-list="[row.coverImage]" preview-teleported />
              <span v-else style="color: #ccc;">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" :label="t('common.name')" width="200" show-overflow-tooltip />
          <el-table-column :label="t('activity.type')" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" round>{{ activityTypeLabel(row.activityType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="location" :label="t('activity.location')" min-width="120" show-overflow-tooltip />
          <el-table-column :label="t('activity.time')" min-width="160" align="center">
            <template #default="{ row }">
              <div style="font-size: 12px;">{{ formatDate(row.startTime) }}</div>
              <div style="font-size: 11px; color: #999;">~ {{ formatDate(row.endTime) }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('activity.participants')" width="100" align="center">
            <template #default="{ row }">
              {{ row.currentParticipants || 0 }} / {{ row.maxParticipants || '∞' }}
            </template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" round>{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <el-popconfirm :title="t('activity.deleteConfirm')" @confirm="handleDelete(row.id)">
                <template #reference><el-button link type="danger" size="small" @click.stop>{{ t('common.delete') }}</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="ag-pagination">
          <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
            :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @change="loadData" />
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('activity.editTitle') : t('activity.addTitle')" width="700px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('activity.type')">
              <el-select v-model="form.activityType" style="width: 100%">
                <el-option :label="t('activity.typeShow')" :value="1" />
                <el-option :label="t('activity.typeWorkshop')" :value="2" />
                <el-option :label="t('activity.typeLecture')" :value="3" />
                <el-option :label="t('activity.typeExhibition')" :value="4" />
                <el-option :label="t('activity.typeOther')" :value="5" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('activity.cover')">
          <el-upload v-model:file-list="coverFileList" list-type="picture-card" :auto-upload="false"
            :on-change="handleCoverChange" :on-remove="handleCoverRemove" accept="image/*" :limit="1">
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('activity.location')"><el-input v-model="form.location" /></el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('activity.startTime')">
              <el-date-picker v-model="form.startTime" type="datetime" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('activity.endTime')">
              <el-date-picker v-model="form.endTime" type="datetime" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('activity.maxParticipants')">
              <el-input-number v-model="form.maxParticipants" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('activity.deadline')">
              <el-date-picker v-model="form.registrationDeadline" type="datetime" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert v-if="form.maxParticipants >= 100" :title="t('activity.approval.largeActivityHint')" type="warning" show-icon :closable="false" style="margin-bottom: 16px;" />
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('activity.contactPerson')"><el-input v-model="form.contactPerson" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('activity.contactPhone')"><el-input v-model="form.contactPhone" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('common.description')"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item :label="t('activity.content')"><el-input v-model="form.content" type="textarea" :rows="5" /></el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">{{ t('activity.statusDraft') }}</el-radio>
            <el-radio :value="1">{{ t('activity.statusOpen') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getActivityList, getActivity, addActivity, updateActivity, deleteActivity } from '@/api/activity'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()
const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref(null)
const typeFilter = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const coverFileList = ref([])
const selectedIds = ref([])

const defaultForm = {
  name: '', activityType: 1, coverImage: '', description: '', content: '',
  location: '', startTime: null, endTime: null, maxParticipants: 0,
  registrationDeadline: null, contactPerson: '', contactPhone: '', status: 0
}
const form = ref({ ...defaultForm })
const rules = { name: [{ required: true, message: () => t('common.required'), trigger: 'blur' }] }

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

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function fileToBase64(file) {
  return new Promise(resolve => {
    const reader = new FileReader()
    reader.onload = e => resolve(e.target.result)
    reader.readAsDataURL(file)
  })
}

async function handleCoverChange(uploadFile) {
  if (uploadFile.raw) form.value.coverImage = await fileToBase64(uploadFile.raw)
}
function handleCoverRemove() { form.value.coverImage = '' }

async function loadData() {
  loading.value = true
  try {
    const res = await getActivityList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, status: statusFilter.value ?? undefined, activityType: typeFilter.value ?? undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    try {
      const res = await getActivity(row.id)
      form.value = { ...(res.data || row) }
      coverFileList.value = form.value.coverImage ? [{ name: 'cover', url: form.value.coverImage, uid: Date.now(), status: 'success' }] : []
    } catch {
      form.value = { ...row }
      coverFileList.value = []
    }
  } else {
    form.value = { ...defaultForm }
    coverFileList.value = []
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    isEdit.value ? await updateActivity(form.value) : await addActivity(form.value)
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await deleteActivity(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

function handleSelectionChange(rows) { selectedIds.value = rows.map(r => r.id) }

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(t('activity.batchDeleteConfirm', { count: selectedIds.value.length }), t('common.confirm'), { type: 'warning' })
    await Promise.all(selectedIds.value.map(id => deleteActivity(id)))
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    loadData()
  } catch { /* cancelled */ }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
:deep(.el-upload-list--picture-card .el-upload-list__item),
:deep(.el-upload--picture-card) {
  width: 100px;
  height: 100px;
}
</style>
