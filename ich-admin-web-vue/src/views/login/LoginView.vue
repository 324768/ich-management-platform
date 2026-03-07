<template>
  <div class="login-container">
    <div class="login-left">
      <div class="login-left-content">
        <div class="login-brand">
          <div class="brand-icon">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="#60A5FA"/>
              <path d="M2 17L12 22L22 17" stroke="#3B82F6" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="#93C5FD" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <h1>ICH Platform</h1>
          <p>Intangible Cultural Heritage<br/>Management System</p>
        </div>

        <div class="login-features">
          <div class="feature-item" v-for="(f, i) in features" :key="i">
            <div class="feature-icon">
              <el-icon :size="18"><component :is="f.icon" /></el-icon>
            </div>
            <div class="feature-text">
              <h4>{{ f.title }}</h4>
              <span>{{ f.desc }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="login-left-footer">
        <p>Preserving cultural heritage through digital innovation</p>
      </div>

      <div class="bg-orb orb-1"></div>
      <div class="bg-orb orb-2"></div>
      <div class="bg-orb orb-3"></div>
    </div>

    <div class="login-right">
      <div class="login-form-wrapper">
        <div class="login-form-header">
          <h2>Welcome back</h2>
          <p>Enter your credentials to access the admin panel</p>
        </div>

        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
          @keyup.enter="handleLogin"
        >
          <div class="form-label">Username</div>
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="Enter your username"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>

          <div class="form-label">Password</div>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="Enter your password"
              size="large"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <div class="form-options">
              <el-checkbox v-model="rememberMe">Remember me</el-checkbox>
            </div>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              Sign In
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-form-footer">
          <p>ICH Management Platform &copy; 2025</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const loginFormRef = ref(null)
const loading = ref(false)
const rememberMe = ref(false)

const features = reactive([
  { icon: 'Collection', title: 'Heritage Management', desc: 'Manage intangible cultural heritage items and categories' },
  { icon: 'DataAnalysis', title: 'Data Analytics', desc: 'Real-time insights and comprehensive reporting' },
  { icon: 'ShoppingBag', title: 'E-Commerce', desc: 'Cultural product marketplace with order management' },
])

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [{ required: true, message: 'Please enter username', trigger: 'blur' }],
  password: [{ required: true, message: 'Please enter password', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.login(loginForm)
    await authStore.fetchAdminInfo().catch(() => {})
    ElMessage.success('Login successful')
    router.push('/dashboard')
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

// ── Left Panel ──
.login-left {
  flex: 1;
  background: #0F172A;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.login-left-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 60px 56px;
  position: relative;
  z-index: 1;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;

  &.orb-1 {
    width: 400px;
    height: 400px;
    background: #2563EB;
    top: -10%;
    left: -10%;
  }

  &.orb-2 {
    width: 300px;
    height: 300px;
    background: #06B6D4;
    bottom: 10%;
    right: -5%;
    opacity: 0.25;
  }

  &.orb-3 {
    width: 200px;
    height: 200px;
    background: #8B5CF6;
    top: 50%;
    left: 40%;
    opacity: 0.2;
  }
}

.login-brand {
  margin-bottom: 56px;

  .brand-icon {
    width: 56px;
    height: 56px;
    border-radius: 14px;
    background: rgba(37, 99, 235, 0.15);
    border: 1px solid rgba(37, 99, 235, 0.2);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 28px;
  }

  h1 {
    font-size: 30px;
    font-weight: 800;
    color: #F8FAFC;
    margin-bottom: 10px;
    letter-spacing: -0.03em;
  }

  p {
    font-size: 15px;
    color: rgba(148, 163, 184, 0.8);
    line-height: 1.6;
    letter-spacing: -0.01em;
  }
}

.login-features {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 18px 20px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: rgba(255, 255, 255, 0.06);
    border-color: rgba(255, 255, 255, 0.1);
    transform: translateX(4px);
  }
}

.feature-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: rgba(37, 99, 235, 0.15);
  color: #60A5FA;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature-text {
  h4 {
    font-size: 14px;
    font-weight: 600;
    color: #E2E8F0;
    margin-bottom: 3px;
    letter-spacing: -0.01em;
  }

  span {
    font-size: 12.5px;
    color: rgba(148, 163, 184, 0.65);
    line-height: 1.4;
  }
}

.login-left-footer {
  padding: 24px 56px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  position: relative;
  z-index: 1;

  p {
    color: rgba(148, 163, 184, 0.4);
    font-size: 12px;
    letter-spacing: 0.02em;
  }
}

// ── Right Panel ──
.login-right {
  width: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FFFFFF;
  padding: 60px;
  position: relative;
}

.login-form-wrapper {
  width: 100%;
  max-width: 380px;
}

.login-form-header {
  margin-bottom: 36px;

  h2 {
    font-size: 26px;
    font-weight: 800;
    color: #0F172A;
    margin-bottom: 8px;
    letter-spacing: -0.03em;
  }

  p {
    font-size: 14px;
    color: #94A3B8;
    line-height: 1.5;
  }
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
  letter-spacing: -0.01em;
}

.login-form {
  :deep(.el-input__wrapper) {
    border-radius: 12px;
    padding: 4px 16px;
    box-shadow: 0 0 0 1px #E2E8F0;
    transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
    background: #FAFCFF;

    &:hover {
      box-shadow: 0 0 0 1px #CBD5E1;
      background: #FFFFFF;
    }

    &.is-focus {
      box-shadow: 0 0 0 2px #2563EB;
      background: #FFFFFF;
    }
  }

  :deep(.el-input__inner) {
    font-size: 14px;
    height: 44px;
    color: #1E293B;
  }

  :deep(.el-form-item) {
    margin-bottom: 20px;
  }
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.login-btn {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 12px;
  background: #2563EB;
  border: none;
  letter-spacing: -0.01em;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: #1D4ED8;
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(37, 99, 235, 0.35);
  }

  &:active {
    transform: translateY(0);
    box-shadow: 0 2px 8px rgba(37, 99, 235, 0.25);
  }
}

.login-form-footer {
  margin-top: 48px;
  text-align: center;

  p {
    font-size: 12px;
    color: #CBD5E1;
  }
}

@media (max-width: 1024px) {
  .login-left { display: none; }
  .login-right { width: 100%; }
}
</style>
