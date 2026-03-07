<template>
  <!-- Antigravity Layout.tsx exact: h-screen flex flex-col bg-[#FAFBFC] -->
  <div class="ag-layout" :class="{ dark: themeStore.theme === 'dark' }">
    <!-- Navbar: Antigravity Navbar.tsx exact: sticky top-0, pt-9, bg-[#FAFBFC] -->
    <nav class="ag-navbar">
      <div class="ag-navbar-inner">
        <!-- Logo: Antigravity NavLogo.tsx exact: basis-[200px] shrink min-w-0 -->
        <div class="ag-logo-wrap">
          <router-link to="/" class="ag-logo" draggable="false">
            <div class="ag-logo-icon">
              <!-- Antigravity-style gradient logo icon -->
              <svg width="32" height="32" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect width="32" height="32" rx="8" fill="url(#ag-logo-grad)"/>
                <path d="M16 7L9 11V21L16 25L23 21V11L16 7Z" fill="white" fill-opacity="0.9"/>
                <path d="M16 13L12 15.5V20L16 22.5L20 20V15.5L16 13Z" fill="url(#ag-logo-inner)"/>
                <defs>
                  <linearGradient id="ag-logo-grad" x1="0" y1="0" x2="32" y2="32">
                    <stop stop-color="#6366f1"/>
                    <stop offset="1" stop-color="#8b5cf6"/>
                  </linearGradient>
                  <linearGradient id="ag-logo-inner" x1="12" y1="13" x2="20" y2="22.5">
                    <stop stop-color="#c7d2fe"/>
                    <stop offset="1" stop-color="#818cf8"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <span class="ag-logo-text">ICH Platform</span>
          </router-link>
        </div>

        <!-- Nav Pills: Antigravity NavMenu.tsx exact: flex-1 flex justify-center -->
        <div class="ag-nav-center">
          <nav class="ag-pills" ref="pillsRef">
            <!-- Sliding indicator -->
            <span
              class="ag-pill-indicator"
              :style="indicatorStyle"
            />
            <router-link
              v-for="(tab, idx) in mainTabs"
              :key="tab.path"
              :ref="el => setPillRef(el, idx)"
              :to="tab.path"
              class="ag-pill"
              :class="{ active: isTabActive(tab) }"
              draggable="false"
            >
              {{ tab.label }}
            </router-link>
          </nav>
        </div>

        <!-- Right buttons: Antigravity NavSettings.tsx exact: flex items-center gap-2 -->
        <div class="ag-nav-right">
          <!-- Theme toggle: Antigravity exact: w-10 h-10 rounded-full bg-gray-100 -->
          <button
            class="ag-circle-btn"
            @click="themeStore.toggleTheme"
            :title="themeStore.theme === 'light' ? 'Switch to Dark' : 'Switch to Light'"
          >
            <!-- Moon icon (Lucide Moon) - shown in light mode -->
            <svg v-if="themeStore.theme === 'light'" class="ag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
            </svg>
            <!-- Sun icon (Lucide Sun) - shown in dark mode -->
            <svg v-else class="ag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="5"/>
              <line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/>
              <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
              <line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/>
              <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
            </svg>
          </button>

          <!-- Language switch: Antigravity LanguageDropdown exact -->
          <div class="ag-lang-dropdown" ref="langDropdownRef">
            <button
              class="ag-circle-btn ag-lang-btn"
              @click="langDropdownOpen = !langDropdownOpen"
              title="Switch Language"
            >
              <span class="ag-lang-code">{{ currentLangShort }}</span>
            </button>
            <div class="ag-dropdown-menu" v-show="langDropdownOpen">
              <button
                v-for="lang in languages"
                :key="lang.code"
                class="ag-dropdown-item"
                :class="{ active: themeStore.language === lang.code }"
                @click="switchLang(lang.code)"
              >
                <span>{{ lang.label }}</span>
                <span class="ag-dropdown-short">{{ lang.short }}</span>
              </button>
            </div>
          </div>

          <!-- Avatar + Dropdown -->
          <div class="ag-avatar-dropdown" ref="avatarDropdownRef">
            <button
              class="ag-avatar-btn"
              @click="avatarDropdownOpen = !avatarDropdownOpen"
              :title="adminName"
            >
              <img v-if="adminAvatar" :src="adminAvatar" class="ag-avatar-img" alt="avatar" />
              <span v-else class="ag-avatar-text">{{ adminInitial }}</span>
            </button>
            <div class="ag-dropdown-menu ag-avatar-menu" v-show="avatarDropdownOpen">
              <div class="ag-avatar-info">
                <div class="ag-avatar-info-avatar">
                  <img v-if="adminAvatar" :src="adminAvatar" alt="avatar" />
                  <span v-else>{{ adminInitial }}</span>
                </div>
                <div class="ag-avatar-info-text">
                  <span class="ag-avatar-name">{{ adminName }}</span>
                  <span class="ag-avatar-role">{{ adminEmail }}</span>
                </div>
              </div>
              <div class="ag-dropdown-divider"></div>
              <button class="ag-dropdown-item ag-dropdown-logout" @click="handleLogout">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                  <polyline points="16 17 21 12 16 7"/>
                  <line x1="21" y1="12" x2="9" y2="12"/>
                </svg>
                <span>{{ t('navbar.signOut') }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </nav>

    <!-- Main: Antigravity Layout.tsx exact: flex-1 overflow-hidden flex flex-col relative -->
    <main class="ag-main">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const { t } = useI18n()

// ── Navigation items (Antigravity Navbar.tsx exact navItems) ──
const mainTabs = computed(() => [
  { label: t('navbar.dashboard'), path: '/dashboard', match: ['/dashboard'] },
  { label: t('navbar.content'), path: '/content/category', match: ['/content'] },
  { label: t('navbar.products'), path: '/product/category', match: ['/product'] },
  { label: t('navbar.orders'), path: '/order', match: ['/order'] },
  { label: t('navbar.users'), path: '/user', match: ['/user'] },
  { label: t('navbar.system'), path: '/system/admin', match: ['/system'] },
])

function isTabActive(tab) {
  if (tab.path === '/dashboard') return route.path === '/dashboard' || route.path === '/'
  return tab.match.some(m => route.path.startsWith(m))
}

// ── Sliding pill indicator ──
const pillsRef = ref(null)
const pillRefs = ref([])
const indicatorStyle = ref({ opacity: 0 })

function setPillRef(el, idx) {
  if (el) pillRefs.value[idx] = el.$el || el
}

function updateIndicator() {
  const activeIdx = mainTabs.value.findIndex(t => isTabActive(t))
  if (activeIdx < 0 || !pillRefs.value[activeIdx] || !pillsRef.value) {
    indicatorStyle.value = { opacity: 0 }
    return
  }
  const pillEl = pillRefs.value[activeIdx]
  const navEl = pillsRef.value
  const pillRect = pillEl.getBoundingClientRect()
  const navRect = navEl.getBoundingClientRect()
  indicatorStyle.value = {
    width: pillRect.width + 'px',
    transform: `translateX(${pillRect.left - navRect.left - 4}px)`,
    opacity: 1,
  }
}

watch(() => route.path, () => nextTick(updateIndicator))
watch(() => themeStore.language, () => nextTick(() => setTimeout(updateIndicator, 50)))

// ── Admin avatar / info ──
const adminName = computed(() => authStore.adminInfo?.nickname || authStore.adminInfo?.username || 'Admin')
const adminAvatar = computed(() => authStore.adminInfo?.avatar || '')
const adminEmail = computed(() => authStore.adminInfo?.email || '')
const adminInitial = computed(() => {
  const name = adminName.value
  return name ? name.charAt(0).toUpperCase() : 'A'
})
const avatarDropdownOpen = ref(false)
const avatarDropdownRef = ref(null)

// ── Language dropdown (Antigravity constants.ts exact LANGUAGES) ──
const languages = [
  { code: 'zh', label: '简体中文', short: 'ZH' },
  { code: 'zh-TW', label: '繁體中文', short: 'TW' },
  { code: 'en', label: 'English', short: 'EN' },
]

const langDropdownOpen = ref(false)
const langDropdownRef = ref(null)

const currentLangShort = computed(() => {
  const found = languages.find(l => l.code === themeStore.language)
  return found ? found.short : 'ZH'
})

function switchLang(code) {
  themeStore.setLanguage(code)
  langDropdownOpen.value = false
}

function handleClickOutside(e) {
  if (langDropdownRef.value && !langDropdownRef.value.contains(e.target)) {
    langDropdownOpen.value = false
  }
  if (avatarDropdownRef.value && !avatarDropdownRef.value.contains(e.target)) {
    avatarDropdownOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  nextTick(() => setTimeout(updateIndicator, 80))
  window.addEventListener('resize', updateIndicator)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('resize', updateIndicator)
})

// ── Logout ──
function handleLogout() {
  avatarDropdownOpen.value = false
  ElMessageBox.confirm(t('navbar.signOutConfirm'), t('navbar.confirm'), {
    confirmButtonText: t('navbar.signOut'),
    cancelButtonText: t('navbar.cancel'),
    type: 'warning',
  }).then(() => {
    authStore.logout()
    router.push('/login')
  }).catch(() => {})
}
</script>

<style lang="scss" scoped>
// ══════════════════════════════════════════════════════════════
// Antigravity Layout.tsx + Navbar.tsx + NavMenu.tsx + NavSettings.tsx
// PIXEL-PERFECT replication of exact Tailwind classes
// ══════════════════════════════════════════════════════════════

// ── Layout: h-screen flex flex-col bg-[#FAFBFC] ──
.ag-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #FAFBFC;
}

// ── Navbar: sticky top-0 z-50, pt-9, bg-[#FAFBFC] ──
// pt-9 = padding-top: 36px → creates the gap from browser top
.ag-navbar {
  position: sticky;
  top: 0;
  z-index: 50;
  padding-top: 36px;           // pt-9 — KEY: gap from browser top
  transition: all 0.2s;
  background: #FAFBFC;
}

// ── Navbar inner: max-w-7xl mx-auto px-8, flex items-center h-16 gap-4 ──
.ag-navbar-inner {
  max-width: 1280px;            // max-w-7xl
  margin: 0 auto;
  padding: 0 32px;              // px-8
  display: flex;
  align-items: center;
  height: 64px;                 // h-16
  gap: 16px;                    // gap-4
  position: relative;
  z-index: 10;
}

// ── Logo wrap: basis-[200px] shrink min-w-0 ──
.ag-logo-wrap {
  flex-basis: 200px;
  flex-shrink: 1;
  min-width: 0;
}

// ── Logo: flex items-center gap-2 text-xl font-semibold text-gray-900 ──
.ag-logo {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: 8px;                     // gap-2
  text-decoration: none;

  .ag-logo-icon {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;

    img, svg {
      width: 32px;               // w-8
      height: 32px;              // h-8
      cursor: pointer;
      transition: transform 0.15s;
      &:active { transform: scale(0.95); }
    }
  }

  .ag-logo-text {
    font-size: 20px;             // text-xl
    font-weight: 600;            // font-semibold
    color: #111827;              // text-gray-900
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

// ── Nav Center: flex-1 flex justify-center ──
.ag-nav-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

// ── Pills: flex items-center gap-1 bg-gray-100 rounded-full p-1 ──
.ag-pills {
  display: flex;
  align-items: center;
  gap: 4px;                     // gap-1
  background: #f3f4f6;          // bg-gray-100
  border-radius: 9999px;        // rounded-full
  padding: 4px;                 // p-1
  position: relative;
}

// ── Sliding indicator behind active pill ──
.ag-pill-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  height: calc(100% - 8px);
  border-radius: 9999px;
  background: #111827;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1), width 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s;
  pointer-events: none;
  z-index: 0;
}

// ── Pill item: px-4 xl:px-6 py-2 rounded-full text-sm font-medium ──
.ag-pill {
  padding: 8px 16px;            // py-2 px-4
  border-radius: 9999px;        // rounded-full
  font-size: 14px;              // text-sm
  font-weight: 500;             // font-medium
  transition: color 0.25s ease, background 0.2s ease;
  white-space: nowrap;
  text-decoration: none;
  color: #374151;               // text-gray-700
  cursor: pointer;
  position: relative;
  z-index: 1;

  &:hover:not(.active) {
    color: #111827;              // hover:text-gray-900
    background: rgba(0, 0, 0, 0.04);
  }

  // Active: text-white, background handled by sliding indicator
  &.active {
    color: #ffffff;
    background: transparent;
  }
}

// Wider pills on xl screens (xl:px-6)
@media (min-width: 1280px) {
  .ag-pill { padding: 8px 24px; }
}

// ── Right Buttons: flex items-center gap-2 ──
.ag-nav-right {
  display: flex;
  align-items: center;
  gap: 8px;                     // gap-2
  flex-shrink: 0;
}

// ── Circle button: w-10 h-10 rounded-full bg-gray-100 hover:bg-gray-200 ──
.ag-circle-btn {
  width: 40px;                  // w-10
  height: 40px;                 // h-10
  border-radius: 9999px;        // rounded-full
  background: #f3f4f6;          // bg-gray-100
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.15s;
  color: #374151;               // text-gray-700
  position: relative;

  &:hover {
    background: #e5e7eb;         // hover:bg-gray-200
  }
}

// ── Lucide-style SVG icon: w-5 h-5 ──
.ag-icon {
  width: 20px;                  // w-5
  height: 20px;                 // h-5
  color: #374151;               // text-gray-700
}

// ── Avatar button ──
.ag-avatar-dropdown {
  position: relative;
}
.ag-avatar-btn {
  width: 40px;
  height: 40px;
  border-radius: 9999px;
  border: 2px solid #e5e7eb;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
  &:hover {
    border-color: #a5b4fc;
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
  }
}
.ag-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 9999px;
}
.ag-avatar-text {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  line-height: 1;
}
.ag-avatar-menu {
  min-width: 220px;
}
.ag-avatar-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
}
.ag-avatar-info-avatar {
  width: 36px;
  height: 36px;
  border-radius: 9999px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  img { width: 100%; height: 100%; object-fit: cover; }
  span { color: #fff; font-size: 14px; font-weight: 700; }
}
.ag-avatar-info-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.ag-avatar-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ag-avatar-role {
  font-size: 12px;
  color: #9ca3af;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ag-dropdown-divider {
  height: 1px;
  background: #f3f4f6;
  margin: 4px 0;
}
.ag-dropdown-logout {
  color: #dc2626 !important;
  gap: 8px;
  svg { color: #dc2626; }
  &:hover {
    background: #fef2f2 !important;
  }
}

// ── Language button ──
.ag-lang-btn {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.ag-lang-code {
  font-size: 12px;
  font-weight: 700;
  color: #374151;
}

// ── Language dropdown menu ──
.ag-lang-dropdown {
  position: relative;
}
.ag-dropdown-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 4px;
  min-width: 160px;
  box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -2px rgba(0,0,0,0.05);
  z-index: 100;
}
.ag-dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  transition: background 0.1s;

  &:hover { background: #f3f4f6; }
  &.active {
    background: #eff6ff;
    color: #2563eb;
    font-weight: 500;
  }
}
.ag-dropdown-short {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  .active & { color: #3b82f6; }
}

// ── Main: flex-1 overflow-hidden flex flex-col relative ──
.ag-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}
</style>
