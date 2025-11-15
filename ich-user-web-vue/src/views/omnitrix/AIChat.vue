<script setup>
import { ref } from 'vue'

const input = ref('')
const messages = ref([{ role: 'assistant', text: '你好，我是 AI 助手。' }])

function send() {
  if (!input.value.trim()) return
  messages.value.push({ role: 'user', text: input.value })
  // 这里可调用后端 API，现用占位回复
  messages.value.push({ role: 'assistant', text: `收到：「${input.value}」` })
  input.value = ''
}
</script>

<template>
  <div class="ai-chat">
    <h2>AI 助手对话</h2>
    <div class="chat-box">
      <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        <b>{{ m.role === 'user' ? '我' : '助手' }}</b>：{{ m.text }}
      </div>
    </div>
    <div class="input-row">
      <input v-model="input" placeholder="输入你的问题..." @keyup.enter="send" />
      <button @click="send">发送</button>
    </div>
  </div>
  
</template>

<style scoped>
.ai-chat { padding: 16px; }
.chat-box { border: 1px solid #ddd; border-radius: 8px; padding: 12px; min-height: 200px; margin: 12px 0; }
.msg { margin: 6px 0; }
.msg.user { text-align: right; }
.input-row { display: flex; gap: 8px; }
input { flex: 1; padding: 8px; }
button { padding: 8px 12px; }
</style>



