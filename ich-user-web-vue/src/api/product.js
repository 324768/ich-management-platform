import request from './index'

// ========== 商品分类 ==========
export function getProductCategoryTree() {
  return request.get('/product/category/tree')
}

// ========== 商品 ==========
export function getProductList(params) {
  return request.get('/product/list', { params })
}

export function getProduct(id) {
  return request.get(`/product/${id}`)
}
