<template>
  <div class="ops-page">
    <div class="ops-scroll">
      <div class="ops-container">

        <!-- Top: Overview Console -->
        <div class="term-window">
          <div class="term-header">
            <div class="term-title">
              <div class="term-dots">
                <span class="term-dot red"></span>
                <span class="term-dot yellow"></span>
                <span class="term-dot green"></span>
              </div>
              ops --telemetry
            </div>
            <div class="term-right">ready</div>
          </div>
          <div class="term-body">
            <div class="op-title-row">
              <span class="op-arrow">&gt;</span> LLMOps Telemetry Dashboard
            </div>
            <div class="op-stats-row">
              <span class="op-dollar">$</span> conversations:
              <strong class="op-accent">{{ stats.totalConversations || 0 }}</strong>
              <span class="op-sep">|</span>
              messages:
              <strong class="op-accent">{{ stats.totalMessages || 0 }}</strong>
              <span class="op-sep">|</span>
              today:
              <strong class="op-accent">{{ stats.todayMessages || 0 }}</strong>
              <span class="op-tag">--avg-score={{ stats.avgScore ? stats.avgScore.toFixed(1) : '-' }}</span>
              <span class="op-tag">--latency={{ stats.avgLatencyMs ? Math.round(stats.avgLatencyMs) + 'ms' : '-' }}</span>
            </div>
          </div>
        </div>

        <!-- Top Agents Window -->
        <div class="term-window" v-if="stats.topAgents && stats.topAgents.length">
          <div class="term-header">
            <div class="term-title">
              <div class="term-dots">
                <span class="term-dot red"></span>
                <span class="term-dot yellow"></span>
                <span class="term-dot green"></span>
              </div>
              agents --top
            </div>
            <div class="term-right">ranked</div>
          </div>
          <div class="agents-body">
            <div class="agent-bar-row" v-for="(agent, idx) in stats.topAgents" :key="idx">
              <span class="agent-rank">#{{ idx + 1 }}</span>
              <span class="agent-name">{{ agent.agent }}</span>
              <div class="agent-bar-track">
                <div class="agent-bar-fill" :style="{ width: agentBarWidth(agent.count) + '%' }"></div>
              </div>
              <span class="agent-count">{{ agent.count }}</span>
            </div>
          </div>
        </div>

        <!-- Score Distribution -->
        <div class="term-window" v-if="stats.scoreDistribution">
          <div class="term-header">
            <div class="term-title">
              <div class="term-dots">
                <span class="term-dot red"></span>
                <span class="term-dot yellow"></span>
                <span class="term-dot green"></span>
              </div>
              scores --distribution
            </div>
            <div class="term-right">histogram</div>
          </div>
          <div class="score-dist-body">
            <div class="score-dist-item" v-for="(val, key) in stats.scoreDistribution" :key="key">
              <div class="score-dist-label">{{ key }}</div>
              <div class="score-dist-val">{{ val || 0 }}</div>
            </div>
          </div>
        </div>

        <!-- Search Panel -->
        <div class="search-panel">
          <div class="search-panel-top">
            <div class="term-title">
              <div class="term-dots">
                <span class="term-dot red"></span>
                <span class="term-dot yellow"></span>
                <span class="term-dot green"></span>
              </div>
              search --traces
            </div>
            <div class="term-right">Type to filter results.</div>
          </div>
          <div class="search-panel-body">
            <span class="op-dollar">$</span>
            <span>find Q</span>
            <input
              v-model="searchKeyword"
              class="search-input"
              placeholder="Search by query, agent, intent..."
              @keyup.enter="loadTraces"
            />
            <button class="search-btn" @click="loadTraces">Q execute</button>
          </div>
        </div>

        <!-- Trace Table -->
        <div class="ops-table-window">
          <div class="term-header">
            <div class="term-title">
              <div class="term-dots">
                <span class="term-dot red"></span>
                <span class="term-dot yellow"></span>
                <span class="term-dot green"></span>
              </div>
              traces --list
            </div>
            <div class="term-right">
              <span>{{ tracesTotal }} records</span>
            </div>
          </div>
          <table class="ops-table">
            <thead>
              <tr>
                <th style="width:150px">Time</th>
                <th style="width:140px">Agent</th>
                <th style="width:80px">Score</th>
                <th>Query</th>
                <th style="width:90px">Latency</th>
                <th style="width:80px">Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in traces" :key="t.traceId">
                <td class="td-muted">{{ t.createTime }}</td>
                <td class="td-bold">{{ t.subAgent || t.intent }}</td>
                <td>
                  <span class="score-pill" :class="scorePillClass(t.selfScore)">
                    {{ t.selfScore != null ? Number(t.selfScore).toFixed(1) : '-' }}
                  </span>
                </td>
                <td class="td-query" :title="t.userQuery">{{ truncate(t.userQuery, 60) }}</td>
                <td class="td-muted">{{ t.latencyMs }}ms</td>
                <td>
                  <span class="status-dot" :class="t.status === 'success' ? 'ok' : 'err'"></span>
                  {{ t.status }}
                </td>
              </tr>
              <tr v-if="!traces.length">
                <td colspan="6" class="td-empty">$ echo "No telemetry data recorded."</td>
              </tr>
            </tbody>
          </table>
          <div class="ops-pagination">
            <el-pagination
              layout="total, prev, pager, next"
              :total="tracesTotal"
              :page-size="20"
              :current-page="tracesPage"
              @current-change="onTracePage"
              small
            />
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { getAiDashboard, getAiTraces } from '@/api/ai'

