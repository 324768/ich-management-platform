<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <nav class="ag-sub-pills">
          <router-link to="/system/admin" class="ag-sub-pill" :class="{ active: $route.path === '/system/admin' }">{{ t('system.tabs.admin') }}</router-link>
          <router-link to="/system/role" class="ag-sub-pill" :class="{ active: $route.path === '/system/role' }">{{ t('system.tabs.role') }}</router-link>
          <router-link to="/system/notification" class="ag-sub-pill" :class="{ active: $route.path === '/system/notification' }">{{ t('notification.tab') }}</router-link>
        </nav>
        <div class="toolbar-actions">
          <button class="ag-btn" @click="openDialog()">
            <el-icon :size="14"><Plus /></el-icon>
            <span>{{ t('notification.add') }}</span>
          </button>
        </div>
      </div>

      <div class="ag-card">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-input v-model="keyword" :placeholder="t('notification.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="typeFilter" :placeholder="t('notification.type')" clearable style="width: 160px" @change="loadData">
              <el-option :label="t('notification.typeSystem')" :value="1" />
              <el-option :label="t('notification.typeActivity')" :value="2" />
              <el-option :label="t('notification.typeOrder')" :value="3" />
              <el-option :label="t('notification.typeSecurity')" :value="4" />
            </el-select>
            <el-select v-model="publishFilter" :placeholder="t('notification.publishStatus')" clearable style="width: 140px" @change="loadData">
              <el-option :label="t('notification.unpublished')" :value="0" />
              <el-option :label="t('notification.published')" :value="1" />
            </el-select>
          </div>
        </div>

        <el-table :data="tableData" v-loading="loading">
          <el-table-column prop="id" :label="t('common.id')" width="70" align="center" />
          <el-table-column prop="title" :label="t('notification.title')" min-width="200" show-overflow-tooltip />
          <el-table-column :label="t('notification.type')" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="msgTypeTag(row.messageType)" size="small" round>{{ msgTypeLabel(row.messageType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('notification.receiver')" width="120" align="center">
            <template #default="{ row }">
              <span>{{ receiverLabel(row.receiverType) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('notification.publishStatus')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isPublished ? 'success' : 'info'" size="small" round>
                {{ row.isPublished ? t('notification.published') : t('notification.unpublished') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('notification.publishTime')" width="160" align="center">
            <template #default="{ row }">{{ row.publishTime ? formatDate(row.publishTime) : '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('common.createdAt')" width="160" align="center">
            <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="200" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDialog(row)">{{ t('common.edit') }}</el-button>
              <el-button v-if="!row.isPublished" link type="success" size="small" @click="handlePublish(row)">{{ t('notification.publish') }}</el-button>
              <el-popconfirm :title="t('notification.deleteConfirm')" @confirm="handleDelete(row.id)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('notification.editTitle') : t('notification.addTitle')" width="600px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item :label="t('notification.title')" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item :label="t('notification.type')" prop="messageType">
          <el-select v-model="form.messageType" style="width: 100%">
            <el-option :label="t('notification.typeSystem')" :value="1" />
            <el-option :label="t('notification.typeActivity')" :value="2" />
            <el-option :label="t('notification.typeOrder')" :value="3" />
            <el-option :label="t('notification.typeSecurity')" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('notification.receiver')">
          <el-select v-model="form.receiverType" style="width: 100%">
            <el-option :label="t('notification.allUsers')" :value="0" />
            <el-option :label="t('notification.specificUsers')" :value="1" />
            <el-option :label="t('notification.specificRoles')" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.receiverType > 0" :label="t('notification.receiverIds')" prop="receiverIds">
          <el-input v-model="form.receiverIds" :placeholder="t('notification.receiverIdsPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('notification.content')" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item :label="t('notification.expireTime')">
          <el-date-picker v-model="form.expireTime" type="datetime" style="width: 100%" />
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
import { getNotificationList, getNotification, addNotification, updateNotification, deleteNotification, publishNotification } from '@/api/notification'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()

const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const typeFilter = ref(null)
const publishFilter = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const defaultForm = { title: '', messageType: 1, receiverType: 0, receiverIds: '', content: '', expireTime: null }
const form = ref({ ...defaultForm })
const rules = {
  title: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
  messageType: [{ required: true, message: () => t('common.required'), trigger: 'change' }],
  receiverIds: [{ validator: (rule, value, callback) => {
    if (form.value.receiverType > 0 && !value?.trim()) callback(new Error(t('common.required')))
    else callback()
  }, trigger: 'blur' }],
  content: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
}

function msgTypeLabel(type) {
  const map = { 1: t('notification.typeSystem'), 2: t('notification.typeActivity'), 3: t('notification.typeOrder'), 4: t('notification.typeSecurity') }
  return map[type] || '-'
}

function msgTypeTag(type) {
  return { 1: '', 2: 'success', 3: 'warning', 4: 'danger' }[type] || 'info'
}

function receiverLabel(type) {
  const map = { 0: t('notification.allUsers'), 1: t('notification.specificUsers'), 2: t('notification.specificRoles') }
  return map[type] || '-'
}

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadData() {
  loading.value = true
  try {
    const res = await getNotificationList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, messageType: typeFilter.value ?? undefined, isPublished: publishFilter.value ?? undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    try {
      const res = await getNotification(row.id)
      form.value = { ...(res.data || row) }
    } catch { form.value = { ...row } }
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
    isEdit.value ? await updateNotification(form.value) : await addNotification(form.value)
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(t('notification.publishConfirm'), t('common.confirm'), { type: 'warning' })
    await publishNotification(row.id)
    ElMessage.success(t('notification.publishSuccess'))
    loadData()
  } catch { /* cancelled */ }
}

async function handleDelete(id) {
  await deleteNotification(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
</style>
