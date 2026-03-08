import request from './index'

export function getVideoExhibitionList() {
  return request.get('/video/exhibition/list')
}

export function getVideoExhibition(id) {
  return request.get(`/video/exhibition/${id}`)
}
