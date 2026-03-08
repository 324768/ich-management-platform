import request from './index'

export function getNotificationList(params) {
  return request.get('/notification/list', { params })
}

export function getNotification(id) {
  return request.get(`/notification/${id}`)
}
