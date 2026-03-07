<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <h2 class="ag-page-title">{{ t('user.title') }}</h2>
      </div>

      <div class="ag-card">
        <div class="table-toolbar">
          <el-input v-model="keyword" :placeholder="t('user.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <el-table :data="tableData" v-loading="loading">
        <el-table-column width="80" :label="t('common.avatar')" align="center" class-name="avatar-col">
          <template #default="{ row }">
            <div class="table-avatar">
              <img v-if="row.avatar" :src="row.avatar" alt="" />
              <span v-else>{{ (row.nickname || row.username || '?').charAt(0).toUpperCase() }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="username" :label="t('user.username')" align="center" />
        <el-table-column prop="nickname" :label="t('user.nickname')" align="center" />
        <el-table-column prop="email" :label="t('user.email')" align="center" />
        <el-table-column prop="phone" :label="t('user.phone')" align="center" />
        <el-table-column prop="status" :label="t('common.status')" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="val => handleStatusChange(row, val)" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('user.created')" width="170" align="center">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
      </el-table>
        <div class="ag-pagination">
          <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
            :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @change="loadData" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getUserList, updateUserStatus } from '@/api/user'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadData() {
  loading.value = true
  try {
    const res = await getUserList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function handleStatusChange(row, val) {
  const status = val ? 1 : 0
  await updateUserStatus(row.id, status)
  ElMessage.success(t('common.statusUpdated'))
  loadData()
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
</style>
