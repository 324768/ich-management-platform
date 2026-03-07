<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <span class="ag-page-title">视频展览管理</span>
        <div class="toolbar-actions">
          <button v-if="selectedIds.length" class="ag-btn-danger" @click="handleBatchDelete">
            <el-icon :size="14"><Delete /></el-icon>
            <span>批量删除 ({{ selectedIds.length }})</span>
          </button>
          <button class="ag-btn-secondary" @click="handleExport">
            <el-icon :size="14"><Download /></el-icon>
            <span>导出</span>
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
            <el-input v-model="searchKeyword" placeholder="搜索标题" clearable style="width: 200px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px" @change="loadData">
              <el-option label="上架" :value="1" />
              <el-option label="下架" :value="0" />
            </el-select>
          </div>
        </div>
        <el-table :data="filteredData" v-loading="loading" @row-click="handleRowClick" @selection-change="handleSelectionChange" class="cursor-row">
          <el-table-column type="selection" width="45" align="center" />
          <el-table-column prop="id" :label="t('common.id')" width="70" align="center" />
          <el-table-column label="封面" width="100" align="center">
            <template #default="{ row }">
              <el-image v-if="row.coverUrl" :src="row.coverUrl" fit="cover" style="width: 60px; height: 40px; border-radius: 4px;" :preview-src-list="[row.coverUrl]" preview-teleported />
              <span v-else style="color: #ccc;">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.title" size="small" @click.stop @change="handleInlineUpdate(row)" />
            </template>
          </el-table-column>
          <el-table-column label="时长" width="90" align="center">
            <template #default="{ row }">
              {{ row.duration ? formatDuration(row.duration) : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="viewCount" label="浏览" width="80" align="center" />
          <el-table-column prop="likeCount" label="点赞" width="80" align="center" />
          <el-table-column prop="sort" :label="t('common.sort')" width="90" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.sort" :min="0" :controls="false" size="small" style="width: 60px" @click.stop @change="handleInlineUpdate(row)" />
            </template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="90" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.status" :active-value="1" :inactive-value="0" size="small" @click.stop @change="handleInlineUpdate(row)" />
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="140" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openDialog(row)">{{ t('common.edit') }}</el-button>
              <el-popconfirm title="确定删除该视频吗？" @confirm="handleDelete(row.id)">
                <template #reference><el-button link type="danger" size="small" @click.stop>{{ t('common.delete') }}</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑视频' : '新增视频'" width="640px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="视频URL" prop="videoUrl"><el-input v-model="form.videoUrl" placeholder="输入视频链接或上传后的URL" /></el-form-item>
        <el-form-item label="封面URL"><el-input v-model="form.coverUrl" placeholder="输入封面图链接" /></el-form-item>
        <el-form-item label="时长(秒)"><el-input-number v-model="form.duration" :min="0" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
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
import { getVideoExhibitionList, addVideoExhibition, updateVideoExhibition, deleteVideoExhibition } from '@/api/video'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportToCSV } from '@/utils/export'

const { t } = useI18n()

const tableData = ref([])
const filteredData = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const statusFilter = ref(null)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const defaultForm = { title: '', videoUrl: '', coverUrl: '', duration: null, description: '', sort: 0, status: 1 }
const form = ref({ ...defaultForm })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  videoUrl: [{ required: true, message: '请输入视频URL', trigger: 'blur' }],
}

function formatDuration(s) {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${m}:${String(sec).padStart(2, '0')}`
}

async function loadData() {
  loading.value = true
  try {
    const res = await getVideoExhibitionList(statusFilter.value)
    tableData.value = res.data || []
    applyClientFilter()
  } finally { loading.value = false }
}

function applyClientFilter() {
  let data = [...tableData.value]
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    data = data.filter(r => r.title?.toLowerCase().includes(kw))
  }
  filteredData.value = data
}

function openDialog(row) {
  isEdit.value = !!row
  form.value = row ? { ...row } : { ...defaultForm }
  dialogVisible.value = true
}

const selectedIds = ref([])
function handleSelectionChange(rows) { selectedIds.value = rows.map(r => r.id) }

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条记录吗？`, '批量删除', { type: 'warning' })
    await Promise.all(selectedIds.value.map(id => deleteVideoExhibition(id)))
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    loadData()
  } catch { /* cancelled */ }
}

function handleExport() {
  exportToCSV(tableData.value, [
    { label: 'ID', key: 'id' },
    { label: '标题', key: 'title' },
    { label: '时长', key: 'duration', formatter: v => v ? formatDuration(v) : '' },
    { label: '浏览', key: 'viewCount' },
    { label: '点赞', key: 'likeCount' },
    { label: '排序', key: 'sort' },
    { label: '状态', key: 'status', formatter: v => v === 1 ? '上架' : '下架' },
  ], '视频展览')
}

function handleRowClick(row, column, event) {
  if (event.target.closest('.el-input, .el-input-number, .el-switch, .el-button, .el-popconfirm, .el-checkbox')) return
}

async function handleInlineUpdate(row) {
  try {
    await updateVideoExhibition(row)
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    isEdit.value ? await updateVideoExhibition(form.value) : await addVideoExhibition(form.value)
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await deleteVideoExhibition(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';

.ag-page-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}
</style>
