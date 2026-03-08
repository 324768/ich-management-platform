import request from './index'

export function listCart(userId) {
  return request.get('/cart/list', { params: { userId } })
}

export function addToCart(userId, productId, quantity = 1) {
  return request.post('/cart/add', null, { params: { userId, productId, quantity } })
}

export function updateQuantity(userId, productId, quantity) {
  return request.put('/cart/quantity', null, { params: { userId, productId, quantity } })
}

export function removeFromCart(userId, productId) {
  return request.delete('/cart/remove', { params: { userId, productId } })
}

export function clearCart(userId) {
  return request.delete('/cart/clear', { params: { userId } })
}

export function checkItem(userId, productId, checked) {
  return request.put('/cart/check', null, { params: { userId, productId, checked } })
}

export function checkAll(userId, checked) {
  return request.put('/cart/checkAll', null, { params: { userId, checked } })
}
