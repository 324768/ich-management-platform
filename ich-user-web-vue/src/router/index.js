import { createRouter, createWebHistory } from 'vue-router'

const AIChat = () => import('../views/omnitrix/AIChat.vue')
const SmartAnalysis = () => import('../views/omnitrix/SmartAnalysis.vue')

const routes = [
  {
    path: '/',
    name: 'home',
    component: {
      template: '<div style="padding:16px">欢迎使用 非物质文化遗产管理平台 用户端（示例主页）。</div>'
    }
  },
  {
    path: '/omnitrix/chat',
    name: 'omnitrix-chat',
    component: AIChat
  },
  {
    path: '/omnitrix/analysis',
    name: 'omnitrix-analysis',
    component: SmartAnalysis
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router



