import request from './index'

export function login(data) {
  return request.post('/admin/auth/login', data)
}

export function getAdminInfo() {
  return request.get('/admin/auth/info')
}
