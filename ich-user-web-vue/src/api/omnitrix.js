// 简单占位 API 封装，后续可替换为 axios 并抽取 baseURL

export async function chat(messages) {
  // TODO: 调用后端 /api/omnitrix/chat
  return { reply: `Echo: ${messages[messages.length - 1]?.text || ''}` }
}

export async function analyze(content) {
  // TODO: 调用后端 /api/omnitrix/analyze
  return { summary: `长度：${(content || '').length}` }
}



