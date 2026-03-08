import request from './index'

// ========== 系统通知 ==========
export function getNotificationList(params) {
  return request.get('/admin/notification/list', { params })
}
export function getNotification(id) {
  return request.get(`/admin/notification/${id}`)
}
export function addNotification(data) {
  return request.post('/admin/notification/add', data)
}
export function updateNotification(data) {
  return request.put('/admin/notification/update', data)
}
export function deleteNotification(id) {
  return request.delete(`/admin/notification/${id}`)
}
export function publishNotification(id) {
  return request.put(`/admin/notification/publish/${id}`)
}
