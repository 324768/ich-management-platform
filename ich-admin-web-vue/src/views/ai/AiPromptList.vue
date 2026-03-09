<template>
  <div class="ai-prompt">
    <div class="page-header">
      <h2>Prompt Configuration</h2>
      <el-button type="primary" @click="openDialog(null)">Add Prompt</el-button>
    </div>

    <el-card shadow="hover">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="promptKey" label="Key" width="200" />
        <el-table-column prop="content" label="Content" show-overflow-tooltip />
        <el-table-column prop="description" label="Description" width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="Status" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? 'Active' : 'Disabled' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="140">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">Edit</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? 'Edit Prompt' : 'Add Prompt'" width="650px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="Key">
          <el-input v-model="form.promptKey" placeholder="e.g. master_brain_system" />
        </el-form-item>
        <el-form-item label="Content">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="Prompt content" />
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item label="Status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="handleSave">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPrompts, savePrompt, deletePrompt } from '@/api/ai'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref({})

onMounted(() => loadList())

async function loadList() {
  loading.value = true
  try {
    const res = await listPrompts()
    list.value = res.data || []
  } catch (e) { list.value = [] }
  loading.value = false
}

function openDialog(row) {
  form.value = row
    ? { ...row }
    : { promptKey: '', content: '', description: '', status: 1 }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    await savePrompt(form.value)
    ElMessage.success('Saved')
    dialogVisible.value = false
    await loadList()
  } catch (e) { /* handled by interceptor */ }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('Delete this prompt?', 'Confirm')
  try {
    await deletePrompt(row.id)
    ElMessage.success('Deleted')
    await loadList()
  } catch (e) { /* ignore */ }
}
</script>

<style scoped>
.ai-prompt { padding: 20px; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; color: #1f2937; }
</style>
