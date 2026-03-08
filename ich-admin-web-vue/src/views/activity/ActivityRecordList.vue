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
            <el-input v-model="keyword" :placeholder="t('activity.historySearchPlaceholder')" clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="typeFilter" :placeholder="t('activity.type')" clearable style="width: 140px" @change="loadData">
              <el-option :label="t('activity.typeShow')" :value="1" />
              <el-option :label="t('activity.typeWorkshop')" :value="2" />
              <el-option :label="t('activity.typeLecture')" :value="3" />
              <el-option :label="t('activity.typeExhibition')" :value="4" />
              <el-option :label="t('activity.typeOther')" :value="5" />
            </el-select>
          </div>
        </div>

        <el-table :data="tableData" v-loading="loading" @row-click="row => router.push('/activity/history/' + row.id)" style="cursor: pointer;">
          <el-table-column prop="id" :label="t('common.id')" width="70" align="center" />
          <el-table-column :label="t('activity.cover')" width="80" align="center">
            <template #default="{ row }">
              <el-image v-if="row.coverImage" :src="row.coverImage" fit="cover" style="width: 48px; height: 48px; border-radius: 6px;" />
              <span v-else style="color: #ccc;">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" :label="t('common.name')" width="200" show-overflow-tooltip />
          <el-table-column :label="t('activity.type')" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" round>{{ activityTypeLabel(row.activityType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="location" :label="t('activity.location')" min-width="120" show-overflow-tooltip />
          <el-table-column :label="t('activity.time')" min-width="160" align="center">
            <template #default="{ row }">
              <div style="font-size: 12px;">{{ formatDate(row.startTime) }}</div>
              <div style="font-size: 11px; color: #999;">~ {{ formatDate(row.endTime) }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('activity.participants')" width="100" align="center">
            <template #default="{ row }">
              {{ row.currentParticipants || 0 }} / {{ row.maxParticipants || '∞' }}
            </template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="100" align="center">
            <template #default>
              <el-tag size="small" round>{{ t('activity.statusEnded') }}</el-tag>
            </template>
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
import { useRouter } from 'vue-router'
import { getActivityList } from '@/api/activity'

const { t } = useI18n()
const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const keyword = ref('')
const typeFilter = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

function activityTypeLabel(type) {
  const map = { 1: t('activity.typeShow'), 2: t('activity.typeWorkshop'), 3: t('activity.typeLecture'), 4: t('activity.typeExhibition'), 5: t('activity.typeOther') }
  return map[type] || '-'
}

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadData() {
  loading.value = true
  try {
    const res = await getActivityList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, status: 3, activityType: typeFilter.value ?? undefined })
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
</style>
