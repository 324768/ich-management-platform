<script setup>
import { ref } from 'vue'
import { chat } from '@/api/omnitrix'

const content = ref('')
const result = ref('')
const loading = ref(false)

async function analyze() {
  const text = content.value.trim()
  if (!text) return
  loading.value = true
  result.value = ''
  try {
    const prompt = `请对以下内容进行智能分析，包括主题概述、关键信息提取和分析建议：\n\n${text}`
    const res = await chat('analysis_' + Date.now(), prompt, localStorage.getItem('ich_user_id') || '1')
    result.value = res.data?.reply || res.data?.answer || '分析完成'
  } catch (e) {
    result.value = '分析失败：' + (e.message || '请稍后重试')
  }
  loading.value = false
}

function renderResult(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}
</script>

<template>
  <div class="smart-analysis">
    <h2>智能分析</h2>
    <p class="desc">粘贴非遗相关文本或内容，AI 将为你提取关键信息并生成分析报告。</p>
    <textarea v-model="content" rows="8" placeholder="在此粘贴需要分析的内容..." />
    <div class="actions">
      <button @click="analyze" :disabled="loading || !content.trim()">
        {{ loading ? '分析中...' : '开始分析' }}
      </button>
    </div>
    <div v-if="result" class="result">
      <h3>分析结果</h3>
      <div class="result-text" v-html="renderResult(result)"></div>
    </div>
  </div>
</template>

<style scoped>
.smart-analysis { padding: 24px; max-width: 800px; margin: 0 auto; }
h2 { font-size: 20px; font-weight: 600; color: #1f2937; margin: 0 0 8px; }
.desc { color: #6b7280; font-size: 14px; margin: 0 0 16px; }
textarea { width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; resize: vertical; outline: none; }
textarea:focus { border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,.1); }
.actions { margin: 16px 0; }
button { padding: 10px 24px; background: #6366f1; color: #fff; border: none; border-radius: 8px; font-size: 14px; cursor: pointer; }
button:hover:not(:disabled) { background: #4f46e5; }
button:disabled { opacity: .5; cursor: not-allowed; }
.result { background: #fff; border: 1px solid #e5e7eb; padding: 20px; border-radius: 8px; margin-top: 8px; }
.result h3 { margin: 0 0 12px; font-size: 16px; color: #1f2937; }
.result-text { font-size: 14px; line-height: 1.7; color: #374151; }
</style>



