<template>
  <div class="ops-page">
    <div class="ops-scroll">
      <div class="ops-container">

        <!-- Overview Console -->
        <div class="term-window">
          <div class="term-header">
            <div class="term-title">
              <div class="term-dots">
                <span class="term-dot red"></span>
                <span class="term-dot yellow"></span>
                <span class="term-dot green"></span>
              </div>
              agents --config
            </div>
            <div class="term-right">
              <button class="term-action-btn" @click="openDialog(null)">+ new agent</button>
            </div>
          </div>
          <div class="term-body">
            <div class="op-title-row">
              <span class="op-arrow">&gt;</span> Sub-Agent Configuration
            </div>
            <div class="op-stats-row">
              <span class="op-dollar">$</span> total:
              <strong class="op-accent">{{ list.length }}</strong>
              <span class="op-sep">|</span>
              active:
              <strong class="op-accent">{{ list.filter(a => a.status === 1).length }}</strong>
              <span class="op-sep">|</span>
              registered:
              <strong class="op-accent">{{ registeredAgents.length }}</strong>
              <span class="op-tag">--dynamic-agents</span>
            </div>
          </div>
        </div>

        <!-- Agent Table -->
        <div class="ops-table-window">
          <div class="term-header">
            <div class="term-title">
              <div class="term-dots">
                <span class="term-dot red"></span>
                <span class="term-dot yellow"></span>
                <span class="term-dot green"></span>
              </div>
              agents --list
            </div>
            <div class="term-right">{{ list.length }} entries</div>
          </div>
          <table class="ops-table">
            <thead>
              <tr>
                <th style="width:50px">ID</th>
                <th style="width:150px">Code</th>
                <th style="width:140px">Name</th>
                <th>Description</th>
                <th style="width:70px">Ver</th>
                <th style="width:80px">Status</th>
                <th style="width:160px">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in list" :key="row.id">
                <td class="td-muted">{{ row.id }}</td>
                <td class="td-code">{{ row.agentCode }}</td>
                <td class="td-bold">{{ row.agentName }}</td>
                <td class="td-desc" :title="row.description">{{ truncate(row.description, 50) }}</td>
                <td class="td-muted">v{{ row.version }}</td>
                <td>
                  <span class="status-badge" :class="row.status === 1 ? 'active' : 'inactive'"
                    @click="toggleStatus(row)">
                    {{ row.status === 1 ? 'ON' : 'OFF' }}
                  </span>
                </td>
                <td>
                  <button class="row-btn edit" @click="openDialog(row)">edit</button>
                  <button class="row-btn danger" @click="handleDelete(row)">rm</button>
                </td>
              </tr>
              <tr v-if="!list.length">
                <td colspan="7" class="td-empty">$ echo "No agent configurations found."</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Registered Agents -->
        <div class="term-window">
          <div class="term-header">
            <div class="term-title">
              <div class="term-dots">
                <span class="term-dot red"></span>
                <span class="term-dot yellow"></span>
                <span class="term-dot green"></span>
              </div>
              registry --all
            </div>
            <div class="term-right">
              <button class="term-action-btn" @click="loadRegistered">↻ refresh</button>
            </div>
          </div>
          <div class="registry-body">
            <span class="registry-tag" v-for="code in registeredAgents" :key="code">
              <span class="registry-dot"></span>
              {{ code }}
            </span>
            <span v-if="!registeredAgents.length" class="registry-empty">
              $ echo "No agents registered in runtime."
            </span>
          </div>
        </div>

        <!-- Edit Dialog -->
        <el-dialog
          v-model="dialogVisible"
          :title="form.id ? '$ edit --agent ' + form.agentCode : '$ new --agent'"
          width="700px"
          class="ops-dialog"
        >
          <div class="dialog-form">
            <div class="form-row">
              <label class="form-label">agent_code</label>
              <input
                v-model="form.agentCode"
                :disabled="!!form.id"
                class="form-input"
                :class="{ disabled: !!form.id }"
                placeholder="e.g. custom_helper"
              />
            </div>
            <div class="form-row">
              <label class="form-label">agent_name</label>
              <input v-model="form.agentName" class="form-input" placeholder="Display name" />
            </div>
            <div class="form-row">
              <label class="form-label">description</label>
              <textarea v-model="form.description" class="form-textarea" rows="2" placeholder="Agent description"></textarea>
            </div>
            <div class="form-row">
              <label class="form-label">system_prompt</label>
              <textarea v-model="form.systemPrompt" class="form-textarea code" rows="6" placeholder="Agent system prompt"></textarea>
            </div>
            <div class="form-row">
              <label class="form-label">routing_keywords</label>
              <textarea v-model="form.routingKeywords" class="form-textarea" rows="2" placeholder='["keyword1","keyword2"]'></textarea>
            </div>
            <div class="form-row-half">
              <div class="form-row">
                <label class="form-label">knowledge_base_id</label>
                <input v-model.number="form.knowledgeBaseId" class="form-input" type="number" placeholder="optional" />
              </div>
              <div class="form-row">
                <label class="form-label">fallback_strategy</label>
                <input v-model="form.fallbackStrategy" class="form-input" placeholder="e.g. general" />
              </div>
            </div>
            <div class="form-row">
              <label class="form-label">status</label>
              <div class="form-status-toggle">
                <span
                  class="status-badge clickable"
                  :class="form.status === 1 ? 'active' : 'inactive'"
                  @click="form.status = form.status === 1 ? 0 : 1"
                >
                  {{ form.status === 1 ? 'ON' : 'OFF' }}
                </span>
                <span class="form-hint">Click to toggle</span>
              </div>
            </div>
          </div>
          <template #footer>
            <div class="dialog-footer">
              <button class="dialog-btn cancel" @click="dialogVisible = false">cancel</button>
              <button class="dialog-btn save" @click="handleSave">$ save --commit</button>
            </div>
          </template>
        </el-dialog>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAgents, saveAgent, deleteAgent, changeAgentStatus, getRegisteredAgents } from '@/api/ai'

