<script setup>
import { ref, nextTick, onMounted, onUnmounted, watch, computed } from 'vue'
import { chatStream, listConversations, createConversation, deleteConversation, getConversation, sendFeedback, regenerateStream } from '@/api/omnitrix'
import { marked } from 'marked'

marked.setOptions({ breaks: true, gfm: true })

const AGENT_NAMES = {
  admin_assistant: '管理助手',
  content_assistant: '非遗文化助手',
  commerce_assistant: '文创商城助手',
  user_assistant: '个人服务助手',
  browse_history_assistant: '浏览历史助手',
  recommend_assistant: '智能推荐助手',
  knowledge_assistant: '知识库问答助手',
  general_assistant: '通用助手'
}

const expanded = ref(false)
const userId = ref(localStorage.getItem('ich_user_id') || '1')
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

function toggle() { expanded.value = !expanded.value; if (expanded.value && !conversations.value.length) loadConversations() }

onUnmounted(() => { closeStream() })

function closeStream() { if (eventSource) { eventSource.close(); eventSource = null } }

function stopGeneration() {
  closeStream()
  const last = messages.value[messages.value.length - 1]
  if (last && last.streaming) { last.streaming = false; last.content += '\n\n*[generation stopped]*' }
  isStreaming.value = false
}

async function loadConversations() {
  try { const res = await listConversations(userId.value); conversations.value = res.data || [] } catch (e) { conversations.value = [] }
}

async function startNewChat() {
  closeStream()
  try {
    const res = await createConversation(userId.value)
    currentSessionId.value = res.data.sessionId; currentConvId.value = res.data.id; messages.value = []
    await loadConversations()
  } catch (e) { currentSessionId.value = 'session_' + Date.now(); currentConvId.value = null; messages.value = [] }
}

async function selectConversation(conv) {
  closeStream(); currentSessionId.value = conv.sessionId; currentConvId.value = conv.id
  try {
    const res = await getConversation(conv.id, userId.value)
    messages.value = (res.data.messages || []).map(m => ({ role: m.role === 'user' ? 'user' : 'assistant', content: m.content, agent: m.subAgent, messageId: m.id || null }))
    scrollToBottom()
  } catch (e) { messages.value = [] }
}

async function removeConversation(conv) {
  try {
    await deleteConversation(conv.id, userId.value)
    if (currentConvId.value === conv.id) { currentSessionId.value = ''; currentConvId.value = null; messages.value = [] }
    await loadConversations()
  } catch (e) {}
}

function send() {
  const q = input.value.trim(); if (!q || isStreaming.value) return
  input.value = ''; autoResize()
  if (!currentSessionId.value) currentSessionId.value = 'session_' + Date.now()
  messages.value.push({ role: 'user', content: q })
  messages.value.push({ role: 'assistant', content: '', agent: '', streaming: true, thinking: true, thinkingContent: '', showThinking: false })
  isStreaming.value = true; scrollToBottom(); closeStream()
  eventSource = chatStream(currentSessionId.value, q, userId.value)
  const idx = messages.value.length - 1
  eventSource.addEventListener('thinking', (e) => { messages.value[idx].thinkingContent += e.data; scrollToBottom() })
  eventSource.addEventListener('chunk', (e) => { messages.value[idx].thinking = false; messages.value[idx].content += e.data; scrollToBottom() })
  eventSource.addEventListener('agent', (e) => { try { const d = JSON.parse(e.data); messages.value[idx].agent = d.agent || e.data } catch (_) { messages.value[idx].agent = e.data } })
  eventSource.addEventListener('done', (e) => {
    messages.value[idx].streaming = false; messages.value[idx].thinking = false; isStreaming.value = false
    try { const d = JSON.parse(e.data); if (d.messageId) messages.value[idx].messageId = d.messageId; if (d.model) messages.value[idx].model = d.model } catch (_) {}
    closeStream(); loadConversations()
  })
  eventSource.addEventListener('error_msg', (e) => { messages.value[idx].thinking = false; messages.value[idx].content += '\n\n' + e.data; messages.value[idx].streaming = false; isStreaming.value = false; closeStream() })
  eventSource.onerror = () => { if (isStreaming.value) { messages.value[idx].streaming = false; messages.value[idx].thinking = false; isStreaming.value = false }; closeStream() }
}