const stats = ref({})
const traces = ref([])
const tracesTotal = ref(0)
const tracesPage = ref(1)
const searchKeyword = ref('')

const maxAgentCount = computed(() => {
  if (!stats.value.topAgents || !stats.value.topAgents.length) return 1
  return Math.max(...stats.value.topAgents.map(a => a.count), 1)
})

onMounted(() => {
  loadDashboard()
  loadTraces()
})

async function loadDashboard() {
  try {
    const res = await getAiDashboard()
    stats.value = res.data || {}
  } catch (e) { /* ignore */ }
}

async function loadTraces() {
  try {
    const res = await getAiTraces({ pageNum: tracesPage.value, pageSize: 20 })
    traces.value = res.data?.list || []
    tracesTotal.value = res.data?.total || 0
  } catch (e) { traces.value = [] }
}

function onTracePage(page) {
  tracesPage.value = page
  loadTraces()
}

function agentBarWidth(count) {
  return Math.round((count / maxAgentCount.value) * 100)
}

function scorePillClass(score) {
  if (score == null) return ''
  const s = Number(score)
  if (s >= 8) return 'high'
  if (s >= 5) return 'mid'
  return 'low'
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

/* Agents Bar Chart */
.agents-body {
  padding: 20px 24px;
}
.agent-bar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  font-size: 13px;
  color: #374151;
}
.agent-rank {
  color: #9CA3AF;
  font-size: 11px;
  width: 24px;
  flex-shrink: 0;
}
.agent-name {
  width: 150px;
  flex-shrink: 0;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.agent-bar-track {
  flex: 1;
  height: 8px;
  background: #F3F4F6;
  border-radius: 4px;
  overflow: hidden;
}
.agent-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #C88270, #E14F3C);
  border-radius: 4px;
  transition: width 0.6s ease;
}
.agent-count {
  color: #9CA3AF;
  font-size: 12px;
  width: 40px;
  text-align: right;
  flex-shrink: 0;
}

/* Score Distribution */
.score-dist-body {
  display: flex;
  gap: 0;
  padding: 24px;
}
.score-dist-item {
  flex: 1;
  text-align: center;
  padding: 16px 8px;
  border-right: 1px dashed #F3F4F6;
}
.score-dist-item:last-child {
  border-right: none;
}
.score-dist-label {
  font-size: 11px;
  color: #9CA3AF;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}
.score-dist-val {
  font-size: 28px;
  font-weight: 600;
  color: #111827;
}

/* Search Panel */
.search-panel {
  display: flex;
  flex-direction: column;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 24px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(0,0,0,0.02);
}
.search-panel-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px dashed #E5E7EB;
  padding: 12px 16px;
  font-size: 12px;
  color: #6B7280;
}
.search-panel-body {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #9CA3AF;
  gap: 8px;
}
.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-family: inherit;
  font-size: 13px;
  color: #374151;
  background: transparent;
  padding: 4px 0;
}
.search-input::placeholder {
  color: #D1D5DB;
}
.search-btn {
  margin-left: auto;
  border: 1px solid #E5E7EB;
  background: #F9FAFB;
  font-family: inherit;
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 6px;
  color: #6B7280;
  cursor: pointer;
  transition: background 0.2s;
}
.search-btn:hover {
  background: #F3F4F6;
}

/* Trace Table */
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
.td-query {
  color: #6B7280;
  font-family: -apple-system, system-ui, sans-serif;
}
.td-empty {
  text-align: center;
  padding: 60px 16px !important;
  color: #9CA3AF;
}

/* Score Pill */
.score-pill {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
}
.score-pill.high { background: #DCFCE7; color: #166534; }
.score-pill.mid { background: #FEF9C3; color: #854D0E; }
.score-pill.low { background: #FEE2E2; color: #991B1B; }

/* Status Dot */
.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
}
.status-dot.ok { background: #22C55E; }
.status-dot.err { background: #EF4444; }

/* Pagination */
.ops-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px dashed #F3F4F6;
}
</style>
