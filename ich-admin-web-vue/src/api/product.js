import request from './index'

// ========== 商品分类 ==========
export function getProductCategoryTree() {
  return request.get('/admin/product/category/tree')
}
export function addProductCategory(data) {
  return request.post('/admin/product/category/add', data)
}
export function updateProductCategory(data) {
  return request.put('/admin/product/category/update', data)
}
export function getProductCategory(id) {
  return request.get(`/admin/product/category/${id}`)
}
export function deleteProductCategory(id) {
  return request.delete(`/admin/product/category/${id}`)
}

// ========== 商品 ==========
export function getProductList(params) {
  return request.get('/admin/product/list', { params })
}
export function getProduct(id) {
  return request.get(`/admin/product/${id}`)
}
export function addProduct(data) {
  return request.post('/admin/product/add', data)
}
export function updateProduct(data) {
  return request.put('/admin/product/update', data)
}
export function deleteProduct(id) {
  return request.delete(`/admin/product/${id}`)
}
export function updateProductStatus(id, status) {
  return request.put('/admin/product/status', null, { params: { id, status } })
}
