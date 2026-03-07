<template>
  <div class="header">
    <div class="header-left">
      <button class="collapse-btn" @click="$emit('toggle')">
        <el-icon :size="18">
          <component :is="collapsed ? 'Expand' : 'Fold'" />
        </el-icon>
      </button>

      <div class="header-search">
        <el-icon :size="15" class="search-icon"><Search /></el-icon>
        <span class="search-placeholder">Search...</span>
        <span class="search-shortcut">⌘K</span>
      </div>
    </div>

    <div class="header-right">
      <button class="header-icon-btn">
        <el-icon :size="18"><Bell /></el-icon>
        <span class="notification-dot"></span>
      </button>

      <div class="header-divider"></div>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <div class="user-avatar">
            <span>{{ avatarLetter }}</span>
          </div>
          <div class="user-meta">
            <span class="user-name">{{ adminInfo.nickname || adminInfo.username || 'Admin' }}</span>
            <span class="user-role">Administrator</span>
          </div>
          <el-icon :size="14" class="arrow-icon"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>Profile
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>Sign Out
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { ElMessageBox } from 'element-plus'

defineProps({ collapsed: Boolean })
defineEmits(['toggle'])

const router = useRouter()
const authStore = useAuthStore()
const adminInfo = computed(() => authStore.adminInfo || {})
const avatarLetter = computed(() => {
  const name = adminInfo.value.nickname || adminInfo.value.username || 'A'
  return name.charAt(0).toUpperCase()
})

function handleCommand(command) {
  if (command === 'logout') {
    ElMessageBox.confirm('Are you sure you want to sign out?', 'Confirm', {
      confirmButtonText: 'Sign Out',
      cancelButtonText: 'Cancel',
      type: 'warning',
    }).then(() => {
      authStore.logout()
      router.push('/login')
    }).catch(() => {})
  }
}
</script>

<style lang="scss" scoped>
.header {
  height: 64px;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  border-bottom: 1px solid #F1F5F9;
  flex-shrink: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid #E2E8F0;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  color: #64748B;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: #F8FAFC;
    border-color: #CBD5E1;
    color: #2563EB;
  }
}

.header-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #F8FAFC;
  border: 1px solid #F1F5F9;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  min-width: 220px;

  &:hover {
    border-color: #E2E8F0;
    background: #F1F5F9;
  }

  .search-icon {
    color: #94A3B8;
  }

  .search-placeholder {
    font-size: 13px;
    color: #94A3B8;
    flex: 1;
  }

  .search-shortcut {
    font-size: 11px;
    color: #CBD5E1;
    background: #FFFFFF;
    border: 1px solid #E2E8F0;
    border-radius: 6px;
    padding: 2px 6px;
    font-weight: 500;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  color: #64748B;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: #F8FAFC;
    color: #475569;
  }

  .notification-dot {
    position: absolute;
    top: 8px;
    right: 9px;
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #EF4444;
    border: 1.5px solid #FFFFFF;
  }
}

.header-divider {
  width: 1px;
  height: 28px;
  background: #F1F5F9;
  margin: 0 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 10px 6px 6px;
  border-radius: 12px;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: #F8FAFC;
  }
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563EB 0%, #3B82F6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  span {
    color: #FFFFFF;
    font-size: 14px;
    font-weight: 700;
    letter-spacing: 0;
  }
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #1E293B;
}

.user-role {
  font-size: 11px;
  color: #94A3B8;
  font-weight: 400;
}

.arrow-icon {
  color: #CBD5E1;
  margin-left: 2px;
}
</style>