function regenerate() {
  if (!currentSessionId.value || isStreaming.value) return
  const lastIdx = messages.value.length - 1
  if (lastIdx >= 0 && messages.value[lastIdx].role === 'assistant') messages.value.splice(lastIdx, 1)
  messages.value.push({ role: 'assistant', content: '', agent: '', streaming: true, thinking: true, thinkingContent: '', showThinking: false })
  isStreaming.value = true; scrollToBottom(); closeStream()
  eventSource = regenerateStream(currentSessionId.value, userId.value)
  const idx = messages.value.length - 1
  eventSource.addEventListener('thinking', (e) => { messages.value[idx].thinkingContent += e.data; scrollToBottom() })
  eventSource.addEventListener('chunk', (e) => { messages.value[idx].thinking = false; messages.value[idx].content += e.data; scrollToBottom() })
  eventSource.addEventListener('agent', (e) => { try { const d = JSON.parse(e.data); messages.value[idx].agent = d.agent || e.data } catch (_) { messages.value[idx].agent = e.data } })
  eventSource.addEventListener('done', (e) => {
    messages.value[idx].streaming = false; messages.value[idx].thinking = false; isStreaming.value = false
    try { const d = JSON.parse(e.data); if (d.messageId) messages.value[idx].messageId = d.messageId; if (d.model) messages.value[idx].model = d.model } catch (_) {}
    closeStream(); loadConversations()
  })
  eventSource.addEventListener('error_msg', (e) => { messages.value[idx].thinking = false; messages.value[idx].content += '\n\n' + e.data; messages.value[idx].streaming = false; isStreaming.value = false; closeStream() })
  eventSource.onerror = () => { if (isStreaming.value) { messages.value[idx].streaming = false; messages.value[idx].thinking = false; isStreaming.value = false }; closeStream() }
}

function handleKeydown(e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }
function autoResize() { nextTick(() => { const el = textareaRef.value; if (!el) return; el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 160) + 'px' }) }
watch(input, autoResize)
function scrollToBottom() { nextTick(() => { if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight }) }
async function handleFeedback(msg, value) { if (!msg.messageId || msg.feedback) return; try { await sendFeedback(msg.messageId, value); msg.feedback = value } catch (e) {} }
function toggleThinking(msg) { msg.showThinking = !msg.showThinking }
function copyResponse(msg, idx) { navigator.clipboard.writeText(msg.content).then(() => { copiedIdx.value = idx; setTimeout(() => { copiedIdx.value = -1 }, 2000) }).catch(() => {}) }
function agentLabel(code) { return code ? (AGENT_NAMES[code] || code) : '' }
function renderMarkdown(text) { return text ? marked.parse(text) : '' }
</script>

