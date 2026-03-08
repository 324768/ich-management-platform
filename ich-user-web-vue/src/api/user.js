import request from './index'

export function register(data) {
  return request.post('/user/register', data)
}

export function login(data) {
  return request.post('/user/login', data)
}

export function getUserInfo(id) {
  return request.get(`/user/${id}`)
}

export function updateUser(data) {
  return request.put('/user/update', data)
}

export function updatePassword(userId, oldPassword, newPassword) {
  return request.put('/user/password', null, { params: { userId, oldPassword, newPassword } })
}

// ========== 收货地址 ==========
export function listAddresses(userId) {
  return request.get('/user/address/list', { params: { userId } })
}

export function addAddress(data) {
  return request.post('/user/address/add', data)
}

export function updateAddress(data) {
  return request.put('/user/address/update', data)
}

export function deleteAddress(userId, addressId) {
  return request.delete('/user/address/delete', { params: { userId, addressId } })
}

export function setDefaultAddress(userId, addressId) {
  return request.put('/user/address/default', null, { params: { userId, addressId } })
}
