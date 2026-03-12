<script setup>
import { ref, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { chatStream, listConversations, createConversation, deleteConversation, getConversation, sendFeedback, regenerateStream, exportConversation } from '@/api/omnitrix'
import { marked } from 'marked'
import { getUserInfo } from '@/utils/token'

marked.setOptions({ breaks: true, gfm: true })

const AGENT_NAMES = {
  admin_assistant: '管理助手',
  content_assistant: '非遗文化助手',
  commerce_assistant: '文创商城助手',
  user_assistant: '个人服务助手',
  recommend_assistant: '智能推荐助手',
  knowledge_assistant: '知识库问答助手',
  general_assistant: '通用助手'
}

const storedUser = getUserInfo()
const userId = ref(storedUser?.id || localStorage.getItem('ich_user_id') || '1')
const input = ref('')
const messages = ref([])
const conversations = ref([])
const currentSessionId = ref('')
const currentConvId = ref(null)
const isStreaming = ref(false)
const showSidebar = ref(true)
const darkMode = ref(false)
const chatBodyRef = ref(null)
const textareaRef = ref(null)
const copiedIdx = ref(-1)
let eventSource = null

onMounted(() => {
  console.log('[AIChat] 页面已加载，发送消息后会显示 SSE 调试信息')
  loadConversations()
})
onUnmounted(() => { closeStream() })

function closeStream() {
  if (eventSource) { eventSource.close(); eventSource = null }
}

function stopGeneration() {
  closeStream()
  const last = messages.value[messages.value.length - 1]
  if (last && last.streaming) {
    last.streaming = false
    last.content += '\n\n*[generation stopped]*'
  }
  isStreaming.value = false
}

function toggleDarkMode() { darkMode.value = !darkMode.value }
function collapseSidebar() { showSidebar.value = !showSidebar.value }
function snapRight() { showSidebar.value = false }

async function loadConversations() {
  try {
    const res = await listConversations(userId.value)
    conversations.value = res.data || []
  } catch (e) { conversations.value = [] }
}

async function startNewChat() {
  closeStream()
  try {
    const res = await createConversation(userId.value)
    currentSessionId.value = res.data.sessionId
    currentConvId.value = res.data.id
    messages.value = []
    await loadConversations()
  } catch (e) {
    currentSessionId.value = 'session_' + Date.now()
    currentConvId.value = null
    messages.value = []
  }
}

async function selectConversation(conv) {
  closeStream()
  currentSessionId.value = conv.sessionId
  currentConvId.value = conv.id
  try {
    const res = await getConversation(conv.id, userId.value)
    messages.value = (res.data.messages || []).map(m => ({
      role: m.role === 'user' ? 'user' : 'assistant',
      content: m.content, agent: m.subAgent,
      messageId: m.id || null
    }))
    scrollToBottom()
  } catch (e) { messages.value = [] }
}

async function removeConversation(conv) {
  try {
    await deleteConversation(conv.id, userId.value)
    if (currentConvId.value === conv.id) {
      currentSessionId.value = ''; currentConvId.value = null; messages.value = []
    }
    await loadConversations()
  } catch (e) { /* ignore */ }
}

function send() {
  const q = input.value.trim()
  if (!q || isStreaming.value) return
  input.value = ''
  autoResize()
  if (!currentSessionId.value) currentSessionId.value = 'session_' + Date.now()

  messages.value.push({ role: 'user', content: q })
  messages.value.push({ role: 'assistant', content: '', agent: '', streaming: true, thinking: true, thinkingContent: '', showThinking: false, callChain: [], showCallChain: false })
  isStreaming.value = true
  scrollToBottom()

  closeStream()
  eventSource = chatStream(currentSessionId.value, q, userId.value)
  console.log('[SSE] EventSource created, url:', eventSource.url)
  const idx = messages.value.length - 1

  eventSource.onopen = () => console.log('[SSE] Connection opened')
  eventSource.onerror = (err) => console.log('[SSE] Connection error', err)

  eventSource.addEventListener('thinking', (e) => {
    messages.value[idx].thinkingContent += e.data
    scrollToBottom()
  })
  eventSource.addEventListener('chunk', (e) => {
    messages.value[idx].thinking = false
    messages.value[idx].content += e.data
    scrollToBottom()
  })
  eventSource.addEventListener('agent', (e) => {
    try {
      const d = JSON.parse(e.data)
      messages.value[idx].agent = d.agent || e.data
    } catch (_) {
      messages.value[idx].agent = e.data
    }
  })
  // 调用链事件：代理调度
  eventSource.addEventListener('agent_dispatch', (e) => {
    console.log('[SSE] agent_dispatch received:', e.data)
    try {
      const d = JSON.parse(e.data)
      if (!messages.value[idx].callChain) messages.value[idx].callChain = []
      messages.value[idx].callChain.push({
        type: 'dispatch',
        agent: d.agent,
        agentName: d.agentName,
        query: d.query,
        timestamp: d.timestamp,
        status: 'running',
        result: ''
      })
      console.log('[SSE] callChain updated:', messages.value[idx].callChain)
    } catch (_) { /* ignore */ }
    scrollToBottom()
  })
  // 调用链事件：代理结果
  eventSource.addEventListener('agent_result', (e) => {
    console.log('[SSE] agent_result received:', e.data)
    try {
      const d = JSON.parse(e.data)
      if (messages.value[idx].callChain && messages.value[idx].callChain.length > 0) {
        const last = messages.value[idx].callChain[messages.value[idx].callChain.length - 1]
        if (last.agent === d.agent) {
          last.status = (d.status === 'SUCCESS' || d.status === 'success') ? 'success' : ((d.status === 'EMPTY' || d.status === 'empty') ? 'empty' : 'failed')
          last.result = d.result || ''
          last.latencyMs = d.latencyMs
        }
      }
      messages.value[idx].thinking = false
    } catch (_) { /* ignore */ }
    scrollToBottom()
  })
  eventSource.addEventListener('done', (e) => {
    messages.value[idx].streaming = false
    messages.value[idx].thinking = false
    isStreaming.value = false
    try {
      const d = JSON.parse(e.data)
      if (d.messageId) messages.value[idx].messageId = d.messageId
      if (d.model) messages.value[idx].model = d.model
    } catch (_) {}
    // 检测购物车操作并触发刷新事件
    const content = messages.value[idx].content || ''
    if (content.includes('加入购物车') || content.includes('从购物车移除') || content.includes('清空购物车') || content.includes('购物车商品数量')) {
      localStorage.setItem('cart_updated', Date.now())
      window.dispatchEvent(new StorageEvent('cart_updated', { key: 'cart_updated', newValue: Date.now() }))
    }
    closeStream(); loadConversations()
  })
  eventSource.addEventListener('error_msg', (e) => {
    messages.value[idx].thinking = false
    messages.value[idx].content += '\n\n' + e.data
    messages.value[idx].streaming = false; isStreaming.value = false; closeStream()
  })
  eventSource.onerror = () => {
    if (isStreaming.value) {
      messages.value[idx].streaming = false
      messages.value[idx].thinking = false
      isStreaming.value = false
    }
    closeStream()
  }
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function autoResize() {
  nextTick(() => {
    const el = textareaRef.value
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 200) + 'px'
  })
}

watch(input, autoResize)

function scrollToBottom() {
  nextTick(() => { if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight })
}

async function handleFeedback(msg, value) {
  if (!msg.messageId || msg.feedback) return
  try {
    await sendFeedback(msg.messageId, value)
    msg.feedback = value
  } catch (e) { /* ignore */ }
}

function regenerate() {
  if (!currentSessionId.value || isStreaming.value) return
  // 移除前端最后一条 assistant 消息
  const lastIdx = messages.value.length - 1
  if (lastIdx >= 0 && messages.value[lastIdx].role === 'assistant') {
    messages.value.splice(lastIdx, 1)
  }
  // 添加新的 assistant 占位
  messages.value.push({ role: 'assistant', content: '', agent: '', streaming: true, thinking: true, thinkingContent: '', showThinking: false, callChain: [], showCallChain: false })
  isStreaming.value = true
  scrollToBottom()

  closeStream()
  eventSource = regenerateStream(currentSessionId.value, userId.value)
  const idx = messages.value.length - 1

  eventSource.addEventListener('thinking', (e) => {
    messages.value[idx].thinkingContent += e.data
    scrollToBottom()
  })
  eventSource.addEventListener('chunk', (e) => {
    messages.value[idx].thinking = false
    messages.value[idx].content += e.data
    scrollToBottom()
  })
  eventSource.addEventListener('agent', (e) => {
    try {
      const d = JSON.parse(e.data)
      messages.value[idx].agent = d.agent || e.data
    } catch (_) {
      messages.value[idx].agent = e.data
    }
  })
  // 调用链事件：代理调度
  eventSource.addEventListener('agent_dispatch', (e) => {
    try {
      const d = JSON.parse(e.data)
      if (!messages.value[idx].callChain) messages.value[idx].callChain = []
      messages.value[idx].callChain.push({
        type: 'dispatch',
        agent: d.agent,
        agentName: d.agentName,
        query: d.query,
        timestamp: d.timestamp,
        status: 'running',
        result: ''
      })
    } catch (_) { /* ignore */ }
    scrollToBottom()
  })
  // 调用链事件：代理结果（后端发小写 success/empty/failed）
  eventSource.addEventListener('agent_result', (e) => {
    try {
      const d = JSON.parse(e.data)
      if (messages.value[idx].callChain && messages.value[idx].callChain.length > 0) {
        const last = messages.value[idx].callChain[messages.value[idx].callChain.length - 1]
        if (last.agent === d.agent) {
          last.status = (d.status === 'SUCCESS' || d.status === 'success') ? 'success' : ((d.status === 'EMPTY' || d.status === 'empty') ? 'empty' : 'failed')
          last.result = d.result || ''
          last.latencyMs = d.latencyMs
        }
      }
      messages.value[idx].thinking = false
    } catch (_) { /* ignore */ }
    scrollToBottom()
  })
  eventSource.addEventListener('done', (e) => {
    messages.value[idx].streaming = false
    messages.value[idx].thinking = false
    isStreaming.value = false
    try {
      const d = JSON.parse(e.data)
      if (d.messageId) messages.value[idx].messageId = d.messageId
      if (d.model) messages.value[idx].model = d.model
    } catch (_) {}
    closeStream(); loadConversations()
  })
  eventSource.addEventListener('error_msg', (e) => {
    messages.value[idx].thinking = false
    messages.value[idx].content += '\n\n' + e.data
    messages.value[idx].streaming = false; isStreaming.value = false; closeStream()
  })
  eventSource.onerror = () => {
    if (isStreaming.value) {
      messages.value[idx].streaming = false
      messages.value[idx].thinking = false
      isStreaming.value = false
    }
    closeStream()
  }
}

async function handleExport() {
  if (!currentConvId.value) return
  try {
    const res = await exportConversation(currentConvId.value, userId.value)
    const md = res.data || ''
    const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'chat-export.md'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) { /* ignore */ }
}

function toggleThinking(msg) {
  msg.showThinking = !msg.showThinking
}

function copyResponse(msg, idx) {
  navigator.clipboard.writeText(msg.content).then(() => {
    copiedIdx.value = idx
    setTimeout(() => { copiedIdx.value = -1 }, 2000)
  }).catch(() => {})
}

function agentLabel(code) {
  if (!code) return ''
  return AGENT_NAMES[code] || code
}

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}
</script>

