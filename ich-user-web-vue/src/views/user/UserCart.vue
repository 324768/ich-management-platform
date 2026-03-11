<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/utils/token'
import { listCart, updateQuantity, removeFromCart, clearCart, checkItem, checkAll } from '@/api/cart'

const router = useRouter()
const user = getUserInfo()
const cartItems = ref([])
const loading = ref(false)
const allChecked = ref(false)

// 监听购物车更新事件
function handleCartUpdate() {
  loadCart()
}

onMounted(() => {
  loadCart()
  window.addEventListener('storage', handleCartUpdate)
  window.addEventListener('cart_updated', handleCartUpdate)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleCartUpdate)
  window.removeEventListener('cart_updated', handleCartUpdate)
})

const loadCart = async () => {
  if (!user?.id) return
  loading.value = true
  try {
    const res = await listCart(user.id)
    cartItems.value = (res.data || []).map(item => ({ ...item, selected: item.selected === 1 }))
    updateAllChecked()
  } catch { cartItems.value = [] }
  loading.value = false
}

const updateAllChecked = () => {
  allChecked.value = cartItems.value.length > 0 && cartItems.value.every(i => i.selected)
}

const handleCheck = async (item) => {
  item.selected = !item.selected
  try {
    await checkItem(user.id, item.productId, item.selected ? 1 : 0)
  } catch { item.selected = !item.selected }
  updateAllChecked()
}

const handleCheckAll = async () => {
  const newVal = !allChecked.value
  allChecked.value = newVal
  cartItems.value.forEach(i => i.selected = newVal)
  try { await checkAll(user.id, newVal ? 1 : 0) } catch { loadCart() }
}

const handleQuantity = async (item, delta) => {
  const newQty = item.quantity + delta
  if (newQty < 1) return
  const old = item.quantity
  item.quantity = newQty
  try { await updateQuantity(user.id, item.productId, newQty) } catch { item.quantity = old }
}

const handleRemove = async (item) => {
  if (!confirm('确定移除该商品？')) return
  try {
    await removeFromCart(user.id, item.productId)
    cartItems.value = cartItems.value.filter(i => i.productId !== item.productId)
  } catch { /* ignore */ }
}

const handleClear = async () => {
  if (!confirm('确定清空购物车？')) return
  try {
    await clearCart(user.id)
    cartItems.value = []
  } catch { /* ignore */ }
}

const selectedItems = computed(() => cartItems.value.filter(i => i.selected))
const totalPrice = computed(() => selectedItems.value.reduce((sum, i) => sum + (i.productPrice || i.price || 0) * i.quantity, 0))

onMounted(loadCart)
</script>

<template>
  <div class="page-container">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="router.push('/user')">← 返回</button>
        <h2>我的购物车</h2>
        <button v-if="cartItems.length" class="clear-btn" @click="handleClear">清空</button>
      </div>

      <div v-if="loading" class="empty-state">加载中...</div>
      <div v-else-if="cartItems.length === 0" class="empty-state">
        <p>购物车空空如也~</p>
        <button class="go-btn" @click="router.push('/shop')">去逛逛</button>
      </div>
      <div v-else>
        <div class="cart-list">
          <div v-for="item in cartItems" :key="item.id" class="cart-item">
            <label class="checkbox" @click="handleCheck(item)">
              <span :class="['check-icon', { checked: item.selected }]">✓</span>
            </label>
            <div class="item-img">
              <img v-if="item.productImage" :src="item.productImage" />
              <div v-else class="img-placeholder">商品</div>
            </div>
            <div class="item-info">
              <p class="item-name">{{ item.productName || '商品 #' + item.productId }}</p>
              <p class="item-price">¥{{ (item.productPrice ?? item.price)?.toFixed(2) || '0.00' }}</p>
            </div>
            <div class="item-qty">
              <button @click="handleQuantity(item, -1)" :disabled="item.quantity <= 1">−</button>
              <span>{{ item.quantity }}</span>
              <button @click="handleQuantity(item, 1)">+</button>
            </div>
            <button class="remove-btn" @click="handleRemove(item)">删除</button>
          </div>
        </div>

        <div class="cart-footer">
          <label class="checkbox" @click="handleCheckAll">
            <span :class="['check-icon', { checked: allChecked }]">✓</span>
            <span>全选</span>
          </label>
          <div class="footer-right">
            <span class="total">合计: <strong>¥{{ totalPrice.toFixed(2) }}</strong></span>
            <button class="checkout-btn" :disabled="selectedItems.length === 0">结算({{ selectedItems.length }})</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 24px 0 60px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.page-header h2 { flex: 1; font-size: 20px; font-weight: 700; margin: 0; }
