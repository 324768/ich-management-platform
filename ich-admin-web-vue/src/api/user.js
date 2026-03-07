import request from './index'

export function getUserList(params) {
  return request.get('/admin/user/list', { params })
}

export function getUser(id) {
  return request.get(`/admin/user/${id}`)
}

export function updateUserStatus(userId, status) {
  return request.put('/admin/user/status', null, { params: { userId, status } })
}
