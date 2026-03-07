const TOKEN_KEY = 'ich_admin_token'
const ADMIN_KEY = 'ich_admin_info'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ADMIN_KEY)
}

export function getAdminInfo() {
  const info = localStorage.getItem(ADMIN_KEY)
  return info ? JSON.parse(info) : null
}

export function setAdminInfo(info) {
  localStorage.setItem(ADMIN_KEY, JSON.stringify(info))
}