<template>
  <div class="omnitrix-float" :class="{ 'dark-mode': darkMode }">
    <!-- ====== 悬浮按钮（绿色圆球） ====== -->
    <button v-if="!expanded" class="omnitrix-orb" @click="toggle" title="Omnitrix AI">
      <svg viewBox="0 0 35 33" fill="none" class="orb-logo">
        <path d="M13.2371 21.0407L24.3186 12.8506C24.8619 12.4491 25.6384 12.6057 25.8973 13.2294C27.2597 16.5185 26.651 20.4712 23.9403 23.1851C21.2297 25.8989 17.4581 26.4941 14.0108 25.1386L10.2449 26.8843C15.6463 30.5806 22.2053 29.6665 26.304 25.5601C29.5551 22.3051 30.562 17.8683 29.6205 13.8673L29.629 13.8758C28.2637 7.99809 29.9647 5.64871 33.449 0.844576C33.5314 0.730667 33.6139 0.616757 33.6964 0.5L29.1113 5.09055V5.07631L13.2343 21.0436" fill="currentColor"/>
        <path d="M10.9503 23.0313C7.07343 19.3235 7.74185 13.5853 11.0498 10.2763C13.4959 7.82722 17.5036 6.82767 21.0021 8.2971L24.7595 6.55998C24.0826 6.07017 23.215 5.54334 22.2195 5.17313C17.7198 3.31926 12.3326 4.24192 8.67479 7.90126C5.15635 11.4239 4.0499 16.8403 5.94992 21.4622C7.36924 24.9165 5.04257 27.3598 2.69884 29.826C1.86829 30.7002 1.0349 31.5745 0.36364 32.5L10.9474 23.0341" fill="currentColor"/>
      </svg>
    </button>

    <!-- ====== 展开的聊天面板 ====== -->
    <div v-if="expanded" class="omnitrix-panel">
      <div class="omnitrix-layout" :class="{ 'sidebar-hidden': !showSidebar }">
        <!-- Beige 侧栏 -->
        <aside v-show="showSidebar" class="om-sidebar">
          <div class="om-sb-head">
            <div class="om-logo-group">
              <svg viewBox="0 0 35 33" fill="none"><path d="M13.2371 21.0407L24.3186 12.8506C24.8619 12.4491 25.6384 12.6057 25.8973 13.2294C27.2597 16.5185 26.651 20.4712 23.9403 23.1851C21.2297 25.8989 17.4581 26.4941 14.0108 25.1386L10.2449 26.8843C15.6463 30.5806 22.2053 29.6665 26.304 25.5601C29.5551 22.3051 30.562 17.8683 29.6205 13.8673L29.629 13.8758C28.2637 7.99809 29.9647 5.64871 33.449 0.844576C33.5314 0.730667 33.6139 0.616757 33.6964 0.5L29.1113 5.09055V5.07631L13.2343 21.0436" fill="currentColor"/><path d="M10.9503 23.0313C7.07343 19.3235 7.74185 13.5853 11.0498 10.2763C13.4959 7.82722 17.5036 6.82767 21.0021 8.2971L24.7595 6.55998C24.0826 6.07017 23.215 5.54334 22.2195 5.17313C17.7198 3.31926 12.3326 4.24192 8.67479 7.90126C5.15635 11.4239 4.0499 16.8403 5.94992 21.4622C7.36924 24.9165 5.04257 27.3598 2.69884 29.826C1.86829 30.7002 1.0349 31.5745 0.36364 32.5L10.9474 23.0341" fill="currentColor"/></svg>
            </div>
          </div>
          <div class="om-sb-nav-item" @click="startNewChat">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/><line x1="12" y1="7" x2="12" y2="13"/><line x1="9" y1="10" x2="15" y2="10"/></svg>
            <span>New Desktop</span>
          </div>
          <div class="om-sb-section-title">Desktops</div>
          <div class="om-sb-conv-list">
            <div v-for="c in conversations" :key="c.id" class="om-sb-conv" :class="{ active: currentConvId === c.id }" @click="selectConversation(c)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
              <span class="conv-title">{{ c.title || '新对话' }}</span>
              <button class="conv-del" @click.stop="removeConversation(c)">×</button>
            </div>
            <div v-if="!conversations.length" class="om-sb-empty">暂无对话</div>
          </div>
          <div class="om-sb-spacer"></div>
        </aside>

        <!-- 白色主聊天区 -->
        <main class="om-white-box">
          <div class="om-header">
            <div class="om-ctrl-btns">
              <div class="om-ctrl red" @click="toggle"></div>
              <div class="om-ctrl green" @click="showSidebar = !showSidebar"></div>
              <div class="om-ctrl blue" @click="darkMode = !darkMode"></div>
            </div>
            <div class="om-title">
              <svg viewBox="0 0 35 33" fill="none" class="om-title-logo"><path d="M13.2371 21.0407L24.3186 12.8506C24.8619 12.4491 25.6384 12.6057 25.8973 13.2294C27.2597 16.5185 26.651 20.4712 23.9403 23.1851C21.2297 25.8989 17.4581 26.4941 14.0108 25.1386L10.2449 26.8843C15.6463 30.5806 22.2053 29.6665 26.304 25.5601C29.5551 22.3051 30.562 17.8683 29.6205 13.8673L29.629 13.8758C28.2637 7.99809 29.9647 5.64871 33.449 0.844576C33.5314 0.730667 33.6139 0.616757 33.6964 0.5L29.1113 5.09055V5.07631L13.2343 21.0436" fill="currentColor"/><path d="M10.9503 23.0313C7.07343 19.3235 7.74185 13.5853 11.0498 10.2763C13.4959 7.82722 17.5036 6.82767 21.0021 8.2971L24.7595 6.55998C24.0826 6.07017 23.215 5.54334 22.2195 5.17313C17.7198 3.31926 12.3326 4.24192 8.67479 7.90126C5.15635 11.4239 4.0499 16.8403 5.94992 21.4622C7.36924 24.9165 5.04257 27.3598 2.69884 29.826C1.86829 30.7002 1.0349 31.5745 0.36364 32.5L10.9474 23.0341" fill="currentColor"/></svg>
              <span>Omnitrix</span>
            </div>
          </div>

          <div class="om-chat-body" ref="chatBodyRef">
            <!-- 欢迎页 -->
            <div v-if="!messages.length" class="om-welcome">
              <svg class="om-welcome-logo" viewBox="0 0 35 33" fill="none"><path d="M13.2371 21.0407L24.3186 12.8506C24.8619 12.4491 25.6384 12.6057 25.8973 13.2294C27.2597 16.5185 26.651 20.4712 23.9403 23.1851C21.2297 25.8989 17.4581 26.4941 14.0108 25.1386L10.2449 26.8843C15.6463 30.5806 22.2053 29.6665 26.304 25.5601C29.5551 22.3051 30.562 17.8683 29.6205 13.8673L29.629 13.8758C28.2637 7.99809 29.9647 5.64871 33.449 0.844576C33.5314 0.730667 33.6139 0.616757 33.6964 0.5L29.1113 5.09055V5.07631L13.2343 21.0436" class="wl-path"/><path d="M10.9503 23.0313C7.07343 19.3235 7.74185 13.5853 11.0498 10.2763C13.4959 7.82722 17.5036 6.82767 21.0021 8.2971L24.7595 6.55998C24.0826 6.07017 23.215 5.54334 22.2195 5.17313C17.7198 3.31926 12.3326 4.24192 8.67479 7.90126C5.15635 11.4239 4.0499 16.8403 5.94992 21.4622C7.36924 24.9165 5.04257 27.3598 2.69884 29.826C1.86829 30.7002 1.0349 31.5745 0.36364 32.5L10.9474 23.0341" class="wl-path"/></svg>
              <p class="om-welcome-text">你好，有什么可以帮你的吗？</p>
              <div class="om-quick">
                <button @click="input='介绍一下京剧';send()">介绍一下京剧</button>
                <button @click="input='推荐文创商品';send()">推荐文创商品</button>
              </div>
            </div>

            <!-- 消息列表 -->
            <div v-for="(msg, i) in messages" :key="i" class="om-msg" :class="msg.role">
              <div class="om-bubble">
                <div v-if="msg.thinking && msg.streaming" class="om-thinking">
                  <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                  <span class="thinking-label">思考中...</span>
                </div>
                <div v-if="msg.thinkingContent && msg.role==='assistant'" class="om-think-panel">
                  <button class="think-toggle" @click="toggleThinking(msg)">
                    <svg width="12" height="12" viewBox="0 0 12 12" :class="{ rotated: msg.showThinking }"><path d="M4 2l4 4-4 4" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    <span>思考过程</span>
                  </button>
                  <div v-if="msg.showThinking" class="think-content" v-html="renderMarkdown(msg.thinkingContent)"></div>
                </div>
                <div v-if="msg.role==='assistant'" class="om-text md-body" v-html="renderMarkdown(msg.content)"></div>
                <div v-else class="om-text">{{ msg.content }}</div>
                <div v-if="msg.streaming && !msg.thinking" class="om-caret"></div>
                <div v-if="msg.role==='assistant' && !msg.streaming" class="om-meta">
                  <span v-if="msg.agent" class="om-agent">{{ agentLabel(msg.agent) }}</span>
                  <span v-if="msg.model" class="om-model">{{ msg.model }}</span>
                </div>
                <div v-if="msg.role==='assistant' && !msg.streaming" class="om-actions">
                  <button class="act-btn" @click="copyResponse(msg, i)">{{ copiedIdx === i ? '✓' : '📋' }}</button>
                  <button v-if="i === messages.length - 1" class="act-btn" @click="regenerate">🔄</button>
                  <template v-if="msg.messageId">
                    <button class="act-btn" :class="{ active: msg.feedback === 1 }" @click="handleFeedback(msg, 1)">👍</button>
                    <button class="act-btn" :class="{ active: msg.feedback === -1 }" @click="handleFeedback(msg, -1)">👎</button>
                  </template>
                </div>
              </div>
            </div>
          </div>

          <div class="om-input-area">
            <div class="om-input-wrap">
              <textarea ref="textareaRef" v-model="input" :disabled="isStreaming" rows="1"
                placeholder="输入消息..." @keydown="handleKeydown" autocomplete="off"></textarea>
              <button v-if="isStreaming" class="om-stop-btn" @click="stopGeneration">
                <svg width="14" height="14" viewBox="0 0 16 16"><rect x="3" y="3" width="10" height="10" rx="2" fill="currentColor"/></svg>
              </button>
              <button v-else class="om-send-btn" @click="send" :disabled="!input.trim()">
                <svg width="14" height="14" viewBox="0 0 16 16"><path d="M2 14l12-6L2 2v5l8 1-8 1z" fill="currentColor"/></svg>
              </button>
            </div>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ====== 悬浮按钮 ====== */
