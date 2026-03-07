<template>
  <!-- Antigravity Dashboard.tsx exact: h-full w-full overflow-y-auto -->
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <!-- Greeting + Actions: Antigravity exact: flex justify-between items-center -->
      <div class="ag-greeting">
        <div>
          <h1 class="ag-greeting-title">{{ t('dashboard.greeting', { name: adminName }) }}</h1>
        </div>
        <div class="ag-greeting-actions">
          <button class="ag-btn-outline" @click="$router.push('/content/item')">
            <!-- Plus icon (Lucide Plus) -->
            <svg class="ag-btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            <span>{{ t('dashboard.addContent') }}</span>
          </button>
          <button class="ag-btn-primary" @click="handleRefresh" :disabled="isRefreshing">
            <!-- RefreshCw icon (Lucide RefreshCw) -->
            <svg class="ag-btn-icon" :class="{ 'ag-spin': isRefreshing }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            <span class="ag-btn-text">{{ isRefreshing ? t('dashboard.refreshing') : t('dashboard.refreshData') }}</span>
          </button>
        </div>
      </div>

      <!-- Stats Cards: Antigravity exact: grid grid-cols-2 md:grid-cols-5 gap-3 -->
      <div class="ag-stats-grid">
        <div class="ag-stat-card" v-for="(stat, i) in statsCards" :key="i">
          <div class="ag-stat-top">
            <div class="ag-stat-icon" :style="{ background: stat.iconBg }">
              <!-- Lucide SVG icons inline -->
              <svg :style="{ color: stat.iconColor }" class="ag-stat-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-html="stat.iconPath"></svg>
            </div>
          </div>
          <div class="ag-stat-value">{{ stat.value }}</div>
          <div class="ag-stat-label">{{ stat.label }}</div>
          <div class="ag-stat-extra" v-if="stat.extra" :class="stat.extraClass">{{ stat.extra }}</div>
        </div>
      </div>

      <!-- Two-column layout: Antigravity exact: grid grid-cols-1 md:grid-cols-2 gap-4 -->
      <div class="ag-two-col">
        <div class="ag-card">
          <div class="ag-card-header">
            <h3>{{ t('dashboard.visitorTrends') }}</h3>
            <span class="ag-card-desc">{{ t('dashboard.visitorTrendsDesc') }}</span>
          </div>
          <div ref="lineChartRef" class="ag-chart"></div>
        </div>
        <div class="ag-card">
          <div class="ag-card-header">
            <h3>{{ t('dashboard.recentOrders') }}</h3>
            <span class="ag-card-desc">{{ t('dashboard.recentOrdersDesc') }}</span>
          </div>
          <div class="ag-order-list">
            <div class="ag-order-row" v-for="order in recentOrders" :key="order.orderNo">
              <div class="ag-order-info">
                <span class="ag-order-no">{{ order.orderNo }}</span>
                <span class="ag-order-customer">{{ order.customer }}</span>
              </div>
              <div class="ag-order-right">
                <span class="ag-order-amount">¥{{ order.amount }}</span>
                <el-tag :type="statusType(order.status)" size="small" round>{{ order.status }}</el-tag>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Quick Links: Antigravity exact: grid grid-cols-2 gap-3 -->
      <div class="ag-quick-links">
        <button class="ag-link-card ag-link-indigo" @click="$router.push('/content/item')">
          <span class="ag-link-text">{{ t('dashboard.viewAllHeritage') }}</span>
          <!-- ArrowRight icon (Lucide ArrowRight) -->
          <svg class="ag-link-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>
          </svg>
        </button>
        <button class="ag-link-card ag-link-purple" @click="$router.push('/order')">
          <span class="ag-link-text">{{ t('dashboard.manageOrders') }}</span>
          <svg class="ag-link-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/auth'
import { getDashboardStats, getRecentOrders, getWeeklyTrend } from '@/api/dashboard'
import * as echarts from 'echarts'

const { t } = useI18n()
const authStore = useAuthStore()
const adminName = computed(() => authStore.adminInfo?.nickname || authStore.adminInfo?.username || 'Admin')

const lineChartRef = ref(null)
let lineChart = null
const isRefreshing = ref(false)

// 统计数据
const statsData = ref({ heritageItems: 0, totalUsers: 0, products: 0, orders: 0, lowStock: 0 })

