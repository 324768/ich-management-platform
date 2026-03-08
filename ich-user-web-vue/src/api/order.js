import request from './index'

export function createOrder(data) {
  return request.post('/order/create', data)
}

export function getOrder(orderNo) {
  return request.get(`/order/${orderNo}`)
}

export function listOrders(userId, params) {
  return request.get('/order/list', { params: { userId, ...params } })
}

export function payOrder(orderNo, payType = 1) {
  return request.post('/order/pay', null, { params: { orderNo, payType } })
}

export function cancelOrder(orderNo, userId) {
  return request.post('/order/cancel', null, { params: { orderNo, userId } })
}

export function confirmReceive(orderNo, userId) {
  return request.post('/order/confirm', null, { params: { orderNo, userId } })
}
