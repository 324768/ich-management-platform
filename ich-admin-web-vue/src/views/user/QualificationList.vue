<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
    <div class="ag-page-toolbar">
      <nav class="ag-sub-pills">
        <router-link to="/user" class="ag-sub-pill" :class="{ active: $route.path === '/user' }">{{ t('user.tabs.list') }}</router-link>
        <router-link to="/user/qualification" class="ag-sub-pill" :class="{ active: $route.path === '/user/qualification' }">{{ t('user.tabs.qualification') }}</router-link>
      </nav>
    </div>

    <div class="ag-page-toolbar">
      <el-input v-model="keyword" :placeholder="t('qualification.searchPlaceholder')" clearable
        style="width: 260px" @keyup.enter="loadData" />
      <el-select v-model="filterStatus" :placeholder="t('qualification.status')" clearable style="width: 140px" @change="loadData">
        <el-option :label="t('qualification.statusPending')" :value="0" />
        <el-option :label="t('qualification.statusApproved')" :value="1" />
        <el-option :label="t('qualification.statusRejected')" :value="2" />
      </el-select>
    </div>

    <div class="ag-card">
      <el-table :data="tableData" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" align="center" />
        <el-table-column prop="userName" :label="t('qualification.userName')" width="120" align="center" />
        <el-table-column prop="userPhone" :label="t('qualification.userPhone')" width="130" align="center" />
        <el-table-column :label="t('qualification.type')" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" round :type="row.qualificationType === 1 ? 'warning' : 'primary'">
              {{ row.qualificationType === 1 ? t('qualification.typeInheritor') : t('qualification.typeCompany') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="t('qualification.qualificationTitle')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="t('qualification.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" round :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('qualification.applyTime')" width="160" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">{{ t('qualification.detail') }}</el-button>
            <template v-if="row.status === 0">
              <el-button size="small" type="success" @click="handleApprove(row)">{{ t('qualification.approve') }}</el-button>
              <el-button size="small" type="danger" @click="openReject(row)">{{ t('qualification.reject') }}</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="ag-pagination" v-if="total > pageSize">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
          :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </div>

    <!-- ========== 详情弹窗 ========== -->
    <el-dialog v-model="detailVisible" :title="t('qualification.detail')" width="700px" destroy-on-close>
      <template v-if="currentRow">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="ID">{{ currentRow.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('qualification.status')">
            <el-tag size="small" round :type="statusType(currentRow.status)">{{ statusLabel(currentRow.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('qualification.userName')">{{ currentRow.userName }}</el-descriptions-item>
          <el-descriptions-item :label="t('qualification.userPhone')">{{ currentRow.userPhone }}</el-descriptions-item>
          <el-descriptions-item :label="t('qualification.type')">
            {{ currentRow.qualificationType === 1 ? t('qualification.typeInheritor') : t('qualification.typeCompany') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('qualification.qualificationTitle')">{{ currentRow.title || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('qualification.description')" :span="2">{{ currentRow.description || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('qualification.applyTime')">{{ formatDateTime(currentRow.createTime) }}</el-descriptions-item>
          <el-descriptions-item :label="t('qualification.reviewTime')">{{ currentRow.reviewTime ? formatDateTime(currentRow.reviewTime) : '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('qualification.rejectReason')" :span="2" v-if="currentRow.status === 2">{{ currentRow.rejectReason || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 身份证 -->
        <div class="detail-section" v-if="currentRow.idCardFront || currentRow.idCardBack">
          <h4 class="section-title-sm">{{ t('qualification.idCardFront') }} / {{ t('qualification.idCardBack') }}</h4>
          <div class="image-gallery">
            <el-image v-if="currentRow.idCardFront" :src="currentRow.idCardFront" fit="cover" class="preview-img"
              :preview-src-list="[currentRow.idCardFront, currentRow.idCardBack].filter(Boolean)" preview-teleported />
            <el-image v-if="currentRow.idCardBack" :src="currentRow.idCardBack" fit="cover" class="preview-img"
              :preview-src-list="[currentRow.idCardFront, currentRow.idCardBack].filter(Boolean)" :initial-index="1" preview-teleported />
          </div>
        </div>

        <!-- 证明材料 -->
        <div class="detail-section" v-if="currentRow.materials?.length">
          <h4 class="section-title-sm">{{ t('qualification.materials') }}</h4>
          <div class="image-gallery">
            <el-image v-for="(img, idx) in currentRow.materials" :key="'mat-'+idx" :src="img" fit="cover" class="preview-img"
              :preview-src-list="currentRow.materials" :initial-index="idx" preview-teleported />
          </div>
        </div>

        <!-- 资格证书 -->
        <div class="detail-section" v-if="currentRow.certificateImages?.length">
          <h4 class="section-title-sm">{{ t('qualification.certificateImages') }}</h4>
          <div class="image-gallery">
            <el-image v-for="(img, idx) in currentRow.certificateImages" :key="'cert-'+idx" :src="img" fit="cover" class="preview-img"
              :preview-src-list="currentRow.certificateImages" :initial-index="idx" preview-teleported />
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- ========== 拒绝弹窗 ========== -->
    <el-dialog v-model="rejectVisible" :title="t('qualification.reject')" width="450px" destroy-on-close>
      <el-input v-model="rejectReason" type="textarea" :rows="3" :placeholder="t('qualification.rejectReasonPlaceholder')" />
      <template #footer>
        <el-button @click="rejectVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" @click="handleReject" :loading="reviewing">{{ t('qualification.reject') }}</el-button>
      </template>
    </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getQualificationList, reviewQualification } from '@/api/qualification'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()

const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const filterStatus = ref(null)

const detailVisible = ref(false)
const currentRow = ref(null)

const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref(null)
const reviewing = ref(false)

function statusLabel(s) {
  return { 0: t('qualification.statusPending'), 1: t('qualification.statusApproved'), 2: t('qualification.statusRejected') }[s] || '-'
}

function statusType(s) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[s] || 'info'
}

function formatDateTime(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    if (filterStatus.value !== null && filterStatus.value !== '') params.status = filterStatus.value
    const res = await getQualificationList(params)
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  currentRow.value = row
  detailVisible.value = true
}

async function handleApprove(row) {
  await ElMessageBox.confirm(t('qualification.approveConfirm'), t('common.confirm'))
  reviewing.value = true
  try {
    await reviewQualification({ id: row.id, status: 1 })
    ElMessage.success(t('qualification.statusApproved'))
    loadData()
  } finally {
    reviewing.value = false
  }
}

function openReject(row) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function handleReject() {
  reviewing.value = true
  try {
    await reviewQualification({ id: rejectTarget.value.id, status: 2, rejectReason: rejectReason.value })
    ElMessage.success(t('qualification.statusRejected'))
    rejectVisible.value = false
    loadData()
  } finally {
    reviewing.value = false
  }
}

onMounted(() => { loadData() })
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';

.detail-section {
  margin-top: 16px;
}

.section-title-sm {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 8px;
}

.image-gallery {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.preview-img {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  border: 1px solid #f3f4f6;
  cursor: pointer;
}
</style>
