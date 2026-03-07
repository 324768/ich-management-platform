<template>
  <div class="login-page">
    <!-- Top Navbar -->
    <nav class="top-nav">
      <div class="nav-left">
        <div class="nav-logo">
          <svg width="28" height="28" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="8" fill="url(#nav-logo-g)"/>
            <path d="M16 7L9 11V21L16 25L23 21V11L16 7Z" fill="white" fill-opacity="0.9"/>
            <path d="M16 13L12 15.5V20L16 22.5L20 20V15.5L16 13Z" fill="url(#nav-logo-i)"/>
            <defs>
              <linearGradient id="nav-logo-g" x1="0" y1="0" x2="32" y2="32"><stop stop-color="#6366f1"/><stop offset="1" stop-color="#8b5cf6"/></linearGradient>
              <linearGradient id="nav-logo-i" x1="12" y1="13" x2="20" y2="22.5"><stop stop-color="#c7d2fe"/><stop offset="1" stop-color="#818cf8"/></linearGradient>
            </defs>
          </svg>
          <span class="nav-logo-text">ICH Platform</span>
        </div>
      </div>
      <div class="nav-right">
        <!-- Language Switcher -->
        <div class="lang-dropdown" ref="langDropdownRef">
          <button class="lang-btn" @click="langDropdownOpen = !langDropdownOpen">
            <span class="lang-code">{{ currentLangShort }}</span>
          </button>
          <Transition name="dropdown">
            <div class="lang-panel" v-if="langDropdownOpen">
              <button
                v-for="lang in languages"
                :key="lang.code"
                class="lang-item"
                :class="{ active: themeStore.language === lang.code }"
                @click="switchLang(lang.code)"
              >
                <span>{{ lang.label }}</span>
                <span class="lang-short">{{ lang.short }}</span>
              </button>
            </div>
          </Transition>
        </div>

        <div class="layout-dropdown" ref="dropdownRef">
          <button class="nav-btn-outline" @click="dropdownOpen = !dropdownOpen">
            {{ $t('login.layout') }}
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" :class="{ rotated: dropdownOpen }"><polyline points="6 9 12 15 18 9"/></svg>
          </button>
          <Transition name="dropdown">
            <div class="dropdown-panel" v-if="dropdownOpen">
              <div v-for="pos in positions" :key="pos.value" class="dropdown-item" :class="{ active: cardPosition === pos.value }" @click="selectPosition(pos.value)">
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <rect v-if="pos.value === 'left'" x="1" y="2" width="5" height="12" rx="2" fill="currentColor"/>
                  <rect v-if="pos.value === 'left'" x="8" y="2" width="7" height="12" rx="2" fill="currentColor" opacity="0.15"/>
                  <rect v-if="pos.value === 'center'" x="0" y="2" width="4" height="12" rx="2" fill="currentColor" opacity="0.15"/>
                  <rect v-if="pos.value === 'center'" x="5.5" y="2" width="5" height="12" rx="2" fill="currentColor"/>
                  <rect v-if="pos.value === 'center'" x="12" y="2" width="4" height="12" rx="2" fill="currentColor" opacity="0.15"/>
                  <rect v-if="pos.value === 'right'" x="0" y="2" width="7" height="12" rx="2" fill="currentColor" opacity="0.15"/>
                  <rect v-if="pos.value === 'right'" x="10" y="2" width="5" height="12" rx="2" fill="currentColor"/>
                </svg>
                <span>{{ pos.label }}</span>
                <svg v-if="cardPosition === pos.value" class="check-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
              </div>
            </div>
          </Transition>
        </div>
      </div>
    </nav>

    <!-- Main Content -->
    <div class="main-content" :class="`layout-${cardPosition}`">
      <!-- Left Column -->
      <div class="left-col">
        <h1 class="hero-title">{{ $t('login.heroTitle1') }}<br/>{{ $t('login.heroTitle2') }}</h1>
        <p class="hero-subtitle">{{ $t('login.heroSubtitle') }}</p>


        <div class="login-card">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form" @keyup.enter="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" :placeholder="$t('login.usernamePlaceholder')" size="large">
                <template #prefix>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" :placeholder="$t('login.passwordPlaceholder')" size="large" show-password>
                <template #prefix>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                </template>
              </el-input>
            </el-form-item>

            <div class="login-options">
              <el-checkbox v-model="rememberMe">{{ $t('login.rememberMe') }}</el-checkbox>
            </div>

            <button type="button" class="login-btn" :disabled="loading" @click="handleLogin">
              <span v-if="!loading">{{ $t('login.loginBtn') }}</span>
              <span v-else>{{ $t('login.logging') }}</span>
            </button>
          </el-form>

          <p class="login-disclaimer">{{ $t('login.disclaimer') }} &copy; 2025</p>
        </div>
      </div>

      <!-- Right Column: Decorative UI Cards -->
      <div class="right-col">
        <div class="deco-container">
        <div class="deco-scene">
          <!-- Card 1: Pill Segmented Control -->
          <div class="deco-card card-tabs">
            <div class="seg-control">
              <span
                v-for="(tab, i) in decoTabs"
                :key="i"
                class="seg-item"
                :class="{ active: activeDecoTab === i }"
                @click="activeDecoTab = i"
              >{{ tab.label }}</span>
            </div>
          </div>

          <!-- Card 2: Main workspace (content changes with tab) -->
          <div class="deco-card card-workspace">
            <div class="ws-tabs">
              <span class="ws-tab" :class="{ active: activeDecoTab === i }" v-for="(tab, i) in decoTabs" :key="i" @click="activeDecoTab = i">{{ tab.label }}</span>
            </div>
            <div class="ws-search">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#999" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              <span>{{ $t('login.search') }}</span>
            </div>
            <div class="ws-grid">
              <div class="ws-item" v-for="item in decoTabs[activeDecoTab].items" :key="item">
                <div class="ws-icon" :style="{ background: decoTabs[activeDecoTab].color }"></div>
                <span>{{ item }}</span>
              </div>
            </div>
            <div class="ws-actions">
              <span class="ws-btn-outline">{{ $t('login.cancel') }}</span>
              <span class="ws-btn-solid">{{ $t('login.view') }}</span>
            </div>
          </div>

          <!-- Card 3: Progress -->
          <div class="deco-card card-progress">
            <div class="prog-header">
              <span>{{ $t('login.progress') }}</span>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#999" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </div>
            <div class="prog-list">
              <div class="prog-item" v-for="(item, i) in progressItems" :key="i">
                <span class="prog-check done">
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                </span>
                <span>{{ item.text }}</span>
              </div>
            </div>
          </div>

          <!-- Card 4: Context panel -->
          <div class="deco-card card-context">
            <div class="ctx-header">
              <span>{{ $t('login.dataOverview') }}</span>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#999" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </div>
            <div class="ctx-list">
              <div class="ctx-item" v-for="item in contextItems" :key="item.label">
                <span class="ctx-icon" :style="{ background: item.bg, color: item.color }">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path v-if="item.icon === 'file'" d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <path v-if="item.icon === 'grid'" d="M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z"/>
                    <path v-if="item.icon === 'box'" d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                    <circle v-if="item.icon === 'chart'" cx="12" cy="12" r="10"/><path v-if="item.icon === 'chart'" d="M12 2v10l8.5 5"/>
                    <path v-if="item.icon === 'users'" d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle v-if="item.icon === 'users'" cx="9" cy="7" r="4"/><path v-if="item.icon === 'users'" d="M23 21v-2a4 4 0 0 0-3-3.87"/><path v-if="item.icon === 'users'" d="M16 3.13a4 4 0 0 1 0 7.75"/>
                  </svg>
                </span>
                <span>{{ item.label }}</span>
              </div>
            </div>
          </div>
        </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const loginFormRef = ref(null)
