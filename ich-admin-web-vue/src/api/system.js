import request from './index'

// ========== 管理员 ==========
export function getAdminList(params) {
  return request.get('/admin/system/admin/list', { params })
}
export function getAdmin(id) {
  return request.get(`/admin/system/admin/${id}`)
}
export function addAdmin(data, password) {
  return request.post('/admin/system/admin/add', data, { params: { password } })
}
export function updateAdmin(data) {
  return request.put('/admin/system/admin/update', data)
}
export function deleteAdmin(id) {
  return request.delete(`/admin/system/admin/${id}`)
}
export function updateAdminStatus(adminId, status) {
  return request.put('/admin/system/admin/status', null, { params: { adminId, status } })
}
export function assignRoles(adminId, roleIds) {
  return request.put('/admin/system/admin/roles', roleIds, { params: { adminId } })
}

// ========== 角色 ==========
export function getRoleList() {
  return request.get('/admin/system/role/list')
}
export function addRole(data) {
  return request.post('/admin/system/role/add', data)
}
export function updateRole(data) {
  return request.put('/admin/system/role/update', data)
}
export function deleteRole(id) {
  return request.delete(`/admin/system/role/${id}`)
}
