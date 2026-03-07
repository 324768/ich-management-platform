import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken, getAdminInfo } from '@/utils/token'

NProgress.configure({ showSpinner: false })

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: 'Login' }
  },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: 'Dashboard', icon: 'Odometer' }
      },
      {
        path: 'user',
        name: 'UserManage',
        component: () => import('@/views/user/UserList.vue'),
        meta: { title: 'User Management', icon: 'User' }
      },
      {
        path: 'content/category',
        name: 'ContentCategory',
        component: () => import('@/views/content/CategoryList.vue'),
        meta: { title: 'ICH Categories', icon: 'Collection', parent: 'ICH Content' }
      },
      {
        path: 'content/item',
        name: 'ContentItem',
        component: () => import('@/views/content/ItemList.vue'),
        meta: { title: 'ICH Items', icon: 'Document', parent: 'ICH Content' }
      },
      {
        path: 'content/item/:id',
        name: 'ContentItemDetail',
        component: () => import('@/views/content/ItemDetail.vue'),
        meta: { title: 'ICH Item Detail', parent: 'ICH Content' }
      },
      {
        path: 'content/heritage',
        name: 'HeritageMan',
        component: () => import('@/views/content/HeritageManList.vue'),
        meta: { title: 'Heritage Bearers', icon: 'Avatar', parent: 'ICH Content' }
      },
      {
        path: 'content/heritage/:id',
        name: 'HeritageManDetail',
        component: () => import('@/views/content/HeritageManDetail.vue'),
        meta: { title: 'Heritage Bearer Detail', parent: 'ICH Content' }
      },
      {
        path: 'product/category',
        name: 'ProductCategory',
        component: () => import('@/views/product/ProductCategoryList.vue'),
        meta: { title: 'Product Categories', icon: 'Grid', parent: 'Products' }
      },
      {
        path: 'product/list',
        name: 'ProductList',
        component: () => import('@/views/product/ProductList.vue'),
        meta: { title: 'Product List', icon: 'ShoppingBag', parent: 'Products' }
      },
      {
        path: 'product/:id',
        name: 'ProductDetail',
        component: () => import('@/views/product/ProductDetail.vue'),
        meta: { title: 'Product Detail', parent: 'Products' }
      },
      {
        path: 'order',
        name: 'OrderManage',
        component: () => import('@/views/order/OrderList.vue'),
        meta: { title: 'Order Management', icon: 'List' }
      },
      {
        path: 'system/admin',
        name: 'AdminManage',
        component: () => import('@/views/system/AdminList.vue'),
        meta: { title: 'Administrators', icon: 'UserFilled', parent: 'System' }
      },
      {
        path: 'system/role',
        name: 'RoleManage',
        component: () => import('@/views/system/RoleList.vue'),
        meta: { title: 'Roles', icon: 'Key', parent: 'System' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  const token = getToken()
  if (to.path === '/login') {
    token ? next('/dashboard') : next()
  } else if (!token) {
    next('/login')
  } else {
    // Fetch admin info if not loaded yet
    if (!getAdminInfo()) {
      try {
        const { useAuthStore } = await import('@/store/auth')
        const authStore = useAuthStore()
        await authStore.fetchAdminInfo()
      } catch (e) { /* ignore */ }
    }
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
