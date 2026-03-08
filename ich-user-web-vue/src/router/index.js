import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../components/layout/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { noLayout: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/Register.vue'),
    meta: { noLayout: true }
  },
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', name: 'home', component: () => import('../views/Home.vue') },
      { path: 'culture', name: 'culture', component: () => import('../views/culture/CultureList.vue') },
      { path: 'culture/:id', name: 'culture-detail', component: () => import('../views/culture/CultureDetail.vue') },
      { path: 'activity', name: 'activity', component: () => import('../views/activity/ActivityList.vue') },
      { path: 'activity/:id', name: 'activity-detail', component: () => import('../views/activity/ActivityDetail.vue') },
      { path: 'shop', name: 'shop', component: () => import('../views/shop/ShopIndex.vue') },
      { path: 'shop/product/:id', name: 'product-detail', component: () => import('../views/shop/ProductDetail.vue') },
      { path: 'inheritor', name: 'inheritor', component: () => import('../views/inheritor/InheritorList.vue') },
      { path: 'inheritor/:id', name: 'inheritor-detail', component: () => import('../views/inheritor/InheritorDetail.vue') },
      { path: 'video', name: 'video', component: () => import('../views/video/VideoList.vue') },
      { path: 'video/:id', name: 'video-detail', component: () => import('../views/video/VideoDetail.vue') },
      { path: 'article', name: 'article', component: () => import('../views/article/ArticleList.vue') },
      { path: 'notification', name: 'notification', component: () => import('../views/notification/NotificationList.vue') },
      { path: 'user', name: 'user', component: () => import('../views/user/UserCenter.vue') },
      { path: 'user/cart', name: 'user-cart', component: () => import('../views/user/UserCart.vue') },
      { path: 'user/orders', name: 'user-orders', component: () => import('../views/user/UserOrders.vue') },
      { path: 'user/activity-records', name: 'user-activity-records', component: () => import('../views/user/UserActivityRecords.vue') },
      { path: 'user/history', name: 'user-history', component: () => import('../views/user/UserHistory.vue') },
      { path: 'user/address', name: 'user-address', component: () => import('../views/user/UserAddress.vue') },
      { path: 'user/password', name: 'user-password', component: () => import('../views/user/UserPassword.vue') },
      { path: 'omnitrix/chat', name: 'omnitrix-chat', component: () => import('../views/omnitrix/AIChat.vue') },
      { path: 'omnitrix/analysis', name: 'omnitrix-analysis', component: () => import('../views/omnitrix/SmartAnalysis.vue') },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('ich_user_token')
  if (!to.meta.noLayout && !token && to.name !== 'home') {
    next({ name: 'login' })
  } else {
    next()
  }
})

export default router