const loading = ref(false)
const rememberMe = ref(false)
const cardPosition = ref('left')
const activeDecoTab = ref(0)
const decoTabs = computed(() => [
  { label: t('login.tab1'), items: t('login.tab1Items'), color: '#6366f1' },
  { label: t('login.tab2'), items: t('login.tab2Items'), color: '#8b5cf6' },
  { label: t('login.tab3'), items: t('login.tab3Items'), color: '#3b82f6' },
])
const dropdownOpen = ref(false)
const dropdownRef = ref(null)

// Language switcher
const langDropdownOpen = ref(false)
const langDropdownRef = ref(null)
const languages = [
  { code: 'zh', label: '简体中文', short: 'ZH' },
  { code: 'zh-TW', label: '繁體中文', short: 'TW' },
  { code: 'en', label: 'English', short: 'EN' },
]
const currentLangShort = computed(() => {
  const found = languages.find(l => l.code === themeStore.language)
  return found ? found.short : 'ZH'
})
function switchLang(code) {
  themeStore.setLanguage(code)
  langDropdownOpen.value = false
}

const positions = computed(() => [
  { value: 'left', label: t('login.layoutLeft') },
  { value: 'center', label: t('login.layoutCenter') },
  { value: 'right', label: t('login.layoutRight') },
])

