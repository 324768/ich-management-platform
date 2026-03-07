import request from './index'

export function getDashboardStats() {
  return request.get('/admin/dashboard/stats')
}

export function getRecentOrders() {
  return request.get('/admin/dashboard/recent-orders')
}

export function getWeeklyTrend() {
  return request.get('/admin/dashboard/weekly-trend')
}
