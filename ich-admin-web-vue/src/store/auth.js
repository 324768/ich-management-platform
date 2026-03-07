import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getAdminInfo as getAdminInfoApi } from '@/api/auth'
import { getToken, setToken, removeToken, getAdminInfo, setAdminInfo } from '@/utils/token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken() || '')
  const adminInfo = ref(getAdminInfo() || {})

  async function login(loginForm) {
    const res = await loginApi(loginForm)
    token.value = res.data
    setToken(res.data)
    return res
  }

  async function fetchAdminInfo() {
    const res = await getAdminInfoApi()
    adminInfo.value = res.data
    setAdminInfo(res.data)
    return res.data
  }

  function logout() {
    token.value = ''
    adminInfo.value = {}
    removeToken()
  }

  return { token, adminInfo, login, fetchAdminInfo, logout }
})