.back-btn { background: none; border: none; font-size: 15px; color: var(--ich-primary, #8B2020); cursor: pointer; padding: 6px 0; }
.clear-btn { background: none; border: 1px solid #ddd; border-radius: 16px; padding: 4px 16px; font-size: 13px; color: #999; cursor: pointer; }
.clear-btn:hover { border-color: #ff4d4f; color: #ff4d4f; }

.empty-state { text-align: center; padding: 80px 0; color: #bbb; font-size: 15px; }
.go-btn { margin-top: 16px; padding: 8px 32px; background: var(--ich-primary, #8B2020); color: #fff; border: none; border-radius: 20px; cursor: pointer; font-size: 14px; }

.cart-list { display: flex; flex-direction: column; gap: 12px; }
.cart-item {
  display: flex; align-items: center; gap: 16px; padding: 16px 20px;
  background: #fff; border-radius: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.checkbox { display: flex; align-items: center; gap: 8px; cursor: pointer; user-select: none; }
.check-icon {
  width: 22px; height: 22px; border-radius: 50%; border: 2px solid #ddd;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; color: transparent; transition: 0.2s;
}
.check-icon.checked { background: var(--ich-primary, #8B2020); border-color: var(--ich-primary, #8B2020); color: #fff; }
.item-img { width: 80px; height: 80px; border-radius: 8px; overflow: hidden; flex-shrink: 0; }
.item-img img { width: 100%; height: 100%; object-fit: cover; }
.img-placeholder { width: 100%; height: 100%; background: #f5f5f5; display: flex; align-items: center; justify-content: center; color: #ccc; font-size: 13px; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 14px; font-weight: 600; color: #333; margin: 0 0 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-price { font-size: 15px; color: #ff4d4f; font-weight: 600; margin: 0; }
.item-qty { display: flex; align-items: center; gap: 8px; }
.item-qty button { width: 28px; height: 28px; border-radius: 50%; border: 1px solid #ddd; background: #fff; cursor: pointer; font-size: 16px; display: flex; align-items: center; justify-content: center; }
.item-qty button:hover:not(:disabled) { border-color: var(--ich-primary, #8B2020); }
.item-qty button:disabled { opacity: 0.4; cursor: not-allowed; }
.item-qty span { min-width: 24px; text-align: center; font-size: 14px; }
.remove-btn { background: none; border: none; color: #ccc; font-size: 13px; cursor: pointer; padding: 4px 8px; }
.remove-btn:hover { color: #ff4d4f; }

.cart-footer {
  position: sticky; bottom: 0; margin-top: 20px; padding: 16px 24px;
  background: #fff; border-radius: 12px; box-shadow: 0 -2px 12px rgba(0,0,0,0.08);
  display: flex; align-items: center; justify-content: space-between;
}
.footer-right { display: flex; align-items: center; gap: 20px; }
.total { font-size: 14px; color: #666; }
.total strong { font-size: 18px; color: #ff4d4f; }
.checkout-btn {
  padding: 10px 36px; background: var(--ich-primary, #8B2020); color: #fff;
  border: none; border-radius: 24px; font-size: 15px; cursor: pointer;
}
.checkout-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
