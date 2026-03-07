<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <nav class="ag-sub-pills">
          <router-link to="/system/admin" class="ag-sub-pill" :class="{ active: $route.path === '/system/admin' }">{{ t('system.tabs.admin') }}</router-link>
          <router-link to="/system/role" class="ag-sub-pill" :class="{ active: $route.path === '/system/role' }">{{ t('system.tabs.role') }}</router-link>
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
            <el-input v-model="keyword" :placeholder="t('system.admin.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
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
              <el-form-item :label="t('common.status')">
                <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 120px" @change="loadData">
                  <el-option :label="t('common.active')" :value="1" />
                  <el-option :label="t('common.disabled')" :value="0" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="small" @click="loadData">查询</el-button>
                <el-button size="small" @click="resetFilters">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </transition>
        <el-table :data="tableData" v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column width="80" :label="t('common.avatar')" align="center" class-name="avatar-col">
          <template #default="{ row }">
            <div class="table-avatar">
              <img v-if="row.avatar" :src="row.avatar" alt="" />
              <span v-else>{{ (row.nickname || row.username || '?').charAt(0).toUpperCase() }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="username" :label="t('system.admin.username')" align="center" />
        <el-table-column prop="nickname" :label="t('system.admin.nickname')" align="center" />
        <el-table-column prop="email" :label="t('system.admin.email')" align="center" />
        <el-table-column prop="phone" :label="t('system.admin.phone')" align="center" />
        <el-table-column prop="status" :label="t('common.status')" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="val => handleStatusChange(row, val)" />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" :label="t('system.admin.lastLogin')" align="center">
          <template #default="{ row }">{{ formatDate(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="warning" size="small" @click="openRoleDialog(row)">{{ t('system.admin.roles') }}</el-button>
            <el-popconfirm :title="t('system.admin.deleteConfirm')" @confirm="handleDelete(row.id)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('system.admin.editTitle') : t('system.admin.addTitle')" width="500px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <div class="avatar-upload-wrapper">
          <div class="avatar-upload" @click="triggerAvatarInput">
            <img v-if="form.avatar" :src="form.avatar" alt="" />
            <div v-else class="avatar-placeholder">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
            </div>
            <div class="avatar-upload-overlay">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            </div>
          </div>
          <input ref="avatarInputRef" type="file" accept="image/*" style="display:none" @change="handleAvatarChange" />
        </div>
        <el-form-item :label="t('system.admin.username')" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item v-if="!isEdit" :label="t('system.admin.password')" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item :label="t('system.admin.nickname')"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item :label="t('system.admin.email')"><el-input v-model="form.email" /></el-form-item>
        <el-form-item :label="t('system.admin.phone')"><el-input v-model="form.phone" /></el-form-item>
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

    <el-dialog v-model="roleDialogVisible" :title="t('system.admin.assignRoles')" width="450px" destroy-on-close>
      <p style="margin-bottom: 16px; color: #64748B;">
        {{ t('system.admin.selectRolesFor', { name: currentAdmin?.nickname || currentAdmin?.username }) }}
      </p>
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="role in allRoles" :key="role.id" :value="role.id" style="display: block; margin-bottom: 8px;">
          {{ role.name }} <span style="color: #94A3B8; font-size: 12px;">{{ role.description }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleAssignRoles" :loading="roleSubmitting">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/auth'
import { getAdminList, getAdmin, addAdmin, updateAdmin, deleteAdmin, updateAdminStatus, assignRoles } from '@/api/system'
import { getRoleList } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportToCSV } from '@/utils/export'

const { t } = useI18n()
const authStore = useAuthStore()
const avatarInputRef = ref(null)


const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref(null)
const showAdvanced = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const defaultForm = { username: '', password: '', nickname: '', email: '', phone: '', avatar: '', status: 1 }
const form = ref({ ...defaultForm })

const rules = {
  username: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
  password: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
}

const roleDialogVisible = ref(false)
const currentAdmin = ref(null)
const selectedRoleIds = ref([])
const allRoles = ref([])
const roleSubmitting = ref(false)

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAdminList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    try {
      const res = await getAdmin(row.id)
      const detail = res.data || row
      form.value = { ...detail }
    } catch {
      form.value = { ...row }
    }
  } else {
    form.value = { ...defaultForm }
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateAdmin(form.value)
    } else {
      const { password, ...data } = form.value
      await addAdmin(data, password)
    }
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
    // If editing current admin, refresh adminInfo for greeting
    if (isEdit.value && form.value.id === authStore.adminInfo?.id) {
      authStore.fetchAdminInfo().catch(() => {})
    }
  } finally { submitting.value = false }
}

async function handleStatusChange(row, val) {
  await updateAdminStatus(row.id, val ? 1 : 0)
  ElMessage.success(t('common.statusUpdated'))
  loadData()
}

async function handleDelete(id) {
  await deleteAdmin(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

async function openRoleDialog(row) {
  currentAdmin.value = row
  selectedRoleIds.value = row.roleIds || []
  if (allRoles.value.length === 0) {
    const res = await getRoleList()
    allRoles.value = res.data || []
  }
  roleDialogVisible.value = true
}

async function handleAssignRoles() {
  roleSubmitting.value = true
  try {
    await assignRoles(currentAdmin.value.id, selectedRoleIds.value)
    ElMessage.success(t('system.admin.rolesAssigned'))
    roleDialogVisible.value = false
    loadData()
  } finally { roleSubmitting.value = false }
}

function triggerAvatarInput() {
  avatarInputRef.value?.click()
}

function handleAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    form.value.avatar = ev.target.result
  }
  reader.readAsDataURL(file)
}

const selectedIds = ref([])
function handleSelectionChange(rows) { selectedIds.value = rows.map(r => r.id) }

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条记录吗？`, '批量删除', { type: 'warning' })
    await Promise.all(selectedIds.value.map(id => deleteAdmin(id)))
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    loadData()
  } catch { /* cancelled */ }
}

function handleExport() {
  exportToCSV(tableData.value, [
    { label: 'ID', key: 'id' },
    { label: '用户名', key: 'username' },
    { label: '昵称', key: 'nickname' },
    { label: '邮箱', key: 'email' },
    { label: '手机号', key: 'phone' },
    { label: '状态', key: 'status', formatter: v => v === 1 ? '启用' : '禁用' },
    { label: '最后登录', key: 'lastLoginTime', formatter: v => formatDate(v) },
  ], '管理员')
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = null
  loadData()
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';

:deep(.avatar-col .cell) {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0;
}
.table-avatar {
  width: 36px;
  height: 36px;
  border-radius: 9999px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  flex-shrink: 0;
  img { width: 100%; height: 100%; object-fit: cover; }
  span { color: #fff; font-size: 14px; font-weight: 700; }
}

.avatar-upload-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}
.avatar-upload {
  width: 80px;
  height: 80px;
  border-radius: 9999px;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: box-shadow 0.2s;
  &:hover {
    box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.2);
    .avatar-upload-overlay { opacity: 1; }
  }
  img { width: 100%; height: 100%; object-fit: cover; }
}
.avatar-placeholder {
  color: rgba(255,255,255,0.7);
}
.avatar-upload-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 28px;
  background: rgba(0,0,0,0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s;
}
</style>
