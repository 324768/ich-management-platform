<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <nav class="ag-sub-pills">
          <router-link to="/product/category" class="ag-sub-pill" :class="{ active: $route.path === '/product/category' }">{{ t('product.tabs.category') }}</router-link>
          <router-link to="/product/list" class="ag-sub-pill" :class="{ active: $route.path === '/product/list' }">{{ t('product.tabs.list') }}</router-link>
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
            <el-input v-model="keyword" :placeholder="t('product.item.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="statusFilter" :placeholder="t('common.status')" clearable style="width: 140px" @change="loadData">
              <el-option :label="t('product.item.onSale')" :value="1" />
              <el-option :label="t('product.item.offShelf')" :value="0" />
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
              <el-form-item :label="t('common.belongCategory')">
                <el-select v-model="categoryFilter" :placeholder="t('common.selectCategory')" clearable style="width: 180px" @change="loadData">
                  <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="价格范围">
                <el-input-number v-model="priceMin" :min="0" :precision="2" :controls="false" placeholder="最低" style="width: 100px" />
                <span style="margin: 0 4px; color: #9ca3af;">-</span>
                <el-input-number v-model="priceMax" :min="0" :precision="2" :controls="false" placeholder="最高" style="width: 100px" />
              </el-form-item>
              <el-form-item label="库存范围">
                <el-input-number v-model="stockMin" :min="0" :controls="false" placeholder="最低" style="width: 80px" />
                <span style="margin: 0 4px; color: #9ca3af;">-</span>
                <el-input-number v-model="stockMax" :min="0" :controls="false" placeholder="最高" style="width: 80px" />
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
        <el-table-column prop="id" :label="t('common.id')" width="80" align="center" />
        <el-table-column prop="name" :label="t('product.item.productName')" show-overflow-tooltip align="center" />
        <el-table-column :label="t('common.belongCategory')" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ getCategoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="price" :label="t('product.item.price')" align="center">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="stock" :label="t('product.item.stock')" align="center" />
        <el-table-column prop="sale" :label="t('product.item.sales')" align="center" />
        <el-table-column prop="status" :label="t('common.status')" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>{{ row.status === 1 ? t('product.item.onSale') : t('product.item.offShelf') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="180" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">{{ t('common.edit') }}</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'" size="small"
              @click="handleToggleStatus(row)">{{ row.status === 1 ? t('product.item.offShelf') : t('product.item.onSale') }}</el-button>
            <el-popconfirm :title="t('product.item.deleteConfirm')" @confirm="handleDelete(row.id)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('product.item.editTitle') : t('product.item.addTitle')" width="650px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item :label="t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('product.item.subtitle')"><el-input v-model="form.subTitle" /></el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('common.belongCategory')">
              <el-select v-model="form.categoryId" :placeholder="t('common.selectCategory')" clearable style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.linkedHeritage')">
              <el-select v-model="form.heritageManId" :placeholder="t('common.selectHeritage')" clearable filterable style="width: 100%">
                <el-option v-for="h in heritageManOptions" :key="h.id" :label="h.name" :value="h.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('product.item.price')" prop="price"><el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('product.item.stock')" prop="stock"><el-input-number v-model="form.stock" :min="0" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('product.item.mainImage')">
          <el-upload
            v-model:file-list="mainImageFileList"
            list-type="picture-card"
            :auto-upload="false"
            :on-change="handleMainImageChange"
            :on-remove="handleMainImageRemove"
            accept="image/*"
            :limit="1"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('product.item.subImages')">
          <el-upload
            v-model:file-list="subImagesFileList"
            list-type="picture-card"
            :auto-upload="false"
            :on-change="handleSubImageChange"
            :on-remove="handleSubImageRemove"
            accept="image/*"
            multiple
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('product.item.detail')"><el-input v-model="form.detailDesc" type="textarea" :rows="4" /></el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">{{ t('product.item.onSale') }}</el-radio>
            <el-radio :value="0">{{ t('product.item.offShelf') }}</el-radio>
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
import { getProductList, getProduct, addProduct, updateProduct, deleteProduct, updateProductStatus, getProductCategoryTree } from '@/api/product'
import { getHeritageManList } from '@/api/content'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportToCSV } from '@/utils/export'

const { t } = useI18n()
const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref(null)
const categoryFilter = ref(null)
const showAdvanced = ref(false)
const priceMin = ref(null)
const priceMax = ref(null)
const stockMin = ref(null)
const stockMax = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const categoryOptions = ref([])
const heritageManOptions = ref([])
const defaultForm = { name: '', subTitle: '', categoryId: null, heritageManId: null, price: 0, stock: 0, mainImage: '', subImages: '', detailDesc: '', status: 0 }
const form = ref({ ...defaultForm })
const mainImageFileList = ref([])
const subImagesFileList = ref([])

function fileToBase64(file) {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.readAsDataURL(file)
  })
}

