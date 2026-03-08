<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <nav class="ag-sub-pills">
          <router-link to="/activity/list" class="ag-sub-pill" :class="{ active: $route.path === '/activity/list' }">{{ t('activity.tabs.list') }}</router-link>
          <router-link to="/activity/record" class="ag-sub-pill" :class="{ active: $route.path === '/activity/record' }">{{ t('activity.tabs.records') }}</router-link>
          <router-link to="/activity/approval" class="ag-sub-pill" :class="{ active: $route.path === '/activity/approval' }">{{ t('activity.tabs.approval') }}</router-link>
        </nav>
      </div>

      <div class="ag-card">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-input v-model="keyword" :placeholder="t('activity.searchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>
        </div>

        <!-- 三个子标签：待审批 / 已通过 / 已拒绝 -->
        <el-tabs v-model="activeTab" @tab-change="loadData">
          <el-tab-pane :label="t('activity.approval.pending')" name="pending">
            <approval-table :data="tableData" :loading="loading" :show-actions="true"
              @approve="handleApprove" @reject="openReject" @detail="goDetail" />
          </el-tab-pane>
          <el-tab-pane :label="t('activity.approval.approved')" name="approved">
            <approval-table :data="tableData" :loading="loading" :show-actions="false" @detail="goDetail" />
          </el-tab-pane>
          <el-tab-pane :label="t('activity.approval.rejected')" name="rejected">
            <approval-table :data="tableData" :loading="loading" :show-actions="false" :show-reject-reason="true" @detail="goDetail" />
          </el-tab-pane>
        </el-tabs>

        <div class="ag-pagination" v-if="total > pageSize">
          <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
            :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @change="loadData" />
        </div>
      </div>

      <!-- 拒绝弹窗 -->
      <el-dialog v-model="rejectVisible" :title="t('activity.approval.reject')" width="450px" destroy-on-close>
        <el-input v-model="rejectReason" type="textarea" :rows="3" :placeholder="t('activity.approval.rejectReasonPlaceholder')" />
        <template #footer>
          <el-button @click="rejectVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="danger" @click="handleReject" :loading="reviewing">{{ t('activity.approval.reject') }}</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, defineComponent, h } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { getApprovalList, reviewActivity } from '@/api/activity'
import { ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTag, ElButton } from 'element-plus'

const { t } = useI18n()
const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const activeTab = ref('pending')

const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref(null)
const reviewing = ref(false)

const tabStatusMap = { pending: 1, approved: 2, rejected: 3 }

function activityTypeLabel(type) {
  const map = { 1: t('activity.typeShow'), 2: t('activity.typeWorkshop'), 3: t('activity.typeLecture'), 4: t('activity.typeExhibition'), 5: t('activity.typeOther') }
  return map[type] || '-'
}

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value, approvalStatus: tabStatusMap[activeTab.value] }
    if (keyword.value) params.keyword = keyword.value
    const res = await getApprovalList(params)
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(row) {
  router.push(`/activity/detail/${row.id}`)
}

async function handleApprove(row) {
  await ElMessageBox.confirm(t('activity.approval.approveConfirm'), t('common.confirm'))
  reviewing.value = true
  try {
    await reviewActivity({ id: row.id, approvalStatus: 2 })
    ElMessage.success(t('activity.approval.approved'))
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
    await reviewActivity({ id: rejectTarget.value.id, approvalStatus: 3, rejectReason: rejectReason.value })
    ElMessage.success(t('activity.approval.rejected'))
    rejectVisible.value = false
    loadData()
  } finally {
    reviewing.value = false
  }
}

onMounted(() => { loadData() })

// 内联子组件：审批表格
const ApprovalTable = defineComponent({
  name: 'ApprovalTable',
  props: {
    data: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    showActions: { type: Boolean, default: false },
    showRejectReason: { type: Boolean, default: false },
  },
  emits: ['approve', 'reject', 'detail'],
  setup(props, { emit }) {
    return () => h(ElTable, { data: props.data, loading: props.loading, style: 'width: 100%', size: 'small', tableLayout: 'fixed' }, () => [
      h(ElTableColumn, { prop: 'id', label: 'ID', align: 'center' }),
      h(ElTableColumn, { prop: 'name', label: t('common.name'), showOverflowTooltip: true }),
      h(ElTableColumn, { label: t('activity.type'), align: 'center' }, {
        default: ({ row }) => h(ElTag, { size: 'small', round: true }, () => activityTypeLabel(row.activityType))
      }),
      h(ElTableColumn, { prop: 'maxParticipants', label: t('activity.maxParticipants'), align: 'center' }),
      h(ElTableColumn, { prop: 'organizer', label: t('activity.organizer'), align: 'center' }),
      h(ElTableColumn, { label: t('activity.startTime'), align: 'center' }, {
        default: ({ row }) => formatDate(row.startTime)
      }),
      ...(props.showRejectReason ? [
        h(ElTableColumn, { prop: 'rejectReason', label: t('activity.approval.rejectReason'), showOverflowTooltip: true })
      ] : []),
      h(ElTableColumn, { label: t('common.actions'), align: 'center' }, {
        default: ({ row }) => {
          const btns = [h(ElButton, { size: 'small', onClick: () => emit('detail', row) }, () => t('common.edit'))]
          if (props.showActions) {
            btns.push(h(ElButton, { size: 'small', type: 'success', onClick: () => emit('approve', row) }, () => t('activity.approval.approve')))
            btns.push(h(ElButton, { size: 'small', type: 'danger', onClick: () => emit('reject', row) }, () => t('activity.approval.reject')))
          }
          return btns
        }
      }),
    ])
  }
})
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
</style>
