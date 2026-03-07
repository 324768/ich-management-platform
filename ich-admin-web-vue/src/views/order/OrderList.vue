<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <h2 class="ag-page-title">{{ t('order.title') }}</h2>
        <div class="toolbar-actions">
          <button class="ag-btn-secondary" @click="handleExport">
            <el-icon :size="14"><Download /></el-icon>
            <span>导出</span>
          </button>
        </div>
      </div>

      <div class="ag-card">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-input v-model="orderNo" :placeholder="t('order.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="statusFilter" :placeholder="t('common.status')" clearable style="width: 140px" @change="loadData">
              <el-option :label="t('order.pending')" :value="0" />
              <el-option :label="t('order.paid')" :value="1" />
              <el-option :label="t('order.shipped')" :value="2" />
              <el-option :label="t('order.completed')" :value="3" />
              <el-option :label="t('order.cancelled')" :value="4" />
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
              <el-form-item label="金额范围">
                <el-input-number v-model="amountMin" :min="0" :precision="2" :controls="false" placeholder="最低" style="width: 100px" />
                <span style="margin: 0 4px; color: #9ca3af;">-</span>
                <el-input-number v-model="amountMax" :min="0" :precision="2" :controls="false" placeholder="最高" style="width: 100px" />
              </el-form-item>
              <el-form-item label="下单时间">
                <el-date-picker v-model="dateRange" type="daterange" range-separator="-" start-placeholder="开始" end-placeholder="结束" style="width: 240px" value-format="YYYY-MM-DD" @change="loadData" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="small" @click="loadData">查询</el-button>
                <el-button size="small" @click="resetFilters">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </transition>
        <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="orderNo" :label="t('order.orderNo')" width="230" align="center" />
        <el-table-column prop="userId" :label="t('order.userId')" align="center" />
        <el-table-column prop="totalAmount" :label="t('order.total')" align="center">
          <template #default="{ row }">¥{{ row.totalAmount || '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="payAmount" :label="t('order.payment')" align="center">
          <template #default="{ row }">¥{{ row.payAmount || '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" align="center">
          <template #default="{ row }">
            <el-tag :type="orderStatusType(row.status)" size="small" round>{{ orderStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('order.created')" align="center">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">{{ t('order.detail') }}</el-button>
            <el-button v-if="row.status === 1" link type="success" size="small" @click="handleShip(row.orderNo)">{{ t('order.ship') }}</el-button>
            <el-popconfirm v-if="row.status === 0" :title="t('order.cancelConfirm')" @confirm="handleCancel(row.orderNo)">
              <template #reference><el-button link type="danger" size="small">{{ t('order.cancelOrder') }}</el-button></template>
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

    <el-dialog v-model="detailVisible" :title="t('order.orderDetail')" width="700px" destroy-on-close>
      <el-descriptions :column="2" border v-if="currentOrder">
        <el-descriptions-item :label="t('order.orderNo')">{{ currentOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.status')">
          <el-tag :type="orderStatusType(currentOrder.status)" size="small">{{ orderStatusLabel(currentOrder.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('order.userId')">{{ currentOrder.userId }}</el-descriptions-item>
        <el-descriptions-item :label="t('order.totalAmount')">¥{{ currentOrder.totalAmount }}</el-descriptions-item>
        <el-descriptions-item :label="t('order.payType')">{{ payTypeLabel(currentOrder.payType) }}</el-descriptions-item>
        <el-descriptions-item :label="t('order.paymentTime')">{{ formatDate(currentOrder.paymentTime) }}</el-descriptions-item>
        <el-descriptions-item :label="t('order.receiver')">{{ currentOrder.receiverName || '-' }} {{ currentOrder.receiverPhone || '' }}</el-descriptions-item>
        <el-descriptions-item :label="t('order.shipping')">{{ currentOrder.shippingName || '-' }} {{ currentOrder.shippingCode || '' }}</el-descriptions-item>
        <el-descriptions-item :label="t('order.address')" :span="2">{{ [currentOrder.receiverProvince, currentOrder.receiverCity, currentOrder.receiverRegion, currentOrder.receiverDetailAddress].filter(Boolean).join(' ') || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('order.note')" :span="2">{{ currentOrder.note || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin: 20px 0 12px; font-weight: 600;">{{ t('order.orderItems') }}</h4>
      <el-table :data="currentOrder?.orderItems || []" stripe size="small">
        <el-table-column prop="productName" :label="t('order.productCol')" />
        <el-table-column prop="productQuantity" :label="t('order.qty')" width="80" />
        <el-table-column :label="t('order.priceCol')" width="100">
          <template #default="{ row }">¥{{ row.productPrice || '0' }}</template>
        </el-table-column>
        <el-table-column :label="t('order.subtotal')" width="120">
          <template #default="{ row }">¥{{ ((row.productPrice || 0) * (row.productQuantity || 0)).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getOrderList, getOrder, shipOrder, updateOrderStatus } from '@/api/order'
import { ElMessage } from 'element-plus'
import { exportToCSV } from '@/utils/export'

const { t } = useI18n()

const tableData = ref([])
const loading = ref(false)
const orderNo = ref('')
const statusFilter = ref(null)
const showAdvanced = ref(false)
const amountMin = ref(null)
const amountMax = ref(null)
const dateRange = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detailVisible = ref(false)
const currentOrder = ref(null)

const statusKeys = { 0: 'order.pending', 1: 'order.paid', 2: 'order.shipped', 3: 'order.completed', 4: 'order.cancelled' }
const statusTypeMap = { 0: 'info', 1: 'primary', 2: 'warning', 3: 'success', 4: 'danger' }
function orderStatusLabel(s) { return t(statusKeys[s] || 'order.pending') }
function orderStatusType(s) { return statusTypeMap[s] ?? 'info' }
function payTypeLabel(pt) { const m = { 1: 'order.alipay', 2: 'order.wechat', 3: 'order.bankCard' }; return m[pt] ? t(m[pt]) : '-' }

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadData() {
  loading.value = true
  try {
    const res = await getOrderList({ pageNum: pageNum.value, pageSize: pageSize.value, orderNo: orderNo.value || undefined, status: statusFilter.value != null ? statusFilter.value : undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function viewDetail(row) {
  try {
    const res = await getOrder(row.orderNo)
    currentOrder.value = res.data
    detailVisible.value = true
  } catch (e) { /* handled by interceptor */ }
}

async function handleShip(no) {
  await shipOrder(no)
  ElMessage.success(t('order.orderShipped'))
  loadData()
}

async function handleCancel(no) {
  await updateOrderStatus(no, 4)
  ElMessage.success(t('order.orderCancelled'))
  loadData()
}

function resetFilters() {
  orderNo.value = ''
  statusFilter.value = null
  amountMin.value = null
  amountMax.value = null
  dateRange.value = null
  loadData()
}

function handleExport() {
  exportToCSV(tableData.value, [
    { label: '订单号', key: 'orderNo' },
    { label: '用户ID', key: 'userId' },
    { label: '总额', key: 'totalAmount' },
    { label: '实付', key: 'payAmount' },
    { label: '状态', key: 'status', formatter: v => ({0:'待支付',1:'已支付',2:'已发货',3:'已完成',4:'已取消'})[v] || v },
    { label: '创建时间', key: 'createTime', formatter: v => formatDate(v) },
  ], '订单')
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
.ag-page-title {
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}
</style>
