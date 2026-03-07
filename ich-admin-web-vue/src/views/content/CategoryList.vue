<template>
  <div class="ag-page-scroll">
    <div class="ag-page-content">
      <div class="ag-page-toolbar">
        <nav class="ag-sub-pills">
          <router-link to="/content/category" class="ag-sub-pill" :class="{ active: $route.path === '/content/category' }">{{ t('content.tabs.category') }}</router-link>
          <router-link to="/content/item" class="ag-sub-pill" :class="{ active: $route.path === '/content/item' }">{{ t('content.tabs.items') }}</router-link>
          <router-link to="/content/heritage" class="ag-sub-pill" :class="{ active: $route.path === '/content/heritage' }">{{ t('content.tabs.heritageMan') }}</router-link>
        </nav>
        <button class="ag-btn" @click="openDialog()">
          <el-icon :size="14"><Plus /></el-icon>
          <span>{{ t('common.add') }}</span>
        </button>
      </div>

      <div class="ag-card">
        <el-table :data="treeData" v-loading="loading" row-key="id" default-expand-all>
          <el-table-column prop="name" :label="t('common.name')" width="120" align="center" />
          <el-table-column :label="t('content.category.icon')" align="center">
            <template #default="{ row }">
              <el-image v-if="row.icon" :src="row.icon" fit="cover" style="width: 36px; height: 36px; border-radius: 6px;" :preview-src-list="[row.icon]" preview-teleported />
              <span v-else style="color: #ccc;">-</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('content.category.detailImages')" align="center">
            <template #default="{ row }">
              <span style="color: #6366f1; font-weight: 500;">{{ row.detailImages ? (Array.isArray(row.detailImages) ? row.detailImages.length : 0) : 0 }}{{ t('common.imageUnit') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="level" :label="t('common.level')" align="center" />
          <el-table-column prop="sort" :label="t('common.sort')" align="center" />
          <el-table-column prop="status" :label="t('common.status')" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>{{ row.status === 1 ? t('common.active') : t('common.disabled') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="140" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDialog(row)">{{ t('common.edit') }}</el-button>
              <el-popconfirm :title="t('content.category.deleteConfirm')" @confirm="handleDelete(row.id)">
                <template #reference><el-button link type="danger" size="small">{{ t('common.delete') }}</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('content.category.editTitle') : t('content.category.addTitle')" width="600px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item :label="t('common.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('common.parent')">
          <el-select v-model="form.parentId" clearable :placeholder="t('common.topLevel')" style="width: 100%">
            <el-option :label="t('common.topLevel')" :value="0" />
            <el-option v-for="c in topCategories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.sort')">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item :label="t('content.category.icon')">
          <el-upload
            v-model:file-list="iconFileList"
            list-type="picture-card"
            :auto-upload="false"
            :on-change="handleIconChange"
            :on-remove="handleIconRemove"
            accept="image/*"
            :limit="1"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('content.category.detailImages')">
          <el-upload
            v-model:file-list="uploadFileList"
            list-type="picture-card"
            :auto-upload="false"
            :on-change="handleImageChange"
            :on-remove="handleImageRemove"
            accept="image/*"
            multiple
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
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
import { getCategoryTree, getCategory, addCategory, updateCategory, deleteCategory } from '@/api/content'
import { ElMessage } from 'element-plus'

const { t } = useI18n()


const treeData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = ref({ name: '', parentId: 0, sort: 0, icon: '', detailImages: [], status: 1 })
const iconFileList = ref([])
const uploadFileList = ref([])
const rules = { name: [{ required: true, message: () => t('common.required'), trigger: 'blur' }] }

const topCategories = computed(() => treeData.value.map(c => ({ id: c.id, name: c.name })))

function fileToBase64(file) {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.readAsDataURL(file)
  })
}

async function handleIconChange(uploadFile) {
  if (uploadFile.raw) {
    if (uploadFile.raw.size > 5 * 1024 * 1024) {
      ElMessage.warning(t('common.fileTooLarge'))
      iconFileList.value = []
      return
    }
    form.value.icon = await fileToBase64(uploadFile.raw)
  }
}

function handleIconRemove() {
  form.value.icon = ''
}

async function handleImageChange(uploadFile) {
  if (uploadFile.raw) {
    if (uploadFile.raw.size > 5 * 1024 * 1024) {
      ElMessage.warning(t('common.fileTooLarge'))
      uploadFileList.value = uploadFileList.value.filter(f => f.uid !== uploadFile.uid)
      return
    }
    const base64 = await fileToBase64(uploadFile.raw)
    uploadFile._base64 = base64
    syncImagesToForm()
  }
}

function handleImageRemove() {
  syncImagesToForm()
}

function syncImagesToForm() {
  form.value.detailImages = uploadFileList.value
    .filter(f => f._base64 || f.url)
    .map(f => f._base64 || f.url)
}

async function loadData() {
  loading.value = true
  try {
    const res = await getCategoryTree()
    treeData.value = res.data || []
  } finally { loading.value = false }
}

async function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    try {
      const res = await getCategory(row.id)
      const detail = res.data || row
      const images = detail.detailImages ? [...detail.detailImages] : []
      form.value = { ...detail, detailImages: images }
      iconFileList.value = detail.icon ? [{ name: 'icon', url: detail.icon, uid: Date.now() + 100, status: 'success' }] : []
      uploadFileList.value = images.map((src, idx) => ({
        name: `image-${idx}`,
        url: src,
        _base64: src,
        uid: Date.now() + idx,
        status: 'success'
      }))
    } catch {
      form.value = { ...row, detailImages: [] }
      iconFileList.value = []
      uploadFileList.value = []
    }
  } else {
    form.value = { name: '', parentId: 0, sort: 0, icon: '', detailImages: [], status: 1 }
    iconFileList.value = []
    uploadFileList.value = []
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    isEdit.value ? await updateCategory(form.value) : await addCategory(form.value)
    ElMessage.success(isEdit.value ? t('common.updated') : t('common.created'))
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await deleteCategory(id)
  ElMessage.success(t('common.deleted'))
  loadData()
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
@use '@/styles/ag-page.scss';
:deep(.el-upload-list--picture-card .el-upload-list__item),
:deep(.el-upload--picture-card) {
  width: 100px;
  height: 100px;
}
</style>
