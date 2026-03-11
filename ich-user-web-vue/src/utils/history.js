import { getUserInfo } from './token'
import request from '@/api/index'

const MAX_HISTORY = 100

/**
 * 记录浏览历史
 * @param {string} type - culture|activity|product|inheritor|video|article
 * @param {object} data - { targetId, title, image, desc }
 */
export function addBrowseHistory(type, data) {
  const user = getUserInfo()
  if (!user?.id || !data?.targetId) return
  const key = `ich_history_${user.id}_${type}`
  let list = []
  try { list = JSON.parse(localStorage.getItem(key) || '[]') } catch { list = [] }
  list = list.filter(item => item.targetId !== data.targetId)
  list.unshift({
    targetId: data.targetId,
    title: data.title || '',
    image: data.image || '',
    desc: data.desc || '',
    viewTime: new Date().toISOString()
  })
  if (list.length > MAX_HISTORY) list = list.slice(0, MAX_HISTORY)
  localStorage.setItem(key, JSON.stringify(list))

  // 同时保存到后端数据库，供AI查询使用
  try {
    request.post('/content/browse/add', {
      userId: user.id,
      targetType: type,
      targetId: data.targetId,
      targetTitle: data.title || '',
      source: 'web'
    }).catch(() => {})
  } catch (e) {}
}
