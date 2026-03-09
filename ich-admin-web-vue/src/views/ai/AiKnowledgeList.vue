<template>
  <div class="ai-knowledge">
    <div class="page-header">
      <h2>Knowledge Base</h2>
      <el-button type="primary" @click="openDialog(null)">Add Knowledge</el-button>
    </div>

    <el-card shadow="hover">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="question" label="Question" show-overflow-tooltip />
        <el-table-column prop="answer" label="Answer" show-overflow-tooltip width="300" />
        <el-table-column prop="keywords" label="Keywords" width="160" />
        <el-table-column prop="hitCount" label="Hits" width="70" />
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
    <el-dialog v-model="dialogVisible" :title="form.id ? 'Edit Knowledge' : 'Add Knowledge'" width="600px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="Question">
          <el-input v-model="form.question" />
        </el-form-item>
        <el-form-item label="Answer">
          <el-input v-model="form.answer" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="Keywords">
          <el-input v-model="form.keywords" placeholder="Comma separated" />
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
import { listKnowledge, saveKnowledge, deleteKnowledge } from '@/api/ai'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref({})

onMounted(() => loadList())

async function loadList() {
  loading.value = true
  try {
    const res = await listKnowledge()
    list.value = res.data || []
  } catch (e) { list.value = [] }
  loading.value = false
}

function openDialog(row) {
  form.value = row ? { ...row } : { question: '', answer: '', keywords: '', status: 1 }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    await saveKnowledge(form.value)
    ElMessage.success('Saved')
    dialogVisible.value = false
    await loadList()
  } catch (e) { /* handled by interceptor */ }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('Delete this knowledge entry?', 'Confirm')
  try {
    await deleteKnowledge(row.id)
    ElMessage.success('Deleted')
    await loadList()
  } catch (e) { /* ignore */ }
}
</script>

<style scoped>
.ai-knowledge { padding: 20px; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; color: #1f2937; }
</style>
