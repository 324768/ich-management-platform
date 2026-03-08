import request from './index'

// ========== 分类 ==========
export function getCategoryTree() {
  return request.get('/content/category/tree')
}

export function getCategoryList(parentId) {
  return request.get('/content/category/list', { params: { parentId } })
}

// ========== 非遗项目 ==========
export function getItemList(params) {
  return request.get('/content/item/list', { params })
}

export function getItem(id) {
  return request.get(`/content/item/${id}`)
}

// ========== 传承人 ==========
export function getHeritageManList(params) {
  return request.get('/content/heritage/list', { params })
}

export function getHeritageMan(id) {
  return request.get(`/content/heritage/${id}`)
}

// ========== 活动 ==========
export function getActivityList(params) {
  return request.get('/content/activity/list', { params })
}

export function getActivity(id) {
  return request.get(`/content/activity/${id}`)
}

export function registerActivity(data) {
  return request.post('/content/activity/register', data)
}

// ========== 活动评论 ==========
export function getActivityComments(activityId, params) {
  return request.get('/content/activity/comment/list', { params: { activityId, ...params } })
}

export function addActivityComment(data) {
  return request.post('/content/activity/comment/add', data)
}

// ========== 活动浏览记录 ==========
export function addActivityViewLog(data) {
  return request.post('/content/activity/viewlog/add', data)
}