.omnitrix-float { position: fixed; bottom: 24px; right: 24px; z-index: 99999; font-family: 'Google Sans', Roboto, 'Segoe UI', sans-serif; }

.omnitrix-orb {
  width: 52px; height: 52px; border-radius: 50%; cursor: pointer;
  border: 2px solid transparent; padding: 0;
  background: linear-gradient(#fff, #fff) padding-box, linear-gradient(135deg, #58B6F4, #72F17E) border-box;
  box-shadow: 0 4px 16px rgba(0,0,0,0.18), 0 0 0 0 rgba(114,241,126,0.4);
  display: flex; align-items: center; justify-content: center;
  transition: transform 0.2s, box-shadow 0.3s;
  animation: orb-pulse 2.5s ease-in-out infinite;
}
.omnitrix-orb:hover { transform: scale(1.1); box-shadow: 0 6px 24px rgba(0,0,0,0.22); animation: none; }
.orb-logo { width: 24px; height: 24px; color: #1a1a1a; }
@keyframes orb-pulse {
  0%, 100% { box-shadow: 0 4px 16px rgba(0,0,0,0.18), 0 0 0 0 rgba(114,241,126,0.4); }
  50% { box-shadow: 0 4px 16px rgba(0,0,0,0.18), 0 0 0 8px rgba(114,241,126,0); }
}

/* ====== 展开面板 ====== */
.omnitrix-panel {
  position: fixed; bottom: 24px; right: 24px;
  width: 680px; height: 560px;
  border-radius: 24px; overflow: hidden;
  box-shadow: 0 8px 32px rgba(0,0,0,0.18), 0 1px 3px rgba(0,0,0,0.1);
  animation: panel-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
@keyframes panel-in {
  from { opacity: 0; transform: scale(0.85) translateY(20px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.omnitrix-layout {
  display: flex; width: 100%; height: 100%; overflow: hidden;
  background: #F9F6F1;
}
.omnitrix-layout.sidebar-hidden .om-sidebar { display: none; }

/* ====== Beige 侧栏 ====== */
.om-sidebar {
  width: 200px; flex-shrink: 0; display: flex; flex-direction: column;
  padding: 14px; box-sizing: border-box; overflow-y: auto; color: #1a1a1a;
  transition: width 0.3s;
}
.om-sidebar::-webkit-scrollbar { display: none; }

.om-sb-head { display: flex; align-items: center; margin-bottom: 16px; padding: 0 4px; }
.om-logo-group {
  display: flex; align-items: center; justify-content: center;
  border: 2px solid transparent;
  background: linear-gradient(#fff, #fff) padding-box, linear-gradient(135deg, #58B6F4, #72F17E) border-box;
  border-radius: 50%; width: 32px; height: 32px;
}
.om-logo-group svg { width: 18px; height: 18px; color: #000; }

.om-sb-nav-item {
  display: flex; align-items: center; gap: 10px; padding: 8px 10px;
  border-radius: 8px; cursor: pointer; font-size: 13px; font-weight: 500;
  color: #1a1a1a; transition: background 0.2s; margin-bottom: 4px;
}
.om-sb-nav-item:hover { background: #EBE9E4; }
.om-sb-nav-item svg { width: 16px; height: 16px; flex-shrink: 0; stroke-width: 1.5; }

.om-sb-section-title {
  font-size: 11px; color: #999; margin: 16px 0 6px 10px; font-weight: 500;
  text-transform: uppercase; letter-spacing: 0.04em;
}

.om-sb-conv-list { flex: 1; overflow-y: auto; }
.om-sb-conv-list::-webkit-scrollbar { display: none; }

.om-sb-conv {
  display: flex; align-items: center; gap: 8px; padding: 7px 10px;
  border-radius: 8px; cursor: pointer; transition: background 0.2s; margin-bottom: 2px;
}
.om-sb-conv:hover { background: #EBE9E4; }
.om-sb-conv.active { background: #E2DFDA; }
.om-sb-conv svg { width: 14px; height: 14px; flex-shrink: 0; color: #666; }
.conv-title { font-size: 12px; color: #1a1a1a; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-del {
  width: 20px; height: 20px; border: none; background: transparent;
  cursor: pointer; color: #999; font-size: 14px; border-radius: 50%;
  opacity: 0; transition: opacity 0.2s;
  display: flex; align-items: center; justify-content: center;
}
.om-sb-conv:hover .conv-del { opacity: 1; }
.conv-del:hover { background: #d4d1cc; color: #333; }
.om-sb-empty { text-align: center; color: #999; font-size: 12px; padding: 20px 0; }
.om-sb-spacer { flex-grow: 1; }

/* ====== 白色主区域 ====== */
.om-white-box {
  flex: 1; background: #ffffff; border-radius: 16px; margin: 12px 12px 12px 0;
  box-shadow: 0 4px 20px rgba(0,0,0,0.03);
  display: flex; flex-direction: column; overflow: hidden;
  border: 1px solid #EBE9E4;
}
.omnitrix-layout.sidebar-hidden .om-white-box { margin: 12px; }

/* ====== 头部 ====== */
.om-header {
  padding: 12px 14px 8px 14px;
  display: flex; align-items: center; justify-content: space-between;
  flex-shrink: 0;
}
.om-ctrl-btns { display: flex; gap: 6px; align-items: center; }
.om-ctrl {
  width: 10px; height: 10px; border-radius: 50%; cursor: pointer;
  border: 2px solid #fff; transition: transform 0.2s;
  box-sizing: content-box;
}
.om-ctrl:hover { transform: scale(1.2); }
.om-ctrl.red { background: #ea4335; box-shadow: 0 0 0 1px #ea4335; }
.om-ctrl.green { background: #34a853; box-shadow: 0 0 0 1px #34a853; }
.om-ctrl.blue { background: #1a73e8; box-shadow: 0 0 0 1px #1a73e8; }

.om-title {
  display: flex; align-items: center; gap: 5px;
  font-size: 16px; font-weight: 500; color: #444746;
  padding: 4px 10px; border-radius: 999px; user-select: none;
}
.om-title-logo { width: 18px; height: 18px; color: #000; }

/* ====== 聊天主体 ====== */
.om-chat-body {
  flex: 1; overflow-y: auto; padding: 12px;
  display: flex; flex-direction: column; gap: 8px;
  scrollbar-width: none;
}
.om-chat-body::-webkit-scrollbar { display: none; }

.om-welcome {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  flex: 1; gap: 10px;
}
.om-welcome-logo { width: 40px; height: 40px; }
.wl-path { fill: #000; }
.om-welcome-text { margin: 0; color: #5f6368; font-size: 14px; }
.om-quick { display: flex; gap: 6px; flex-wrap: wrap; justify-content: center; }
.om-quick button {
  padding: 6px 14px; border: 1px solid #e3e3e3; border-radius: 999px;
  background: #fff; cursor: pointer; font-size: 12px; color: #1e1e1e;
  transition: all 0.2s;
}
.om-quick button:hover { background: #f0f4f9; color: #1a73e8; border-color: #1a73e8; }

/* ====== 消息气泡 ====== */
.om-msg { display: flex; }
.om-msg.user { justify-content: flex-end; }
.om-bubble {
  max-width: 85%; padding: 8px 12px; border-radius: 14px;
  font-size: 13px; line-height: 1.55; word-break: break-word;
}
.om-msg.user .om-bubble { background: #f0f4f9; color: #1e1e1e; white-space: pre-wrap; }
.om-msg.assistant .om-bubble { background: #fff; border: 1px solid #e3e3e3; color: #1e1e1e; }
.om-text { display: block; }
.om-meta { display: flex; align-items: center; gap: 4px; margin-top: 4px; }
.om-agent { font-size: 10px; color: #5f6368; padding: 1px 6px; background: #f0f4f9; border-radius: 4px; }
.om-model { font-size: 9px; color: #9aa0a6; padding: 1px 4px; background: #f8f9fa; border-radius: 4px; font-family: monospace; }

.om-actions { display: flex; gap: 2px; margin-top: 4px; opacity: 0; transition: opacity 0.2s; }
.om-bubble:hover .om-actions { opacity: 1; }
.act-btn {
  border: none; background: transparent; cursor: pointer;
  font-size: 12px; padding: 2px 5px; border-radius: 4px;
  color: #5f6368; transition: all 0.2s;
}
.act-btn:hover { background: #f0f4f9; color: #1a73e8; }
.act-btn.active { background: #e8f0fe; color: #1a73e8; }

/* 思考指示器 */
.om-thinking { display: flex; align-items: center; gap: 4px; padding: 4px 0; }
.dot { width: 5px; height: 5px; border-radius: 50%; background: #1a73e8; animation: dot-bounce 1.4s ease-in-out infinite; }
.dot:nth-child(2) { animation-delay: 0.16s; }
.dot:nth-child(3) { animation-delay: 0.32s; }
.thinking-label { font-size: 11px; color: #5f6368; margin-left: 4px; }
@keyframes dot-bounce { 0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }

/* 思维链折叠 */
.om-think-panel { margin-bottom: 6px; border-radius: 6px; border: 1px solid #e8eaed; background: #fafbfc; overflow: hidden; }
.think-toggle {
  display: flex; align-items: center; gap: 4px; border: none; background: transparent;
  cursor: pointer; font-size: 11px; color: #5f6368; padding: 4px 8px; width: 100%;
}
.think-toggle:hover { background: #f0f4f9; }
.think-toggle svg { transition: transform 0.2s; }
.think-toggle svg.rotated { transform: rotate(90deg); }
.think-content { padding: 4px 8px 8px; font-size: 11px; color: #5f6368; line-height: 1.5; border-top: 1px solid #e8eaed; max-height: 120px; overflow-y: auto; }

.om-caret { display: inline-block; width: 2px; height: 16px; background: #1e1e1e; margin-left: 2px; vertical-align: text-bottom; animation: caret-blink 1s steps(1) infinite; }
@keyframes caret-blink { 50% { opacity: 0; } }

/* Markdown */
.md-body :deep(h1) { font-size: 1.3em; margin: 8px 0 4px; font-weight: 600; }
.md-body :deep(h2) { font-size: 1.15em; margin: 6px 0 4px; font-weight: 600; }
.md-body :deep(h3) { font-size: 1.05em; margin: 4px 0; font-weight: 600; }
.md-body :deep(p) { margin: 3px 0; }
.md-body :deep(ul), .md-body :deep(ol) { padding-left: 18px; margin: 3px 0; }
.md-body :deep(li) { margin: 1px 0; }
.md-body :deep(code) { background: #f0f4f9; padding: 1px 4px; border-radius: 3px; font-size: 12px; font-family: monospace; }
.md-body :deep(pre) { background: #1e1e1e; color: #d4d4d4; padding: 8px 10px; border-radius: 8px; overflow-x: auto; margin: 6px 0; font-size: 12px; line-height: 1.5; }
.md-body :deep(pre code) { background: transparent; padding: 0; color: inherit; }
.md-body :deep(table) { border-collapse: collapse; width: 100%; margin: 6px 0; }
.md-body :deep(th), .md-body :deep(td) { border: 1px solid #e3e3e3; padding: 4px 8px; text-align: left; font-size: 12px; }
.md-body :deep(th) { background: #f0f4f9; font-weight: 600; }
.md-body :deep(blockquote) { border-left: 3px solid #1a73e8; margin: 6px 0; padding: 3px 10px; color: #5f6368; }
.md-body :deep(a) { color: #1a73e8; text-decoration: none; }

/* ====== 输入区域 ====== */
.om-input-area { padding: 8px 12px 12px; flex-shrink: 0; }
.om-input-wrap {
  display: flex; align-items: flex-end;
  background: #f0f4f9; border: 1px solid #e3e3e3; border-radius: 14px;
  transition: all 0.2s;
}
.om-input-wrap:focus-within { background: #fff; border-color: #1a73e8; box-shadow: 0 1px 4px rgba(26,115,232,0.2); }
.om-input-wrap textarea {
  flex: 1; box-sizing: border-box; padding: 10px 6px 10px 12px;
  background: transparent; border: none; font-size: 13px; outline: none;
  color: #1e1e1e; font-family: inherit; resize: none; max-height: 160px; line-height: 1.5;
}
.om-input-wrap textarea::placeholder { color: #9aa0a6; }
.om-input-wrap textarea:disabled { opacity: 0.5; }

.om-send-btn, .om-stop-btn {
  width: 30px; height: 30px; border: none; border-radius: 8px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  margin: 3px 4px 3px 0; flex-shrink: 0; transition: all 0.2s;
}
.om-send-btn { background: #1a73e8; color: #fff; }
.om-send-btn:hover { background: #1557b0; }
.om-send-btn:disabled { background: #c4c7c5; cursor: default; }
.om-stop-btn { background: #ea4335; color: #fff; }
.om-stop-btn:hover { background: #c5221f; }

/* ====== 深色模式 ====== */
.dark-mode .omnitrix-panel { box-shadow: 0 8px 32px rgba(0,0,0,0.4); }
.dark-mode .omnitrix-layout { background: #131314; }
.dark-mode .om-sidebar { color: #ccc; }
.dark-mode .om-sb-nav-item { color: #ccc; }
.dark-mode .om-sb-nav-item:hover { background: #222; }
.dark-mode .om-sb-conv:hover { background: #222; }
.dark-mode .om-sb-conv.active { background: #333; }
.dark-mode .conv-title { color: #eee; }
.dark-mode .om-sb-section-title { color: #666; }
.dark-mode .om-logo-group { background: linear-gradient(#131314, #131314) padding-box, linear-gradient(135deg, #58B6F4, #72F17E) border-box; }
.dark-mode .om-logo-group svg { color: #fff; }

.dark-mode .om-white-box { background: #1E1F20; border-color: #333; }
.dark-mode .om-title { color: #fff; }
.dark-mode .om-title-logo { color: #fff; }
.dark-mode .om-ctrl { border-color: #1E1F20; }

.dark-mode .om-welcome-text { color: #aaa; }
.dark-mode .wl-path { fill: #fff; }
.dark-mode .om-quick button { background: #1E1F20; border-color: #555; color: #e0e0e0; }
.dark-mode .om-quick button:hover { background: #333; color: #8ab4f8; border-color: #8ab4f8; }

.dark-mode .om-msg.user .om-bubble { background: #1E1F20; color: #e0e0e0; }
.dark-mode .om-msg.assistant .om-bubble { background: #131314; border-color: #333; color: #e0e0e0; }
.dark-mode .om-caret { background: #fff; }
.dark-mode .om-agent { color: #aaa; background: #1E1F20; }
.dark-mode .om-model { color: #666; background: #1E1F20; }
.dark-mode .om-think-panel { border-color: #333; background: #1a1a1b; }
.dark-mode .think-toggle { color: #aaa; }
.dark-mode .think-content { color: #888; border-top-color: #333; }
.dark-mode .dot { background: #8ab4f8; }
.dark-mode .thinking-label { color: #888; }
.dark-mode .act-btn { color: #888; }
.dark-mode .act-btn:hover { background: #333; color: #8ab4f8; }

.dark-mode .om-input-wrap { background: #1E1F20; border-color: #555; }
.dark-mode .om-input-wrap:focus-within { background: #2a2a2b; border-color: #1a73e8; }
.dark-mode .om-input-wrap textarea { color: #fff; }
.dark-mode .om-input-wrap textarea::placeholder { color: #888; }

.dark-mode .md-body :deep(code) { background: #2a2a2b; color: #e0e0e0; }
.dark-mode .md-body :deep(pre) { background: #0d0d0d; }
.dark-mode .md-body :deep(th) { background: #1E1F20; }
.dark-mode .md-body :deep(th), .dark-mode .md-body :deep(td) { border-color: #444; }
.dark-mode .md-body :deep(blockquote) { border-left-color: #8ab4f8; color: #aaa; }
.dark-mode .md-body :deep(a) { color: #8ab4f8; }

/* ====== 响应式 ====== */
@media (max-width: 768px) {
  .omnitrix-panel { width: calc(100vw - 16px); height: calc(100vh - 80px); bottom: 8px; right: 8px; border-radius: 16px; }
  .om-sidebar { width: 160px; }
}
</style>
