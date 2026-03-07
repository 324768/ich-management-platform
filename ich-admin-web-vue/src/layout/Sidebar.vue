<template>
  <div class="sidebar">
    <div class="sidebar-logo">
      <div class="logo-icon">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
          <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="#60A5FA"/>
          <path d="M2 17L12 22L22 17" stroke="#3B82F6" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M2 12L12 17L22 12" stroke="#93C5FD" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <transition name="logo-fade">
        <span v-show="!collapsed" class="logo-text">ICH Platform</span>
      </transition>
    </div>

    <div class="sidebar-section" v-show="!collapsed">
      <span class="section-label">MAIN</span>
    </div>

    <el-scrollbar class="sidebar-menu-wrap">
      <el-menu
        :default-active="currentPath"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="transparent"
        text-color="rgba(203,213,225,0.8)"
        active-text-color="#FFFFFF"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>Dashboard</template>
        </el-menu-item>

        <el-menu-item index="/user">
          <el-icon><User /></el-icon>
          <template #title>Users</template>
        </el-menu-item>

        <el-sub-menu index="content">
          <template #title>
            <el-icon><Collection /></el-icon>
            <span>ICH Content</span>
          </template>
          <el-menu-item index="/content/category">Categories</el-menu-item>
          <el-menu-item index="/content/item">Heritage Items</el-menu-item>
          <el-menu-item index="/content/heritage">Heritage Bearers</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="product">
          <template #title>
            <el-icon><ShoppingBag /></el-icon>
            <span>Products</span>
          </template>
          <el-menu-item index="/product/category">Categories</el-menu-item>
          <el-menu-item index="/product/list">Product List</el-menu-item>
          <el-menu-item index="/product/stock-alert">Stock Alert</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/video/exhibition">
          <el-icon><VideoCamera /></el-icon>
          <template #title>Video Exhibition</template>
        </el-menu-item>

        <el-menu-item index="/order">
          <el-icon><List /></el-icon>
          <template #title>Orders</template>
        </el-menu-item>

        <div class="sidebar-section menu-section" v-show="!collapsed">
          <span class="section-label">SETTINGS</span>
        </div>

        <el-sub-menu index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>System</span>
          </template>
          <el-menu-item index="/system/admin">Administrators</el-menu-item>
          <el-menu-item index="/system/role">Roles</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-scrollbar>

    <div class="sidebar-footer" v-show="!collapsed">
      <div class="footer-badge">
        <div class="badge-dot"></div>
        <span>System Online</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

defineProps({ collapsed: Boolean })
const route = useRoute()
const currentPath = computed(() => route.path)
</script>

<style lang="scss" scoped>
.sidebar {
  height: 100vh;
  background: linear-gradient(180deg, #0F172A 0%, #1E293B 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;

  &::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 1px;
    height: 100%;
    background: linear-gradient(180deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.01) 100%);
  }
}

.sidebar-logo {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  flex-shrink: 0;

  .logo-icon {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    background: rgba(37, 99, 235, 0.12);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .logo-text {
    font-size: 17px;
    font-weight: 700;
    color: #F8FAFC;
    letter-spacing: -0.03em;
    white-space: nowrap;
  }
}

.logo-fade-enter-active,
.logo-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.logo-fade-enter-from,
.logo-fade-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}

.sidebar-section {
  padding: 20px 20px 8px;

  .section-label {
    font-size: 10px;
    font-weight: 700;
    color: rgba(148, 163, 184, 0.4);
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  &.menu-section {
    padding: 16px 20px 8px;
  }
}

.sidebar-menu-wrap {
  flex: 1;
  overflow: hidden;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  flex-shrink: 0;
}

.footer-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: rgba(16, 185, 129, 0.08);
  border-radius: 10px;
  border: 1px solid rgba(16, 185, 129, 0.12);

  .badge-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #10B981;
    box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
    animation: pulse-dot 2s ease-in-out infinite;
  }

  span {
    font-size: 12px;
    font-weight: 500;
    color: rgba(16, 185, 129, 0.8);
    letter-spacing: 0.01em;
  }
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

// ── Menu Styles ──
:deep(.el-menu) {
  border-right: none;
  padding: 4px 12px;

  .el-menu-item,
  .el-sub-menu__title {
    border-radius: 10px;
    margin-bottom: 2px;
    height: 42px;
    line-height: 42px;
    font-size: 13.5px;
    font-weight: 400;
    letter-spacing: -0.01em;
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    position: relative;

    .el-icon {
      font-size: 18px;
      width: 18px;
      margin-right: 10px;
    }

    &:hover {
      background: rgba(255, 255, 255, 0.05) !important;
      color: #E2E8F0 !important;
    }
  }

  .el-menu-item.is-active {
    background: rgba(37, 99, 235, 0.15) !important;
    color: #fff !important;
    font-weight: 500;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 20px;
      background: #2563EB;
      border-radius: 0 3px 3px 0;
    }

    .el-icon {
      color: #60A5FA;
    }
  }

  .el-sub-menu {
    .el-menu {
      padding: 0 0 0 8px;
    }

    .el-menu-item {
      padding-left: 48px !important;
      height: 38px;
      line-height: 38px;
      font-size: 13px;
      color: rgba(148, 163, 184, 0.7);
      margin-bottom: 1px;

      &:hover {
        color: #E2E8F0 !important;
        background: rgba(255, 255, 255, 0.04) !important;
      }

      &.is-active {
        background: rgba(37, 99, 235, 0.1) !important;
        color: #60A5FA !important;
        font-weight: 500;

        &::before {
          width: 2px;
          height: 16px;
          background: #3B82F6;
        }
      }
    }
  }

  .el-sub-menu__icon-arrow {
    color: rgba(148, 163, 184, 0.4);
    font-size: 12px;
  }

  .el-sub-menu.is-opened > .el-sub-menu__title {
    color: #E2E8F0 !important;

    .el-sub-menu__icon-arrow {
      color: rgba(148, 163, 184, 0.6);
    }
  }
}

:deep(.el-menu--collapse) {
  padding: 4px 8px;

  .el-sub-menu__title {
    padding: 0 !important;
    justify-content: center;

    span, .el-sub-menu__icon-arrow {
      display: none;
    }

    .el-icon {
      margin-right: 0;
    }
  }

  .el-menu-item {
    padding: 0 !important;
    justify-content: center;

    span {
      display: none;
    }

    .el-icon {
      margin-right: 0;
    }

    &.is-active::before {
      left: 0;
      width: 3px;
      height: 18px;
    }
  }
}
</style>
