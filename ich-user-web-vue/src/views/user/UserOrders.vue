<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/utils/token'
import { listOrders, cancelOrder, confirmReceive } from '@/api/order'

const router = useRouter()
const user = getUserInfo()
const orders = ref([])
const loading = ref(false)
const activeStatus = ref(null)
const pageNum = ref(1)

const statusTabs = [
  { label: '全部', value: null },
  { label: '待付款', value: 0 },
  { label: '待发货', value: 1 },
  { label: '待收货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已取消', value: 4 },
]

const statusText = (s) => {
  const map = { 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已取消' }
  return map[s] || '未知'
}

const loadOrders = async () => {
  if (!user?.id) return
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: 20 }
    if (activeStatus.value !== null) params.status = activeStatus.value
    const res = await listOrders(user.id, params)
    orders.value = res.data?.list || []
  } catch { orders.value = [] }
  loading.value = false
}

const switchStatus = (val) => {
  activeStatus.value = val
  pageNum.value = 1
  loadOrders()
}

const handleCancel = async (order) => {
  if (!confirm('确定取消该订单？')) return
  try {
    await cancelOrder(order.orderNo, user.id)
    loadOrders()
  } catch (e) { alert('取消失败') }
}

const handleConfirm = async (order) => {
  if (!confirm('确认已收到货？')) return
  try {
    await confirmReceive(order.orderNo, user.id)
    loadOrders()
  } catch (e) { alert('确认失败') }
}

const formatTime = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(loadOrders)
</script>

<template>
  <div class="page-container">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="router.push('/user')">← 返回</button>
        <h2>我的订单</h2>
      </div>

      <div class="status-tabs">
        <span v-for="tab in statusTabs" :key="tab.label"
              :class="['stab', { active: activeStatus === tab.value }]"
              @click="switchStatus(tab.value)">{{ tab.label }}</span>
      </div>

      <div v-if="loading" class="empty-state">加载中...</div>
      <div v-else-if="orders.length === 0" class="empty-state">暂无订单</div>
      <div v-else class="order-list">
        <div v-for="order in orders" :key="order.id" class="order-card">
          <div class="order-top">
            <span class="order-no">订单号: {{ order.orderNo }}</span>
            <span :class="['order-status', 'status-' + order.status]">{{ statusText(order.status) }}</span>
          </div>
          <div class="order-items">
            <div v-for="item in (order.items || [])" :key="item.id" class="oi">
              <div class="oi-img">
                <img v-if="item.productImage" :src="item.productImage" />
                <div v-else class="oi-placeholder">商品</div>
              </div>
              <div class="oi-info">
                <p class="oi-name">{{ item.productName || '商品' }}</p>
                <p class="oi-spec">x{{ item.quantity }}</p>
              </div>
              <span class="oi-price">¥{{ item.price?.toFixed(2) || '0.00' }}</span>
            </div>
          </div>
          <div class="order-bottom">
            <span class="order-time">{{ formatTime(order.createTime) }}</span>
            <div class="order-actions">
              <span class="order-total">合计: <strong>¥{{ order.totalAmount?.toFixed(2) || '0.00' }}</strong></span>
              <button v-if="order.status === 0" class="act-btn cancel" @click="handleCancel(order)">取消订单</button>
              <button v-if="order.status === 2" class="act-btn confirm" @click="handleConfirm(order)">确认收货</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 24px 0 60px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
.page-header h2 { flex: 1; font-size: 20px; font-weight: 700; margin: 0; }
.back-btn { background: none; border: none; font-size: 15px; color: var(--ich-primary, #8B2020); cursor: pointer; }

.status-tabs { display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; }
.stab {
  padding: 6px 18px; border-radius: 20px; font-size: 13px; cursor: pointer;
  background: #f5f5f5; color: #666; transition: 0.2s; user-select: none;
}
.stab:hover { background: #eee; }
.stab.active { background: var(--ich-primary, #8B2020); color: #fff; }

.empty-state { text-align: center; padding: 80px 0; color: #bbb; font-size: 15px; }

.order-list { display: flex; flex-direction: column; gap: 16px; }
.order-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); overflow: hidden; }
.order-top { display: flex; justify-content: space-between; align-items: center; padding: 14px 20px; border-bottom: 1px solid #f5f5f5; }
.order-no { font-size: 13px; color: #999; }
.order-status { font-size: 13px; font-weight: 600; }
.status-0 { color: #ff9900; }
.status-1 { color: #1890ff; }
.status-2 { color: #52c41a; }
.status-3 { color: #999; }
.status-4 { color: #ccc; }

.order-items { padding: 12px 20px; }
.oi { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.oi-img { width: 60px; height: 60px; border-radius: 8px; overflow: hidden; flex-shrink: 0; }
.oi-img img { width: 100%; height: 100%; object-fit: cover; }
.oi-placeholder { width: 100%; height: 100%; background: #f5f5f5; display: flex; align-items: center; justify-content: center; color: #ccc; font-size: 12px; }
.oi-info { flex: 1; min-width: 0; }
.oi-name { font-size: 14px; color: #333; margin: 0 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.oi-spec { font-size: 12px; color: #999; margin: 0; }
.oi-price { font-size: 14px; color: #ff4d4f; font-weight: 600; }

.order-bottom { display: flex; justify-content: space-between; align-items: center; padding: 12px 20px; border-top: 1px solid #f5f5f5; }
.order-time { font-size: 12px; color: #bbb; }
.order-actions { display: flex; align-items: center; gap: 12px; }
.order-total { font-size: 13px; color: #666; }
.order-total strong { color: #ff4d4f; font-size: 16px; }
.act-btn { padding: 5px 16px; border-radius: 16px; font-size: 13px; cursor: pointer; border: 1px solid #ddd; background: #fff; transition: 0.2s; }
.act-btn.cancel:hover { border-color: #ff4d4f; color: #ff4d4f; }
.act-btn.confirm { background: var(--ich-primary, #8B2020); color: #fff; border-color: var(--ich-primary, #8B2020); }
</style>