const list = ref([])
const dialogVisible = ref(false)
const form = ref({})
const registeredAgents = ref([])

onMounted(() => {
  loadList()
  loadRegistered()
})

async function loadList() {
  try {
    const res = await listAgents()
    list.value = res.data?.list || res.data || []
  } catch (e) { list.value = [] }
}

async function loadRegistered() {
  try {
    const res = await getRegisteredAgents()
    registeredAgents.value = res.data || []
  } catch (e) { registeredAgents.value = [] }
}

function openDialog(row) {
  form.value = row
    ? { ...row }
    : { agentCode: '', agentName: '', description: '', systemPrompt: '', routingKeywords: '[]', knowledgeBaseId: null, fallbackStrategy: 'general', status: 1 }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    await saveAgent(form.value)
    ElMessage.success('Saved')
    dialogVisible.value = false
    await loadList()
    await loadRegistered()
  } catch (e) { /* handled by interceptor */ }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('Delete this agent configuration?', 'Confirm')
  try {
    await deleteAgent(row.id)
    ElMessage.success('Deleted')
    await loadList()
    await loadRegistered()
  } catch (e) { /* ignore */ }
}

async function toggleStatus(row) {
  try {
    await changeAgentStatus({ id: row.id, status: row.status === 1 ? 0 : 1 })
    ElMessage.success('Status updated')
    await loadList()
    await loadRegistered()
  } catch (e) { /* ignore */ }
}

function truncate(str, len) {
  if (!str) return ''
  return str.length > len ? str.substring(0, len) + '...' : str
}
</script>

<style scoped>
.ops-page {
  height: 100%;
  background: #ffffff;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, "Liberation Mono", monospace;
}
.ops-scroll {
  height: 100%;
  overflow-y: auto;
  padding: 40px;
}
.ops-container {
  max-width: 1040px;
  margin: 0 auto;
}

