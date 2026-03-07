import request from './index'

export function getVideoExhibitionList(status) {
  return request.get('/admin/video/exhibition/list', { params: { status } })
}

export function getVideoExhibition(id) {
  return request.get(`/admin/video/exhibition/${id}`)
}

export function addVideoExhibition(data) {
  return request.post('/admin/video/exhibition/add', data)
}

export function updateVideoExhibition(data) {
  return request.put('/admin/video/exhibition/update', data)
}

export function deleteVideoExhibition(id) {
  return request.delete(`/admin/video/exhibition/${id}`)
}
