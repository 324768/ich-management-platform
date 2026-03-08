import api from './index'

export function getQualificationList(params) {
  return api.get('/admin/qualification/list', { params })
}

export function reviewQualification(params) {
  return api.put('/admin/qualification/review', null, { params })
}

export function checkUserHeritage(userId) {
  return api.get(`/admin/qualification/check/${userId}`)
}