/* Terminal Window */
.term-window {
  background: #fff;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 24px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.02);
}
.term-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #E5E7EB;
  font-size: 13px;
  color: #6B7280;
  background: #fff;
}
.term-title {
  display: flex;
  align-items: center;
  color: #6B7280;
  font-size: 12px;
}
.term-dots {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-right: 12px;
}
.term-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-block;
}
.term-dot.red { background: #FF5F56; }
.term-dot.yellow { background: #FFBD2E; }
.term-dot.green { background: #27C93F; }
.term-right {
  font-size: 12px;
  color: #9CA3AF;
  display: flex;
  align-items: center;
  gap: 8px;
}
.term-action-btn {
  border: 1px solid #E5E7EB;
  background: #F9FAFB;
  font-family: inherit;
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 6px;
  color: #374151;
  cursor: pointer;
  transition: all 0.2s;
}
.term-action-btn:hover {
  background: #F3F4F6;
  border-color: #D1D5DB;
}

/* Overview Body */
.term-body {
  padding: 40px 24px;
  text-align: center;
}
.op-title-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 26px;
  font-weight: 500;
  color: #111827;
  margin-bottom: 28px;
  letter-spacing: -0.5px;
}
.op-arrow {
  color: #E14F3C;
  font-weight: 400;
  font-size: 26px;
}
.op-stats-row {
  font-size: 13px;
  color: #6B7280;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
.op-dollar {
  color: #22C55E;
  font-weight: bold;
  font-size: 14px;
}
.op-accent {
  color: #E14F3C;
  font-weight: 500;
}
.op-sep {
  color: #D1D5DB;
  margin: 0 4px;
}
.op-tag {
  border: 1px solid #E5E7EB;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: #6B7280;
  margin-left: 4px;
}

/* Agent Table */
.ops-table-window {
  background: #fff;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0,0,0,0.02);
  margin-bottom: 24px;
}
.ops-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  table-layout: fixed;
}
.ops-table th {
  text-align: left;
  padding: 12px 16px;
  background: #FAFAFA;
  border-bottom: 1px solid #E5E7EB;
  color: #6B7280;
  font-weight: 500;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.ops-table td {
  padding: 12px 16px;
  border-bottom: 1px dashed #F3F4F6;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ops-table tr:last-child td {
  border-bottom: none;
}
.ops-table tr:hover td {
  background: #F9FAFB;
}
.td-muted { color: #9CA3AF; }
.td-bold { font-weight: 500; }
.td-code {
  color: #E14F3C;
  font-weight: 500;
}
.td-desc {
  color: #6B7280;
  font-family: -apple-system, system-ui, sans-serif;
}
.td-empty {
  text-align: center;
  padding: 60px 16px !important;
  color: #9CA3AF;
}

/* Status Badge */
.status-badge {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;
  display: inline-block;
}
.status-badge.active {
  background: #DCFCE7;
  color: #166534;
}
.status-badge.inactive {
  background: #F3F4F6;
  color: #9CA3AF;
}
.status-badge:hover {
  opacity: 0.8;
}
.status-badge.clickable {
  cursor: pointer;
}

/* Row Action Buttons */
.row-btn {
  border: 1px solid #E5E7EB;
  background: #fff;
  font-family: inherit;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
  margin-right: 4px;
  color: #374151;
}
.row-btn:hover {
  background: #F3F4F6;
}
.row-btn.edit {
  color: #2563EB;
  border-color: #BFDBFE;
}
.row-btn.edit:hover {
  background: #EFF6FF;
}
.row-btn.danger {
  color: #DC2626;
  border-color: #FECACA;
}
.row-btn.danger:hover {
  background: #FEF2F2;
}

/* Registry */
.registry-body {
  padding: 20px 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.registry-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid #E5E7EB;
  border-radius: 6px;
  font-size: 12px;
  color: #374151;
  background: #FAFAFA;
  transition: all 0.2s;
}
.registry-tag:hover {
  background: #F3F4F6;
  border-color: #D1D5DB;
}
.registry-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22C55E;
  display: inline-block;
}
.registry-empty {
  color: #9CA3AF;
  font-size: 13px;
}

/* Dialog Form (terminal style) */
.dialog-form {
  padding: 8px 0;
}
.form-row {
  margin-bottom: 16px;
}
.form-row-half {
  display: flex;
  gap: 16px;
}
.form-row-half .form-row {
  flex: 1;
}
.form-label {
  display: block;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
  color: #6B7280;
  margin-bottom: 6px;
  letter-spacing: 0.02em;
}
.form-label::before {
  content: '$ ';
  color: #22C55E;
  font-weight: bold;
}
.form-input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 12px;
  border: 1px solid #E5E7EB;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  font-size: 13px;
  color: #111827;
  background: #FAFAFA;
  outline: none;
  transition: all 0.2s;
}
.form-input:focus {
  border-color: #93C5FD;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.08);
}
.form-input.disabled {
  background: #F3F4F6;
  color: #9CA3AF;
  cursor: not-allowed;
}
.form-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 12px;
  border: 1px solid #E5E7EB;
  border-radius: 6px;
  font-family: -apple-system, system-ui, sans-serif;
  font-size: 13px;
  color: #111827;
  background: #FAFAFA;
  outline: none;
  resize: vertical;
  transition: all 0.2s;
}
.form-textarea.code {
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}
.form-textarea:focus {
  border-color: #93C5FD;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.08);
}
.form-status-toggle {
  display: flex;
  align-items: center;
  gap: 12px;
}
.form-hint {
  font-size: 11px;
  color: #9CA3AF;
}

/* Dialog Footer */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.dialog-btn {
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
  padding: 8px 20px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #E5E7EB;
}
.dialog-btn.cancel {
  background: #fff;
  color: #6B7280;
}
.dialog-btn.cancel:hover {
  background: #F3F4F6;
}
.dialog-btn.save {
  background: #111827;
  color: #fff;
  border-color: #111827;
}
.dialog-btn.save:hover {
  background: #1F2937;
}
</style>
