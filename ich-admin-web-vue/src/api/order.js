import request from './index'

export function getOrderList(params) {
  return request.get('/admin/order/list', { params })
}

export function getOrder(orderNo) {
  return request.get(`/admin/order/${orderNo}`)
}

export function shipOrder(orderNo) {
  return request.put('/admin/order/ship', null, { params: { orderNo } })
}

export function updateOrderStatus(orderNo, status) {
  return request.put('/admin/order/status', null, { params: { orderNo, status } })
}
