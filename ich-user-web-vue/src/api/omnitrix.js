import request from './index'
import { getToken } from '@/utils/token'

/** 同步聊天 */
export function chat(sessionId, message, userId) {
  return request.post('/ai/chat', { sessionId, message }, { params: { userId }, timeout: 60000 })
}

/** SSE 流式聊天 — 返回 EventSource */
export function chatStream(sessionId, message, userId) {
  const token = getToken()
  const params = new URLSearchParams({ sessionId, message, userId: userId || '1' })
  if (token) {
    params.append('userToken', token)
  }
  return new EventSource(`/api/ai/chat/stream?${params.toString()}`)
}

/** 创建对话 */
export function createConversation(userId, title) {
  return request.post('/ai/conversation/create', title ? { title } : {}, { params: { userId } })
}

/** 获取对话列表 */
export function listConversations(userId) {
  return request.get('/ai/conversation/list', { params: { userId } })
}

/** 获取对话详情 */
export function getConversation(id, userId) {
  return request.get(`/ai/conversation/${id}`, { params: { userId } })
}

/** 删除对话 */
export function deleteConversation(id, userId) {
  return request.delete(`/ai/conversation/${id}`, { params: { userId } })
}

/** 用户反馈：点赞/踩 */
export function sendFeedback(messageId, feedback) {
  return request.post(`/ai/feedback/${messageId}`, null, { params: { feedback } })
}

/** 重新生成（流式） — 返回 EventSource */
export function regenerateStream(sessionId, userId) {
  const token = getToken()
  const params = new URLSearchParams({ sessionId, userId: userId || '1' })
  if (token) {
    params.append('userToken', token)
  }
  return new EventSource(`/api/ai/chat/regenerate?${params.toString()}`)
}

/** 导出对话为 Markdown */
export function exportConversation(id, userId) {
  return request.get(`/ai/conversation/${id}/export`, { params: { userId } })
}
