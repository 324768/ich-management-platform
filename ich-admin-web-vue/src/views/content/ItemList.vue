<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <nav class="ag-sub-pills">
          <router-link to="/content/category" class="ag-sub-pill" :class="{ active: $route.path === '/content/category' }">{{ t('content.tabs.category') }}</router-link>
          <router-link to="/content/item" class="ag-sub-pill" :class="{ active: $route.path === '/content/item' }">{{ t('content.tabs.items') }}</router-link>
          <router-link to="/content/heritage" class="ag-sub-pill" :class="{ active: $route.path === '/content/heritage' }">{{ t('content.tabs.heritageMan') }}</router-link>
        </nav>
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
            <el-input v-model="keyword" :placeholder="t('content.item.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="statusFilter" :placeholder="t('common.status')" clearable style="width: 140px" @change="loadData">
              <el-option :label="t('content.item.published')" :value="1" />
              <el-option :label="t('content.item.draft')" :value="0" />
            </el-select>
            <button class="ag-btn-secondary" @click="showAdvanced = !showAdvanced">
              <el-icon :size="14"><Filter /></el-icon>
              <span>高级筛选</span>
            </button>
          </div>
        </div>
        <transition name="slide">
          <div v-show="showAdvanced" class="advanced-filter">
            <el-form :inline="true" size="small">
              <el-form-item label="级别">
                <el-select v-model="levelFilter" placeholder="全部" clearable style="width: 120px" @change="loadData">
                  <el-option v-for="i in 5" :key="i" :label="`${i}级`" :value="i" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="small" @click="loadData">查询</el-button>
                <el-button size="small" @click="resetFilters">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </transition>
        <el-table :data="tableData" v-loading="loading" @row-click="handleRowClick" @selection-change="handleSelectionChange" style="cursor: pointer;">
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column prop="id" :label="t('common.id')" width="70" align="center" />
        <el-table-column prop="name" :label="t('common.name')" show-overflow-tooltip align="center" />
        <el-table-column :label="t('common.belongCategory')" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ getCategoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="regionName" :label="t('content.item.regionName')" show-overflow-tooltip align="center" />
        <el-table-column prop="status" :label="t('common.status')" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>{{ row.status === 1 ? t('content.item.published') : t('content.item.draft') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">{{ t('common.edit') }}</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'" size="small"
              @click="handleToggleStatus(row)">{{ row.status === 1 ? t('content.item.unpublish') : t('content.item.publish') }}</el-button>
            <el-popconfirm :title="t('content.item.deleteConfirm')" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger" size="small">{{ t('common.delete') }}</el-button></template>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('content.item.editTitle') : t('content.item.addTitle')" width="700px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="130px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.level')"><el-input-number v-model="form.level" :min="1" :max="5" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('common.belongCategory')">
          <el-select v-model="form.categoryId" :placeholder="t('common.selectCategory')" clearable style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('content.item.regionName')"><el-input v-model="form.regionName" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('content.item.regionCode')"><el-input v-model="form.regionCode" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('content.item.coverImage')">
          <el-upload
            v-model:file-list="coverFileList"
            list-type="picture-card"
            :auto-upload="false"
            :on-change="handleCoverChange"
            :on-remove="handleCoverRemove"
            accept="image/*"
            :limit="1"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('common.description')"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item :label="t('content.item.content')"><el-input v-model="form.content" type="textarea" :rows="5" /></el-form-item>
        <el-form-item :label="t('content.item.declarationUnit')"><el-input v-model="form.declarationUnit" /></el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">{{ t('content.item.published') }}</el-radio>
            <el-radio :value="0">{{ t('content.item.draft') }}</el-radio>
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
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { getItemList, getItem, addItem, updateItem, deleteItem, updateItemStatus, getCategoryTree } from '@/api/content'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportToCSV } from '@/utils/export'

const { t } = useI18n()
const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref(null)
const levelFilter = ref(null)
const showAdvanced = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const categoryOptions = ref([])
const defaultForm = { name: '', level: null, categoryId: null, regionName: '', regionCode: '', coverImage: '', description: '', content: '', declarationUnit: '', status: 0 }
const form = ref({ ...defaultForm })
const coverFileList = ref([])
const rules = { name: [{ required: true, message: () => t('common.required'), trigger: 'blur' }] }

function fileToBase64(file) {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.readAsDataURL(file)
  })
}

async function handleCoverChange(uploadFile) {
  if (uploadFile.raw) {
    form.value.coverImage = await fileToBase64(uploadFile.raw)
  }
}

function handleCoverRemove() {
  form.value.coverImage = ''
}

function flattenCategoryTree(nodes, result = [], prefix = '') {
  nodes.forEach(n => {
    result.push({ id: n.id, name: prefix + n.name })
    if (n.children?.length) flattenCategoryTree(n.children, result, prefix + n.name + ' / ')
  })
  return result
}

function getCategoryName(id) {
  if (!id) return '-'
  const found = categoryOptions.value.find(c => c.id === id)
  return found ? found.name : id
}

async function loadCategoryOptions() {
  try {
    const res = await getCategoryTree()
    categoryOptions.value = flattenCategoryTree(res.data || [])
  } catch { categoryOptions.value = [] }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getItemList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, status: statusFilter.value ?? undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    try {
      const res = await getItem(row.id)
      const detail = res.data || row
      form.value = { ...detail }
      coverFileList.value = detail.coverImage ? [{ name: 'cover', url: detail.coverImage, uid: Date.now(), status: 'success' }] : []
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
    isEdit.value ? await updateItem(form.value) : await addItem(form.value)
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleToggleStatus(row) {
  await updateItemStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(t('common.statusUpdated'))
  loadData()
}

async function handleDelete(id) {
  await deleteItem(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

const selectedIds = ref([])
function handleSelectionChange(rows) { selectedIds.value = rows.map(r => r.id) }

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条记录吗？`, '批量删除', { type: 'warning' })
    await Promise.all(selectedIds.value.map(id => deleteItem(id)))
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    loadData()
  } catch { /* cancelled */ }
}

function handleExport() {
  exportToCSV(tableData.value, [
    { label: 'ID', key: 'id' },
    { label: '名称', key: 'name' },
    { label: '级别', key: 'level' },
    { label: '区域', key: 'regionName' },
    { label: '状态', key: 'status', formatter: v => v === 1 ? '已发布' : '草稿' },
  ], '非遗项目')
}

function handleRowClick(row, column, event) {
  if (event.target.closest('.el-button, .el-popconfirm, .el-switch, .el-checkbox')) return
  router.push(`/content/item/${row.id}`)
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = null
  levelFilter.value = null
  loadData()
}

onMounted(() => {
  loadData()
  loadCategoryOptions()
})
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
:deep(.el-upload-list--picture-card .el-upload-list__item),
:deep(.el-upload--picture-card) {
  width: 100px;
  height: 100px;
}
</style>
