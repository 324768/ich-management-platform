const TOKEN_KEY = 'ich_user_token'
const USER_KEY = 'ich_user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getUserInfo() {
  const info = localStorage.getItem(USER_KEY)
  return info ? JSON.parse(info) : null
}

export function setUserInfo(info) {
  localStorage.setItem(USER_KEY, JSON.stringify(info))
}
