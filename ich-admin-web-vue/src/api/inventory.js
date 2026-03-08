import request from './index'

// ========== 库存记录 ==========
export function getInventoryRecordList(params) {
  return request.get('/admin/inventory/record/list', { params })
}
export function addInventoryRecord(data) {
  return request.post('/admin/inventory/record/add', data)
}
export function deleteInventoryRecord(id) {
  return request.delete(`/admin/inventory/record/${id}`)
}
