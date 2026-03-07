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
            <el-input v-model="keyword" :placeholder="t('content.heritage.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <button class="ag-btn-secondary" @click="showAdvanced = !showAdvanced">
              <el-icon :size="14"><Filter /></el-icon>
              <span>高级筛选</span>
            </button>
          </div>
        </div>
        <transition name="slide">
          <div v-show="showAdvanced" class="advanced-filter">
            <el-form :inline="true" size="small">
              <el-form-item label="性别">
                <el-select v-model="genderFilter" placeholder="全部" clearable style="width: 100px" @change="loadData">
                  <el-option :label="t('content.heritage.male')" :value="1" />
                  <el-option :label="t('content.heritage.female')" :value="2" />
                </el-select>
              </el-form-item>
              <el-form-item label="级别">
                <el-select v-model="levelFilter" placeholder="全部" clearable style="width: 120px" @change="loadData">
                  <el-option :label="t('content.heritage.national')" :value="1" />
                  <el-option :label="t('content.heritage.provincial')" :value="2" />
                  <el-option :label="t('content.heritage.municipal')" :value="3" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('common.status')">
                <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 100px" @change="loadData">
                  <el-option :label="t('content.heritage.active')" :value="1" />
                  <el-option :label="t('content.heritage.inactive')" :value="0" />
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
        <el-table-column prop="name" :label="t('common.name')" align="center" />
        <el-table-column prop="gender" :label="t('content.heritage.gender')" align="center">
          <template #default="{ row }">{{ row.gender === 1 ? t('content.heritage.male') : t('content.heritage.female') }}</template>
        </el-table-column>
        <el-table-column prop="level" :label="t('common.level')" align="center">
          <template #default="{ row }">{{ {1:t('content.heritage.national'),2:t('content.heritage.provincial'),3:t('content.heritage.municipal')}[row.level] || row.level }}</template>
        </el-table-column>
        <el-table-column :label="t('common.belongCategory')" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ getCategoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="title" :label="t('content.heritage.title')" show-overflow-tooltip align="center" />
        <el-table-column prop="phone" :label="t('user.phone')" align="center" />
        <el-table-column prop="status" :label="t('common.status')" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>{{ row.status === 1 ? t('content.heritage.active') : t('content.heritage.inactive') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">{{ t('common.edit') }}</el-button>
            <el-popconfirm :title="t('content.heritage.deleteConfirm')" @confirm="handleDelete(row.id)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('content.heritage.editTitle') : t('content.heritage.addTitle')" width="700px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="140px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('content.heritage.gender')">
              <el-radio-group v-model="form.gender">
                <el-radio :value="1">{{ t('content.heritage.male') }}</el-radio>
                <el-radio :value="2">{{ t('content.heritage.female') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('common.level')">
              <el-select v-model="form.level" :placeholder="t('content.heritage.selectLevel')" style="width:100%">
                <el-option :value="1" :label="t('content.heritage.national')" />
                <el-option :value="2" :label="t('content.heritage.provincial')" />
                <el-option :value="3" :label="t('content.heritage.municipal')" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('content.heritage.title')"><el-input v-model="form.title" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('user.phone')"><el-input v-model="form.phone" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('user.email')"><el-input v-model="form.email" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('common.belongCategory')">
              <el-select v-model="form.categoryId" :placeholder="t('common.selectCategory')" clearable style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.belongItem')">
              <el-select v-model="form.itemId" :placeholder="t('common.selectItem')" clearable filterable style="width: 100%">
                <el-option v-for="item in itemOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('content.heritage.idCard')"><el-input v-model="form.idCard" /></el-form-item>
        <el-form-item :label="t('content.heritage.address')"><el-input v-model="form.address" /></el-form-item>
        <el-form-item :label="t('content.heritage.skill')"><el-input v-model="form.skill" /></el-form-item>
        <el-form-item :label="t('content.heritage.introduction')"><el-input v-model="form.introduction" type="textarea" :rows="3" /></el-form-item>
        <el-form-item :label="t('content.heritage.achievement')"><el-input v-model="form.achievement" type="textarea" :rows="3" /></el-form-item>
        <el-form-item :label="t('content.heritage.avatarUrl')">
          <el-upload
            v-model:file-list="avatarFileList"
            list-type="picture-card"
            :auto-upload="false"
            :on-change="handleAvatarChange"
            :on-remove="handleAvatarRemove"
            accept="image/*"
            :limit="1"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">{{ t('content.heritage.active') }}</el-radio>
            <el-radio :value="0">{{ t('content.heritage.inactive') }}</el-radio>
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
import { getHeritageManList, getHeritageMan, addHeritageMan, updateHeritageMan, deleteHeritageMan, getCategoryTree, getItemList } from '@/api/content'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportToCSV } from '@/utils/export'

const { t } = useI18n()
const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const genderFilter = ref(null)
const levelFilter = ref(null)
const statusFilter = ref(null)
const showAdvanced = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const categoryOptions = ref([])
const itemOptions = ref([])
const defaultForm = { name: '', gender: 1, level: null, categoryId: null, itemId: null, title: '', phone: '', email: '', idCard: '', address: '', skill: '', introduction: '', achievement: '', avatar: '', status: 1 }
const form = ref({ ...defaultForm })
const avatarFileList = ref([])
const rules = { name: [{ required: true, message: () => t('common.required'), trigger: 'blur' }] }

function fileToBase64(file) {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.readAsDataURL(file)
  })
}

async function handleAvatarChange(uploadFile) {
  if (uploadFile.raw) {
    form.value.avatar = await fileToBase64(uploadFile.raw)
  }
}

function handleAvatarRemove() {
  form.value.avatar = ''
}

function getCategoryName(id) {
  if (!id) return '-'
  const found = categoryOptions.value.find(c => c.id === id)
  return found ? found.name : id
}

function flattenCategoryTree(nodes, result = [], prefix = '') {
  nodes.forEach(n => {
    result.push({ id: n.id, name: prefix + n.name })
    if (n.children?.length) flattenCategoryTree(n.children, result, prefix + n.name + ' / ')
  })
  return result
}

async function loadRelationOptions() {
  try {
    const [catRes, itemRes] = await Promise.all([
      getCategoryTree(),
      getItemList({ pageNum: 1, pageSize: 999 })
    ])
    categoryOptions.value = flattenCategoryTree(catRes.data || [])
    itemOptions.value = (itemRes.data?.list || []).map(i => ({ id: i.id, name: i.name }))
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getHeritageManList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    try {
      const res = await getHeritageMan(row.id)
      const detail = res.data || row
      form.value = { ...detail }
      avatarFileList.value = detail.avatar ? [{ name: 'avatar', url: detail.avatar, uid: Date.now(), status: 'success' }] : []
    } catch {
      form.value = { ...row }
      avatarFileList.value = []
    }
  } else {
    form.value = { ...defaultForm }
    avatarFileList.value = []
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    isEdit.value ? await updateHeritageMan(form.value) : await addHeritageMan(form.value)
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await deleteHeritageMan(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

const selectedIds = ref([])
function handleSelectionChange(rows) { selectedIds.value = rows.map(r => r.id) }

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条记录吗？`, '批量删除', { type: 'warning' })
    await Promise.all(selectedIds.value.map(id => deleteHeritageMan(id)))
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    loadData()
  } catch { /* cancelled */ }
}

function handleExport() {
  exportToCSV(tableData.value, [
    { label: 'ID', key: 'id' },
    { label: '姓名', key: 'name' },
    { label: '性别', key: 'gender', formatter: v => v === 1 ? '男' : '女' },
    { label: '级别', key: 'level', formatter: v => ({1:'国家级',2:'省级',3:'市级'})[v] || v },
    { label: '称号', key: 'title' },
    { label: '电话', key: 'phone' },
    { label: '状态', key: 'status', formatter: v => v === 1 ? '启用' : '禁用' },
  ], '传承人')
}

function handleRowClick(row, column, event) {
  if (event.target.closest('.el-button, .el-popconfirm, .el-switch, .el-checkbox')) return
  router.push(`/content/heritage/${row.id}`)
}

function resetFilters() {
  keyword.value = ''
  genderFilter.value = null
  levelFilter.value = null
  statusFilter.value = null
  loadData()
}

onMounted(() => {
  loadData()
  loadRelationOptions()
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
