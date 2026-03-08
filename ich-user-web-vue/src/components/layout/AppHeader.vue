<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getUserInfo as getStoredUser, removeToken } from '@/utils/token'

const router = useRouter()
const route = useRoute()

const navItems = [
  { name: '首页', path: '/' },
  { name: '非遗文化', path: '/culture' },
  { name: '非遗活动', path: '/activity' },
  { name: '周边商城', path: '/shop' },
  { name: '传承人', path: '/inheritor' },
  { name: '非遗视频', path: '/video' },
  { name: '文章资讯', path: '/article' },
  { name: '系统通知', path: '/notification' },
]

const user = computed(() => {
  return getStoredUser()
})

const isActive = (path) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

const handleLogout = () => {
  removeToken()
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <div class="header-left">
        <router-link to="/" class="logo">
          <svg class="logo-icon" viewBox="0 0 40 40" fill="none">
            <path d="M20 4L8 12v16l12 8 12-8V12L20 4z" stroke="#8B2020" stroke-width="2" fill="none"/>
            <path d="M20 10L12 15v10l8 5 8-5V15l-8-5z" stroke="#8B2020" stroke-width="1.5" fill="rgba(139,32,32,0.1)"/>
            <circle cx="20" cy="20" r="4" fill="#8B2020"/>
            <path d="M20 4v6M20 30v6M4.5 12L12 15M28 25l7.5 3M4.5 28L12 25M28 15l7.5-3" stroke="#8B2020" stroke-width="1" opacity="0.5"/>
          </svg>
        </router-link>
        <nav class="main-nav">
          <router-link v-for="item in navItems" :key="item.path"
                       :to="item.path"
                       :class="['nav-item', { active: isActive(item.path) }]">
            {{ item.name }}
          </router-link>
        </nav>
      </div>
      <div class="header-right" v-if="user">
        <a href="javascript:void(0)" class="header-action" @click="handleLogout">退出</a>
        <a href="javascript:void(0)" class="header-action">联系客服</a>
        <router-link to="/user" class="header-action">我的购物车</router-link>
        <router-link to="/user" class="user-avatar-link">
          <div class="user-avatar" v-if="user.avatar" :style="{ backgroundImage: `url(${user.avatar})` }"></div>
          <div class="user-avatar user-avatar-letter" v-else>{{ (user.nickname || user.username || '?')[0] }}</div>
        </router-link>
      </div>
      <div class="header-right" v-else>
        <router-link to="/login" class="header-action">登录</router-link>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--ich-white);
  border-bottom: 1px solid var(--ich-border);
  box-shadow: var(--ich-shadow-sm);
}

.header-inner {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: var(--ich-header-height);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.logo {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.logo-icon {
  width: 40px;
  height: 40px;
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-item {
  padding: 8px 14px;
  font-size: 14px;
  color: var(--ich-text-primary);
  border-radius: var(--ich-radius-sm);
  transition: var(--ich-transition);
  white-space: nowrap;
  text-decoration: none;
}

.nav-item:hover {
  color: var(--ich-primary);
  background: var(--ich-primary-bg);
}

.nav-item.active {
  color: var(--ich-primary);
  font-weight: 600;
  position: relative;
}

.nav-item.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 60%;
  height: 2px;
  background: var(--ich-primary);
  border-radius: 1px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.header-action {
  font-size: 13px;
  color: var(--ich-text-secondary);
  text-decoration: none;
  cursor: pointer;
  transition: var(--ich-transition);
}

.header-action:hover {
  color: var(--ich-primary);
}

.user-avatar-link {
  text-decoration: none;
  display: flex;
  align-items: center;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-size: cover;
  background-position: center;
  border: 2px solid var(--ich-border);
  transition: var(--ich-transition);
}

.user-avatar-letter {
  background: linear-gradient(135deg, var(--ich-primary), #6B3A6B);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar-link:hover .user-avatar {
  border-color: var(--ich-primary);
  box-shadow: 0 0 0 3px rgba(139, 32, 32, 0.15);
}

@media (max-width: 1100px) {
  .main-nav { gap: 0; }
  .nav-item { padding: 8px 8px; font-size: 13px; }
}
</style>