const statsCards = computed(() => [
  {
    label: t('dashboard.stats.heritageItems'), value: statsData.value.heritageItems.toLocaleString(),
    iconBg: '#eff6ff', iconColor: '#3b82f6',
    iconPath: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    extra: t('dashboard.stats.sufficient'), extraClass: 'text-green'
  },
  {
    label: t('dashboard.stats.totalUsers'), value: statsData.value.totalUsers.toLocaleString(),
    iconBg: '#f0fdf4', iconColor: '#22c55e',
    iconPath: '<path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/><path d="M5 3v4"/><path d="M19 17v4"/><path d="M3 5h4"/><path d="M17 19h4"/>',
  },
  {
    label: t('dashboard.stats.products'), value: statsData.value.products.toLocaleString(),
    iconBg: '#faf5ff', iconColor: '#a855f7',
    iconPath: '<path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/><path d="M5 3v4"/><path d="M19 17v4"/><path d="M3 5h4"/><path d="M17 19h4"/>',
  },
  {
    label: t('dashboard.stats.orders'), value: statsData.value.orders.toLocaleString(),
    iconBg: '#ecfeff', iconColor: '#06b6d4',
    iconPath: '<path d="M12 8V4H8"/><rect width="16" height="12" x="4" y="8" rx="2"/><path d="M2 14h2"/><path d="M20 14h2"/><path d="M15 13v2"/><path d="M9 13v2"/>',
  },
  {
    label: t('dashboard.stats.lowStock'), value: statsData.value.lowStock.toLocaleString(),
    iconBg: '#fff7ed', iconColor: '#f97316',
    iconPath: '<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
    extra: t('dashboard.stats.belowThreshold'), extraClass: 'text-gray'
  },
])

const recentOrders = ref([])

const statusMap = { 0: 'Pending', 1: 'Paid', 2: 'Shipped', 3: 'Completed', 4: 'Cancelled' }

function statusType(status) {
  const map = { Paid: 'primary', Shipped: 'warning', Completed: 'success', Pending: 'info', Cancelled: 'danger' }
  return map[status] || 'info'
}

async function loadDashboardData() {
  try {
    const [statsRes, ordersRes, trendRes] = await Promise.all([getDashboardStats(), getRecentOrders(), getWeeklyTrend()])
    if (statsRes.data) {
      statsData.value = statsRes.data
    }
    if (ordersRes.data) {
      recentOrders.value = ordersRes.data.map(o => ({
        orderNo: o.orderNo,
        customer: o.receiverName || `用户${o.userId}`,
        amount: o.payAmount ? Number(o.payAmount).toFixed(2) : '0.00',
        status: statusMap[o.status] || 'Pending'
      }))
    }
    if (trendRes.data && lineChart) {
      const dates = Object.keys(trendRes.data)
      const counts = Object.values(trendRes.data)
      const weekdays = ['日', '一', '二', '三', '四', '五', '六']
      const labels = dates.map(d => {
        const dt = new Date(d + 'T00:00:00')
        return `周${weekdays[dt.getDay()]}(${d.substring(5)})`
      })
      lineChart.setOption({ xAxis: { data: labels }, series: [{ data: counts }] })
    }
  } catch (e) {
    console.warn('仪表盘数据加载失败，使用默认值', e)
  }
}

function handleRefresh() {
  window.location.reload()
}

function initCharts() {
  lineChart = echarts.init(lineChartRef.value)
  lineChart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: '#111827', borderColor: 'transparent', textStyle: { color: '#f9fafb', fontSize: 13 }, padding: [8, 12], borderRadius: 8 },
    grid: { top: 16, right: 16, bottom: 28, left: 44 },
    xAxis: { type: 'category', data: [], axisLine: { lineStyle: { color: '#f3f4f6' } }, axisTick: { show: false }, axisLabel: { color: '#9ca3af', fontSize: 12 } },
    yAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#f3f4f6', type: 'dashed' } }, axisLabel: { color: '#9ca3af', fontSize: 12 } },
    series: [{
      name: 'Orders', type: 'line', smooth: true, data: [],
      lineStyle: { color: '#3b82f6', width: 2 },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(59,130,246,0.15)' }, { offset: 1, color: 'rgba(59,130,246,0.01)' }]) },
      itemStyle: { color: '#3b82f6', borderWidth: 2, borderColor: '#fff' }, symbol: 'circle', symbolSize: 6
    }]
  })
}

function handleResize() { lineChart?.resize() }

onMounted(() => { initCharts(); loadDashboardData(); window.addEventListener('resize', handleResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); lineChart?.dispose() })
</script>

<style lang="scss" scoped>
// ══════════════════════════════════════════════════════════════
// Antigravity Dashboard.tsx — PIXEL-PERFECT replication
// ══════════════════════════════════════════════════════════════

// ── Page: h-full w-full overflow-y-auto ──
.ag-page-scroll { height: 100%; width: 100%; overflow-y: auto; }

// ── Content: p-5 space-y-4 max-w-7xl mx-auto ──
.ag-page-content {
  padding: 20px;                // p-5
  max-width: 1280px;            // max-w-7xl
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;                    // space-y-4
}