const progressItems = computed(() => [
  { text: t('login.prog1') },
  { text: t('login.prog2') },
  { text: t('login.prog3') },
  { text: t('login.prog4') },
  { text: t('login.prog5') },
  { text: t('login.prog6') },
])

const contextItems = computed(() => [
  { label: t('login.ctx1'), icon: 'file', color: '#6366f1', bg: '#eef2ff' },
  { label: t('login.ctx2'), icon: 'users', color: '#8b5cf6', bg: '#f5f3ff' },
  { label: t('login.ctx3'), icon: 'box', color: '#3b82f6', bg: '#eff6ff' },
  { label: t('login.ctx4'), icon: 'chart', color: '#10b981', bg: '#ecfdf5' },
  { label: t('login.ctx5'), icon: 'grid', color: '#f59e0b', bg: '#fffbeb' },
])

function selectPosition(val) {
  cardPosition.value = val
  dropdownOpen.value = false
}

function handleClickOutside(e) {
  if (dropdownRef.value && !dropdownRef.value.contains(e.target)) {
    dropdownOpen.value = false
  }
  if (langDropdownRef.value && !langDropdownRef.value.contains(e.target)) {
    langDropdownOpen.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

const loginForm = reactive({ username: '', password: '' })
const loginRules = computed(() => ({
  username: [{ required: true, message: t('login.usernamePlaceholder'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordPlaceholder'), trigger: 'blur' }]
}))

async function handleLogin() {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await authStore.login(loginForm)
    await authStore.fetchAdminInfo().catch(() => {})
    ElMessage.success(t('login.loginSuccess'))
    router.push('/dashboard')
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
$beige: #FAF8F5;
$brown: #1a1410;
$brown-light: #6b5e54;
$accent: #c4642a;

// ── Page ──
.login-page {
  width: 100%;
  min-height: 100vh;
  background: $beige;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow-x: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

// ── Top Nav ──
.top-nav {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 80px;
}

.nav-left {
  flex: 0 0 auto;
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nav-logo-text {
  font-size: 17px;
  font-weight: 700;
  color: $brown;
  letter-spacing: -0.02em;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nav-btn-outline {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: $brown;
  background: transparent;
  border: 1px solid #d6d0c8;
  border-radius: 9999px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover { background: rgba(0,0,0,0.03); border-color: #bbb5ad; }

  svg {
    transition: transform 0.2s;
    &.rotated { transform: rotate(180deg); }
  }
}

// ── Language Dropdown ──
.lang-dropdown {
  position: relative;
}

.lang-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid #d6d0c8;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;

  &:hover { background: rgba(0,0,0,0.03); border-color: #bbb5ad; }
}

.lang-code {
  font-size: 12px;
  font-weight: 700;
  color: $brown;
}

.lang-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 12px;
  padding: 4px;
  min-width: 160px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  z-index: 20;
}

.lang-item {
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

  &:hover { background: #f9f8f6; }
  &.active { background: #fdf5ef; color: $accent; font-weight: 500; }
}

.lang-short {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  .active & { color: $accent; }
}

// ── Layout Dropdown ──
.layout-dropdown {
  position: relative;
}

.dropdown-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 160px;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  padding: 6px;
  transform-origin: top right;
  z-index: 20;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  transition: background 0.12s;

  &:hover { background: #f9f8f6; }
  &.active { color: $accent; background: #fdf5ef; }

  svg:first-child { flex-shrink: 0; }
  span { flex: 1; }
  .check-icon { color: $accent; flex-shrink: 0; }
}

.dropdown-enter-active { transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1); }
.dropdown-leave-active { transition: all 0.15s ease-in; }
.dropdown-enter-from, .dropdown-leave-to { opacity: 0; transform: scale(0.95) translateY(-4px); }

// ── Main Content ──
.main-content {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  align-items: center;
  padding: 0 80px 60px 160px;
  gap: 60px;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);

  &.layout-left {
    .left-col { order: 1; }
    .right-col { order: 2; }
  }
  &.layout-center {
    justify-content: center;
    .right-col { display: none; }
  }
  &.layout-right {
    padding: 0 160px 60px 80px;
    .left-col { order: 2; }
    .right-col { order: 1; }
  }
}

// ── Left Column ──
.left-col {
  flex: 0 0 400px;
  max-width: 400px;
  text-align: center;
}

.hero-title {
  font-size: 46px;
  font-weight: 400;
  color: $brown;
  line-height: 1.2;
  letter-spacing: -0.02em;
  font-style: italic;
  font-family: Georgia, 'Times New Roman', serif;
  margin-bottom: 14px;
  text-align: center;
}

.hero-subtitle {
  font-size: 14px;
  color: $brown-light;
  margin-bottom: 32px;
  line-height: 1.5;
  text-align: center;
}

// ── Login Card ──
.login-card {
  background: #fff;
  border-radius: 16px;
  padding: 28px 32px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  border: 1px solid #eee;
  text-align: left;
}

.login-form {
  :deep(.el-form-item) { margin-bottom: 16px; }

  :deep(.el-input__wrapper) {
    border-radius: 9999px;
    padding: 4px 18px;
    box-shadow: 0 0 0 1px #d6d0c8;
    background: #fff;
    transition: all 0.15s;

    &:hover { box-shadow: 0 0 0 1px #bbb5ad; }
    &.is-focus { box-shadow: 0 0 0 2px $accent; }
  }

  :deep(.el-input__prefix) { color: #bbb5ad; }

  :deep(.el-input__inner) {
    font-size: 14px;
    height: 40px;
    color: $brown;

    &::placeholder { color: #bbb5ad; }
  }
}

.login-options {
  display: flex;
  align-items: center;
  margin-bottom: 20px;

  :deep(.el-checkbox__label) { font-size: 13px; color: $brown-light; }
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: #000;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  transition: all 0.2s;
  letter-spacing: 0.04em;

  &:hover {
    background: #222;
    box-shadow: 0 4px 14px rgba(0,0,0,0.18);
  }

  &:disabled { opacity: 0.7; cursor: not-allowed; }
}

.login-disclaimer {
  margin-top: 20px;
  text-align: center;
  font-size: 11px;
  color: #bbb5ad;
  line-height: 1.6;
}

// ── Right Column: Decorative Scene ──
.right-col {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 480px;
}

.deco-container {
  position: relative;
  aspect-ratio: 1 / 1;
  width: 100%;
  max-width: 725px;
  background:
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent calc(100% / 12 - 1px),
      rgba(0,0,0,0.04) calc(100% / 12 - 1px),
      rgba(0,0,0,0.04) calc(100% / 12)
    ),
    repeating-linear-gradient(
      90deg,
      transparent,
      transparent calc(100% / 12 - 1px),
      rgba(0,0,0,0.04) calc(100% / 12 - 1px),
      rgba(0,0,0,0.04) calc(100% / 12)
    );
  border: 1px solid rgba(0,0,0,0.06);
  border-radius: 24px;
  padding: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.deco-scene {
  position: relative;
  width: 100%;
  height: 100%;
}

.deco-card {
  position: absolute;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06), 0 0 1px rgba(0,0,0,0.08);
}

// Card 1: Pill Segmented Control
.card-tabs {
  top: 16px;
  left: 20%;
  right: 15%;
  padding: 0;
  background: transparent;
  box-shadow: none;
  border-radius: 0;
  display: flex;
  justify-content: center;
}

.seg-control {
  display: inline-flex;
  background: #fff;
  border-radius: 9999px;
  padding: 4px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06), 0 0 1px rgba(0,0,0,0.08);
}

.seg-item {
  padding: 8px 28px;
  font-size: 12px;
  color: #999;
  border-radius: 9999px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;

  &:hover { color: #666; }

  &.active {
    color: $brown;
    background: #fff;
    font-weight: 600;
    box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  }
}

// Card 2: Workspace
.card-workspace {
  top: 64px;
  left: 10px;
  width: 280px;
  padding: 0;
}

.ws-tabs {
  display: flex;
  border-bottom: 1px solid #f0ece6;
}

.ws-tab {
  padding: 10px 18px;
  font-size: 12px;
  color: #999;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color 0.15s;

  &.active {
    color: $brown;
    font-weight: 600;
    border-bottom-color: $brown;
  }
}

.ws-search {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 14px;
  padding: 5px 10px;
  background: #f9f8f6;
  border-radius: 6px;
  font-size: 11px;
  color: #999;
}

.ws-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 0 14px 10px;
}

.ws-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 0;
  cursor: default;

  .ws-icon {
    width: 38px;
    height: 28px;
    border-radius: 4px 4px 0 0;
    position: relative;
    transition: background 0.2s;

    &::after {
      content: '';
      position: absolute;
      bottom: -4px;
      left: 4px;
      right: 4px;
      height: 4px;
      background: inherit;
      opacity: 0.5;
      border-radius: 0 0 2px 2px;
    }
  }

  span { font-size: 10px; color: $brown-light; }
}

.ws-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  padding: 8px 14px;
  border-top: 1px solid #f0ece6;
}

.ws-btn-outline {
  padding: 4px 12px;
  font-size: 10px;
  color: $brown-light;
  border: 1px solid #d6d0c8;
  border-radius: 5px;
  cursor: default;
}

.ws-btn-solid {
  padding: 4px 12px;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: #56b3f5;
  border-radius: 5px;
  cursor: default;
}

// Card 3: Progress
.card-progress {
  bottom: 16px;
  left: 60px;
  width: 280px;
  padding: 16px 20px;
}

.prog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  color: $brown;
}

.prog-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.prog-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: $brown-light;
}

.prog-check {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &.done {
    background: #3bb8a0;
    border: none;
  }
}

// Card 4: Context
.card-context {
  top: 64px;
  right: 10px;
  width: 180px;
  padding: 16px 18px;
}

.ctx-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  color: $brown;
}

.ctx-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ctx-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: $brown-light;
}

.ctx-icon {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

// ── Responsive ──
@media (max-width: 1200px) {
  .main-content { padding: 0 40px 48px 80px; gap: 48px; }
  .deco-container { max-width: 440px; }
}

@media (max-width: 960px) {
  .right-col { display: none !important; }
  .main-content { justify-content: center; padding: 0 40px 48px; }
  .left-col { flex: 0 0 auto; }
}

@media (max-width: 480px) {
  .top-nav { padding: 16px 20px; }
  .main-content { padding: 0 20px 32px; }
  .left-col { max-width: 100%; }
  .hero-title { font-size: 36px; }
  .login-card { padding: 24px; }
}
</style>