async function handleMainImageChange(uploadFile) {
  if (uploadFile.raw) {
    form.value.mainImage = await fileToBase64(uploadFile.raw)
  }
}

function handleMainImageRemove() {
  form.value.mainImage = ''
}

async function handleSubImageChange(uploadFile) {
  if (uploadFile.raw) {
    uploadFile._base64 = await fileToBase64(uploadFile.raw)
    syncSubImages()
  }
}

function handleSubImageRemove() {
  syncSubImages()
}

function syncSubImages() {
  const images = subImagesFileList.value
    .filter(f => f._base64 || f.url)
    .map(f => f._base64 || f.url)
  form.value.subImages = images.length ? JSON.stringify(images) : ''
}

const rules = {
  name: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
  price: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
  stock: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
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
    const [catRes, hRes] = await Promise.all([
      getProductCategoryTree(),
      getHeritageManList({ pageNum: 1, pageSize: 999 })
    ])
    categoryOptions.value = flattenCategoryTree(catRes.data || [])
    heritageManOptions.value = (hRes.data?.list || []).map(h => ({ id: h.id, name: h.name }))
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getProductList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, status: statusFilter.value ?? undefined, categoryId: categoryFilter.value ?? undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    try {
      const res = await getProduct(row.id)
      const detail = res.data || row
      form.value = { ...detail }
      mainImageFileList.value = detail.mainImage ? [{ name: 'main', url: detail.mainImage, uid: Date.now(), status: 'success' }] : []
      const subArr = detail.subImages ? (function() { try { return JSON.parse(detail.subImages) } catch { return [] } })() : []
      subImagesFileList.value = subArr.map((src, idx) => ({ name: `sub-${idx}`, url: src, _base64: src, uid: Date.now() + idx + 1, status: 'success' }))
    } catch {
      form.value = { ...row }
      mainImageFileList.value = []
      subImagesFileList.value = []
    }
  } else {
    form.value = { ...defaultForm }
    mainImageFileList.value = []
    subImagesFileList.value = []
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    isEdit.value ? await updateProduct(form.value) : await addProduct(form.value)
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleToggleStatus(row) {
  await updateProductStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(t('common.statusUpdated'))
  loadData()
}

async function handleDelete(id) {
  await deleteProduct(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

const selectedIds = ref([])
function handleSelectionChange(rows) { selectedIds.value = rows.map(r => r.id) }

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条记录吗？`, '批量删除', { type: 'warning' })
    await Promise.all(selectedIds.value.map(id => deleteProduct(id)))
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    loadData()
  } catch { /* cancelled */ }
}

function handleExport() {
  exportToCSV(tableData.value, [
    { label: 'ID', key: 'id' },
    { label: '商品名称', key: 'name' },
    { label: '价格', key: 'price' },
    { label: '库存', key: 'stock' },
    { label: '销量', key: 'sale' },
    { label: '状态', key: 'status', formatter: v => v === 1 ? '在售' : '下架' },
  ], '文创商品')
}

function handleRowClick(row, column, event) {
  if (event.target.closest('.el-button, .el-popconfirm, .el-switch, .el-checkbox')) return
  router.push(`/product/${row.id}`)
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = null
  categoryFilter.value = null
  priceMin.value = null
  priceMax.value = null
  stockMin.value = null
  stockMax.value = null
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
