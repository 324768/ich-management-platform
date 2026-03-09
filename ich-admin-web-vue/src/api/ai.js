import request from './index'

// ========== Dashboard ==========
export function getAiDashboard() {
  return request.get('/admin/ai/dashboard')
}

export function getAiTraces(params) {
  return request.get('/admin/ai/traces', { params })
}

export function getAiConversations(params) {
  return request.get('/admin/ai/conversations', { params })
}

export function getAiConversation(id) {
  return request.get(`/admin/ai/conversations/${id}`)
}

// ========== Knowledge Base ==========
export function listKnowledge(params) {
  return request.get('/admin/ai/config/knowledge/list', { params })
}

export function saveKnowledge(data) {
  return request.post('/admin/ai/config/knowledge/save', data)
}

export function deleteKnowledge(id) {
  return request.delete(`/admin/ai/config/knowledge/${id}`)
}

// ========== Prompt Config ==========
export function listPrompts(params) {
  return request.get('/admin/ai/config/prompt/list', { params })
}

export function savePrompt(data) {
  return request.post('/admin/ai/config/prompt/save', data)
}

export function deletePrompt(id) {
  return request.delete(`/admin/ai/config/prompt/${id}`)
}

// ========== Agent Config ==========
export function listAgents(params) {
  return request.get('/admin/ai/agent/list', { params })
}

export function getAgent(agentCode) {
  return request.get(`/admin/ai/agent/${agentCode}`)
}

export function saveAgent(data) {
  return request.post('/admin/ai/agent/save', data)
}

export function deleteAgent(id) {
  return request.delete(`/admin/ai/agent/${id}`)
}

export function changeAgentStatus(data) {
  return request.put('/admin/ai/agent/changeStatus', data)
}

export function getRegisteredAgents() {
  return request.get('/admin/ai/agent/registered')
}