<template>
  <div class="omnitrix-page" :class="{ 'dark-mode': darkMode }">
    <!-- 侧栏 -->
    <aside class="sidebar" :class="{ hidden: !showSidebar }">
      <div class="sb-head">
        <span class="sb-title">对话列表</span>
        <button class="new-btn" @click="startNewChat">＋</button>
      </div>
      <div class="conv-list">
        <div v-for="c in conversations" :key="c.id"
          class="conv-item" :class="{ active: currentConvId === c.id }"
          @click="selectConversation(c)">
          <span class="conv-title">{{ c.title || '新对话' }}</span>
          <button class="del-btn" @click.stop="removeConversation(c)">×</button>
        </div>
        <div v-if="!conversations.length" class="empty">暂无对话</div>
      </div>
    </aside>

    <!-- 主聊天区 -->
    <main class="chat-main">
      <div class="chat-header">
        <div class="control-buttons">
          <div class="control-btn red" @click="toggleDarkMode"></div>
          <div class="control-btn green" @click="collapseSidebar"></div>
          <div class="control-btn blue" @click="snapRight"></div>
        </div>
        <div class="header-title">
          <svg class="logo" viewBox="0 0 35 33" fill="none">
            <path d="M13.2371 21.0407L24.3186 12.8506C24.8619 12.4491 25.6384 12.6057 25.8973 13.2294C27.2597 16.5185 26.651 20.4712 23.9403 23.1851C21.2297 25.8989 17.4581 26.4941 14.0108 25.1386L10.2449 26.8843C15.6463 30.5806 22.2053 29.6665 26.304 25.5601C29.5551 22.3051 30.562 17.8683 29.6205 13.8673L29.629 13.8758C28.2637 7.99809 29.9647 5.64871 33.449 0.844576C33.5314 0.730667 33.6139 0.616757 33.6964 0.5L29.1113 5.09055V5.07631L13.2343 21.0436" class="logo-path"/>
            <path d="M10.9503 23.0313C7.07343 19.3235 7.74185 13.5853 11.0498 10.2763C13.4959 7.82722 17.5036 6.82767 21.0021 8.2971L24.7595 6.55998C24.0826 6.07017 23.215 5.54334 22.2195 5.17313C17.7198 3.31926 12.3326 4.24192 8.67479 7.90126C5.15635 11.4239 4.0499 16.8403 5.94992 21.4622C7.36924 24.9165 5.04257 27.3598 2.69884 29.826C1.86829 30.7002 1.0349 31.5745 0.36364 32.5L10.9474 23.0341" class="logo-path"/>
          </svg>
          <span class="title-text">Omnitrix</span>
        </div>
        <button v-if="currentConvId" class="export-btn" @click="handleExport" title="导出对话">
          <svg width="16" height="16" viewBox="0 0 16 16"><path d="M8 1v9M4 6l4 4 4-4M2 12v2h12v-2" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
      </div>

      <div class="chat-body" ref="chatBodyRef">
        <div v-if="!messages.length" class="welcome">
          <svg class="w-logo" viewBox="0 0 35 33" fill="none">
            <path d="M13.2371 21.0407L24.3186 12.8506C24.8619 12.4491 25.6384 12.6057 25.8973 13.2294C27.2597 16.5185 26.651 20.4712 23.9403 23.1851C21.2297 25.8989 17.4581 26.4941 14.0108 25.1386L10.2449 26.8843C15.6463 30.5806 22.2053 29.6665 26.304 25.5601C29.5551 22.3051 30.562 17.8683 29.6205 13.8673L29.629 13.8758C28.2637 7.99809 29.9647 5.64871 33.449 0.844576C33.5314 0.730667 33.6139 0.616757 33.6964 0.5L29.1113 5.09055V5.07631L13.2343 21.0436" class="logo-path"/>
            <path d="M10.9503 23.0313C7.07343 19.3235 7.74185 13.5853 11.0498 10.2763C13.4959 7.82722 17.5036 6.82767 21.0021 8.2971L24.7595 6.55998C24.0826 6.07017 23.215 5.54334 22.2195 5.17313C17.7198 3.31926 12.3326 4.24192 8.67479 7.90126C5.15635 11.4239 4.0499 16.8403 5.94992 21.4622C7.36924 24.9165 5.04257 27.3598 2.69884 29.826C1.86829 30.7002 1.0349 31.5745 0.36364 32.5L10.9474 23.0341" class="logo-path"/>
          </svg>
          <h2 class="welcome-title">你好，我是 Omnitrix AI</h2>
          <p class="welcome-desc">非物质文化遗产智能助手，有什么可以帮你的吗？</p>
          <div class="quick-actions">
            <button @click="input='介绍一下京剧';send()">介绍一下京剧</button>
            <button @click="input='推荐文创商品';send()">推荐文创商品</button>
            <button @click="input='查看我的订单';send()">查看我的订单</button>
          </div>
        </div>

        <div v-for="(msg, i) in messages" :key="i" class="om-msg" :class="msg.role">
          <div class="om-bubble">
            <div v-if="msg.thinking && msg.streaming" class="om-thinking">
              <span class="thinking-dot"></span>
              <span class="thinking-dot"></span>
              <span class="thinking-dot"></span>
              <span class="thinking-label">{{ (msg.callChain && msg.callChain.length > 0) ? '生成回复中...' : '思考中...' }}</span>
            </div>
            <!-- 思维链折叠面板 -->
            <div v-if="msg.thinkingContent && msg.role==='assistant'" class="om-thinking-panel">
              <button class="thinking-toggle" @click="toggleThinking(msg)">
                <svg width="12" height="12" viewBox="0 0 12 12" :class="{ rotated: msg.showThinking }"><path d="M4 2l4 4-4 4" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
                <span>思考过程</span>
              </button>
              <div v-if="msg.showThinking" class="thinking-content" v-html="renderMarkdown(msg.thinkingContent)"></div>
            </div>
            <!-- 调用链折叠面板 -->
            <div v-if="msg.callChain && msg.callChain.length > 0 && msg.role==='assistant'" class="om-callchain-panel">
              <button class="callchain-toggle" @click="msg.showCallChain = !msg.showCallChain">
                <svg width="12" height="12" viewBox="0 0 12 12" :class="{ rotated: msg.showCallChain }"><path d="M4 2l4 4-4 4" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
                <span>调用过程 ({{ msg.callChain.length }}个代理)</span>
              </button>
              <div v-if="msg.showCallChain" class="callchain-content">
                <div v-for="(call, ci) in msg.callChain" :key="ci" class="call-item" :class="'status-' + call.status">
                  <div class="call-header">
                    <span class="call-icon">
                      <template v-if="call.status === 'running'">⏳</template>
                      <template v-else-if="call.status === 'success'">✅</template>
                      <template v-else-if="call.status === 'empty'">⚪</template>
                      <template v-else-if="call.status === 'failed'">❌</template>
                      <template v-else>🔄</template>
                    </span>
                    <span class="call-agent">{{ call.agentName || call.agent }}</span>
                    <span v-if="call.latencyMs" class="call-time">{{ (call.latencyMs / 1000).toFixed(1) }}s</span>
                  </div>
                  <div class="call-query">{{ call.query }}</div>
                  <div v-if="call.result && call.status !== 'running'" class="call-result">
                    {{ call.result.length > 100 ? call.result.substring(0, 100) + '...' : call.result }}
                  </div>
                </div>
              </div>
            </div>
            <div v-if="msg.role === 'assistant'" class="om-text markdown-body" v-html="renderMarkdown(msg.content)"></div>
            <div v-else class="om-text">{{ msg.content }}</div>
            <div v-if="msg.streaming && !msg.thinking" class="om-caret"></div>
            <div v-if="msg.role==='assistant' && !msg.streaming" class="om-meta">
              <span v-if="msg.agent" class="om-agent">{{ agentLabel(msg.agent) }}</span>
              <span v-if="msg.model" class="om-model">{{ msg.model }}</span>
            </div>
            <div v-if="msg.role==='assistant' && !msg.streaming" class="om-actions">
              <button class="action-btn" @click="copyResponse(msg, i)" :title="copiedIdx === i ? '已复制' : '复制'">
                {{ copiedIdx === i ? '✓' : '📋' }}
              </button>
              <button v-if="i === messages.length - 1" class="action-btn" @click="regenerate" title="重新生成">🔄</button>
              <template v-if="msg.messageId">
                <button class="action-btn" :class="{ active: msg.feedback === 1 }" @click="handleFeedback(msg, 1)" title="有帮助">👍</button>
                <button class="action-btn" :class="{ active: msg.feedback === -1 }" @click="handleFeedback(msg, -1)" title="没帮助">👎</button>
              </template>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input-area">
        <div class="input-wrap">
          <textarea ref="textareaRef" v-model="input" :disabled="isStreaming" rows="1"
            placeholder="输入消息，Shift+Enter 换行" @keydown="handleKeydown" autocomplete="off"></textarea>
          <button v-if="isStreaming" class="stop-btn" @click="stopGeneration" title="停止生成">
            <svg width="16" height="16" viewBox="0 0 16 16"><rect x="3" y="3" width="10" height="10" rx="2" fill="currentColor"/></svg>
          </button>
          <button v-else class="send-btn" @click="send" :disabled="!input.trim()" title="发送">
            <svg width="16" height="16" viewBox="0 0 16 16"><path d="M2 14l12-6L2 2v5l8 1-8 1z" fill="currentColor"/></svg>
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* ===== Omnitrix(3.0) 风格 ===== */
.omnitrix-page {
  display: flex;
  height: calc(100vh - 60px);
  background: #ffffff;
  font-family: 'Google Sans', Roboto, 'Segoe UI', sans-serif;
  border: 1px solid #e3e3e3;
  border-radius: 24px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.12), 0 8px 24px rgba(0,0,0,0.15);
  overflow: hidden;
  margin: 8px;
  transition: background 0.3s, border-color 0.3s;
}

