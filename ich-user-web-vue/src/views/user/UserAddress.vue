<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/utils/token'
import { listAddresses, addAddress, updateAddress, deleteAddress, setDefaultAddress } from '@/api/user'

const router = useRouter()
const user = getUserInfo()
const addresses = ref([])
const loading = ref(false)
const showForm = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = ref({ receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: 0 })

const loadAddresses = async () => {
  if (!user?.id) return
  loading.value = true
  try {
    const res = await listAddresses(user.id)
    addresses.value = res.data || []
  } catch { addresses.value = [] }
  loading.value = false
}

const openAdd = () => {
  editingId.value = null
  form.value = { receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: 0 }
  showForm.value = true
}

const openEdit = (addr) => {
  editingId.value = addr.id
  form.value = { ...addr }
  showForm.value = true
}

const saveForm = async () => {
  if (!form.value.receiverName?.trim()) { alert('请输入收货人姓名'); return }
  if (!form.value.receiverPhone?.trim()) { alert('请输入手机号'); return }
  if (!form.value.detailAddress?.trim()) { alert('请输入详细地址'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateAddress({ ...form.value, id: editingId.value, userId: user.id })
    } else {
      await addAddress({ ...form.value, userId: user.id })
    }
    showForm.value = false
    loadAddresses()
  } catch (e) {
    alert('保存失败: ' + (e.response?.data?.message || e.message))
  }
  saving.value = false
}

const handleDelete = async (addr) => {
  if (!confirm('确定删除该地址？')) return
  try {
    await deleteAddress(user.id, addr.id)
    loadAddresses()
  } catch { alert('删除失败') }
}

const handleSetDefault = async (addr) => {
  try {
    await setDefaultAddress(user.id, addr.id)
    loadAddresses()
  } catch { alert('设置失败') }
}

onMounted(loadAddresses)
</script>

<template>
  <div class="page-container">
    <div class="container">
      <div class="page-header">
        <button class="back-btn" @click="router.push('/user')">← 返回</button>
        <h2>收货地址</h2>
        <button class="add-btn" @click="openAdd">+ 新增地址</button>
      </div>

      <div v-if="loading" class="empty-state">加载中...</div>
      <div v-else-if="addresses.length === 0" class="empty-state">
        <p>暂无收货地址</p>
        <button class="go-btn" @click="openAdd">添加地址</button>
      </div>
      <div v-else class="addr-list">
        <div v-for="addr in addresses" :key="addr.id" class="addr-card">
          <div class="addr-info">
            <div class="addr-top">
              <span class="addr-name">{{ addr.receiverName }}</span>
              <span class="addr-phone">{{ addr.receiverPhone }}</span>
              <span v-if="addr.isDefault === 1" class="default-tag">默认</span>
            </div>
            <p class="addr-detail">{{ addr.province }}{{ addr.city }}{{ addr.district }} {{ addr.detailAddress }}</p>
          </div>
          <div class="addr-actions">
            <button @click="openEdit(addr)">编辑</button>
            <button @click="handleDelete(addr)">删除</button>
            <button v-if="addr.isDefault !== 1" @click="handleSetDefault(addr)">设为默认</button>
          </div>
        </div>
      </div>

      <!-- 新增/编辑弹窗 -->
      <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
        <div class="modal-box">
          <div class="modal-header">
            <h3>{{ editingId ? '编辑地址' : '新增地址' }}</h3>
            <span class="modal-close" @click="showForm = false">&times;</span>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label>收货人</label>
              <input v-model="form.receiverName" placeholder="请输入姓名" maxlength="20" />
            </div>
            <div class="form-group">
              <label>手机号</label>
              <input v-model="form.receiverPhone" placeholder="请输入手机号" maxlength="11" />
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>省份</label>
                <input v-model="form.province" placeholder="省" />
              </div>
              <div class="form-group">
                <label>城市</label>
                <input v-model="form.city" placeholder="市" />
              </div>
              <div class="form-group">
                <label>区/县</label>
                <input v-model="form.district" placeholder="区" />
              </div>
            </div>
            <div class="form-group">
              <label>详细地址</label>
              <input v-model="form.detailAddress" placeholder="街道、门牌号等" />
            </div>
          </div>
          <div class="modal-footer">
            <button class="modal-btn cancel" @click="showForm = false">取消</button>
            <button class="modal-btn confirm" :disabled="saving" @click="saveForm">
              {{ saving ? '保存中...' : '保存' }}
            </button>
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
.back-btn { background: none; border: none; font-size: 15px; color: var(--ich-primary, #8B2020); cursor: pointer; }
.add-btn { padding: 6px 20px; background: var(--ich-primary, #8B2020); color: #fff; border: none; border-radius: 20px; font-size: 13px; cursor: pointer; }

.empty-state { text-align: center; padding: 80px 0; color: #bbb; font-size: 15px; }
.go-btn { margin-top: 16px; padding: 8px 32px; background: var(--ich-primary, #8B2020); color: #fff; border: none; border-radius: 20px; cursor: pointer; font-size: 14px; }

.addr-list { display: flex; flex-direction: column; gap: 12px; }
.addr-card {
  display: flex; align-items: center; justify-content: space-between; padding: 18px 24px;
  background: #fff; border-radius: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.addr-info { flex: 1; min-width: 0; }
.addr-top { display: flex; align-items: center; gap: 12px; margin-bottom: 6px; }
.addr-name { font-size: 15px; font-weight: 600; color: #333; }
.addr-phone { font-size: 14px; color: #666; }
.default-tag { font-size: 11px; color: var(--ich-primary, #8B2020); border: 1px solid var(--ich-primary, #8B2020); padding: 1px 8px; border-radius: 10px; }
.addr-detail { font-size: 13px; color: #999; margin: 0; }
.addr-actions { display: flex; gap: 8px; flex-shrink: 0; }
.addr-actions button { background: none; border: 1px solid #eee; padding: 4px 14px; border-radius: 14px; font-size: 12px; color: #666; cursor: pointer; transition: 0.2s; }
.addr-actions button:hover { border-color: var(--ich-primary, #8B2020); color: var(--ich-primary, #8B2020); }

/* 弹窗 */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 9999; }
.modal-box { background: #fff; border-radius: 12px; width: 480px; max-width: 92vw; overflow: hidden; box-shadow: 0 20px 60px rgba(0,0,0,0.2); }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px; border-bottom: 1px solid #f0f0f0; }
.modal-header h3 { font-size: 18px; font-weight: 600; margin: 0; }
.modal-close { font-size: 24px; color: #999; cursor: pointer; }
.modal-body { padding: 24px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-size: 13px; color: #666; margin-bottom: 6px; }
.form-group input { width: 100%; padding: 10px 14px; border: 1px solid #e8e8e8; border-radius: 8px; font-size: 14px; box-sizing: border-box; }
.form-group input:focus { outline: none; border-color: var(--ich-primary, #8B2020); }
.form-row { display: flex; gap: 12px; }
.form-row .form-group { flex: 1; }
.modal-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 24px; border-top: 1px solid #f0f0f0; }
.modal-btn { padding: 8px 24px; border-radius: 8px; font-size: 14px; cursor: pointer; }
.modal-btn.cancel { background: #fff; border: 1px solid #ddd; color: #666; }
.modal-btn.confirm { background: var(--ich-primary, #8B2020); border: none; color: #fff; }
.modal-btn.confirm:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
