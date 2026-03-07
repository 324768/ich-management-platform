import request from './index'

// ========== 分类 ==========
export function getCategoryTree() {
  return request.get('/admin/content/category/tree')
}
export function getCategoryList(parentId) {
  return request.get('/admin/content/category/list', { params: { parentId } })
}
export function getCategory(id) {
  return request.get(`/admin/content/category/${id}`)
}
export function addCategory(data) {
  return request.post('/admin/content/category/add', data)
}
export function updateCategory(data) {
  return request.put('/admin/content/category/update', data)
}
export function deleteCategory(id) {
  return request.delete(`/admin/content/category/${id}`)
}

// ========== 非遗项目 ==========
export function getItemList(params) {
  return request.get('/admin/content/item/list', { params })
}
export function getItem(id) {
  return request.get(`/admin/content/item/${id}`)
}
export function addItem(data) {
  return request.post('/admin/content/item/add', data)
}
export function updateItem(data) {
  return request.put('/admin/content/item/update', data)
}
export function deleteItem(id) {
  return request.delete(`/admin/content/item/${id}`)
}
export function updateItemStatus(id, status) {
  return request.put('/admin/content/item/status', null, { params: { id, status } })
}

// ========== 传承人 ==========
export function getHeritageManList(params) {
  return request.get('/admin/content/heritage/list', { params })
}
export function getHeritageMan(id) {
  return request.get(`/admin/content/heritage/${id}`)
}
export function addHeritageMan(data) {
  return request.post('/admin/content/heritage/add', data)
}
export function updateHeritageMan(data) {
  return request.put('/admin/content/heritage/update', data)
}
export function deleteHeritageMan(id) {
  return request.delete(`/admin/content/heritage/${id}`)
}