// ── Greeting: flex justify-between items-center ──
.ag-greeting {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ag-greeting-title {
  font-size: 24px;              // text-2xl
  font-weight: 700;             // font-bold
  color: #111827;               // text-gray-900
}
.ag-greeting-actions {
  display: flex;
  gap: 8px;                     // gap-2
}

// ── Outline button (AddAccountDialog style) ──
.ag-btn-outline {
  padding: 6px 12px;            // px-3 py-1.5
  background: #fff;
  color: #374151;
  font-size: 12px;              // text-xs
  font-weight: 500;             // font-medium
  border: 1px solid #e5e7eb;
  border-radius: 8px;           // rounded-lg
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;                     // gap-1.5
  transition: all 0.15s;
  box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05);
  &:hover { background: #f9fafb; border-color: #d1d5db; }
}

// ── Primary button: px-3 py-1.5 bg-blue-500 text-white text-xs font-medium rounded-lg ──
.ag-btn-primary {
  padding: 6px 12px;
  background: #3b82f6;          // bg-blue-500
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05);
  transition: background 0.15s;
  &:hover { background: #2563eb; }
  &:disabled { opacity: 0.7; cursor: not-allowed; }
}

// ── Button icon: w-3.5 h-3.5 ──
.ag-btn-icon {
  width: 14px;
  height: 14px;
}
.ag-btn-text {
  display: inline;
}

// ── Spin animation (Lucide RefreshCw) ──
@keyframes ag-spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.ag-spin { animation: ag-spin 1s linear infinite; }

// ── Stats Grid: grid grid-cols-2 md:grid-cols-5 gap-3 ──
.ag-stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;                    // gap-3
}

// ── Stat Card: bg-white rounded-xl p-4 shadow-sm border border-gray-100 ──
.ag-stat-card {
  background: #fff;
  border-radius: 12px;          // rounded-xl
  padding: 16px;                // p-4
  box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05);
  border: 1px solid #f3f4f6;
}

// ── Stat icon wrapper: flex items-center justify-between mb-2 > p-1.5 bg-*-50 rounded-md ──
.ag-stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;           // mb-2
}
.ag-stat-icon {
  padding: 6px;                 // p-1.5
  border-radius: 6px;           // rounded-md
  display: inline-flex;
}
.ag-stat-svg {
  width: 16px;                  // w-4 h-4
  height: 16px;
}

// ── Stat value: text-2xl font-bold text-gray-900 mb-0.5 ──
.ag-stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 2px;           // mb-0.5
}

// ── Stat label: text-xs text-gray-500 ──
.ag-stat-label {
  font-size: 12px;
  color: #6b7280;
}

// ── Stat extra: text-[10px] mt-1 ──
.ag-stat-extra {
  font-size: 10px;
  margin-top: 4px;              // mt-1
}
.text-green { color: #16a34a; }
.text-orange { color: #ea580c; }
.text-gray { color: #9ca3af; }

// ── Two-col: grid grid-cols-1 md:grid-cols-2 gap-4 ──
.ag-two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;                    // gap-4
}

// ── Card: bg-white rounded-2xl p-6 shadow-sm border border-gray-100 ──
.ag-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05);
  border: 1px solid #f3f4f6;
}
.ag-card-header {
  margin-bottom: 16px;
  h3 { font-size: 16px; font-weight: 600; color: #111827; margin-bottom: 4px; }
  .ag-card-desc { font-size: 13px; color: #6b7280; }
}
.ag-chart { height: 260px; width: 100%; }

// ── Order List ──
.ag-order-list { display: flex; flex-direction: column; }
.ag-order-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 0; border-bottom: 1px solid #f3f4f6;
  &:last-child { border-bottom: none; }
}
.ag-order-info { display: flex; flex-direction: column; gap: 2px; }
.ag-order-no { font-size: 13px; font-weight: 500; color: #374151; }
.ag-order-customer { font-size: 12px; color: #9ca3af; }
.ag-order-right { display: flex; align-items: center; gap: 8px; }
.ag-order-amount { font-size: 14px; font-weight: 600; color: #111827; }

// ── Quick Links: Antigravity exact: grid grid-cols-2 gap-3 ──
.ag-quick-links {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;                    // gap-3
}

// ── Link card: bg-*-50 rounded-lg p-3 shadow-sm border border-*-100 ──
.ag-link-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;                // p-3
  border-radius: 8px;           // rounded-lg
  border: none;
  cursor: pointer;
  box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05);
  transition: all 0.15s;
  &:hover { box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); }
}
.ag-link-text { font-weight: 500; font-size: 14px; }
.ag-link-icon {
  width: 16px; height: 16px;
  transition: transform 0.15s;
  .ag-link-card:hover & { transform: translateX(4px); }
}

// ── Indigo: bg-indigo-50 border border-indigo-100 ──
.ag-link-indigo {
  background: #eef2ff; border: 1px solid #e0e7ff;
  .ag-link-text { color: #4338ca; }
  .ag-link-icon { color: #818cf8; }
  &:hover { border-color: #a5b4fc; .ag-link-icon { color: #4f46e5; } }
}

// ── Purple: bg-purple-50 border border-purple-100 ──
.ag-link-purple {
  background: #faf5ff; border: 1px solid #f3e8ff;
  .ag-link-text { color: #7e22ce; }
  .ag-link-icon { color: #c084fc; }
  &:hover { border-color: #d8b4fe; .ag-link-icon { color: #9333ea; } }
}

@media (max-width: 1024px) {
  .ag-stats-grid { grid-template-columns: repeat(2, 1fr); }
  .ag-two-col { grid-template-columns: 1fr; }
}
</style>
