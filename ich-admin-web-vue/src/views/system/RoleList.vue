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
        <el-table :data="tableData" v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column prop="id" :label="t('common.id')" width="70" align="center" />
        <el-table-column prop="name" :label="t('system.role.roleName')" align="center" />
        <el-table-column prop="code" :label="t('system.role.roleCode')" align="center" />
        <el-table-column prop="description" :label="t('common.description')" align="center" />
        <el-table-column prop="status" :label="t('common.status')" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>{{ row.status === 1 ? t('common.active') : t('common.disabled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">{{ t('common.edit') }}</el-button>
            <el-popconfirm :title="t('system.role.deleteConfirm')" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger" size="small">{{ t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('system.role.editTitle') : t('system.role.addTitle')" width="480px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item :label="t('system.role.roleName')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('system.role.roleCode')" prop="code"><el-input v-model="form.code" :placeholder="t('system.role.codePlaceholder')" /></el-form-item>
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
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getRoleList, addRole, updateRole, deleteRole } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportToCSV } from '@/utils/export'

const { t } = useI18n()


const tableData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const defaultForm = { name: '', code: '', description: '', status: 1 }
const form = ref({ ...defaultForm })
const rules = {
  name: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
  code: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const res = await getRoleList()
    tableData.value = res.data || []
  } finally { loading.value = false }
}

function openDialog(row) {
  isEdit.value = !!row
  form.value = row ? { ...row } : { ...defaultForm }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    isEdit.value ? await updateRole(form.value) : await addRole(form.value)
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await deleteRole(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

const selectedIds = ref([])
function handleSelectionChange(rows) { selectedIds.value = rows.map(r => r.id) }

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条记录吗？`, '批量删除', { type: 'warning' })
    await Promise.all(selectedIds.value.map(id => deleteRole(id)))
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    loadData()
  } catch { /* cancelled */ }
}

function handleExport() {
  exportToCSV(tableData.value, [
    { label: 'ID', key: 'id' },
    { label: '角色名称', key: 'name' },
    { label: '角色编码', key: 'code' },
    { label: '描述', key: 'description' },
    { label: '状态', key: 'status', formatter: v => v === 1 ? '启用' : '禁用' },
  ], '角色')
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
</style>
