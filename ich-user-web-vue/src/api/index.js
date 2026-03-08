import axios from 'axios'
import { getToken, removeToken } from '@/utils/token'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      if (res.code === 401) {
        removeToken()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    if (error.response?.status === 401) {
      removeToken()
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default request