/* ===== 侧栏 ===== */
.sidebar {
  width: 260px;
  background: #ffffff;
  border-right: 1px solid #e3e3e3;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s;
}
.sidebar.hidden { width: 0; overflow: hidden; opacity: 0; }
.sb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #e3e3e3;
}
.sb-title { font-size: 14px; font-weight: 500; color: #444746; }
.new-btn {
  width: 28px; height: 28px;
  border: 1px solid #e3e3e3; border-radius: 8px;
  background: #fff; cursor: pointer;
  font-size: 16px; color: #1a73e8;
  display: flex; align-items: center; justify-content: center;
  transition: background 0.2s;
}
.new-btn:hover { background: #f0f4f9; }
.conv-list { flex: 1; overflow-y: auto; padding: 4px; }
.conv-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; margin: 2px 4px;
  border-radius: 12px; cursor: pointer;
  transition: background 0.2s;
}
.conv-item:hover { background: #f0f4f9; }
.conv-item.active { background: #e8f0fe; }
.conv-title {
  font-size: 13px; color: #1e1e1e;
  flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.conv-item:hover .conv-title { color: #1a73e8; }
.del-btn {
  width: 24px; height: 24px; border: none; background: rgba(255,255,255,0.9);
  backdrop-filter: blur(2px); cursor: pointer; color: #5f6368;
  font-size: 14px; border-radius: 50%; opacity: 0;
  box-shadow: -2px 0 8px rgba(0,0,0,0.1);
  transition: opacity 0.2s, transform 0.2s;
}
.conv-item:hover .del-btn { opacity: 1; }
.del-btn:hover { transform: scale(1.1); box-shadow: 0 2px 6px rgba(0,0,0,0.15); }
.empty { text-align: center; color: #5f6368; font-size: 13px; padding: 40px 0; }

/* ===== 主区域 ===== */
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

/* ===== 头部 ===== */
.chat-header {
  padding: 16px 16px 10px 16px;
  display: flex; align-items: center; justify-content: space-between;
  gap: 4px; flex-shrink: 0;
}

/* 三色控制按钮 */
.control-buttons { display: flex; gap: 6px; align-items: center; }
.control-btn {
  width: 10px !important; height: 10px !important;
  min-width: 10px !important; min-height: 10px !important;
  max-width: 10px !important; max-height: 10px !important;
  border-radius: 50%; cursor: pointer;
  border: 2px solid #fff;
  transition: 0.2s;
  box-sizing: content-box !important;
  padding: 0 !important; margin: 0 !important;
  flex-shrink: 0 !important;
}
.control-btn:hover { transform: scale(1.2); }
.control-btn.red { background: #ea4335; box-shadow: 0 0 0 1px #ea4335; }
.control-btn.green { background: #34a853; box-shadow: 0 0 0 1px #34a853; }
.control-btn.blue { background: #1a73e8; box-shadow: 0 0 0 1px #1a73e8; }

.header-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 18px; font-weight: 500; color: #444746;
  padding: 6px 12px; border-radius: 999px;
  cursor: default; user-select: none;
  transition: background 0.2s;
}
.header-title:hover { background: #f0f4f9; }
.logo { width: 22px; height: 22px; }
.logo-path { fill: #000000; transition: fill 0.3s; }
.title-text { line-height: 1; }

/* ===== 聊天主体 ===== */
.chat-body {
  flex: 1; overflow-y: auto;
  padding: 16px;
  display: flex; flex-direction: column; gap: 10px;
  scrollbar-width: none; -ms-overflow-style: none;
}
.chat-body::-webkit-scrollbar { display: none; }

/* 欢迎页 */
.welcome {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  flex: 1; gap: 12px;
}
.w-logo { width: 48px; height: 48px; }
.welcome-title { margin: 0; font-size: 20px; font-weight: 500; color: #1e1e1e; }
.welcome-desc { margin: 0; color: #5f6368; font-size: 14px; }
.quick-actions { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; justify-content: center; }
.quick-actions button {
  padding: 8px 16px;
  border: 1px solid #e3e3e3; border-radius: 999px;
  background: #fff; cursor: pointer;
  font-size: 13px; color: #1e1e1e;
  transition: background 0.2s, color 0.2s;
}
.quick-actions button:hover { background: #f0f4f9; color: #1a73e8; border-color: #1a73e8; }

/* ===== 消息气泡 ===== */
.om-msg { display: flex; margin: 0 0 10px 0; }
.om-msg.user { justify-content: flex-end; }

.om-bubble {
  max-width: 80%; padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px; line-height: 1.55;
  word-break: break-word;
}
.om-msg.user .om-bubble { background: #f0f4f9; color: #1e1e1e; white-space: pre-wrap; }
.om-msg.assistant .om-bubble { background: #ffffff; border: 1px solid #e3e3e3; color: #1e1e1e; }

.om-text { display: block; }
.om-meta {
  display: flex; align-items: center; gap: 6px; margin-top: 6px; flex-wrap: wrap;
}
.om-agent {
  font-size: 11px; color: #5f6368;
  padding: 2px 8px; background: #f0f4f9; border-radius: 4px; display: inline-block;
}
.om-model {
  font-size: 10px; color: #9aa0a6;
  padding: 2px 6px; background: #f8f9fa; border-radius: 4px; display: inline-block;
  font-family: 'Fira Code', monospace;
}

/* ===== 导出按钮 ===== */
.export-btn {
  border: 1px solid #e3e3e3; background: #fff; border-radius: 8px;
  width: 32px; height: 32px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: #5f6368; transition: all 0.2s;
}
.export-btn:hover { background: #f0f4f9; color: #1a73e8; border-color: #1a73e8; }

/* ===== 思维链折叠面板 ===== */
.om-thinking-panel {
  margin-bottom: 8px; border-radius: 8px;
  border: 1px solid #e8eaed; background: #fafbfc; overflow: hidden;
}
.thinking-toggle {
  display: flex; align-items: center; gap: 4px;
  border: none; background: transparent; cursor: pointer;
  font-size: 12px; color: #5f6368; padding: 6px 10px; width: 100%;
  transition: background 0.2s;
}
.thinking-toggle:hover { background: #f0f4f9; }
.thinking-toggle svg { transition: transform 0.2s; flex-shrink: 0; }
.thinking-toggle svg.rotated { transform: rotate(90deg); }
.thinking-content {
  padding: 4px 12px 10px 12px; font-size: 12px; color: #5f6368;
  line-height: 1.5; border-top: 1px solid #e8eaed; max-height: 200px; overflow-y: auto;
}

/* ===== 调用链面板 ===== */
.om-callchain-panel {
  margin-bottom: 8px; border-radius: 8px;
  border: 1px solid #e8eaed; background: #fafbfc; overflow: hidden;
}
.callchain-toggle {
  display: flex; align-items: center; gap: 4px;
  border: none; background: transparent; cursor: pointer;
  font-size: 12px; color: #5f6368; padding: 6px 10px; width: 100%;
  transition: background 0.2s;
}
.callchain-toggle:hover { background: #f0f4f9; }
.callchain-toggle svg { transition: transform 0.2s; flex-shrink: 0; }
.callchain-toggle svg.rotated { transform: rotate(90deg); }
.callchain-content {
  border-top: 1px solid #e8eaed; max-height: 300px; overflow-y: auto;
}
.call-item {
  padding: 8px 12px; border-bottom: 1px solid #f0f0f0;
}
.call-item:last-child { border-bottom: none; }
.call-item.status-running { background: #fffbe6; }
.call-item.status-success { background: #f6ffed; }
.call-item.status-empty { background: #fafafa; }
.call-item.status-failed { background: #fff1f0; }
.call-header {
  display: flex; align-items: center; gap: 6px; font-size: 12px;
  margin-bottom: 4px;
}
.call-icon { font-size: 14px; }
.call-agent { font-weight: 500; color: #333; }
.call-time { color: #999; font-size: 11px; margin-left: auto; }
.call-query {
  font-size: 11px; color: #666; padding-left: 20px; margin-bottom: 4px;
}
.call-result {
  font-size: 11px; color: #888; padding-left: 20px;
  background: rgba(0,0,0,0.02); padding: 4px 8px; border-radius: 4px;
  white-space: pre-wrap; word-break: break-all;
}

/* ===== 思考中指示器 ===== */
.om-thinking {
  display: flex; align-items: center; gap: 4px; padding: 4px 0;
}
.thinking-dot {
  width: 6px; height: 6px; border-radius: 50%; background: #1a73e8;
  animation: thinking-bounce 1.4s ease-in-out infinite;
}
.thinking-dot:nth-child(2) { animation-delay: 0.16s; }
.thinking-dot:nth-child(3) { animation-delay: 0.32s; }
.thinking-label { font-size: 12px; color: #5f6368; margin-left: 4px; }
@keyframes thinking-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* ===== 操作栏 (复制 / 反馈) ===== */
.om-actions {
  display: flex; gap: 2px; margin-top: 6px; opacity: 0; transition: opacity 0.2s;
}
.om-bubble:hover .om-actions { opacity: 1; }
.action-btn {
  border: none; background: transparent; cursor: pointer;
  font-size: 13px; padding: 3px 7px; border-radius: 6px;
  color: #5f6368; transition: all 0.2s;
}
.action-btn:hover { background: #f0f4f9; color: #1a73e8; }
.action-btn.active { background: #e8f0fe; color: #1a73e8; }

/* 光标闪烁 */
.om-caret {
  display: inline-block;
  width: 2px; height: 18px;
  background: #1e1e1e;
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: omnitrix-caret-blink 1s steps(1) infinite;
}
@keyframes omnitrix-caret-blink { 50% { opacity: 0; } }

/* ===== Markdown 样式 ===== */
.markdown-body :deep(h1) { font-size: 1.4em; margin: 12px 0 6px; font-weight: 600; }
.markdown-body :deep(h2) { font-size: 1.2em; margin: 10px 0 4px; font-weight: 600; }
.markdown-body :deep(h3) { font-size: 1.05em; margin: 8px 0 4px; font-weight: 600; }
.markdown-body :deep(p) { margin: 4px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 4px 0; }
.markdown-body :deep(li) { margin: 2px 0; }
.markdown-body :deep(code) {
  background: #f0f4f9; padding: 1px 5px; border-radius: 4px;
  font-size: 13px; font-family: 'Fira Code', monospace;
}
.markdown-body :deep(pre) {
  background: #1e1e1e; color: #d4d4d4; padding: 12px 14px;
  border-radius: 10px; overflow-x: auto; margin: 8px 0;
  font-size: 13px; line-height: 1.5;
}
.markdown-body :deep(pre code) {
  background: transparent; padding: 0; color: inherit;
}
.markdown-body :deep(table) {
  border-collapse: collapse; width: 100%; margin: 8px 0;
}
.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid #e3e3e3; padding: 6px 10px; text-align: left; font-size: 13px;
}
.markdown-body :deep(th) { background: #f0f4f9; font-weight: 600; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid #1a73e8; margin: 8px 0; padding: 4px 12px; color: #5f6368;
}
.markdown-body :deep(hr) { border: none; border-top: 1px solid #e3e3e3; margin: 12px 0; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(a) { color: #1a73e8; text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }

/* ===== 输入区域 ===== */
.chat-input-area {
  padding: 12px 16px 16px 16px;
  flex-shrink: 0;
}
.input-wrap {
  position: relative;
  max-width: 760px;
  margin: 0 auto;
  display: flex; align-items: flex-end;
  background: #f0f4f9;
  border: 1px solid #e3e3e3;
  border-radius: 16px;
  transition: all 0.2s;
}
.input-wrap:focus-within {
  background: #ffffff;
  border-color: #1a73e8;
  box-shadow: 0 1px 4px rgba(26,115,232,0.2);
}
.input-wrap textarea {
  flex: 1; box-sizing: border-box;
  padding: 12px 8px 12px 14px;
  background: transparent;
  border: none;
  font-size: 14px;
  outline: none;
  color: #1e1e1e;
  font-family: inherit;
  resize: none;
  max-height: 200px;
  line-height: 1.5;
}
.input-wrap textarea::placeholder { color: #9aa0a6; }
.input-wrap textarea:disabled { opacity: 0.5; }

.send-btn, .stop-btn {
  width: 34px; height: 34px;
  border: none; border-radius: 10px;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  margin: 4px 6px 4px 0;
  flex-shrink: 0;
  transition: all 0.2s;
}
.send-btn {
  background: #1a73e8; color: #fff;
}
.send-btn:hover { background: #1557b0; }
.send-btn:disabled { background: #c4c7c5; cursor: default; }
.stop-btn {
  background: #ea4335; color: #fff;
}
.stop-btn:hover { background: #c5221f; }

/* ===== 深色模式 ===== */
.omnitrix-page.dark-mode {
  background: #131314 !important;
  border-color: #333;
}
.dark-mode .sidebar { background: #131314; border-right-color: #333; }
.dark-mode .sb-head { border-bottom-color: #333; }
.dark-mode .sb-title { color: #e0e0e0; }
.dark-mode .new-btn { background: #1E1F20; border-color: #555; color: #8ab4f8; }
.dark-mode .new-btn:hover { background: #333; }
.dark-mode .conv-item:hover { background: #2a2a2b; }
.dark-mode .conv-item.active { background: #1a3a5c; }
.dark-mode .conv-title { color: #e0e0e0; }
.dark-mode .conv-item:hover .conv-title { color: #8ab4f8; }
.dark-mode .del-btn { background: rgba(30,31,32,0.9); color: #aaa; }
.dark-mode .empty { color: #888; }

.dark-mode .chat-header { border-bottom-color: #333; }
.dark-mode .control-btn { border-color: #131314; }
.dark-mode .header-title { color: #ffffff; }
.dark-mode .header-title:hover { background: #333; }
.dark-mode .logo-path { fill: #ffffff !important; }
.dark-mode .title-text { color: #ffffff; }

.dark-mode .welcome-title { color: #ffffff; }
.dark-mode .welcome-desc { color: #aaa; }
.dark-mode .quick-actions button {
  background: #1E1F20; border-color: #555; color: #e0e0e0;
}
.dark-mode .quick-actions button:hover {
  background: #333; color: #8ab4f8; border-color: #8ab4f8;
}

.dark-mode .om-msg.user .om-bubble { background: #1E1F20; color: #e0e0e0; }
.dark-mode .om-msg.assistant .om-bubble {
  background: #131314; border-color: #333; color: #e0e0e0;
}
.dark-mode .om-caret { background: #ffffff; }
.dark-mode .om-agent { color: #aaa; background: #1E1F20; }
.dark-mode .om-model { color: #666; background: #1E1F20; }
.dark-mode .export-btn { background: #1E1F20; border-color: #555; color: #aaa; }
.dark-mode .export-btn:hover { background: #333; color: #8ab4f8; border-color: #8ab4f8; }
.dark-mode .om-thinking-panel { border-color: #333; background: #1a1a1b; }
.dark-mode .thinking-toggle { color: #aaa; }
.dark-mode .thinking-toggle:hover { background: #2a2a2b; }
.dark-mode .thinking-content { color: #888; border-top-color: #333; }
.dark-mode .thinking-dot { background: #8ab4f8; }
.dark-mode .thinking-label { color: #888; }
.dark-mode .action-btn { color: #888; }
.dark-mode .action-btn:hover { background: #333; color: #8ab4f8; }
.dark-mode .action-btn.active { background: #1a3a5c; color: #8ab4f8; }

.dark-mode .input-wrap { background: #1E1F20; border-color: #555; }
.dark-mode .input-wrap:focus-within { background: #2a2a2b; border-color: #1a73e8; }
.dark-mode .input-wrap textarea { color: #fff; }
.dark-mode .input-wrap textarea::placeholder { color: #888; }

.dark-mode .markdown-body :deep(code) { background: #2a2a2b; color: #e0e0e0; }
.dark-mode .markdown-body :deep(pre) { background: #0d0d0d; color: #d4d4d4; }
.dark-mode .markdown-body :deep(th) { background: #1E1F20; }
.dark-mode .markdown-body :deep(th), .dark-mode .markdown-body :deep(td) { border-color: #444; }
.dark-mode .markdown-body :deep(blockquote) { border-left-color: #8ab4f8; color: #aaa; }
.dark-mode .markdown-body :deep(hr) { border-top-color: #444; }
.dark-mode .markdown-body :deep(a) { color: #8ab4f8; }
</style>
