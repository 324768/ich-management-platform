<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <nav class="ag-sub-pills">
          <router-link to="/product/category" class="ag-sub-pill" :class="{ active: $route.path === '/product/category' }">{{ t('product.tabs.category') }}</router-link>
          <router-link to="/product/list" class="ag-sub-pill" :class="{ active: $route.path === '/product/list' }">{{ t('product.tabs.list') }}</router-link>
          <router-link to="/product/inventory" class="ag-sub-pill" :class="{ active: $route.path === '/product/inventory' }">{{ t('inventory.tabs.records') }}</router-link>
          <router-link to="/product/stock-alert" class="ag-sub-pill" :class="{ active: $route.path === '/product/stock-alert' }">{{ t('inventory.tabs.alert') }}</router-link>
        </nav>
        <div class="toolbar-actions">
          <button class="ag-btn" @click="openDialog()">
            <el-icon :size="14"><Plus /></el-icon>
            <span>{{ t('inventory.addRecord') }}</span>
          </button>
        </div>
      </div>

      <div class="ag-card">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-input v-model="keyword" :placeholder="t('inventory.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="typeFilter" :placeholder="t('inventory.type')" clearable style="width: 140px" @change="loadData">
              <el-option :label="t('inventory.typeIn')" :value="1" />
              <el-option :label="t('inventory.typeOut')" :value="2" />
              <el-option :label="t('inventory.typeAdjust')" :value="3" />
            </el-select>
          </div>
        </div>

        <el-table :data="tableData" v-loading="loading">
          <el-table-column prop="id" :label="t('common.id')" width="70" align="center" />
          <el-table-column prop="productName" :label="t('inventory.productName')" min-width="160" show-overflow-tooltip />
          <el-table-column :label="t('inventory.type')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.type === 1 ? 'success' : row.type === 2 ? 'danger' : 'warning'" size="small" round>
                {{ typeLabel(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="quantity" :label="t('inventory.quantity')" width="100" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.quantity > 0 ? '#10b981' : '#ef4444', fontWeight: 600 }">
                {{ row.quantity > 0 ? '+' : '' }}{{ row.quantity }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="beforeStock" :label="t('inventory.beforeStock')" width="100" align="center" />
          <el-table-column prop="afterStock" :label="t('inventory.afterStock')" width="100" align="center" />
          <el-table-column prop="reason" :label="t('inventory.reason')" min-width="150" show-overflow-tooltip />
          <el-table-column prop="operatorName" :label="t('inventory.operator')" width="100" align="center" />
          <el-table-column :label="t('common.createdAt')" width="160" align="center">
            <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="80" fixed="right" align="center">
            <template #default="{ row }">
              <el-popconfirm :title="t('inventory.deleteConfirm')" @confirm="handleDelete(row.id)">
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

    <el-dialog v-model="dialogVisible" :title="t('inventory.addRecord')" width="500px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item :label="t('inventory.productName')" prop="productId">
          <el-select v-model="form.productId" filterable style="width: 100%" :placeholder="t('inventory.selectProduct')" @change="handleProductChange">
            <el-option v-for="item in productOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventory.type')" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">{{ t('inventory.typeIn') }}</el-radio>
            <el-radio :value="2">{{ t('inventory.typeOut') }}</el-radio>
            <el-radio :value="3">{{ t('inventory.typeAdjust') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('inventory.quantity')" prop="quantity">
          <el-input-number v-model="form.quantity" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('inventory.reason')">
          <el-input v-model="form.reason" type="textarea" :rows="3" />
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
import { getInventoryRecordList, addInventoryRecord, deleteInventoryRecord } from '@/api/inventory'
import { getProductList } from '@/api/product'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const typeFilter = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const productOptions = ref([])

const defaultForm = { productId: null, productName: '', type: 1, quantity: 1, reason: '' }
const form = ref({ ...defaultForm })
const rules = {
  productId: [{ required: true, message: () => t('common.required'), trigger: 'change' }],
  type: [{ required: true, message: () => t('common.required'), trigger: 'change' }],
  quantity: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
}

function typeLabel(type) {
  const map = { 1: t('inventory.typeIn'), 2: t('inventory.typeOut'), 3: t('inventory.typeAdjust') }
  return map[type] || '-'
}

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadProductOptions() {
  try {
    const res = await getProductList({ pageNum: 1, pageSize: 1000 })
    productOptions.value = res.data?.list || []
  } catch {
    productOptions.value = []
  }
}

function handleProductChange(productId) {
  const product = productOptions.value.find(item => item.id === productId)
  form.value.productName = product?.name || ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getInventoryRecordList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, type: typeFilter.value ?? undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function openDialog() {
  form.value = { ...defaultForm }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await addInventoryRecord(form.value)
    ElMessage.success(t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await deleteInventoryRecord(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

onMounted(loadData)
onMounted(loadProductOptions)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
</style>
