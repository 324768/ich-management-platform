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
          <button class="ag-btn-secondary" @click="handleExport">
            <el-icon :size="14"><Download /></el-icon>
            <span>导出</span>
          </button>
          <div class="threshold-control">
            <span class="threshold-label">预警阈值：</span>
            <el-input-number v-model="threshold" :min="1" :max="9999" size="small" @change="loadData" />
          </div>
        </div>
      </div>

      <div class="alert-summary" v-if="!loading">
        <div class="summary-card danger">
          <div class="summary-num">{{ criticalCount }}</div>
          <div class="summary-label">严重不足 (≤3)</div>
        </div>
        <div class="summary-card warning">
          <div class="summary-num">{{ warningCount }}</div>
          <div class="summary-label">库存偏低</div>
        </div>
        <div class="summary-card info">
          <div class="summary-num">{{ tableData.length }}</div>
          <div class="summary-label">总预警商品</div>
        </div>
      </div>

      <div class="ag-card">
        <el-table :data="tableData" v-loading="loading" @row-click="goDetail" class="cursor-row">
          <el-table-column prop="id" :label="t('common.id')" width="70" align="center" />
          <el-table-column label="封面" width="80" align="center">
            <template #default="{ row }">
              <el-image v-if="row.mainImage" :src="row.mainImage" fit="cover" style="width: 48px; height: 48px; border-radius: 6px;" :preview-src-list="[row.mainImage]" preview-teleported />
              <span v-else style="color: #ccc;">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="商品名称" min-width="160" />
          <el-table-column label="库存" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.stock <= 3 ? 'danger' : 'warning'" size="small" round effect="dark">
                {{ row.stock }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="price" label="价格" width="100" align="center">
            <template #default="{ row }">¥{{ row.price?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="sales" label="销量" width="80" align="center" />
          <el-table-column :label="t('common.status')" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>
                {{ row.status === 1 ? '在售' : '下架' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="100" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="goDetail(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getLowStockProducts } from '@/api/product'
import { exportToCSV } from '@/utils/export'

const { t } = useI18n()
const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const threshold = ref(10)

const criticalCount = computed(() => tableData.value.filter(p => p.stock <= 3).length)
const warningCount = computed(() => tableData.value.filter(p => p.stock > 3).length)

async function loadData() {
  loading.value = true
  try {
    const res = await getLowStockProducts(threshold.value)
    tableData.value = res.data || []
  } finally { loading.value = false }
}

function goDetail(row) {
  router.push(`/product/${row.id}`)
}

function handleExport() {
  exportToCSV(tableData.value, [
    { label: 'ID', key: 'id' },
    { label: '商品名称', key: 'name' },
    { label: '库存', key: 'stock' },
    { label: '价格', key: 'price', formatter: v => v?.toFixed(2) },
    { label: '销量', key: 'sales' },
    { label: '状态', key: 'status', formatter: v => v === 1 ? '在售' : '下架' },
  ], '库存预警')
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

.threshold-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.threshold-label {
  font-size: 13px;
  color: #6b7280;
  white-space: nowrap;
}

.alert-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.summary-card {
  flex: 1;
  padding: 20px 24px;
  border-radius: 12px;
  border: 1px solid #f3f4f6;
  background: #fff;

  .summary-num {
    font-size: 28px;
    font-weight: 800;
    margin-bottom: 4px;
  }

  .summary-label {
    font-size: 13px;
    color: #6b7280;
  }

  &.danger {
    border-color: #fecaca;
    background: #fef2f2;
    .summary-num { color: #dc2626; }
  }

  &.warning {
    border-color: #fed7aa;
    background: #fffbeb;
    .summary-num { color: #d97706; }
  }

  &.info {
    border-color: #bfdbfe;
    background: #eff6ff;
    .summary-num { color: #2563eb; }
  }
}

.cursor-row {
  :deep(tbody tr) {
    cursor: pointer;
  }
}
</style>
