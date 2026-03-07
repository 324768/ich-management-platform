<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <nav class="ag-sub-pills">
          <router-link to="/product/category" class="ag-sub-pill" :class="{ active: $route.path === '/product/category' }">{{ t('product.tabs.category') }}</router-link>
          <router-link to="/product/list" class="ag-sub-pill" :class="{ active: $route.path === '/product/list' }">{{ t('product.tabs.list') }}</router-link>
        </nav>
        <div class="toolbar-actions">
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
            <el-input v-model="searchKeyword" placeholder="搜索分类名称" clearable style="width: 200px" @clear="applyClientFilter" @keyup.enter="applyClientFilter">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px" @change="applyClientFilter">
              <el-option :label="t('common.active')" :value="1" />
              <el-option :label="t('common.disabled')" :value="0" />
            </el-select>
          </div>
        </div>
        <el-table :data="filteredData" v-loading="loading" row-key="id" default-expand-all :indent="48" @row-click="handleRowClick" style="cursor: pointer;">
        <el-table-column prop="name" :label="t('common.name')" width="280" align="center" />
        <el-table-column :label="t('product.category.icon')" align="center">
          <template #default="{ row }">
            <el-image v-if="row.icon" :src="row.icon" fit="cover" style="width: 36px; height: 36px; border-radius: 6px;" :preview-src-list="[row.icon]" preview-teleported />
            <span v-else style="color: #ccc;">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('product.category.detailImages')" align="center">
          <template #default="{ row }">
            <span style="color: #6366f1; font-weight: 500;">{{ row.detailImages ? (Array.isArray(row.detailImages) ? row.detailImages.length : 0) : 0 }}{{ t('common.imageUnit') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.belongCategory')" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ getIchCategoryName(row.ichCategoryId) }}</template>
        </el-table-column>
        <el-table-column prop="description" :label="t('common.description')" align="center">
          <template #default="{ row }">
            <span>{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>{{ row.status === 1 ? t('common.active') : t('common.disabled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="180" align="center">
          <template #default="{ row }">
            <el-button v-if="row.level < 3" link type="success" size="small" @click="openAddChild(row)">{{ t('common.add') }}</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">{{ t('common.edit') }}</el-button>
            <el-popconfirm :title="t('product.category.deleteConfirm')" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger" size="small">{{ t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('product.category.editTitle') : t('product.category.addTitle')" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item :label="t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('common.parent')">
          <el-select v-model="form.parentId" clearable :placeholder="t('common.topLevel')" style="width: 100%">
            <el-option :label="t('common.topLevel')" :value="0" />
            <el-option v-for="c in topCategories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.sort')"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item :label="t('product.category.icon')">
          <el-upload
            v-model:file-list="iconFileList"
            list-type="picture-card"
            :auto-upload="false"
            :on-change="handleIconChange"
            :on-remove="handleIconRemove"
            accept="image/*"
            :limit="1"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('product.category.detailImages')">
          <el-upload
            v-model:file-list="uploadFileList"
            list-type="picture-card"
            :auto-upload="false"
            :on-change="handleImageChange"
            :on-remove="handleImageRemove"
            accept="image/*"
            multiple
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('common.description')"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">{{ t('common.active') }}</el-radio>
            <el-radio :value="0">{{ t('common.disabled') }}</el-radio>
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
import { ref, computed, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { getProductCategoryTree, getProductCategory, addProductCategory, updateProductCategory, deleteProductCategory } from '@/api/product'
import { getCategoryTree } from '@/api/content'
import { ElMessage } from 'element-plus'
import { exportToCSV } from '@/utils/export'

const { t } = useI18n()
const router = useRouter()

const treeData = ref([])
const filteredData = ref([])
const searchKeyword = ref('')
const statusFilter = ref(null)
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const ichCategoryOptions = ref([])
const form = ref({ name: '', parentId: 0, sort: 0, icon: '', detailImages: [], description: '', status: 1 })
const uploadFileList = ref([])
const iconFileList = ref([])
const rules = { name: [{ required: true, message: () => t('common.required'), trigger: 'blur' }] }
const topCategories = computed(() => treeData.value.map(c => ({ id: c.id, name: c.name })))

function fileToBase64(file) {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.readAsDataURL(file)
  })
}

async function handleImageChange(uploadFile) {
  if (uploadFile.raw) {
    const base64 = await fileToBase64(uploadFile.raw)
    uploadFile._base64 = base64
    syncImagesToForm()
  }
}

function handleImageRemove() {
  syncImagesToForm()
}

async function handleIconChange(uploadFile) {
  if (uploadFile.raw) {
    const base64 = await fileToBase64(uploadFile.raw)
    form.value.icon = base64
  }
}

function handleIconRemove() {
  form.value.icon = ''
}

function syncImagesToForm() {
  form.value.detailImages = uploadFileList.value
    .filter(f => f._base64 || f.url)
    .map(f => f._base64 || f.url)
}

function flattenCategoryTree(nodes, result = [], prefix = '') {
  nodes.forEach(n => {
    result.push({ id: n.id, name: prefix + n.name })
    if (n.children?.length) flattenCategoryTree(n.children, result, prefix + n.name + ' / ')
  })
  return result
}

function getIchCategoryName(id) {
  if (!id) return '-'
  const found = ichCategoryOptions.value.find(c => c.id === id)
  return found ? found.name : id
}

async function loadData() {
  loading.value = true
  try {
    const [res, ichRes] = await Promise.all([
      getProductCategoryTree(),
      getCategoryTree()
    ])
    treeData.value = res.data || []
    ichCategoryOptions.value = flattenCategoryTree(ichRes.data || [])
    applyClientFilter()
  } finally { loading.value = false }
}

function applyClientFilter() {
  let data = JSON.parse(JSON.stringify(treeData.value))
  if (searchKeyword.value || statusFilter.value !== null) {
    data = filterTree(data, searchKeyword.value?.toLowerCase(), statusFilter.value)
  }
  filteredData.value = data
}

function filterTree(nodes, kw, status) {
  return nodes.filter(n => {
    if (n.children?.length) n.children = filterTree(n.children, kw, status)
    const nameMatch = !kw || n.name?.toLowerCase().includes(kw)
    const statusMatch = status === null || status === undefined || n.status === status
    return (nameMatch && statusMatch) || (n.children?.length > 0)
  })
}

function openAddChild(parentRow) {
  isEdit.value = false
  form.value = { name: '', parentId: parentRow.id, sort: 0, icon: '', detailImages: [], description: '', status: 1 }
  uploadFileList.value = []
  iconFileList.value = []
  dialogVisible.value = true
}

async function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    try {
      const res = await getProductCategory(row.id)
      const detail = res.data || row
      const images = detail.detailImages ? [...detail.detailImages] : []
      form.value = { ...detail, detailImages: images }
      uploadFileList.value = images.map((src, idx) => ({
        name: `image-${idx}`,
        url: src,
        _base64: src,
        uid: Date.now() + idx,
        status: 'success'
      }))
      iconFileList.value = detail.icon ? [{ name: 'icon', url: detail.icon, uid: Date.now() + 100, status: 'success' }] : []
    } catch {
      form.value = { ...row, detailImages: [] }
      uploadFileList.value = []
      iconFileList.value = []
    }
  } else {
    form.value = { name: '', parentId: 0, sort: 0, icon: '', detailImages: [], description: '', status: 1 }
    uploadFileList.value = []
    iconFileList.value = []
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    isEdit.value ? await updateProductCategory(form.value) : await addProductCategory(form.value)
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await deleteProductCategory(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

function handleRowClick(row, column, event) {
  if (event.target.closest('.el-button, .el-popconfirm, .el-switch')) return
  router.push(`/product/category/${row.id}`)
}

function flattenTree(nodes, result = []) {
  nodes.forEach(n => {
    result.push(n)
    if (n.children?.length) flattenTree(n.children, result)
  })
  return result
}

function handleExport() {
  const flat = flattenTree(treeData.value)
  exportToCSV(flat, [
    { label: 'ID', key: 'id' },
    { label: '名称', key: 'name' },
    { label: '描述', key: 'description' },
    { label: '状态', key: 'status', formatter: v => v === 1 ? '启用' : '禁用' },
  ], '商品分类')
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
:deep(.center-col .cell) {
  text-align: center;
}
:deep(.el-upload-list--picture-card .el-upload-list__item),
:deep(.el-upload--picture-card) {
  width: 100px;
  height: 100px;
}
</style>
