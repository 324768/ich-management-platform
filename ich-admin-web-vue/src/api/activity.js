import request from './index'

// ========== 非遗活动 ==========
export function getActivityList(params) {
  return request.get('/admin/activity/list', { params })
}
export function getActivity(id) {
  return request.get(`/admin/activity/${id}`)
}
export function addActivity(data) {
  return request.post('/admin/activity/add', data)
}
export function updateActivity(data) {
  return request.put('/admin/activity/update', data)
}
export function deleteActivity(id) {
  return request.delete(`/admin/activity/${id}`)
}
export function updateActivityStatus(id, status) {
  return request.put('/admin/activity/status', null, { params: { id, status } })
}

// ========== 活动报名记录 ==========
export function getActivityRecordList(params) {
  return request.get('/admin/activity/record/list', { params })
}
export function getActivityRecordsByActivityId(activityId, params) {
  return request.get('/admin/activity/record/list', { params: { ...params, activityId } })
}
export function updateRecordStatus(id, status) {
  return request.put('/admin/activity/record/status', null, { params: { id, status } })
}
export function deleteActivityRecord(id) {
  return request.delete(`/admin/activity/record/${id}`)
}

// ========== 活动评论 ==========
export function getActivityComments(activityId, params) {
  return request.get('/admin/activity/comment/list', { params: { activityId, ...params } })
}
export function addActivityComment(data) {
  return request.post('/admin/activity/comment/add', data)
}
export function deleteActivityComment(id) {
  return request.delete(`/admin/activity/comment/${id}`)
}

// ========== 活动审批 ==========
export function getApprovalList(params) {
  return request.get('/admin/activity/approval/list', { params })
}
export function reviewActivity(params) {
  return request.put('/admin/activity/approval/review', null, { params })
}

// ========== 活动浏览记录 ==========
export function getActivityViewLogs(activityId, params) {
  return request.get('/admin/activity/viewlog/list', { params: { activityId, ...params } })
}
export function addActivityViewLog(data) {
  return request.post('/admin/activity/viewlog/add', data)
}
